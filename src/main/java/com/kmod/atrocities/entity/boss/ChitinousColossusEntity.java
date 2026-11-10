package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.genetics.Archetype;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Colosso Quitinoso: tanque blindado da linhagem quitinosa.
 * <ul>
 *   <li>Esmagamento: bracos sobem e caem na frente (area).</li>
 *   <li>Varredura: golpe lateral em arco que arremessa longe.</li>
 *   <li>Investida: dispara em linha reta; se bater numa parede fica atordoado e vulneravel.</li>
 *   <li>Saraivada de Espinhos: leque de espinhos das costas.</li>
 *   <li>Terremoto (fase 2): tres aneis de choque que so atingem quem esta no chao.</li>
 * </ul>
 */
public class ChitinousColossusEntity extends AtrocityBoss {

    private static final BossAttack SLAM = new BossAttack("slam", 18, 36, 60, 0f, 5.5f, 4, 1);
    private static final BossAttack SWEEP = new BossAttack("sweep", 12, 26, 50, 0f, 4.5f, 3, 1);
    private static final BossAttack CHARGE = new BossAttack("charge", 20, 46, 160, 6f, 26f, 3, 1);
    private static final BossAttack SPINE_VOLLEY = new BossAttack("spine_volley", 16, 30, 120, 6f, 32f, 2, 1);
    private static final BossAttack QUAKE = new BossAttack("quake", 24, 50, 200, 0f, 12f, 3, 2);
    private static final List<BossAttack> ATTACKS = List.of(SLAM, SWEEP, CHARGE, SPINE_VOLLEY, QUAKE);

    private static final int DASH_TICKS = 20;
    private static final int CHARGE_STUN = 70;

    private Vec3 chargeDirection = Vec3.ZERO;
    private final Set<UUID> chargeHits = new HashSet<>();

    public ChitinousColossusEntity(EntityType<? extends ChitinousColossusEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(0.24D, 12.0D, 6.0D);
    }

    @Override
    public Archetype archetype() {
        return Archetype.CHITINOUS;
    }

    @Override
    protected List<BossAttack> attacks() {
        return ATTACKS;
    }

    @Override
    protected double preferredRange() {
        return 3.0;
    }

