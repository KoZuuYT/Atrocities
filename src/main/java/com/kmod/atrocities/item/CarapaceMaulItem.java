package com.kmod.atrocities.item;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Marreta de Carapaca (trofeu do Colosso Quitinoso): golpe pesado e lento.
 * Botao direito: Impacto Sismico, onda de choque em area com recarga.
 */
public class CarapaceMaulItem extends SwordItem {

    public static final float SLAM_DAMAGE = 30f;
    public static final double SLAM_RADIUS = 4.5;
    public static final int SLAM_COOLDOWN = 160;

    public CarapaceMaulItem(Properties properties) {
        super(BioforgedTier.BIOFORGED, properties.attributes(createAttributes(BioforgedTier.BIOFORGED, 47f, -3.3f)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.onGround()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel server) {
            slam(server, player);
            stack.hurtAndBreak(3, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        player.getCooldowns().addCooldown(this, SLAM_COOLDOWN);
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void slam(ServerLevel level, Player player) {
        Vec3 center = player.position();
        BlockState ground = level.getBlockState(player.blockPosition().below());
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SLAM_RADIUS, 1.5, SLAM_RADIUS),
                entity -> entity != player && entity.isAlive() && !isAlly(player, entity)
                        && entity.distanceToSqr(player) <= SLAM_RADIUS * SLAM_RADIUS);
        for (LivingEntity victim : victims) {
            if (victim.hurt(player.damageSources().playerAttack(player), SLAM_DAMAGE)) {
                Vec3 push = victim.position().subtract(center).multiply(1, 0, 1).normalize().scale(0.9);
                double lift = victim instanceof AtrocityBoss ? 0 : 0.55;
                victim.push(push.x, lift, push.z);
                victim.hurtMarked = true;
            }
        }
        // anel de detritos que se expande
        for (int ring = 1; ring <= 3; ring++) {
            double radius = SLAM_RADIUS * ring / 3.0;
            int points = 14 * ring;
            for (int i = 0; i < points; i++) {
                double angle = Mth.TWO_PI * i / points;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                if (!ground.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, center.y + 0.1, z,
                            4, 0.1, 0.05, 0.1, 0.15);
                }
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, center.y + 0.1, z, 1, 0, 0.02, 0, 0.01);
            }
        }
        GeneticsFx.burst(level, ParticleTypes.EXPLOSION, center.add(0, 0.3, 0), 3, 0.6, 0);
        GeneticsFx.burst(level, GeneticsFx.dust(0x8A7350, 2.2f), center.add(0, 0.4, 0), 40, 1.6, 0.05);
        GeneticsFx.sound(level, center, ModSounds.BOSS_SLAM.get(), 1.6f, 0.9f);
        GeneticsFx.sound(level, center, ModSounds.BOSS_SHOCKWAVE.get(), 0.7f, 1.3f);
    }

    /** Nao acerta os proprios bichos domesticados nem montarias do jogador. */
    static boolean isAlly(Player player, LivingEntity entity) {
        if (entity instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID())) {
            return true;
        }
        return entity.isPassengerOfSameVehicle(player) || player.isAlliedTo(entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.atrocities.carapace_maul", (int) SLAM_DAMAGE, SLAM_COOLDOWN / 20)
                .withStyle(ChatFormatting.GRAY));
    }
}
