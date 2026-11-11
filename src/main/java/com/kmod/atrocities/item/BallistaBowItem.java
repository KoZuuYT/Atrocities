package com.kmod.atrocities.item;

import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Arco Balista (trofeu do Esqueleto Balista): flechas bem mais fortes e, com o arco totalmente puxado,
 * tres flechas em leque gastando so uma.
 */
public class BallistaBowItem extends BowItem {

    public static final double DAMAGE_MULTIPLIER = 2.2;
    public static final float VELOCITY = 3.3f;

    public BallistaBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack ammo = player.getProjectile(stack);
        boolean infinite = player.hasInfiniteMaterials();
        if (ammo.isEmpty() && !infinite) {
            return;
        }
        if (ammo.isEmpty()) {
            ammo = new ItemStack(Items.ARROW);
        }
        float power = getPowerForTime(getUseDuration(stack, entity) - timeLeft);
        if (power < 0.1f) {
            return;
        }
        boolean full = power >= 1f;
        if (level instanceof ServerLevel server) {
            ArrowItem arrowItem = ammo.getItem() instanceof ArrowItem item ? item : (ArrowItem) Items.ARROW;
            for (int i = -1; i <= 1; i++) {
                if (i != 0 && !full) {
                    continue;
                }
                AbstractArrow arrow = arrowItem.createArrow(server, ammo, player, stack);
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + i * 6f, 0f, power * VELOCITY, 1f);
                arrow.setBaseDamage(arrow.getBaseDamage() * DAMAGE_MULTIPLIER);
                arrow.setCritArrow(full);
                if (i != 0 || infinite) {
                    arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
                server.addFreshEntity(arrow);
            }
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    full ? ModSounds.BALLISTA_VOLLEY.get() : net.minecraft.sounds.SoundEvents.ARROW_SHOOT,
                    SoundSource.PLAYERS, 1f, 1f / (level.random.nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        }
        if (!infinite) {
            ammo.shrink(1);
            if (ammo.isEmpty()) {
                player.getInventory().removeItem(ammo);
            }
        }
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.atrocities.ballista_bow").withStyle(ChatFormatting.GRAY));
    }
}
