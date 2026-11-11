package com.kmod.atrocities.item;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Reliquias de mutante com habilidade no botao direito:
 * <ul>
 *   <li>{@link Kind#DETONATOR_CORE}: explosao ao redor de quem usa, sem ferir o dono nem quebrar blocos.</li>
 *   <li>{@link Kind#WEAVER_GLAND}: dispara uma bolota de teia que prende o alvo.</li>
 *   <li>{@link Kind#ETHEREAL_MEMBRANE}: impulso para frente e para cima, com queda lenta.</li>
 * </ul>
 */
public class MutantRelicItem extends Item {

    public enum Kind {
        DETONATOR_CORE(160, 24f),
        WEAVER_GLAND(40, 8f),
        ETHEREAL_MEMBRANE(100, 0f);

        final int cooldown;
        final float damage;

        Kind(int cooldown, float damage) {
            this.cooldown = cooldown;
            this.damage = damage;
        }
    }

    public static final double CORE_RADIUS = 4.5;

    private final Kind kind;

    public MutantRelicItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            switch (kind) {
                case DETONATOR_CORE -> detonate(server, player);
                case WEAVER_GLAND -> web(server, player, stack);
                case ETHEREAL_MEMBRANE -> glide(server, player);
            }
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        if (kind == Kind.ETHEREAL_MEMBRANE) {
            // O impulso tambem roda no cliente para o movimento sair sem atraso.
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(look.x * 1.6, Math.max(0.6, look.y * 1.2 + 0.4), look.z * 1.6);
            player.fallDistance = 0;
        }
        player.getCooldowns().addCooldown(this, kind.cooldown);
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void detonate(ServerLevel level, Player player) {
        Vec3 center = player.position().add(0, 0.8, 0);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CORE_RADIUS),
                e -> e != player && e.isAlive() && !CarapaceMaulItem.isAlly(player, e)
                        && e.distanceToSqr(player) <= CORE_RADIUS * CORE_RADIUS)) {
            if (victim.hurt(player.damageSources().explosion(player, player), kind.damage)) {
                Vec3 push = victim.position().subtract(player.position()).multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(1.6);
                victim.push(push.x, victim instanceof AtrocityBoss ? 0 : 0.6, push.z);
                victim.hurtMarked = true;
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        GeneticsFx.burst(level, GeneticsFx.dust(0x6BFF3A, 2.2f), center, 50, 1.6, 0.2);
        GeneticsFx.sound(level, center, ModSounds.MUTANT_BLAST.get(), 1.6f, 1.1f);
        BossFx.shake(level, center, 16, 0.8f, 12);
    }

    private void web(ServerLevel level, Player player, ItemStack stack) {
        BioProjectile web = new BioProjectile(level, player, BioProjectile.Kind.WEB, kind.damage,
                new ItemStack(com.kmod.atrocities.registry.ModItems.WEB_GLOB.get()));
        web.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 1.8f, 0.5f);
        level.addFreshEntity(web);
        GeneticsFx.sound(level, player.position(), ModSounds.WEAVER_WEB.get(), 1f, 1.2f);
    }

    private void glide(ServerLevel level, Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, true));
        player.hurtMarked = true;
        GeneticsFx.burst(level, ParticleTypes.CLOUD, player.position(), 16, 0.6, 0.05);
        GeneticsFx.burst(level, GeneticsFx.dust(0x5AF0FF, 1.4f), player.position().add(0, 0.8, 0), 20, 0.6, 0.05);
        GeneticsFx.sound(level, player.position(), ModSounds.RAY_GUST.get(), 1f, 1.3f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = "tooltip.atrocities." + switch (kind) {
            case DETONATOR_CORE -> "detonator_core";
            case WEAVER_GLAND -> "weaver_gland";
            case ETHEREAL_MEMBRANE -> "ethereal_membrane";
        };
        tooltip.add(Component.translatable(key, (int) kind.damage, kind.cooldown / 20).withStyle(ChatFormatting.GRAY));
    }
}
