package com.kmod.atrocities.client.entity;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;
import com.kmod.atrocities.client.codex.BossViewer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/**
 * Renderizador GeckoLib dos chefes. Usa {@code geo/entity/<nome>.geo.json},
 * {@code animations/entity/<nome>.animation.json}, {@code textures/entity/<nome>.png} e a mascara
 * {@code <nome>_glowmask.png} para olhos, veias e bolsas que brilham no escuro.
 * <p>
 * Feedback dinamico por cima das animacoes: o corpo amassa ao levar dano e treme/incha enquanto carrega
 * um golpe (quanto mais perto do impacto, mais forte).
 */
public class BossRenderer<T extends AtrocityBoss> extends GeoEntityRenderer<T> {

    public BossRenderer(EntityRendererProvider.Context context, String name, float shadowRadius) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, name), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                               int packedLight, int packedOverlay) {
                if (!BossViewer.silhouette) {
                    super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick,
                            packedLight, packedOverlay);
                }
            }
        });
        this.shadowRadius = shadowRadius;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight) {
        poseStack.pushPose();
        if (entity.hurtTime > 0 && !entity.isDeadOrDying()) {
            float t = Mth.clamp((entity.hurtTime - partialTick) / Math.max(1, entity.hurtDuration), 0f, 1f);
            float squash = Mth.sin(t * Mth.PI) * (entity.isStunned() ? 0.11f : 0.07f);
            poseStack.scale(1f + squash, 1f - squash * 0.85f, 1f + squash);
        }
        float charge = entity.charge();
        if (charge > 0f) {
            float time = entity.tickCount + partialTick;
            float amplitude = 0.015f + 0.07f * charge * charge;
            poseStack.translate(Mth.sin(time * 3.3f) * amplitude, 0, Mth.cos(time * 2.9f) * amplitude);
            float swell = 1f + 0.05f * charge;
            poseStack.scale(swell, 1f + 0.03f * charge, swell);
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
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
