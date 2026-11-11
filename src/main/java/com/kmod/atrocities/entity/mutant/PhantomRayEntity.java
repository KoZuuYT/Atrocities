package com.kmod.atrocities.entity.mutant;

import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.entity.boss.BossAttack;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Arraia-Fantasma: Phantom inchado ate virar uma arraia do tamanho de uma casa. Voa o tempo todo, circulando
 * acima do alvo.
 * <ul>
 *   <li>Mergulho: desce em linha reta atravessando o alvo.</li>
 *   <li>Guincho Sonico: onda em area que cega e atordoa os sentidos.</li>
 *   <li>Rajada de Asas: um bater de asas que joga todos para longe.</li>
 *   <li>Orbes Etereas (Furia): cinco orbes que perseguem o alvo.</li>
 * </ul>
 */
public class PhantomRayEntity extends MutantEntity {

    private static final BossAttack SWOOP = new BossAttack("swoop", 16, 44, 70, 0f, 30f, 5, 1);
    private static final BossAttack SHRIEK = new BossAttack("shriek", 20, 36, 160, 0f, 12f, 3, 1);
    private static final BossAttack GUST = new BossAttack("gust", 12, 30, 110, 0f, 12f, 3, 1);
    private static final BossAttack BARRAGE = new BossAttack("barrage", 18, 50, 180, 4f, 36f, 3, 2);
    private static final List<BossAttack> ATTACKS = List.of(SWOOP, SHRIEK, GUST, BARRAGE);

    private static final int COLOR = 0x5AF0FF;
    private static final int DIVE_TICKS = 18;
    private static final double ORBIT_RADIUS = 10;
    private static final double ORBIT_HEIGHT = 7;

    private double orbitAngle;
    @Nullable
    private Vec3 home;
    private Vec3 diveDirection = Vec3.ZERO;
    private final Set<UUID> diveHits = new HashSet<>();

