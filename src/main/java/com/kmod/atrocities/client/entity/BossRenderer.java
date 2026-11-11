package com.kmod.atrocities.client.entity;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.client.codex.BossViewer;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

import java.util.Map;

/**
 * Renderizador GeckoLib dos chefes e mutantes. Usa {@code geo/entity/<nome>.geo.json},
 * {@code animations/entity/<nome>.animation.json}, {@code textures/entity/<nome>.png} e a mascara
 * {@code <nome>_glowmask.png}.
 * <p>
 * Camadas por cima das animacoes:
 * <ul>
 *   <li><b>Movimento secundario procedural</b>: tecidos respiram (peito, abdomen, bolsas, nucleos), cabecas e
 *   mandibulas tem espasmos organicos, e tudo acelera nas fases mais altas.</li>
 *   <li><b>Emissivo vivo</b>: a mascara pulsa como um coracao, acende durante a carga de um golpe e pisca quando
 *   o chefe esta atordoado; um segundo passe aditivo faz o brilho "estourar" no bloom.</li>
 *   <li><b>Peso</b>: o corpo amassa ao levar dano e treme/incha enquanto carrega um golpe.</li>
 * </ul>
 */
public class BossRenderer<T extends AtrocityBoss> extends GeoEntityRenderer<T> {

    /** Amplitude de respiracao por osso (escala ao redor do pivo). */
    private static final Map<String, Float> BREATHE = Map.of(
            "chest", 0.03f, "torso", 0.03f, "abdomen", 0.045f, "sacs", 0.07f, "mass", 0.035f,
            "pods", 0.06f, "core", 0.08f, "robe", 0.015f);

    /** Ossos com espasmos (rotacao curta e brusca). */
    private static final Map<String, Float> TWITCH = Map.of(
            "head", 0.09f, "jaw", 0.16f, "mandible_left", 0.22f, "mandible_right", 0.22f,
            "fang_left", 0.2f, "fang_right", 0.2f, "head_b", 0.12f, "head_c", 0.12f, "mouth", 0.1f, "face", 0.05f);

    /** Modelos sem peito/torso respiram pelo corpo inteiro. */
    private static final Map<String, Float> BODY_BREATHE = Map.of(
            "chitinous_colossus", 0.018f, "phantom_ray", 0.025f, "devourer_slime", 0.04f, "weaver_spider", 0.012f);

    private final float bodyBreathe;

