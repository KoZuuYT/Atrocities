package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.genetics.Archetype;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
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
 * <p>Golpes:</p>
 * <ul>
 *   <li>Dilacerar: duas garradas rapidas; cada vitima atingida cura a Matriarca.</li>
 *   <li>Bote: salta sobre o alvo e esmaga ao aterrissar.</li>
 *   <li>Jato de Sangue: cone continuo que corroi (Wither).</li>
 *   <li>Chamado da Ninhada: invoca crias da linhagem.</li>
 *   <li>Hemorragia (fase 2+): drena todos ao redor; depois fica saciada (atordoada e vulneravel).</li>
 *   <li>Frenesi (fase 3): tres investidas curtas com garras, uma atras da outra.</li>
 * </ul>
 * <p>Mecanicas:</p>
 * <ul>
 *   <li><b>Fase 1 - Predadora</b>: um projetil que a acerte no meio do Bote a derruba (atordoada, dano extra).</li>
 *   <li><b>Fase 2 - Casulos de Sangue</b>: tres casulos pulsam curando a Matriarca e chocam crias; destrua-os.</li>
 *   <li><b>Fase 3 - Sangria</b>: ela sangra sem parar (perde vida sozinha), mas cada golpe que acerta a cura
 *   bastante. Fuja dos golpes e deixe o sangramento trabalhar.</li>
 * </ul>
 */
public class HemophageMatriarchEntity extends AtrocityBoss {

    private static final BossAttack REND = new BossAttack("rend", 8, 22, 30, 0f, 4f, 4, 1);
    private static final BossAttack LUNGE = new BossAttack("lunge", 12, 34, 90, 5f, 16f, 3, 1);
    private static final BossAttack BLOOD_SPRAY = new BossAttack("blood_spray", 14, 44, 140, 0f, 9f, 3, 1);
    private static final BossAttack BROOD_CALL = new BossAttack("brood_call", 20, 40, 400, 0f, 40f, 2, 1);
    private static final BossAttack HEMORRHAGE = new BossAttack("hemorrhage", 30, 60, 300, 0f, 12f, 3, 2);
    private static final BossAttack FRENZY = new BossAttack("frenzy", 10, 40, 160, 0f, 7f, 4, 3, "rend");
    private static final List<BossAttack> ATTACKS = List.of(REND, LUNGE, BLOOD_SPRAY, BROOD_CALL, HEMORRHAGE, FRENZY);

    private static final int BLOOD = 0x8A0F1A;
    private static final float LIFESTEAL_RATIO = 0.005f;
    /** Sangria (fase 3): vida perdida por segundo e cura por golpe acertado (fracoes da vida maxima). */
    private static final float BLEED_PER_SECOND = 0.005f;
    private static final float FEAST_HEAL = 0.015f;
    private static final int FEAST_INTERVAL = 10;
    private static final int DOWNED_STUN = 70;