    @Override
    protected EntityType<?> fallbackMinion() {
        return EntityType.SILVERFISH;
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "slam" -> slam(level, tick);
            case "sweep" -> sweep(level, tick);
            case "charge" -> charge(level, tick, target);
            case "spine_volley" -> spineVolley(level, tick, target);
            case "quake" -> quake(level, tick);
            default -> {
            }
        }
    }

    private void slam(ServerLevel level, int tick) {
        if (tick == 4) {
            playSound(ModSounds.BOSS_WINDUP.get(), 2f, 0.6f);
        }
        if (tick == SLAM.windup()) {
            Vec3 impact = position().add(forward().scale(getBbWidth() * 0.5 + 1.5));
            strike(impact, 3.5, 1.0f, 1.0, 0.7, null);
            groundCrack(level, impact, 2.0, 60);
            ring(level, ParticleTypes.CLOUD, impact.add(0, 0.2, 0), 3.5, 28);
            GeneticsFx.burst(level, ParticleTypes.EXPLOSION, impact.add(0, 0.5, 0), 2, 0.6, 0);
            playSound(ModSounds.BOSS_SLAM.get(), 3f, 0.7f);
        }
    }

    private void sweep(ServerLevel level, int tick) {
        if (tick == 2) {
            playSound(ModSounds.BOSS_WINDUP.get(), 1.6f, 0.8f);
        }
        if (tick == SWEEP.windup()) {
            double radius = getBbWidth() * 0.5 + 3.5;
            strike(position(), radius, 0.8f, 2.4, 0.35, target -> inFront(target, 80));
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            for (int i = -4; i <= 4; i++) {
                double angle = yaw + i * 0.3;
                Vec3 at = position().add(-Math.sin(angle) * radius * 0.8, getBbHeight() * 0.35, Math.cos(angle) * radius * 0.8);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
            playSound(ModSounds.BOSS_SWING.get(), 2.5f, 0.6f);
        }
    }

    private void charge(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int windup = CHARGE.windup();
        if (tick < windup) {
            if (tick % 4 == 0) {
                groundCrack(level, position().add(forward().scale(-getBbWidth() * 0.4)), 0.8, 10);
                playSound(ModSounds.BOSS_STOMP.get(), 1.4f, 0.8f);
            }
            return;
        }
        if (tick == windup) {
            Vec3 aim = target != null ? target.position().subtract(position()).multiply(1, 0, 1) : forward();
            chargeDirection = aim.lengthSqr() < 1.0E-4 ? forward() : aim.normalize();
            chargeHits.clear();
            playSound(ModSounds.BOSS_CHARGE.get(), 3f, 0.7f);
        }
        if (tick < windup + DASH_TICKS) {
            double speed = phase() >= 2 ? 1.05 : 0.9;
            setDeltaMovement(chargeDirection.x * speed, getDeltaMovement().y, chargeDirection.z * speed);
            setYRot((float) (Mth.atan2(chargeDirection.z, chargeDirection.x) * Mth.RAD_TO_DEG) - 90f);
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            Vec3 front = position().add(chargeDirection.scale(getBbWidth() * 0.5));
            strike(front, getBbWidth() * 0.5 + 0.8, 1.2f, 2.2, 0.6, victim -> chargeHits.add(victim.getUUID()));
            groundCrack(level, position(), getBbWidth() * 0.4, 6);
            if (tick > windup + 2 && horizontalCollision) {
                Vec3 impact = position().add(chargeDirection.scale(getBbWidth() * 0.6)).add(0, getBbHeight() * 0.4, 0);
                GeneticsFx.burst(level, ParticleTypes.EXPLOSION, impact, 3, 0.6, 0);
                groundCrack(level, impact, 1.5, 50);
                playSound(ModSounds.BOSS_SLAM.get(), 3.5f, 0.5f);
                setDeltaMovement(chargeDirection.scale(-0.4));
                stun(CHARGE_STUN);
            }
        } else {
            setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
        }
    }

    private void spineVolley(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick == 6) {
            playSound(ModSounds.BOSS_WINDUP.get(), 1.8f, 1.1f);
        }
        boolean second = phase() >= 2 && tick == SPINE_VOLLEY.windup() + 8;
        if (target == null || (tick != SPINE_VOLLEY.windup() && !second)) {
            return;
        }
        int count = phase() >= 2 ? 9 : 5;
        Vec3 origin = position().add(0, getBbHeight() * 0.85, 0);
        Vec3 aim = target.getEyePosition().subtract(origin);
        double baseYaw = Math.atan2(aim.z, aim.x);
        double horizontal = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        for (int i = 0; i < count; i++) {
            double spread = (i - (count - 1) / 2.0) * (second ? 0.16 : 0.12);
            double yaw = baseYaw + spread;
            BioProjectile spine = new BioProjectile(level, this, BioProjectile.Kind.SPINE, attackDamage(0.45f));
            spine.setPos(origin.x, origin.y, origin.z);
            spine.shoot(Math.cos(yaw) * horizontal, aim.y + horizontal * 0.04, Math.sin(yaw) * horizontal, 1.7f, 1.5f);
            level.addFreshEntity(spine);
        }
        GeneticsFx.burst(level, ParticleTypes.CRIT, origin, 20, 0.8, 0.3);
        playSound(ModSounds.BOSS_SPINE_SHOT.get(), 2.5f, 0.8f + random.nextFloat() * 0.2f);
    }

    private void quake(ServerLevel level, int tick) {
        if (tick == 4) {
            playSound(roarSound(), 2.5f, 1.1f);
        }
        int start = QUAKE.windup();
        for (int ring = 0; ring < 3; ring++) {
            if (tick == start + ring * 8) {
                double radius = 4 + ring * 4;
                double inner = radius - 4;
                strike(position(), radius, 0.6f, 1.1, 0.9,
                        victim -> victim.onGround() && victim.distanceTo(this) >= inner);
                for (int i = 0; i < 12 + ring * 6; i++) {
                    double angle = i / (12.0 + ring * 6) * Mth.TWO_PI;
                    groundCrack(level, position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 0.4, 6);
                }
                ring(level, ParticleTypes.CLOUD, position().add(0, 0.2, 0), radius, 30 + ring * 12);
                playSound(ModSounds.BOSS_SLAM.get(), 3f, 0.55f + ring * 0.1f);
            }
        }
    }

    @Override
    protected void onPhaseTwo(ServerLevel level) {
        GeneticsFx.burst(level, ParticleTypes.CRIT, position().add(0, getBbHeight(), 0), 60, getBbWidth() * 0.5, 0.4);
    }

    @Override
    protected void onAttackCancelled(BossAttack attack) {
        chargeHits.clear();
    }

    @Override
    protected SoundEvent roarSound() {
        return ModSounds.COLOSSUS_ROAR.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.COLOSSUS_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.COLOSSUS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.COLOSSUS_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.5f;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(ModSounds.BOSS_STOMP.get(), 0.8f, 0.9f);
    }
}
