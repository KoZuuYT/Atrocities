package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.genetics.Archetype;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Matriarca Hemofaga: predadora agil que se cura com o sangue das vitimas.
 * <ul>
 *   <li>Dilacerar: duas garradas rapidas; cada vitima atingida cura a Matriarca.</li>
 *   <li>Bote: salta sobre o alvo e esmaga ao aterrissar.</li>
 *   <li>Jato de Sangue: cone continuo que corroi (Wither).</li>
 *   <li>Chamado da Ninhada: invoca crias da linhagem.</li>
 *   <li>Hemorragia (fase 2): drena todos ao redor; depois fica saciada (atordoada e vulneravel).</li>
 * </ul>
 */
public class HemophageMatriarchEntity extends AtrocityBoss {

    private static final BossAttack REND = new BossAttack("rend", 8, 22, 30, 0f, 4f, 4, 1);
    private static final BossAttack LUNGE = new BossAttack("lunge", 12, 34, 90, 5f, 16f, 3, 1);
    private static final BossAttack BLOOD_SPRAY = new BossAttack("blood_spray", 14, 44, 140, 0f, 9f, 3, 1);
    private static final BossAttack BROOD_CALL = new BossAttack("brood_call", 20, 40, 400, 0f, 40f, 2, 1);
    private static final BossAttack HEMORRHAGE = new BossAttack("hemorrhage", 30, 60, 300, 0f, 12f, 3, 2);
    private static final List<BossAttack> ATTACKS = List.of(REND, LUNGE, BLOOD_SPRAY, BROOD_CALL, HEMORRHAGE);

    private static final int BLOOD = 0x8A0F1A;
    private static final float LIFESTEAL_RATIO = 0.005f;

    private boolean airborne;

