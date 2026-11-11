package com.kmod.atrocities.entity.mutant;

import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.entity.boss.BossAttack;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Slime Devorador: um cubo de gelo menta do tamanho de uma casa, com um nucleo brilhante e restos boiando dentro.
 * <ul>
 *   <li>Esmagada: pulinho e pancada em volta.</li>
 *   <li>Quicada Tripla: tres saltos seguidos ate o alvo, com onda de choque em cada pouso.</li>
 *   <li>Cuspe Grudento: bolotas que deixam poças de lentidao.</li>
 *   <li>Engolir: prende alguem dentro do corpo. <b>Resgate</b>: cause 8% da vida dele e ele cospe a vitima,
 *   atordoado.</li>
 *   <li>Divisao: solta slimes menores (mais na Furia).</li>
 * </ul>
 * Ao morrer, se desfaz em quatro slimes.
 */
public class DevourerSlimeEntity extends MutantEntity {

    private static final BossAttack SLAM = new BossAttack("slam", 12, 26, 40, 0f, 4.0f, 5, 1);
    private static final BossAttack BOUNCE = new BossAttack("bounce", 12, 72, 140, 5f, 18f, 4, 1);
    private static final BossAttack SPIT = new BossAttack("spit", 12, 26, 80, 4f, 22f, 4, 1);
    private static final BossAttack ENGULF = new BossAttack("engulf", 14, 84, 220, 0f, 3.5f, 3, 1);
    private static final BossAttack SPLIT = new BossAttack("split", 16, 36, 300, 0f, 24f, 2, 1);
    private static final List<BossAttack> ATTACKS = List.of(SLAM, BOUNCE, SPIT, ENGULF, SPLIT);

    public static final int COLOR = 0x2EE6A8;
    private static final float RESCUE_FRACTION = 0.08f;
    private static final int RESCUE_STUN = 50;
    private static final int[] BOUNCES = {0, 20, 40};

    @Nullable
    private UUID engulfed;
    private float rescueDamage;
    private boolean airborne;
    private int leapTick;

    public DevourerSlimeEntity(EntityType<? extends DevourerSlimeEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return mutantAttributes(0.25D, 0.0D, 0.9D);
    }

    @Override
    public EntityType<?> origin() {
        return EntityType.SLIME;
    }

    @Override
    protected double mutantHealth() {
        return 640;
    }

    @Override
    protected double mutantDamage() {
        return 13;
    }

    @Override
    protected int mutantColor() {
        return COLOR;
    }

    @Override
    protected MutantSounds sounds() {
        return ModSounds.DEVOURER_SLIME;
    }

    @Override
    protected List<BossAttack> attacks() {
        return ATTACKS;
    }

    @Override
    protected double preferredRange() {
        return 2.0;
    }

    @Override
    protected Telegraph telegraph(BossAttack attack) {
        return switch (attack.id()) {
            case "bounce", "spit" -> Telegraph.LINE;
            case "split" -> Telegraph.NONE;
            default -> Telegraph.AREA;
        };
    }

