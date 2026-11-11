package com.kmod.atrocities.entity.mutant;

import com.kmod.atrocities.entity.boss.BossAttack;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Bruxa Caldeirao: bruxa gigante e corcunda, com um caldeirao borbulhante grudado nas costas.
 * <ul>
 *   <li>Concha: golpe com a concha gigante, que enfraquece.</li>
 *   <li>Saraivada de Pocoes: arremessa pocoes de arremesso (5 na Furia).</li>
 *   <li>Nuvem Fervente: tres nuvens de veneno e lentidao em volta do alvo.</li>
 *   <li>Feitico: levita o alvo, que cai de mau jeito.</li>
 *   <li>Transbordar (Furia): o caldeirao ferve e chove pocao nos pontos marcados.</li>
 *   <li>Gole Restaurador: abaixo de 70% da vida, bebe um elixir que cura 15%. <b>Interrompa</b> com 8% da vida
 *   dela em dano e ela derruba o frasco, atordoada.</li>
 * </ul>
 */
public class CauldronWitchEntity extends MutantEntity {

    private static final BossAttack LADLE = new BossAttack("ladle", 8, 20, 30, 0f, 3.4f, 5, 1);
    private static final BossAttack POTIONS = new BossAttack("potions", 12, 26, 70, 3f, 22f, 5, 1);
    private static final BossAttack BREW_CLOUD = new BossAttack("brew_cloud", 14, 30, 160, 0f, 16f, 3, 1);
    private static final BossAttack HEX = new BossAttack("hex", 16, 26, 200, 4f, 24f, 3, 1);
    private static final BossAttack DRINK = new BossAttack("drink", 50, 62, 600, 0f, 40f, 4, 1);
    private static final BossAttack OVERFLOW = new BossAttack("overflow", 30, 64, 260, 0f, 24f, 3, 2);
    private static final List<BossAttack> ATTACKS = List.of(LADLE, POTIONS, BREW_CLOUD, HEX, DRINK, OVERFLOW);

    public static final int COLOR = 0xFFA530;
    private static final float INTERRUPT_FRACTION = 0.08f;
    private static final float HEAL_FRACTION = 0.15f;
    private static final float DRINK_BELOW = 0.7f;
    private static final int INTERRUPT_STUN = 60;
    private static final int OVERFLOW_DROPS = 10;

    private float drinkDamage;
    private final List<Vec3> overflowSpots = new ArrayList<>();

    public CauldronWitchEntity(EntityType<? extends CauldronWitchEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return mutantAttributes(0.27D, 2.0D, 0.6D);
    }

    @Override
    public EntityType<?> origin() {
        return EntityType.WITCH;
    }

    @Override
    protected double mutantHealth() {
        return 500;
    }

    @Override
    protected double mutantDamage() {
        return 12;
    }

    @Override
    protected int mutantColor() {
        return COLOR;
    }

    @Override
    protected MutantSounds sounds() {
        return ModSounds.CAULDRON_WITCH;
    }

    @Override
    protected List<BossAttack> attacks() {
        return ATTACKS;
    }

    @Override
    protected double preferredRange() {
        return 7.0;
    }

    @Override
    protected Telegraph telegraph(BossAttack attack) {
        return switch (attack.id()) {
            case "potions", "hex" -> Telegraph.LINE;
            case "ladle" -> Telegraph.AREA;
            default -> Telegraph.NONE;
        };
    }

    @Override
    protected double telegraphRadius(BossAttack attack) {
        return 3.4;
    }

