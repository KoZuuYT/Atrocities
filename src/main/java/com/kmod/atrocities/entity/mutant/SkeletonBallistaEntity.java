package com.kmod.atrocities.entity.mutant;

import com.kmod.atrocities.entity.boss.BossAttack;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Esqueleto Balista: quatro bracos, um arco de osso fundido ao corpo e mira de atirador.
 * <ul>
 *   <li>Rajada: leque de 3 flechas (5 na Furia).</li>
 *   <li>Disparo de Balista: trava a mira por meio segundo (linha azul) e dispara um virote que atravessa tudo.</li>
 *   <li>Giro de Costelas: gira com as costelas abertas como laminas.</li>
 *   <li>Salto Mortal: salta para tras para manter distancia e atira no ar.</li>
 *   <li>Chuva de Ossos (Furia): marca uma area no alvo e flechas caem do ceu.</li>
 * </ul>
 */
public class SkeletonBallistaEntity extends MutantEntity {

    private static final BossAttack VOLLEY = new BossAttack("volley", 14, 30, 50, 3f, 30f, 5, 1);
    private static final BossAttack BALLISTA = new BossAttack("ballista", 30, 46, 140, 6f, 40f, 3, 1);
    private static final BossAttack RIB_SPIN = new BossAttack("rib_spin", 10, 32, 70, 0f, 3.2f, 4, 1);
    private static final BossAttack BACKFLIP = new BossAttack("backflip", 4, 28, 90, 0f, 5f, 4, 1);
    private static final BossAttack BONE_RAIN = new BossAttack("bone_rain", 24, 64, 240, 5f, 36f, 3, 2);
    private static final List<BossAttack> ATTACKS = List.of(VOLLEY, BALLISTA, RIB_SPIN, BACKFLIP, BONE_RAIN);

    private static final int COLOR = 0x59D8FF;
    private static final int AIM_LOCK = 10;
    private static final double BOLT_RANGE = 40;

    private Vec3 boltDirection = Vec3.ZERO;
    private Vec3 rainCenter = Vec3.ZERO;

    public SkeletonBallistaEntity(EntityType<? extends SkeletonBallistaEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return mutantAttributes(0.30D, 4.0D, 0.6D);
    }

    @Override
    public EntityType<?> origin() {
        return EntityType.SKELETON;
    }

    @Override
    protected double mutantHealth() {
        return 560;
    }

    @Override
    protected double mutantDamage() {
        return 11;
    }

    @Override
    protected int mutantColor() {
        return COLOR;
    }

    @Override
    protected MutantSounds sounds() {
        return ModSounds.SKELETON_BALLISTA;
    }

    @Override
    protected List<BossAttack> attacks() {
        return ATTACKS;
    }

    @Override
    protected double preferredRange() {
        return 11.0;
    }

    @Override
    protected Telegraph telegraph(BossAttack attack) {
        return switch (attack.id()) {
            case "volley" -> Telegraph.LINE;
            case "rib_spin" -> Telegraph.AREA;
            default -> Telegraph.NONE;
        };
    }

