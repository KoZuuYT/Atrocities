package com.kmod.atrocities.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Tipos de render dos efeitos: energia aditiva sem textura, sem escrever profundidade. */
public final class VfxRenderTypes {

    /** Brilho aditivo que respeita a profundidade do mundo (fica atras de paredes). */
    public static final RenderType GLOW = RenderType.create("atrocities_vfx_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .createCompositeState(false));

    private VfxRenderTypes() {
    }
}
