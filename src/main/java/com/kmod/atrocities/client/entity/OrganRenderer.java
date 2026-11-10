package com.kmod.atrocities.client.entity;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.entity.boss.BossOrganEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderizador dos orgaos de chefe. Cada variante tem modelo e textura proprios
 * ({@code organ_<variante>}); a animacao de pulsacao e compartilhada.
 */
public class OrganRenderer extends GeoEntityRenderer<BossOrganEntity> {

    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "animations/entity/boss_organ.animation.json");

    public OrganRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(BossOrganEntity organ) {
                return ResourceLocation.fromNamespaceAndPath(Atrocities.MODID,
                        "geo/entity/organ_" + organ.variant().key() + ".geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(BossOrganEntity organ) {
                return ResourceLocation.fromNamespaceAndPath(Atrocities.MODID,
                        "textures/entity/organ_" + organ.variant().key() + ".png");
            }

            @Override
            public ResourceLocation getAnimationResource(BossOrganEntity organ) {
                return ANIMATION;
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.8f;
    }

    @Override
    public void render(BossOrganEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        // Surge do chao nos primeiros instantes.
        float grow = Mth.clamp((entity.tickCount + partialTick) / 12f, 0f, 1f);
        float eased = 1f - (1f - grow) * (1f - grow);
        poseStack.scale(0.6f + 0.4f * eased, eased, 0.6f + 0.4f * eased);
        if (entity.hurtTime > 0) {
            float t = (entity.hurtTime - partialTick) / Math.max(1, entity.hurtDuration);
            float squash = Mth.sin(Mth.clamp(t, 0f, 1f) * Mth.PI) * 0.12f;
            poseStack.scale(1f + squash, 1f - squash, 1f + squash);
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    protected float getDeathMaxRotation(BossOrganEntity animatable) {
        return 0f;
    }

    @Override
    public int getPackedOverlay(BossOrganEntity animatable, float u, float partialTick) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(animatable.hurtTime > 0));
    }
}