    @Override
    protected double telegraphRadius(BossAttack attack) {
        return 3.6;
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "volley" -> volley(level, tick, target);
            case "ballista" -> ballista(level, tick, target);
            case "rib_spin" -> ribSpin(level, tick);
            case "backflip" -> backflip(level, tick, target);
            case "bone_rain" -> boneRain(level, tick, target);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ flechas

    private Vec3 bowPoint() {
        return front(0.2, 0.62);
    }

    private void shootArrow(ServerLevel level, Vec3 from, Vec3 direction, float speed, float multiplier, float spread) {
        Arrow arrow = new Arrow(level, this, new ItemStack(Items.ARROW), null);
        arrow.setPos(from.x, from.y, from.z);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(attackDamage(multiplier) / speed);
        arrow.setCritArrow(phase() >= 2);
        arrow.shoot(direction.x, direction.y, direction.z, speed, spread);
        level.addFreshEntity(arrow);
    }

    private void volley(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick == 2) {
            playSound(ModSounds.BALLISTA_DRAW.get(), 1.6f, 1.1f);
        }
        if (tick != VOLLEY.windup() || target == null) {
            return;
        }
        Vec3 origin = bowPoint();
        Vec3 aim = target.getEyePosition().subtract(origin);
        double horizontal = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        double baseYaw = Math.atan2(aim.z, aim.x);
        int count = phase() >= 2 ? 5 : 3;
        for (int i = 0; i < count; i++) {
            double yaw = baseYaw + (i - (count - 1) / 2.0) * 0.09;
            Vec3 dir = new Vec3(Math.cos(yaw) * horizontal, aim.y + horizontal * 0.08, Math.sin(yaw) * horizontal);
            shootArrow(level, origin, dir, 2.6f, 0.55f, 1.5f);
        }
        GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 1.3f), origin, 14, 0.3, 0.1);
        playSound(ModSounds.BALLISTA_VOLLEY.get(), 2.2f, 0.9f + random.nextFloat() * 0.2f);
    }

    private void ballista(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int fire = BALLISTA.windup();
        if (tick % 6 == 0 && tick < fire) {
            playSound(ModSounds.BALLISTA_DRAW.get(), 1.8f, 0.6f + tick / (float) fire * 0.6f);
        }
        if (tick < fire - AIM_LOCK && target != null) {
            Vec3 aim = target.getEyePosition().add(0, -0.4, 0).subtract(bowPoint());
            boltDirection = aim.lengthSqr() < 1.0E-4 ? forward() : aim.normalize();
        }
        if (tick >= fire - AIM_LOCK - 8 && tick < fire && tick % 2 == 0) {
            // Linha de mira: trava 10 ticks antes do disparo (passo para o lado e o virote erra).
            boolean locked = tick >= fire - AIM_LOCK;
            Vec3 from = bowPoint();
            for (int i = 1; i < 40; i++) {
                Vec3 p = from.add(boltDirection.scale(i));
                level.sendParticles(GeneticsFx.dust(locked ? 0xFF3B3B : COLOR, locked ? 1.1f : 0.8f),
                        p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        if (tick == fire) {
            fireBolt(level);
        }
    }

    /** Virote da balista: atravessa todos os alvos na linha, sem parar. */
    private void fireBolt(ServerLevel level) {
        Vec3 from = bowPoint();
        Vec3 to = from.add(boltDirection.scale(BOLT_RANGE));
        var clip = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        Vec3 end = clip.getLocation();
        double length = from.distanceTo(end);
        Set<LivingEntity> hit = new HashSet<>();
        AABB box = new AABB(from, end).inflate(1.2);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, this::isValidVictim)) {
            Vec3 rel = victim.position().add(0, victim.getBbHeight() * 0.5, 0).subtract(from);
            double along = rel.dot(boltDirection);
            if (along < 0 || along > length) {
                continue;
            }
            double off = rel.subtract(boltDirection.scale(along)).length();
            if (off <= victim.getBbWidth() * 0.5 + 0.7) {
                hit.add(victim);
            }
        }
        for (LivingEntity victim : hit) {
            if (victim.hurt(damageSources().mobAttack(this), attackDamage(1.6f))) {
                victim.push(boltDirection.x * 1.6, 0.35, boltDirection.z * 1.6);
                victim.hurtMarked = true;
                applyHitEffects(victim);
            }
        }
        for (double d = 0; d < length; d += 0.5) {
            Vec3 p = from.add(boltDirection.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
            if (d % 2 < 0.5) {
                level.sendParticles(GeneticsFx.dust(COLOR, 1.6f), p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0);
            }
        }
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)), end.x, end.y, end.z,
                16, 0.2, 0.2, 0.2, 0.15);
        GeneticsFx.burst(level, ParticleTypes.EXPLOSION, end, 1, 0, 0);
        playSound(ModSounds.BALLISTA_SHOT.get(), 3f, 0.9f);
        BossFx.shake(level, position(), 24, 0.7f, 10);
        setDeltaMovement(getDeltaMovement().add(boltDirection.scale(-0.5)));
    }

    // ------------------------------------------------------------------ corpo a corpo e mobilidade

    private void ribSpin(ServerLevel level, int tick) {
        int start = RIB_SPIN.windup();
        if (tick == 2) {
            playSound(ModSounds.BALLISTA_RATTLE.get(), 2f, 0.8f);
        }
        if (tick >= start && tick <= start + 14 && (tick - start) % 4 == 0) {
            strike(position(), 3.2, 0.5f, 1.0, 0.3, null);
            ring(level, ParticleTypes.SWEEP_ATTACK, position().add(0, getBbHeight() * 0.45, 0), 2.4, 6);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)),
                    getX(), getY() + getBbHeight() * 0.45, getZ(), 6, 1.2, 0.2, 1.2, 0.2);
            playSound(ModSounds.BOSS_SWING.get(), 1.8f, 1.3f);
        }
    }

    private void backflip(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = BACKFLIP.windup();
        if (tick == start) {
            Vec3 back = forward().scale(-1.15);
            setDeltaMovement(back.x, 0.75, back.z);
            hasImpulse = true;
            playSound(ModSounds.MUTANT_LEAP.get(), 1.8f, 1.3f);
            groundCrack(level, position(), 0.8, 14);
        }
        if (tick == start + 9 && target != null) {
            Vec3 origin = bowPoint();
            shootArrow(level, origin, target.getEyePosition().subtract(origin), 2.8f, 0.8f, 0.5f);
            playSound(ModSounds.BALLISTA_VOLLEY.get(), 1.8f, 1.2f);
        }
    }

    private void boneRain(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = BONE_RAIN.windup();
        if (tick == 2 && target != null) {
            rainCenter = target.position();
            playSound(roarSound(), 2.5f, 1.1f);
        }
        if (tick < start) {
            if (tick == 4) {
                // Rajada para o alto.
                for (int i = 0; i < 6; i++) {
                    shootArrow(level, bowPoint(), new Vec3((random.nextDouble() - 0.5) * 0.3, 1, (random.nextDouble() - 0.5) * 0.3),
                            2.5f, 0f, 2f);
                }
                playSound(ModSounds.BALLISTA_VOLLEY.get(), 2.5f, 0.7f);
            }
            if (tick % 2 == 0) {
                ring(level, GeneticsFx.dust(0xFF3B3B, 1.3f), rainCenter.add(0, 0.15, 0), 4.5, 36);
                ring(level, GeneticsFx.dust(COLOR, 1.0f), rainCenter.add(0, 0.15, 0), 4.5 * tick / start, 20);
            }
            return;
        }
        if (tick < start + 32 && tick % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = Math.sqrt(random.nextDouble()) * 4.5;
                Vec3 from = rainCenter.add(Math.cos(angle) * radius, 16, Math.sin(angle) * radius);
                shootArrow(level, from, new Vec3(0, -1, 0), 2.2f, 0.4f, 0.5f);
            }
        }
    }
}