    public BossRenderer(EntityRendererProvider.Context context, String name, float shadowRadius) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, name), true));
        this.bodyBreathe = BODY_BREATHE.getOrDefault(name, 0f);
        addRenderLayer(new LivingGlowLayer());
        this.shadowRadius = shadowRadius;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight) {
        poseStack.pushPose();
        if (entity.hurtTime > 0 && !entity.isDeadOrDying()) {
            float t = Mth.clamp((entity.hurtTime - partialTick) / Math.max(1, entity.hurtDuration), 0f, 1f);
            // Impacto: amassa rapido e volta com um pequeno rebote elastico.
            float squash = Mth.sin(t * Mth.PI) * (entity.isStunned() ? 0.12f : 0.075f)
                    - Mth.sin(t * Mth.TWO_PI) * 0.02f;
            poseStack.scale(1f + squash, 1f - squash * 0.85f, 1f + squash);
        }
        float charge = entity.charge();
        if (charge > 0f) {
            float time = entity.tickCount + partialTick;
            float amplitude = 0.015f + 0.07f * charge * charge;
            poseStack.translate(Mth.sin(time * 3.3f) * amplitude, 0, Mth.cos(time * 2.9f) * amplitude);
            // Antecipacao: encolhe um pouco antes de inchar no fim da carga.
            float swell = 1f + 0.06f * charge - 0.04f * Mth.sin(charge * Mth.PI);
            poseStack.scale(swell, 1f + 0.035f * charge, swell);
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ movimento secundario

    private float time(T animatable, float partialTick) {
        return animatable.tickCount + partialTick + (animatable.getId() % 97) * 3.7f;
    }

    /** Ritmo cresce com a fase e com a carga: o chefe "ofega" quando esta furioso. */
    private float tempo(T animatable) {
        return 1f + 0.35f * (animatable.phase() - 1) + 1.2f * animatable.charge();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, int colour) {
        String name = bone.getName();
        float breathe = BREATHE.getOrDefault(name, 0f);
        if (breathe == 0f && "body".equals(name)) {
            breathe = bodyBreathe;
        }
        float twitch = TWITCH.getOrDefault(name, 0f);
        if ((breathe == 0f && twitch == 0f) || animatable.isDeadOrDying() || BossViewer.silhouette) {
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                    partialTick, packedLight, packedOverlay, colour);
            return;
        }
        float time = time(animatable, partialTick);
        float tempo = tempo(animatable);
        float px = (bone.getPivotX() - bone.getPosX()) / 16f;
        float py = (bone.getPivotY() + bone.getPosY()) / 16f;
        float pz = (bone.getPivotZ() + bone.getPosZ()) / 16f;
        poseStack.pushPose();
        poseStack.translate(px, py, pz);
        if (breathe != 0f) {
            // Respiracao assimetrica: inspira devagar, expira rapido.
            float phase = (time * 0.055f * tempo) % 1f;
            float wave = phase < 0.6f ? phase / 0.6f : 1f - (phase - 0.6f) / 0.4f;
            wave = wave * wave * (3 - 2 * wave);
            float swell = 1f + breathe * wave;
            poseStack.scale(swell, 1f + breathe * 0.6f * wave, swell);
        }
        if (twitch != 0f) {
            // Espasmo: picos raros e curtos, mais frequentes com a carga.
            float seed = name.hashCode() * 0.013f;
            float spike = (float) Math.pow(Math.max(0f, Mth.sin(time * 0.11f * tempo + seed)), 30);
            float jitter = Mth.sin(time * 2.6f + seed) * spike + animatable.charge() * Mth.sin(time * 4.1f + seed) * 0.4f;
            float angle = twitch * jitter;
            poseStack.mulPose(Axis.XP.rotation(angle));
            poseStack.mulPose(Axis.ZP.rotation(angle * 0.6f * Mth.sin(seed * 5f)));
        }
        poseStack.translate(-px, -py, -pz);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
        poseStack.popPose();
    }

    // ------------------------------------------------------------------ emissivo vivo

    /** Batimento: duas batidas curtas seguidas de pausa (lub-dub). */
    static float heartbeat(float time, float tempo) {
        float phase = (time * 0.045f * tempo) % 1f;
        float first = (float) Math.exp(-Math.pow((phase - 0.08f) / 0.04f, 2));
        float second = (float) Math.exp(-Math.pow((phase - 0.24f) / 0.05f, 2)) * 0.7f;
        return Math.min(1f, first + second);
    }

    private final class LivingGlowLayer extends GeoRenderLayer<T> {

        LivingGlowLayer() {
            super(BossRenderer.this);
        }

        @Override
        public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay) {
            if (BossViewer.silhouette || animatable.isInvisible()) {
                return;
            }
            ResourceLocation texture = getTextureResource(animatable);
            RenderType glow = AutoGlowingTexture.getRenderType(texture);
            float time = time(animatable, partialTick);
            float beat = heartbeat(time, tempo(animatable));
            float charge = animatable.charge();
            float intensity = 0.72f + 0.28f * beat;
            intensity = Mth.lerp(charge, intensity, 0.85f + 0.15f * Mth.sin(time * 1.9f));
            if (animatable.isStunned()) {
                // Atordoado: o brilho falha, sinal de que esta vulneravel.
                intensity *= 0.35f + 0.65f * (Mth.sin(time * 1.3f) > 0.2f ? 1f : 0.2f);
            }
            if (animatable.isDeadOrDying()) {
                intensity *= Mth.clamp(1f - animatable.deathTime / 60f, 0f, 1f) * (Mth.sin(time * 2.2f) > -0.3f ? 1f : 0.3f);
            }
            int alpha = Mth.clamp(Math.round(255 * intensity), 0, 255);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, alpha << 24 | 0xFFFFFF);

            // Passe aditivo: o brilho estoura nas batidas e na carga (alimenta o bloom do pos-processamento).
            float boost = 0.12f + 0.3f * beat + 0.55f * charge;
            if (animatable.isStunned() || animatable.isDeadOrDying()) {
                boost *= 0.4f;
            }
            int add = Mth.clamp(Math.round(255 * boost), 0, 255);
            if (add > 4) {
                RenderType additive = RenderType.eyes(AutoGlowingTexture.getEmissiveResource(texture));
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, additive,
                        bufferSource.getBuffer(additive), partialTick, LightTexture.FULL_BRIGHT, packedOverlay,
                        0xFF000000 | add << 16 | add << 8 | add);
            }
        }
    }

    /** Silhueta negra no Codex enquanto a ficha do chefe e secreta. */
    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        return BossViewer.silhouette ? SILHOUETTE : super.getRenderColor(animatable, partialTick, packedLight);
    }

    private static final Color SILHOUETTE = Color.ofARGB(255, 14, 8, 10);

    /** A animacao de morte cuida da queda; sem o tombo lateral vanilla. */
    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0f;
    }

    /** Pisca vermelho so ao levar dano, nao durante toda a animacao de morte. */
    @Override
    public int getPackedOverlay(T animatable, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(animatable.hurtTime > 0));
    }
}
