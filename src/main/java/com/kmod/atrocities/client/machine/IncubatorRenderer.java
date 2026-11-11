package com.kmod.atrocities.client.machine;

import com.kmod.atrocities.machine.BiologicalIncubatorBlockEntity;
import com.kmod.atrocities.machine.MachineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Embriao flutuando no fluido amniotico da Incubadora. Enquanto incuba ele gira, brilha e pulsa
 * no ritmo de um batimento duplo; parado, apenas deriva devagar.
 */
public class IncubatorRenderer implements BlockEntityRenderer<BiologicalIncubatorBlockEntity> {

    private static final float BEAT_PERIOD = 26f;

    private final ItemRenderer itemRenderer;

    public IncubatorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BiologicalIncubatorBlockEntity incubator, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        MachineGlowRenderer.draw(incubator, 0xFFA530, MachineGlowRenderer.Pattern.BUBBLES, partialTick, pose, buffers);
        ItemStack stack = incubator.displayStack();
        Level level = incubator.getLevel();
        if (stack.isEmpty() || level == null) {
            return;
        }
        boolean active = incubator.getBlockState().getValue(MachineBlock.LIT);
        float time = level.getGameTime() + partialTick;
        float beat = active ? heartbeat(time) : 0f;

        pose.pushPose();
        pose.translate(0.5, 0.52 + Mth.sin(time * 0.06f) * 0.035, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * (active ? 1.6f : 0.35f)));
        float scale = 0.6f + beat * 0.08f;
        pose.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, active ? LightTexture.FULL_BRIGHT : packedLight,
                OverlayTexture.NO_OVERLAY, pose, buffers, level, (int) incubator.getBlockPos().asLong());
        pose.popPose();
    }

    /** Batimento "tum-tum": dois pulsos curtos por ciclo. */
    private static float heartbeat(float time) {
        float phase = (time % BEAT_PERIOD) / BEAT_PERIOD;
        float first = pulse(phase, 0f);
        float second = pulse(phase, 0.2f) * 0.7f;
        return Math.max(first, second);
    }

    private static float pulse(float phase, float at) {
        float d = phase - at;
        return d < 0 || d > 0.15f ? 0f : Mth.sin(d / 0.15f * Mth.PI);
    }
}