    @Override
    protected double telegraphRadius(BossAttack attack) {
        return attack == ENGULF ? 3.0 : 4.0;
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "slam" -> slam(level, tick);
            case "bounce" -> bounce(level, tick, target);
            case "spit" -> spit(level, tick, target);
            case "engulf" -> engulf(level, tick);
            case "split" -> split(level, tick);
            default -> {
            }
        }
    }

    private void slam(ServerLevel level, int tick) {
        if (tick == 2) {
            setDeltaMovement(getDeltaMovement().add(0, 0.55, 0));
            hasImpulse = true;
        }
        if (tick == SLAM.windup()) {
            land(level, 4.0, 1.0f);
        }
    }

    private void bounce(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int step = tick - BOUNCE.windup();
        for (int at : BOUNCES) {
            if (step == at) {
                Vec3 to = target != null ? target.position() : position().add(forward().scale(6));
                leapTowards(to, 2.5);
                airborne = true;
                leapTick = tick;
                replayAnimation(BOUNCE.animation());
                playSound(ModSounds.SLIME_SQUISH.get(), 2.5f, 0.6f);
            }
        }
        if (airborne && tick > leapTick + 3 && onGround()) {
            airborne = false;
            land(level, 3.4, 0.8f);
        }
    }

    /** Pouso pesado: onda de choque, gosma e (na Furia) poça de lentidao. */
    private void land(ServerLevel level, double radius, float multiplier) {
        strike(position(), radius + getBbWidth() * 0.5, multiplier, 1.6, 0.6, null);
        groundCrack(level, position(), radius * 0.6, 60);
        GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, position().add(0, 0.3, 0), 60, getBbWidth() * 0.6, 0.3);
        GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 2.2f), position().add(0, 0.3, 0), 30, getBbWidth() * 0.6, 0.1);
        ring(level, ParticleTypes.ITEM_SLIME, position().add(0, 0.2, 0), radius + getBbWidth() * 0.5, 36);
        playSound(ModSounds.SLIME_SQUISH.get(), 3f, 0.45f);
        playSound(ModSounds.MUTANT_LAND.get(), 2f, 0.9f);
        if (phase() >= 2) {
            gooPuddle(level, this, position(), 3.0f, 100);
        }
    }

    private void spit(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick != SPIT.windup() || target == null) {
            return;
        }
        int count = phase() >= 2 ? 5 : 3;
        Vec3 origin = position().add(0, getBbHeight() * 0.7, 0);
        for (int i = 0; i < count; i++) {
            Vec3 spot = i == 0 ? target.position()
                    : target.position().add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6);
            Vec3 delta = spot.subtract(origin);
            double horizontal = delta.horizontalDistance();
            BioProjectile goo = new BioProjectile(level, this, BioProjectile.Kind.GOO, attackDamage(0.6f));
            goo.setPos(origin.x, origin.y, origin.z);
            goo.shoot(delta.x, delta.y + horizontal * 0.4, delta.z, (float) Math.min(1.6, 0.5 + horizontal * 0.06), 2f);
            level.addFreshEntity(goo);
        }
        GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, origin, 20, 0.5, 0.2);
        playSound(ModSounds.WEAVER_SPRAY.get(), 2f, 0.7f);
    }

    /** Engole o alvo mais proximo a frente e o digere ate ser resgatado ou cuspido. */
    private void engulf(ServerLevel level, int tick) {
        int grab = ENGULF.windup();
        int spitAt = ENGULF.duration() - 10;
        if (tick == grab) {
            rescueDamage = 0;
            double reach = 3.5 + getBbWidth() * 0.5;
            level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach), this::isValidVictim).stream()
                    .filter(victim -> inFront(victim, 80) && victim.distanceTo(this) <= reach)
                    .min(Comparator.comparingDouble(this::distanceToSqr))
                    .ifPresent(victim -> {
                        engulfed = victim.getUUID();
                        playSound(ModSounds.SLIME_ENGULF.get(), 3f, 0.7f);
                        hint(level, "slime.engulf", ChatFormatting.GREEN);
                    });
            return;
        }
        LivingEntity victim = engulfedVictim(level);
        if (victim == null || tick < grab) {
            return;
        }
        if (tick < spitAt) {
            Vec3 core = position().add(0, getBbHeight() * 0.25, 0);
            victim.teleportTo(core.x, core.y, core.z);
            victim.setDeltaMovement(Vec3.ZERO);
            victim.fallDistance = 0;
            if (tick % 10 == 0) {
                victim.hurt(damageSources().mobAttack(this), attackDamage(0.3f));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), this);
                GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, core, 16, 0.6, 0.1);
                playSound(ModSounds.SLIME_SQUISH.get(), 1.5f, 1.2f);
            }
        } else if (tick == spitAt) {
            release(level, victim, 1.8, attackDamage(0.5f));
        }
    }

    @Nullable
    private LivingEntity engulfedVictim(ServerLevel level) {
        if (engulfed == null) {
            return null;
        }
        if (level.getEntity(engulfed) instanceof LivingEntity living && living.isAlive() && isValidVictim(living)) {
            return living;
        }
        engulfed = null;
        return null;
    }

    /** Cospe a vitima para a frente. */
    private void release(ServerLevel level, LivingEntity victim, double push, float damage) {
        engulfed = null;
        Vec3 dir = forward();
        Vec3 out = position().add(dir.scale(getBbWidth() * 0.5 + 1.0)).add(0, 0.5, 0);
        victim.teleportTo(out.x, out.y, out.z);
        if (damage > 0) {
            victim.hurt(damageSources().mobAttack(this), damage);
        }
        victim.push(dir.x * push, 0.5, dir.z * push);
        victim.hurtMarked = true;
        GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, out, 40, 0.5, 0.3);
        playSound(ModSounds.SLIME_SQUISH.get(), 2.5f, 0.8f);
    }

    private void split(ServerLevel level, int tick) {
        if (tick == SPLIT.windup()) {
            spawnMinions(level, phase() >= 2 ? 4 : 2, 6);
            GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, position().add(0, getBbHeight() * 0.5, 0), 80,
                    getBbWidth() * 0.6, 0.4);
            playSound(ModSounds.SLIME_SQUISH.get(), 3f, 0.5f);
        }
    }

    /** Poça grudenta de lentidao (Furia e as bolotas de gosma). */
    public static void gooPuddle(ServerLevel level, @Nullable LivingEntity owner, Vec3 at, float radius, int duration) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setOwner(owner);
        cloud.setRadius(radius);
        cloud.setRadiusOnUse(-0.1f);
        cloud.setWaitTime(4);
        cloud.setDuration(duration);
        cloud.setRadiusPerTick(-radius / (duration * 1.5f));
        cloud.setParticle(GeneticsFx.dust(COLOR, 1.3f));
        cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        level.addFreshEntity(cloud);
    }

    // ------------------------------------------------------------------ resgate

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float before = getHealth();
        boolean result = super.hurt(source, amount);
        if (result && engulfed != null && level() instanceof ServerLevel level) {
            rescueDamage += Math.max(0, before - getHealth());
            if (rescueDamage >= getMaxHealth() * RESCUE_FRACTION) {
                LivingEntity victim = engulfedVictim(level);
                if (victim != null) {
                    release(level, victim, 1.0, 0);
                }
                hint(level, "slime.rescued", ChatFormatting.GOLD);
                stun(RESCUE_STUN);
            }
        }
        return result;
    }

    @Override
    protected void onAttackCancelled(BossAttack attack) {
        airborne = false;
        if (engulfed != null && level() instanceof ServerLevel level) {
            LivingEntity victim = engulfedVictim(level);
            if (victim != null) {
                release(level, victim, 1.0, 0);
            }
        }
        engulfed = null;
        rescueDamage = 0;
    }

    @Override
    protected void tickDeath() {
        if (deathTime == DEATH_TICKS - 1 && level() instanceof ServerLevel level) {
            if (engulfed != null) {
                LivingEntity victim = engulfedVictim(level);
                if (victim != null) {
                    release(level, victim, 0.6, 0);
                }
            }
            // Desfaz-se em quatro slimes medios.
            for (int i = 0; i < 4; i++) {
                Slime slime = EntityType.SLIME.create(level);
                if (slime == null) {
                    continue;
                }
                double angle = i * Math.PI / 2;
                slime.setSize(2, true);
                slime.moveTo(getX() + Math.cos(angle), getY() + 0.5, getZ() + Math.sin(angle), random.nextFloat() * 360f, 0);
                slime.setDeltaMovement(Math.cos(angle) * 0.4, 0.5, Math.sin(angle) * 0.4);
                level.addFreshEntity(slime);
            }
            GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, position().add(0, 1, 0), 120, getBbWidth() * 0.6, 0.5);
            playSound(ModSounds.SLIME_SQUISH.get(), 3f, 0.4f);
        }
        super.tickDeath();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isAlive() && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.ITEM_SLIME, getRandomX(0.5), getY() + 0.1, getRandomZ(0.5), 0, 0, 0);
        }
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.SLIME_SQUISH, 0.8f, 0.5f);
    }
}
