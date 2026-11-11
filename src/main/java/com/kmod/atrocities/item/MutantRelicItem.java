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
 *   <li>{@link Kind#RIFT_EYE}: teleporta ate onde o jogador olha, com um estalo de dano na chegada.</li>
 *   <li>{@link Kind#ELASTIC_GEL}: salto elastico; o pouso nao causa dano de queda e vira uma pancada em area.</li>
 *   <li>{@link Kind#POCKET_CAULDRON}: elixir com dois efeitos bons aleatorios.</li>
 * </ul>
 */
public class MutantRelicItem extends Item {

    public enum Kind {
        DETONATOR_CORE(160, 24f),
        WEAVER_GLAND(40, 8f),
        ETHEREAL_MEMBRANE(100, 0f),
        RIFT_EYE(100, 12f),
        ELASTIC_GEL(80, 18f),
        POCKET_CAULDRON(600, 0f);

        final int cooldown;
        final float damage;

        Kind(int cooldown, float damage) {
            this.cooldown = cooldown;
            this.damage = damage;
        }
    }

    public static final double CORE_RADIUS = 4.5;
    public static final double RIFT_RANGE = 24.0;
    public static final double GEL_RADIUS = 3.5;
    private static final String GEL_UNTIL = "atrocities.elastic_gel";

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
                case RIFT_EYE -> rift(server, player);
                case ELASTIC_GEL -> {
                    player.getPersistentData().putLong(GEL_UNTIL, server.getGameTime() + 160);
                    GeneticsFx.burst(server, net.minecraft.core.particles.ParticleTypes.ITEM_SLIME, player.position(), 30, 0.5, 0.2);
                    GeneticsFx.sound(server, player.position(), ModSounds.SLIME_SQUISH.get(), 1.2f, 1.3f);
                }
                case POCKET_CAULDRON -> brew(server, player);
            }
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        if (kind == Kind.ETHEREAL_MEMBRANE) {
            // O impulso tambem roda no cliente para o movimento sair sem atraso.
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(look.x * 1.6, Math.max(0.6, look.y * 1.2 + 0.4), look.z * 1.6);
            player.fallDistance = 0;
        }
        if (kind == Kind.ELASTIC_GEL) {
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(look.x * 0.9, 1.25, look.z * 0.9);
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

    private void rift(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.HitResult hit = player.pick(RIFT_RANGE, 1f, false);
        Vec3 dest = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? eye.add(look.scale(RIFT_RANGE))
                : hit.getLocation().subtract(look.scale(0.8));
        Vec3 from = player.position();
        if (!player.randomTeleport(dest.x, dest.y, dest.z, true)
                && !player.randomTeleport(dest.x, dest.y - 1.5, dest.z, true)) {
            GeneticsFx.sound(level, from, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.5f);
            return;
        }
        player.fallDistance = 0;
        GeneticsFx.burst(level, net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, from.add(0, 1, 0), 40, 0.4, 0.2);
        GeneticsFx.burst(level, net.minecraft.core.particles.ParticleTypes.PORTAL, player.position().add(0, 1, 0), 60, 0.5, 0.6);
        GeneticsFx.burst(level, GeneticsFx.dust(0xFF4FD8, 1.8f), player.position().add(0, 1, 0), 24, 0.6, 0.1);
        GeneticsFx.sound(level, player.position(), ModSounds.RIFT_BLINK.get(), 1.2f, 1.2f);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(2.5),
                e -> e != player && e.isAlive() && !CarapaceMaulItem.isAlly(player, e))) {
            victim.hurt(player.damageSources().playerAttack(player), kind.damage);
        }
    }

    private void brew(ServerLevel level, Player player) {
        List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> pool = new java.util.ArrayList<>(List.of(
                MobEffects.DAMAGE_BOOST, MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED,
                MobEffects.DAMAGE_RESISTANCE, MobEffects.ABSORPTION, MobEffects.FIRE_RESISTANCE));
        java.util.Collections.shuffle(pool, new java.util.Random(level.random.nextLong()));
        for (int i = 0; i < 2; i++) {
            var effect = pool.get(i);
            int amplifier = effect == MobEffects.MOVEMENT_SPEED || effect == MobEffects.ABSORPTION ? 1 : 0;
            player.addEffect(new MobEffectInstance(effect, 240, amplifier));
        }
        GeneticsFx.burst(level, net.minecraft.core.particles.ParticleTypes.WITCH, player.position().add(0, 1.2, 0), 30, 0.5, 0.1);
        GeneticsFx.burst(level, GeneticsFx.dust(0xFFA530, 1.4f), player.position().add(0, 1.0, 0), 20, 0.5, 0.1);
        GeneticsFx.sound(level, player.position(), ModSounds.WITCH_DRINK.get(), 1.2f, 1.2f);
    }

    /** Pouso com o Gel Elastico: sem dano de queda e com uma pancada em volta. */
    public static void registerEvents() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.entity.living.LivingFallEvent.class, event -> {
                    if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level)) {
                        return;
                    }
                    long until = player.getPersistentData().getLong(GEL_UNTIL);
                    if (until <= 0 || until < level.getGameTime()) {
                        return;
                    }
                    player.getPersistentData().remove(GEL_UNTIL);
                    event.setDamageMultiplier(0f);
                    if (event.getDistance() < 3f) {
                        return;
                    }
                    for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(GEL_RADIUS), e -> e != player && e.isAlive()
                                    && !CarapaceMaulItem.isAlly(player, e) && e.distanceToSqr(player) <= GEL_RADIUS * GEL_RADIUS)) {
                        if (victim.hurt(player.damageSources().playerAttack(player), Kind.ELASTIC_GEL.damage)) {
                            Vec3 push = victim.position().subtract(player.position()).multiply(1, 0, 1);
                            push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(1.2);
                            victim.push(push.x, victim instanceof AtrocityBoss ? 0 : 0.5, push.z);
                            victim.hurtMarked = true;
                        }
                    }
                    GeneticsFx.burst(level, net.minecraft.core.particles.ParticleTypes.ITEM_SLIME, player.position(), 60, 1.5, 0.3);
                    GeneticsFx.burst(level, GeneticsFx.dust(0x2EE6A8, 2f), player.position().add(0, 0.2, 0), 30, 1.5, 0.05);
                    GeneticsFx.sound(level, player.position(), ModSounds.SLIME_SQUISH.get(), 1.6f, 0.6f);
                    BossFx.shake(level, player.position(), 12, 0.6f, 10);
                });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = "tooltip.atrocities." + switch (kind) {
            case DETONATOR_CORE -> "detonator_core";
            case WEAVER_GLAND -> "weaver_gland";
            case ETHEREAL_MEMBRANE -> "ethereal_membrane";
            case RIFT_EYE -> "rift_eye";
            case ELASTIC_GEL -> "elastic_gel";
            case POCKET_CAULDRON -> "pocket_cauldron";
        };
        tooltip.add(Component.translatable(key, (int) kind.damage, kind.cooldown / 20).withStyle(ChatFormatting.GRAY));
    }
}