    public PhantomRayEntity(EntityType<? extends PhantomRayEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return mutantAttributes(0.3D, 2.0D, 0.8D).add(Attributes.FLYING_SPEED, 0.6D);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public EntityType<?> origin() {
        return EntityType.PHANTOM;
    }

    @Override
    protected double mutantHealth() {
        return 480;
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
        return ModSounds.PHANTOM_RAY;
    }

    @Override
    protected List<BossAttack> attacks() {
        return ATTACKS;
    }

    @Override
    protected double preferredRange() {
        return ORBIT_RADIUS;
    }

    @Override
    protected Telegraph telegraph(BossAttack attack) {
        return switch (attack.id()) {
            case "swoop" -> Telegraph.LINE;
            case "barrage" -> Telegraph.NONE;
            default -> Telegraph.AREA;
        };
    }

    @Override
    protected double telegraphRadius(BossAttack attack) {
        return attack == SHRIEK ? 10 : 8;
    }

    @Override
    protected boolean canStartAttack(BossAttack attack, LivingEntity target) {
        if (!hasLineOfSight(target)) {
            return false;
        }
        // Guincho e rajada so valem perto (inclusive na vertical).
        return attack.maxRange() > 12 || distanceTo(target) < attack.maxRange() + 6;
    }

    // ------------------------------------------------------------------ voo

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!isAlive()) {
            return;
        }
        boolean diving = currentAttack == SWOOP && attackTick >= SWOOP.windup()
                && attackTick < SWOOP.windup() + DIVE_TICKS;
        if (diving) {
            return;
        }
        LivingEntity target = getTarget();
        Vec3 desired;
        if (!canAct() || isAttacking()) {
            // Parado no ar (golpe, atordoamento, rugido): so flutua.
            setDeltaMovement(getDeltaMovement().scale(0.75).add(0, Math.sin(tickCount * 0.15) * 0.01, 0));
            if (target != null) {
                faceTowards(target, 12f);
            }
            return;
        }
        if (target != null && target.isAlive()) {
            home = null;
            orbitAngle += 0.035;
            desired = target.position().add(Math.cos(orbitAngle) * ORBIT_RADIUS, ORBIT_HEIGHT,
                    Math.sin(orbitAngle) * ORBIT_RADIUS);
        } else {
            // Sem alvo: circula devagar ao redor de onde nasceu.
            if (home == null) {
                home = position();
            }
            orbitAngle += 0.012;
            desired = home.add(Math.cos(orbitAngle) * 8, Math.sin(tickCount * 0.02) * 1.5, Math.sin(orbitAngle) * 8);
        }
        Vec3 steer = desired.subtract(position());
        double max = getAttributeValue(Attributes.FLYING_SPEED) * (phase() >= 2 ? 1.2 : 1.0);
        Vec3 want = steer.lengthSqr() > max * max ? steer.normalize().scale(max) : steer;
        Vec3 motion = getDeltaMovement().scale(0.85).add(want.scale(0.15));
        // Nao atravessa o chao.
        if (!level().getBlockState(blockPosition().below(2)).isAir() && motion.y < 0.05) {
            motion = new Vec3(motion.x, 0.1, motion.z);
        }
        setDeltaMovement(motion);
        if (motion.horizontalDistanceSqr() > 1.0E-3) {
            float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90f;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 8f));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "swoop" -> swoop(level, tick, target);
            case "shriek" -> shriek(level, tick);
            case "gust" -> gust(level, tick);
            case "barrage" -> barrage(level, tick, target);
            default -> {
            }
        }
    }

    private void swoop(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = SWOOP.windup();
        if (tick == 2) {
            playSound(ModSounds.RAY_SWOOP.get(), 2.5f, 0.7f);
        }
        if (tick < start) {
            // Recua e sobe um pouco antes de mergulhar.
            setDeltaMovement(getDeltaMovement().scale(0.6).add(forward().scale(-0.04)).add(0, 0.03, 0));
            return;
        }
        if (tick == start) {
            Vec3 aim = target != null ? target.position().add(0, 0.6, 0).subtract(position()) : forward().add(0, -0.5, 0);
            diveDirection = aim.normalize();
            diveHits.clear();
            playSound(ModSounds.RAY_SWOOP.get(), 3f, 1.0f);
        }
        if (tick < start + DIVE_TICKS) {
            setDeltaMovement(diveDirection.scale(1.35));
            float yaw = (float) (Mth.atan2(diveDirection.z, diveDirection.x) * Mth.RAD_TO_DEG) - 90f;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            strike(position().add(0, getBbHeight() * 0.5, 0), getBbWidth() * 0.5 + 0.6, 1.1f, 1.2, 0.6,
                    victim -> diveHits.add(victim.getUUID()));
            level.sendParticles(GeneticsFx.dust(COLOR, 1.5f), getX(), getY() + 0.5, getZ(), 6, getBbWidth() * 0.4, 0.1,
                    getBbWidth() * 0.4, 0);
            if (horizontalCollision || verticalCollision) {
                diveDirection = new Vec3(diveDirection.x, Math.abs(diveDirection.y) * 0.5 + 0.3, diveDirection.z).normalize();
            }
        } else if (tick == start + DIVE_TICKS) {
            setDeltaMovement(getDeltaMovement().scale(0.5).add(0, 0.7, 0));
        }
    }

    private void shriek(ServerLevel level, int tick) {
        if (tick == SHRIEK.windup()) {
            for (LivingEntity victim : strike(position(), 10, 0.5f, 1.0, 0.2, null)) {
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
                victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0), this);
            }
            Vec3 center = position().add(0, getBbHeight() * 0.5, 0);
            for (int i = 1; i <= 4; i++) {
                ring(level, ParticleTypes.SONIC_BOOM, center, 2.5 * i, 8 + 4 * i);
            }
            playSound(ModSounds.RAY_SHRIEK.get(), 4f, 0.8f);
            BossFx.shake(level, position(), 30, 1.0f, 18);
        }
    }

    private void gust(ServerLevel level, int tick) {
        if (tick == GUST.windup()) {
            strike(position(), 12, 0.3f, 2.6, 0.9, null);
            for (int i = 0; i < 40; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                Vec3 dir = new Vec3(Math.cos(angle), -0.3, Math.sin(angle));
                level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 0, dir.x, dir.y, dir.z, 0.9);
            }
            ring(level, ParticleTypes.GUST, position().add(0, 0.5, 0), 4, 10);
            playSound(ModSounds.RAY_GUST.get(), 3f, 0.8f);
            BossFx.shake(level, position(), 24, 0.6f, 12);
        }
    }

    private void barrage(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = BARRAGE.windup();
        if (tick % 3 == 0 && tick < start) {
            GeneticsFx.burst(level, ParticleTypes.END_ROD, position().add(0, getBbHeight() * 0.5, 0), 4, getBbWidth() * 0.4, 0.05);
        }
        if (target != null && tick >= start && tick < start + 20 && (tick - start) % 4 == 0) {
            int index = (tick - start) / 4;
            double side = (index % 2 == 0 ? 1 : -1) * getBbWidth() * 0.45;
            Vec3 right = new Vec3(-forward().z, 0, forward().x);
            Vec3 origin = position().add(right.scale(side)).add(0, getBbHeight() * 0.5, 0);
            BioProjectile orb = new BioProjectile(level, this, BioProjectile.Kind.ORB, attackDamage(0.5f)).homingOn(target);
            orb.setPos(origin.x, origin.y, origin.z);
            Vec3 aim = target.getEyePosition().subtract(origin);
            orb.shoot(aim.x + right.x * side * 3, aim.y + 2, aim.z + right.z * side * 3, 0.7f, 0f);
            level.addFreshEntity(orb);
            playSound(ModSounds.RAY_ORB.get(), 1.8f, 0.9f + index * 0.08f);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // Sem as asas funcionando, ela despenca.
        setNoGravity(false);
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state,
                                   net.minecraft.core.BlockPos pos) {
    }
}
