package com.kmod.atrocities.item;

import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Lanca-Bile (trofeu da Legiao Coalescida): dispara Bolotas de Bile que estouram em nuvem toxica.
 * Gasta uma Bolota de Bile do inventario por disparo (gratis no criativo).
 */
public class BileLauncherItem extends Item {

    public static final float DAMAGE = 16f;
    public static final int COOLDOWN = 30;

    public BileLauncherItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack launcher = player.getItemInHand(hand);
        ItemStack ammo = findAmmo(player);
        if (ammo.isEmpty() && !player.getAbilities().instabuild) {
            if (!level.isClientSide) {
                GeneticsFx.reject(player, this, Component.translatable("message.atrocities.bile_launcher.no_ammo"));
            }
            return InteractionResultHolder.fail(launcher);
        }
        if (level instanceof ServerLevel server) {
            BioProjectile glob = new BioProjectile(server, player, BioProjectile.Kind.BILE, DAMAGE,
                    new ItemStack(ModItems.BILE_GLOB.get()));
            glob.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 1.9f, 0.8f);
            server.addFreshEntity(glob);
            Vec3 muzzle = player.getEyePosition().add(player.getLookAngle().scale(0.8));
            GeneticsFx.burst(server, GeneticsFx.dust(0x7FA02A, 1.3f), muzzle, 10, 0.12, 0.02);
            GeneticsFx.burst(server, ParticleTypes.ITEM_SLIME, muzzle, 4, 0.1, 0.05);
            GeneticsFx.sound(server, player.position(), ModSounds.LEGION_SPIT.get(), 1f, 1.1f);
            if (!player.getAbilities().instabuild) {
                ammo.shrink(1);
            }
            launcher.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(launcher, level.isClientSide);
    }

    private static ItemStack findAmmo(Player player) {
        if (player.getOffhandItem().is(ModItems.BILE_GLOB.get())) {
            return player.getOffhandItem();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.BILE_GLOB.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(ModItems.ABERRANT_HEART.get());
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 18;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.atrocities.bile_launcher", (int) DAMAGE).withStyle(ChatFormatting.GRAY));
    }
}