    private boolean airborne;
    private int lastFeastTick = -1000;
    private final List<Vec3> cocoonSites = new ArrayList<>();

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
            case "frenzy" -> frenzy(level, tick, target);
            default -> {
            }
        }
    }

    private void claw(ServerLevel level, float multiplier) {
        Vec3 center = position().add(forward().scale(getBbWidth() * 0.5 + 1.2));
        List<LivingEntity> hit = strike(center, 2.6, multiplier, 0.6, 0.2, target -> inFront(target, 70));
        for (LivingEntity victim : hit) {
            heal(getMaxHealth() * LIFESTEAL_RATIO);
            GeneticsFx.stream(level, GeneticsFx.dust(BLOOD, 1.2f), victim.position().add(0, victim.getBbHeight() * 0.5, 0),
                    position().add(0, getBbHeight() * 0.6, 0), 10);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y + 1.2, center.z, 1, 0, 0, 0, 0);
        playSound(ModSounds.MATRIARCH_BITE.get(), 2f, 0.9f + random.nextFloat() * 0.2f);
    }

    private void rend(ServerLevel level, int tick) {
        if (tick == REND.windup() || tick == REND.windup() + 6) {
            claw(level, 0.55f);
        }
    }

    private void frenzy(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = FRENZY.windup();
        if (tick < start) {
            return;
        }
        int local = tick - start;
        if (local % 10 == 0 && local <= 20) {
            if (local > 0) {
                replayAnimation(FRENZY.animation());
            }
            if (target != null) {
                faceTowards(target, 90f);
            }
            // Pequeno bote para frente antes de cada garrada.
            Vec3 dash = forward().scale(0.75);
            setDeltaMovement(dash.x, 0.1, dash.z);
            hasImpulse = true;
            GeneticsFx.burst(level, GeneticsFx.dust(BLOOD, 1.8f), position().add(0, 0.3, 0), 10, 0.4, 0.05);
        }
        if (local % 10 == 3 && local <= 23) {
            claw(level, 0.5f);
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
            // Brilho dourado: a janela para derruba-la com um projetil.
            if (tick % 2 == 0) {
                level.sendParticles(ParticleTypes.WAX_ON, body.x, body.y + 0.6, body.z, 3, 0.5, 0.4, 0.5, 0.2);
            }
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

    // ------------------------------------------------------------------ mecanicas

    @Override
    protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float amount) {
        if (airborne && !onGround() && source.is(DamageTypeTags.IS_PROJECTILE)) {
            // Derrubada no ar: cai, fica atordoada e o tiro causa dano extra.
            Vec3 body = position().add(0, getBbHeight() * 0.5, 0);
            level.sendParticles(ParticleTypes.FLASH, body.x, body.y, body.z, 1, 0, 0, 0, 0);
            GeneticsFx.burst(level, GeneticsFx.dust(BLOOD, 2.6f), body, 50, getBbWidth() * 0.5, 0.2);
            playSound(ModSounds.BOSS_CRITICAL.get(), 2.5f, 0.7f);
            stun(DOWNED_STUN);
            setDeltaMovement(0, -1.2, 0);
            hurtMarked = true;
            hint(level, "matriarch.downed", ChatFormatting.GOLD);
            return amount * 1.5f;
        }
        return amount;
    }

    @Override
    protected void onHitLanded(ServerLevel level, LivingEntity target, float damage) {
        if (phase() >= MAX_PHASE && tickCount - lastFeastTick >= FEAST_INTERVAL) {
            lastFeastTick = tickCount;
            // Banquete: na Sangria, cada golpe que acerta a cura bastante.
            heal(getMaxHealth() * FEAST_HEAL);
            GeneticsFx.stream(level, GeneticsFx.dust(0xFF2030, 1.8f), target.position().add(0, target.getBbHeight() * 0.5, 0),
                    position().add(0, getBbHeight() * 0.6, 0), 16);
            playSound(ModSounds.MATRIARCH_DRAIN.get(), 1.6f, 1.2f);
            hint(level, "matriarch.feast", ChatFormatting.RED);
        }
    }

    @Override
    protected void tickPhaseMechanics(ServerLevel level) {
        if (phase() >= MAX_PHASE) {
            Vec3 body = position().add(0, getBbHeight() * 0.5, 0);
            if (tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA, body.x, body.y, body.z, 2,
                        getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0);
            }
            if (tickCount % 20 == 0 && getHealth() > getMaxHealth() * 0.02f) {
                // Sangria: perde vida sozinha (sem matar; o golpe final e de quem lutou).
                setHealth(Math.max(getMaxHealth() * 0.02f, getHealth() - getMaxHealth() * BLEED_PER_SECOND));
                GeneticsFx.burst(level, GeneticsFx.dust(BLOOD, 1.8f), body, 12, getBbWidth() * 0.4, 0.05);
            }
        }
    }

    @Override
    protected void onPhaseStart(ServerLevel level, int phase) {
        if (phase == 2) {
            cocoonSites.clear();
            cocoonSites.addAll(pointsAround(level, 3, 7.0, 0));
        }
    }

    @Override
    protected void onIntroTick(ServerLevel level, int tick) {
        // Chuva de sangue caindo em volta enquanto ela emerge.
        if (tick < 70 && tick % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                double radius = 2 + random.nextDouble() * 8;
                level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA, getX() + Math.cos(angle) * radius,
                        getY() + 6 + random.nextDouble() * 3, getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
            }
            ring(level, GeneticsFx.dust(BLOOD, 1.6f), position().add(0, 0.2, 0), 1.5 + tick * 0.08, 16);
        }
        if (tick == 20 || tick == 45) {
            playSound(ModSounds.MATRIARCH_SCREECH.get(), 3f, 0.6f);
        }
    }

    @Override
    protected void onTransitionTick(ServerLevel level, int phase, int tick) {
        int before = TRANSITION_TICKS - TRANSITION_CLIMAX_AT;
        Vec3 heart = position().add(0, getBbHeight() * 0.6, 0);
        if (phase == 2 && tick < before) {
            // Filetes de sangue escorrem ate onde os casulos vao brotar.
            float progress = tick / (float) before;
            if (tick % 2 == 0) {
                for (Vec3 site : cocoonSites) {
                    eruptionWarning(level, site, BLOOD, progress);
                    if (tick % 6 == 0) {
                        GeneticsFx.stream(level, GeneticsFx.dust(BLOOD, 1.4f), heart, site.add(0, 0.5, 0), 14);
                    }
                }
            }
        } else if (phase == MAX_PHASE && tick < before) {
            // Ela se rasga: chafariz de sangue cada vez mais forte.
            float progress = tick / (float) before;
            level.sendParticles(GeneticsFx.dust(BLOOD, 2.2f), heart.x, heart.y + 0.5, heart.z, 0, 0, 1, 0, 0.6);
            level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA, heart.x, heart.y + 1, heart.z,
                    1 + Math.round(progress * 4), 1.2, 0.4, 1.2, 0);
            if (tick % 10 == 0) {
                playSound(ModSounds.MATRIARCH_DRAIN.get(), 2f, 0.6f + progress * 0.4f);
            }
        }
    }

    @Override
    protected void onPhaseClimax(ServerLevel level, int phase) {
        if (phase == 2) {
            for (Vec3 site : cocoonSites) {
                spawnOrgan(level, BossOrganEntity.Variant.COCOON, site, 25f, 15f);
            }
            cocoonSites.clear();
        } else if (phase == MAX_PHASE) {
            Vec3 heart = position().add(0, getBbHeight() * 0.6, 0);
            // Os casulos restantes se rompem e escorrem para ela (sem cura: a Sangria comeca agora).
            for (BossOrganEntity cocoon : organs(BossOrganEntity.Variant.COCOON)) {
                Vec3 at = cocoon.position().add(0, cocoon.getBbHeight() * 0.5, 0);
                GeneticsFx.stream(level, GeneticsFx.dust(BLOOD, 2f), at, heart, 20);
                GeneticsFx.burst(level, GeneticsFx.dust(BLOOD, 2.4f), at, 40, 0.6, 0.15);
                cocoon.discard();
            }
            GeneticsFx.burst(level, GeneticsFx.dust(BLOOD, 3f), heart, 160, getBbWidth(), 0.35);
            ring(level, GeneticsFx.dust(BLOOD, 2.4f), position().add(0, 0.2, 0), 6, 48);
            playSound(ModSounds.MATRIARCH_SCREECH.get(), 4f, 0.45f);
        }
    }

    @Override
    public void onOrganDestroyed(ServerLevel level, BossOrganEntity organ) {
        if (organ.variant() == BossOrganEntity.Variant.COCOON) {
            playSound(ModSounds.MATRIARCH_HURT.get(), 2.5f, 0.7f);
            hint(level, organs(BossOrganEntity.Variant.COCOON).isEmpty() ? "matriarch.cocoons_gone"
                    : "matriarch.cocoon_destroyed", ChatFormatting.GOLD);
        }
    }

    @Override
    protected void onAttackCancelled(BossAttack attack) {
        airborne = false;
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