    @Override
    protected boolean canStartAttack(BossAttack attack, LivingEntity target) {
        if (attack == DRINK) {
            return getHealth() < getMaxHealth() * DRINK_BELOW;
        }
        return attack == OVERFLOW || hasLineOfSight(target);
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "ladle" -> ladle(level, tick);
            case "potions" -> potions(level, tick, target);
            case "brew_cloud" -> brewCloud(level, tick, target);
            case "hex" -> hex(level, tick, target);
            case "drink" -> drink(level, tick);
            case "overflow" -> overflow(level, tick, target);
            default -> {
            }
        }
    }

    private void ladle(ServerLevel level, int tick) {
        if (tick == LADLE.windup()) {
            Vec3 at = front(1.2, 0.4);
            for (LivingEntity victim : strike(position(), 3.4 + getBbWidth() * 0.5, 1.0f, 1.4, 0.4,
                    target -> inFront(target, 70))) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
            }
            GeneticsFx.burst(level, ParticleTypes.SWEEP_ATTACK, at, 3, 0.8, 0);
            GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 1.4f), at, 14, 0.8, 0.05);
            playSound(ModSounds.BOSS_SWING.get(), 2f, 1.0f);
        }
    }

    private void potions(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick != POTIONS.windup() || target == null) {
            return;
        }
        int count = phase() >= 2 ? 5 : 3;
        Vec3 origin = position().add(0, getBbHeight() * 0.8, 0);
        for (int i = 0; i < count; i++) {
            Vec3 spot = i == 0 ? target.position()
                    : target.position().add((random.nextDouble() - 0.5) * 5, 0, (random.nextDouble() - 0.5) * 5);
            throwPotion(level, origin, spot, randomHarmful());
        }
        playSound(ModSounds.WITCH_THROW.get(), 2f, 0.8f);
    }

    private Holder<Potion> randomHarmful() {
        return switch (random.nextInt(4)) {
            case 0 -> Potions.HARMING;
            case 1 -> Potions.POISON;
            case 2 -> Potions.SLOWNESS;
            default -> Potions.WEAKNESS;
        };
    }

    private void throwPotion(ServerLevel level, Vec3 origin, Vec3 spot, Holder<Potion> potion) {
        ThrownPotion thrown = new ThrownPotion(level, this);
        thrown.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, potion));
        thrown.setPos(origin.x, origin.y, origin.z);
        Vec3 delta = spot.subtract(origin);
        double horizontal = delta.horizontalDistance();
        thrown.shoot(delta.x, delta.y + horizontal * 0.2, delta.z, 0.75f + (float) horizontal * 0.02f, 3f);
        level.addFreshEntity(thrown);
    }

    private void brewCloud(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick != BREW_CLOUD.windup() || target == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            Vec3 at = i == 0 ? target.position()
                    : target.position().add((random.nextDouble() - 0.5) * 7, 0, (random.nextDouble() - 0.5) * 7);
            AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
            cloud.setOwner(this);
            cloud.setRadius(2.8f);
            cloud.setRadiusOnUse(-0.1f);
            cloud.setWaitTime(10);
            cloud.setDuration(120);
            cloud.setRadiusPerTick(-2.8f / 180f);
            cloud.setParticle(GeneticsFx.dust(COLOR, 1.4f));
            cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
            cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            level.addFreshEntity(cloud);
            GeneticsFx.burst(level, ParticleTypes.WITCH, at.add(0, 0.5, 0), 30, 1.2, 0.1);
        }
        playSound(ModSounds.WITCH_BREW.get(), 2.5f, 0.8f);
    }

    private void hex(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick != HEX.windup() || target == null) {
            return;
        }
        Vec3 from = position().add(0, getBbHeight() * 0.8, 0);
        Vec3 to = target.getEyePosition();
        for (int i = 0; i <= 20; i++) {
            Vec3 p = from.lerp(to, i / 20.0);
            level.sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
            level.sendParticles(GeneticsFx.dust(COLOR, 1.2f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        if (hasLineOfSight(target)) {
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, phase() >= 2 ? 50 : 35, 1), this);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), this);
            GeneticsFx.burst(level, ParticleTypes.WITCH, to, 30, 0.5, 0.1);
        }
        playSound(net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL, 2f, 1.3f);
    }

    /** Bebe o elixir: cura no fim da preparacao se nao for interrompida. */
    private void drink(ServerLevel level, int tick) {
        int fire = DRINK.windup();
        if (tick == 0) {
            drinkDamage = 0;
            hint(level, "witch.drink", ChatFormatting.GOLD);
        }
        if (tick < fire && tick % 8 == 0) {
            playSound(ModSounds.WITCH_DRINK.get(), 1.6f, 0.8f);
            GeneticsFx.burst(level, GeneticsFx.dust(0xFF5FA2, 1.2f), position().add(0, getBbHeight() * 0.85, 0), 6, 0.3, 0.05);
        }
        if (tick == fire) {
            heal(getMaxHealth() * HEAL_FRACTION);
            GeneticsFx.burst(level, ParticleTypes.HEART, position().add(0, getBbHeight() * 0.9, 0), 10, 0.8, 0.1);
            GeneticsFx.burst(level, GeneticsFx.dust(0xFF5FA2, 2f), position().add(0, getBbHeight() * 0.5, 0), 40, 0.8, 0.2);
            playSound(net.minecraft.sounds.SoundEvents.WITCH_CELEBRATE, 2f, 0.7f);
        }
    }

    /** Furia: marca pontos em volta do alvo e faz chover pocoes neles. */
    private void overflow(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int fire = OVERFLOW.windup();
        if (tick == 0) {
            overflowSpots.clear();
            Vec3 center = target != null ? target.position() : position();
            for (int i = 0; i < OVERFLOW_DROPS; i++) {
                Vec3 spot = i == 0 ? center
                        : center.add((random.nextDouble() - 0.5) * 12, 0, (random.nextDouble() - 0.5) * 12);
                overflowSpots.add(groundNear(level, spot.x, spot.z));
            }
            hint(level, "witch.overflow", ChatFormatting.GOLD);
            playSound(ModSounds.WITCH_BREW.get(), 3f, 0.6f);
        }
        if (tick < fire) {
            if (tick % 2 == 0) {
                float progress = tick / (float) fire;
                for (Vec3 spot : overflowSpots) {
                    eruptionWarning(level, spot, COLOR, progress);
                }
                GeneticsFx.burst(level, ParticleTypes.BUBBLE_POP, position().add(0, getBbHeight() * 0.9, 0), 6, 0.5, 0.1);
            }
            return;
        }
        int index = (tick - fire) / 3;
        if ((tick - fire) % 3 == 0 && index < overflowSpots.size()) {
            Vec3 spot = overflowSpots.get(index);
            ThrownPotion thrown = new ThrownPotion(level, this);
            thrown.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, randomHarmful()));
            thrown.setPos(spot.x, spot.y + 12, spot.z);
            thrown.setDeltaMovement(0, -0.8, 0);
            level.addFreshEntity(thrown);
        }
    }

    // ------------------------------------------------------------------ interrupcao

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // As proprias pocoes nao a ferem.
        if (source.getEntity() == this) {
            return false;
        }
        float before = getHealth();
        boolean result = super.hurt(source, amount);
        if (result && currentAttack == DRINK && attackTick < DRINK.windup() && level() instanceof ServerLevel level) {
            drinkDamage += Math.max(0, before - getHealth());
            if (drinkDamage >= getMaxHealth() * INTERRUPT_FRACTION) {
                Vec3 at = position().add(0, getBbHeight() * 0.85, 0);
                GeneticsFx.burst(level, ParticleTypes.SPLASH, at, 40, 0.5, 0.2);
                GeneticsFx.burst(level, GeneticsFx.dust(0xFF5FA2, 1.8f), at, 30, 0.5, 0.1);
                playSound(net.minecraft.sounds.SoundEvents.SPLASH_POTION_BREAK, 2f, 0.8f);
                hint(level, "witch.interrupted", ChatFormatting.GOLD);
                stun(INTERRUPT_STUN);
            }
        }
        return result;
    }

    @Override
    protected void onAttackCancelled(BossAttack attack) {
        drinkDamage = 0;
        overflowSpots.clear();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isAlive() && random.nextInt(3) == 0) {
            // Caldeirao borbulhando nas costas.
            Vec3 back = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(-getBbWidth() * 0.45))
                    .add(0, getBbHeight() * 0.78, 0);
            level().addParticle(GeneticsFx.dust(COLOR, 1.0f), back.x + (random.nextDouble() - 0.5) * 0.6, back.y,
                    back.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.06, 0);
            if (random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.WITCH, back.x, back.y + 0.2, back.z, 0, 0.1, 0);
            }
        }
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.RAVAGER_STEP, 0.4f, 1.4f);
    }
}
