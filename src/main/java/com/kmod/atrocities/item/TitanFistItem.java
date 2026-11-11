package com.kmod.atrocities.item;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Punho do Tita (trofeu do Tita Zumbi): manopla pesada.
 * Botao direito: Rasgo Sismico, uma fileira de terra irrompe a frente e lanca os inimigos para cima.
 */
public class TitanFistItem extends SwordItem {

    public static final float RIFT_DAMAGE = 24f;
    public static final int RIFT_LENGTH = 10;
    public static final int COOLDOWN = 120;

    public TitanFistItem(Properties properties) {
        super(BioforgedTier.BIOFORGED, properties.attributes(createAttributes(BioforgedTier.BIOFORGED, 41f, -3.0f)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.onGround()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel server) {
            rift(server, player);
            stack.hurtAndBreak(2, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void rift(ServerLevel level, Player player) {
        Vec3 dir = Vec3.directionFromRotation(0, player.getYRot());
        Set<LivingEntity> hit = new HashSet<>();
        for (int i = 1; i <= RIFT_LENGTH; i++) {
            Vec3 point = player.position().add(dir.scale(i));
            BlockPos below = BlockPos.containing(point).below();
            BlockState state = level.getBlockState(below);
            if (!state.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), point.x, point.y + 0.3, point.z,
                        14, 0.3, 0.4, 0.3, 0.25);
            }
            level.sendParticles(GeneticsFx.dust(0x7CD64B, 1.3f), point.x, point.y + 0.5, point.z, 4, 0.3, 0.4, 0.3, 0.02);
            level.sendParticles(ParticleTypes.CLOUD, point.x, point.y + 0.2, point.z, 2, 0.2, 0.05, 0.2, 0.02);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(1.3, 1.5, 1.3),
                    e -> e != player && e.isAlive() && !CarapaceMaulItem.isAlly(player, e))) {
                if (hit.add(victim) && victim.hurt(player.damageSources().playerAttack(player), RIFT_DAMAGE)) {
                    double lift = victim instanceof AtrocityBoss ? 0 : 0.9;
                    victim.push(dir.x * 0.3, lift, dir.z * 0.3);
                    victim.hurtMarked = true;
                }
            }
        }
        GeneticsFx.sound(level, player.position(), ModSounds.TITAN_ERUPT.get(), 1.4f, 0.9f);
        GeneticsFx.sound(level, player.position(), ModSounds.BOSS_SLAM.get(), 1.0f, 1.1f);
        BossFx.shake(level, player.position(), 12, 0.5f, 8);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.atrocities.titan_fist", (int) RIFT_DAMAGE, COOLDOWN / 20)
                .withStyle(ChatFormatting.GRAY));
    }
}
