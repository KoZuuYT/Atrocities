package com.kmod.atrocities.client.entity;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renderizador GeckoLib dos chefes. Usa {@code geo/entity/<nome>.geo.json},
 * {@code animations/entity/<nome>.animation.json}, {@code textures/entity/<nome>.png} e a mascara
 * {@code <nome>_glowmask.png} para olhos, veias e bolsas que brilham no escuro.
 */
public class BossRenderer<T extends AtrocityBoss> extends GeoEntityRenderer<T> {

    public BossRenderer(EntityRendererProvider.Context context, String name, float shadowRadius) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, name), true));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = shadowRadius;
    }

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