    public HemophageMatriarchEntity(EntityType<? extends HemophageMatriarchEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.RED);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(0.32D, 4.0D, 0.0D);
    }

    @Override
    public Archetype archetype() {
        return Archetype.HEMOPHAGE;
    }

    @Override
    protected List<BossAttack> attacks() {
        return ATTACKS;
    }

    @Override
    protected double preferredRange() {
        return 2.5;
    }

    @Override
    protected EntityType<?> fallbackMinion() {
        return EntityType.CAVE_SPIDER;
    }

    @Override
    protected boolean canStartAttack(BossAttack attack, LivingEntity target) {
        return attack == BROOD_CALL || hasLineOfSight(target);
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "rend" -> rend(level, tick);
            case "lunge" -> lunge(level, tick, target);
            case "blood_spray" -> bloodSpray(level, tick, target);
            case "brood_call" -> broodCall(level, tick);
            case "hemorrhage" -> hemorrhage(level, tick);
            default -> {
            }
        }
    }

    private void rend(ServerLevel level, int tick) {
        if (tick == REND.windup() || tick == REND.windup() + 6) {
            Vec3 center = position().add(forward().scale(getBbWidth() * 0.5 + 1.2));
            List<LivingEntity> hit = strike(center, 2.6, 0.55f, 0.6, 0.2, target -> inFront(target, 70));
            for (LivingEntity victim : hit) {
                heal(getMaxHealth() * LIFESTEAL_RATIO);
                GeneticsFx.stream(level, GeneticsFx.dust(BLOOD, 1.2f), victim.position().add(0, victim.getBbHeight() * 0.5, 0),
                        position().add(0, getBbHeight() * 0.6, 0), 10);
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y + 1.2, center.z, 1, 0, 0, 0, 0);
            playSound(ModSounds.MATRIARCH_BITE.get(), 2f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    private void lunge(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick == 2) {
            playSound(ModSounds.BOSS_WINDUP.get(), 1.6f, 1.2f);
        }
        if (tick == LUNGE.windup()) {
            Vec3 to = target != null ? target.position().subtract(position()) : forward().scale(8);
            double distance = Math.sqrt(to.x * to.x + to.z * to.z);
            double speed = Math.min(1.8, distance * 0.11);
            Vec3 horizontal = distance < 1.0E-3 ? forward() : new Vec3(to.x / distance, 0, to.z / distance);
            setDeltaMovement(horizontal.x * speed, 0.75, horizontal.z * speed);
            hasImpulse = true;
            airborne = true;
            playSound(ModSounds.MATRIARCH_LEAP.get(), 2.5f, 1f);
            groundCrack(level, position(), 1.2, 30);
        } else if (airborne && !onGround()) {
            // Rastro de sangue durante o salto.
            Vec3 body = position().add(0, getBbHeight() * 0.5, 0);
            level.sendParticles(GeneticsFx.dust(BLOOD, 2f), body.x, body.y, body.z, 6, getBbWidth() * 0.3, 0.2,
                    getBbWidth() * 0.3, 0.01);
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA, body.x, body.y, body.z, 1, 0.3, 0.2, 0.3, 0);
        }
        if (airborne && tick > LUNGE.windup() + 3 && onGround()) {
            airborne = false;
            strike(position(), getBbWidth() * 0.5 + 2.7, 1.0f, 1.2, 0.5, null);
            groundCrack(level, position(), 2.2, 60);
            ring(level, GeneticsFx.dust(BLOOD, 2f), position().add(0, 0.2, 0), 3.2, 30);
            playSound(ModSounds.BOSS_SLAM.get(), 2.5f, 1.1f);
        }
    }

    private void bloodSpray(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = BLOOD_SPRAY.windup();
        if (tick == 4) {
            playSound(ModSounds.MATRIARCH_SCREECH.get(), 2f, 1.2f);
        }
        if (tick < start || tick >= start + 24) {
            return;
        }
        if (target != null) {
            faceTowards(target, 3f);
        }
        Vec3 mouth = position().add(0, getBbHeight() * 0.8, 0).add(forward().scale(getBbWidth() * 0.4));
        for (int i = 0; i < 6; i++) {
            Vec3 direction = forward().add((random.nextDouble() - 0.5) * 0.7, -0.15 + (random.nextDouble() - 0.5) * 0.3,
                    (random.nextDouble() - 0.5) * 0.7).normalize();
            level.sendParticles(GeneticsFx.dust(BLOOD, 1.8f), mouth.x, mouth.y, mouth.z, 0,
                    direction.x, direction.y, direction.z, 0.9);
            level.sendParticles(ParticleTypes.FALLING_LAVA, mouth.x + direction.x * 3, mouth.y + direction.y * 3,
                    mouth.z + direction.z * 3, 1, 0.6, 0.2, 0.6, 0);
        }
        if (tick % 4 == 0) {
            playSound(ModSounds.MATRIARCH_SPRAY.get(), 1.6f, 0.8f + random.nextFloat() * 0.2f);
            List<LivingEntity> hit = strike(position(), getBbWidth() * 0.5 + 8, 0.15f, 0.15, 0.05,
                    victim -> inFront(victim, 35) && hasLineOfSight(victim));
            hit.forEach(victim -> victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 0), this));
        }
    }

    private void broodCall(ServerLevel level, int tick) {
        if (tick == 2) {
            playSound(ModSounds.MATRIARCH_SCREECH.get(), 3f, 0.7f);
        }
        if (tick < BROOD_CALL.windup() && tick % 3 == 0) {
            GeneticsFx.burst(level, GeneticsFx.dust(BLOOD, 1.6f), position().add(0, getBbHeight() * 0.4, 0), 8,
                    getBbWidth() * 0.6, 0.05);
        }
        if (tick == BROOD_CALL.windup()) {
            spawnMinions(level, phase() >= 2 ? 4 : 2, 6);
        }
    }

    private void hemorrhage(ServerLevel level, int tick) {
        int start = HEMORRHAGE.windup();
        if (tick == 2) {
            playSound(ModSounds.MATRIARCH_SCREECH.get(), 3.5f, 0.55f);
        }
        if (tick < start) {
            ring(level, GeneticsFx.dust(BLOOD, 1.5f), position().add(0, 0.3, 0), 12.0 * (1 - tick / (double) start), 24);
            return;
        }
        if (tick <= start + 20 && (tick - start) % 5 == 0) {
            Vec3 heart = position().add(0, getBbHeight() * 0.6, 0);
            List<LivingEntity> hit = strike(position(), 12, 0.12f, -0.3, 0.1, this::hasLineOfSight);
            for (LivingEntity victim : hit) {
                heal(getMaxHealth() * LIFESTEAL_RATIO * 0.8f);
                GeneticsFx.stream(level, GeneticsFx.dust(BLOOD, 1.6f), victim.position().add(0, victim.getBbHeight() * 0.6, 0),
                        heart, 18);
            }
            playSound(ModSounds.MATRIARCH_DRAIN.get(), 2f, 0.8f);
        }
        if (tick == HEMORRHAGE.duration() - 1) {
            stun(50);
        }
    }

    @Override
    protected void onAttackCancelled(BossAttack attack) {
        airborne = false;
    }

    @Override
    protected void onPhaseTwo(ServerLevel level) {
        GeneticsFx.burst(level, GeneticsFx.dust(BLOOD, 3f), position().add(0, getBbHeight() * 0.6, 0), 120,
                getBbWidth(), 0.2);
    }

    @Override
    protected SoundEvent roarSound() {
        return ModSounds.MATRIARCH_ROAR.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MATRIARCH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MATRIARCH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MATRIARCH_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }
}
