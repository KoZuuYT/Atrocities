package com.kmod.atrocities.client.machine;

import com.kmod.atrocities.client.vfx.VfxRenderTypes;
import com.kmod.atrocities.machine.MachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Luzes biologicas das maquinas ligadas: a frente brilha e pulsa, e cada maquina tem uma animacao propria
 * desenhada por cima (bolhas no extrator, rotor na centrifuga, leitura de DNA no sequenciador, orbitas no
 * terminal, bolhas no incubador). So aparece com {@code lit=true}.
 */
public class MachineGlowRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    /** Animacao da frente. */
    public enum Pattern { BUBBLES, ROTOR, READOUT, ORBIT }

    private final int color;
    private final Pattern pattern;

    public MachineGlowRenderer(BlockEntityRendererProvider.Context context, int color, Pattern pattern) {
        this.color = color;
        this.pattern = pattern;
    }

    @Override
    public void render(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        draw(entity, color, pattern, partialTick, pose, buffers);
    }

    public static void draw(BlockEntity entity, int color, Pattern pattern, float partialTick, PoseStack pose,
                            MultiBufferSource buffers) {
        BlockState state = entity.getBlockState();
        if (!state.hasProperty(MachineBlock.LIT) || !state.getValue(MachineBlock.LIT)
                || !state.hasProperty(MachineBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(MachineBlock.FACING);
        float time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() % 24000)
                + partialTick + entity.getBlockPos().hashCode() % 50;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        Matrix4f m = pose.last().pose();
        VertexConsumer out = buffers.getBuffer(VfxRenderTypes.GLOW);
        float z = 0.503f;
        float beat = (float) Math.pow(Math.max(0f, Mth.sin(time * 0.12f)), 6);
        float pulse = 0.55f + 0.2f * Mth.sin(time * 0.07f) + 0.25f * beat;

        // Brilho da frente: centro forte, bordas suaves (anel de quads).
        glowRect(out, m, 0f, 0f, 0.36f, 0.36f, z, color, 0.32f * pulse);
        // Faixa de luz no topo, como um respiro.
        quad(out, m, -0.4f, 0.47f, 0.4f, 0.5f, z, color, 0.55f * pulse, 0f);

        switch (pattern) {
            case BUBBLES -> {
                for (int i = 0; i < 7; i++) {
                    float seed = i * 1.618f;
                    float rise = ((time * (0.012f + i % 3 * 0.004f)) + seed) % 1f;
                    float x = -0.28f + ((seed * 0.37f) % 0.56f) + Mth.sin(time * 0.1f + i) * 0.02f;
                    float y = -0.34f + rise * 0.68f;
                    float size = 0.018f + (i % 3) * 0.008f;
                    float a = Mth.sin(rise * Mth.PI) * 0.9f;
                    quad(out, m, x - size, y - size, x + size, y + size, z + 0.001f, mix(color, 0xFFFFFF, 0.5f), a, a);
                }
            }
            case ROTOR -> {
                float angle = time * 0.35f;
                for (int i = 0; i < 3; i++) {
                    float a = angle + i * Mth.TWO_PI / 3;
                    blade(out, m, a, 0.06f, 0.3f, 0.045f, z + 0.001f, color, 0.8f);
                }
                glowRect(out, m, 0f, 0f, 0.07f, 0.07f, z + 0.002f, mix(color, 0xFFFFFF, 0.6f), 0.9f);
            }
            case READOUT -> {
                for (int i = 0; i < 6; i++) {
                    float h = 0.08f + 0.22f * (0.5f + 0.5f * Mth.sin(time * 0.21f + i * 1.7f));
                    float x0 = -0.27f + i * 0.09f;
                    quad(out, m, x0, -0.3f, x0 + 0.05f, -0.3f + h, z + 0.001f, i % 2 == 0 ? color : 0x4AD8FF, 0.75f, 0.2f);
                }
                float scan = -0.32f + ((time * 0.02f) % 1f) * 0.64f;
                quad(out, m, -0.34f, scan, 0.34f, scan + 0.02f, z + 0.002f, 0xFFFFFF, 0.6f, 0.6f);
            }
            case ORBIT -> {
                for (int i = 0; i < 4; i++) {
                    float a = time * 0.08f + i * Mth.HALF_PI;
                    float x = Mth.cos(a) * 0.24f;
                    float y = Mth.sin(a) * 0.24f;
                    glowRect(out, m, x, y, 0.05f, 0.05f, z + 0.001f, mix(color, 0xFFFFFF, 0.4f), 0.9f);
                }
            }
        }
        pose.popPose();
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, int rgb, float alpha) {
        out.addVertex(m, x, y, z).setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f,
                Mth.clamp(alpha, 0f, 1f));
    }

    /** Retangulo com alfa de baixo ({@code a0}) para cima ({@code a1}). */
    private static void quad(VertexConsumer out, Matrix4f m, float x0, float y0, float x1, float y1, float z, int rgb,
                             float a0, float a1) {
        vertex(out, m, x0, y0, z, rgb, a0);
        vertex(out, m, x1, y0, z, rgb, a0);
        vertex(out, m, x1, y1, z, rgb, a1);
        vertex(out, m, x0, y1, z, rgb, a1);
    }

    /** Brilho retangular: nucleo pleno e moldura que some para fora. */
    private static void glowRect(VertexConsumer out, Matrix4f m, float cx, float cy, float hw, float hh, float z, int rgb,
                                 float alpha) {
        float ix = hw * 0.45f;
        float iy = hh * 0.45f;
        quad(out, m, cx - ix, cy - iy, cx + ix, cy + iy, z, rgb, alpha, alpha);
        float[][] ring = {{-ix, -iy, -hw, -hh}, {ix, -iy, hw, -hh}, {ix, iy, hw, hh}, {-ix, iy, -hw, hh}};
        for (int i = 0; i < 4; i++) {
            float[] a = ring[i];
            float[] b = ring[(i + 1) % 4];
            vertex(out, m, cx + a[0], cy + a[1], z, rgb, alpha);
            vertex(out, m, cx + a[2], cy + a[3], z, rgb, 0f);
            vertex(out, m, cx + b[2], cy + b[3], z, rgb, 0f);
            vertex(out, m, cx + b[0], cy + b[1], z, rgb, alpha);
        }
    }

    private static void blade(VertexConsumer out, Matrix4f m, float angle, float r0, float r1, float width, float z,
                              int rgb, float alpha) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        float px = -s * width;
        float py = c * width;
        vertex(out, m, c * r0 + px, s * r0 + py, z, rgb, alpha);
        vertex(out, m, c * r1 + px * 0.4f, s * r1 + py * 0.4f, z, rgb, 0.1f);
        vertex(out, m, c * r1 - px * 0.4f, s * r1 - py * 0.4f, z, rgb, 0.1f);
        vertex(out, m, c * r0 - px, s * r0 - py, z, rgb, alpha);
    }
}
