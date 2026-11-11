package com.kmod.atrocities.entity.mutant;

import com.kmod.atrocities.entity.boss.BossAttack;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Titã Zumbi: brutamontes de punhos gigantes.
 * <ul>
 *   <li>Esmagamento: os dois punhos descem na frente (area). Na Furia, solta uma segunda onda.</li>
 *   <li>Rasgo Sismico: soca o chao e uma fileira de terra irrompe ate o alvo, lancando quem estiver nela.</li>
 *   <li>Salto Brutal: pula sobre o alvo e cai com uma onda de choque.</li>
 *   <li>Agarrao: pega quem estiver na frente, ergue e arremessa longe.</li>
 *   <li>Chamado da Horda: ruge e zumbis brotam do chao.</li>
 * </ul>
 */
public class ZombieTitanEntity extends MutantEntity {

    private static final BossAttack SMASH = new BossAttack("smash", 14, 30, 40, 0f, 4.2f, 5, 1);
    private static final BossAttack RIFT = new BossAttack("rift", 16, 44, 100, 3f, 16f, 3, 1);
    private static final BossAttack LEAP = new BossAttack("leap", 12, 50, 140, 6f, 20f, 3, 1);
    private static final BossAttack GRAB = new BossAttack("grab", 10, 40, 120, 0f, 3.2f, 3, 1);
    private static final BossAttack HORDE = new BossAttack("horde", 20, 40, 400, 0f, 24f, 2, 1);
    private static final List<BossAttack> ATTACKS = List.of(SMASH, RIFT, LEAP, GRAB, HORDE);

    private static final int COLOR = 0x7CD64B;
    private static final int RIFT_LENGTH = 16;
    private static final int HOLD_TICKS = 14;

    private Vec3 riftDirection = Vec3.ZERO;
    private Vec3 riftOrigin = Vec3.ZERO;
    private final Set<UUID> riftHits = new HashSet<>();
    private boolean airborne;
    private int grabbedId = -1;

    public ZombieTitanEntity(EntityType<? extends ZombieTitanEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return mutantAttributes(0.27D, 6.0D, 0.9D);
    }

    @Override
    public EntityType<?> origin() {
        return EntityType.ZOMBIE;
    }

    @Override
    protected double mutantHealth() {
        return 700;
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
        return ModSounds.ZOMBIE_TITAN;
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
    protected Telegraph telegraph(BossAttack attack) {
        return switch (attack.id()) {
            case "rift", "leap" -> Telegraph.LINE;
            case "horde" -> Telegraph.NONE;
            default -> Telegraph.AREA;
        };
    }

    @Override
    protected double telegraphRadius(BossAttack attack) {
        return attack == GRAB ? 3.2 : 4.6;
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "smash" -> smash(level, tick);
            case "rift" -> rift(level, tick, target);
            case "leap" -> leap(level, tick, target);
            case "grab" -> grab(level, tick);
            case "horde" -> horde(level, tick);
            default -> {
            }
        }
    }

    private void smash(ServerLevel level, int tick) {
        if (tick == 3) {
            playSound(ModSounds.BOSS_WINDUP.get(), 1.8f, 0.7f);
        }
        if (tick == SMASH.windup()) {
            Vec3 impact = front(1.4, 0);
            strike(impact, 3.2, 1.1f, 0.8, 0.8, null);
            groundCrack(level, impact, 2.2, 60);
            ring(level, ParticleTypes.CLOUD, impact.add(0, 0.2, 0), 3.2, 28);
            GeneticsFx.burst(level, ParticleTypes.EXPLOSION, impact.add(0, 0.4, 0), 2, 0.5, 0);
            playSound(ModSounds.BOSS_SLAM.get(), 3f, 0.75f);
        }
        if (phase() >= 2 && tick == SMASH.windup() + 8) {
            // Furia: a pancada ecoa numa segunda onda maior.
            Vec3 impact = front(1.4, 0);
            strike(impact, 5.5, 0.5f, 1.4, 0.5, victim -> victim.onGround());
            ring(level, GeneticsFx.dust(COLOR, 1.8f), impact.add(0, 0.2, 0), 5.5, 40);
            groundCrack(level, impact, 4.0, 40);
            playSound(ModSounds.BOSS_SHOCKWAVE.get(), 2.5f, 0.9f);
        }
    }

    private void rift(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick == 2) {
            playSound(roarSound(), 2f, 1.2f);
        }
        int start = RIFT.windup();
        if (tick == start) {
            Vec3 aim = target != null ? target.position().subtract(position()).multiply(1, 0, 1) : forward();
            riftDirection = aim.lengthSqr() < 1.0E-4 ? forward() : aim.normalize();
            riftOrigin = position().add(riftDirection.scale(getBbWidth() * 0.5 + 0.8));
            riftHits.clear();
            groundCrack(level, riftOrigin, 1.4, 30);
            playSound(ModSounds.BOSS_SLAM.get(), 3f, 0.6f);
        }
        int step = tick - start;
        if (step >= 0 && step < RIFT_LENGTH) {
            // A fenda avanca um bloco por tick; cada ponto lanca para cima quem estiver nele.
            Vec3 point = riftOrigin.add(riftDirection.scale(step + 1));
            Vec3 ground = groundNear(level, point.x, point.z);
            erupt(level, ground);
            strike(ground, 1.7, 0.9f, 0.3, 1.1, victim -> riftHits.add(victim.getUUID()));
            if (step % 3 == 0) {
                playSound(ModSounds.TITAN_ERUPT.get(), 1.6f, 0.8f + step * 0.02f);
            }
        }
    }

    private void erupt(ServerLevel level, Vec3 at) {
        BlockState state = level.getBlockState(BlockPos.containing(at).below());
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.3, at.z, 25,
                    0.4, 0.5, 0.4, 0.3);
        }
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 4, 0.3, 0.1, 0.3, 0.05);
        level.sendParticles(GeneticsFx.dust(COLOR, 1.4f), at.x, at.y + 0.6, at.z, 6, 0.3, 0.6, 0.3, 0.05);
    }

    private void leap(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = LEAP.windup();
        if (tick == start) {
            Vec3 to = target != null ? target.position() : position().add(forward().scale(8));
            leapTowards(to, 3.5);
            airborne = true;
            playSound(ModSounds.MUTANT_LEAP.get(), 2.5f, 0.7f);
            groundCrack(level, position(), 1.2, 30);
        }
        if (airborne && tick > start + 3 && onGround()) {
            airborne = false;
            land(level);
        } else if (airborne && tick == LEAP.duration() - 1) {
            airborne = false;
        }
    }

    private void land(ServerLevel level) {
        strike(position(), 4.8, 1.2f, 1.4, 0.7, null);
        groundCrack(level, position(), 3.2, 90);
        for (int i = 1; i <= 3; i++) {
            ring(level, ParticleTypes.CLOUD, position().add(0, 0.2, 0), 1.6 * i, 18 * i);
        }
        ring(level, GeneticsFx.dust(COLOR, 2f), position().add(0, 0.3, 0), 4.8, 48);
        GeneticsFx.burst(level, ParticleTypes.EXPLOSION, position().add(0, 0.5, 0), 3, 1.2, 0);
        playSound(ModSounds.MUTANT_LAND.get(), 3f, 0.6f);
        playSound(ModSounds.BOSS_SLAM.get(), 3f, 0.6f);
        BossFx.shake(level, position(), 36, 1.3f, 18);
    }

    private void grab(ServerLevel level, int tick) {
        int start = GRAB.windup();
        if (tick == start) {
            grabbedId = -1;
            double reach = getBbWidth() * 0.5 + 2.6;
            LivingEntity victim = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach),
                            entity -> isValidVictim(entity) && inFront(entity, 55) && distanceTo(entity) <= reach + 1)
                    .stream().min((a, b) -> Double.compare(distanceToSqr(a), distanceToSqr(b))).orElse(null);
            playSound(ModSounds.BOSS_SWING.get(), 2f, 0.8f);
            if (victim != null && victim.hurt(damageSources().mobAttack(this), attackDamage(0.4f))) {
                grabbedId = victim.getId();
                playSound(ModSounds.TITAN_GRAB.get(), 2f, 0.8f);
            }
        }
        Entity held = grabbedId >= 0 ? level.getEntity(grabbedId) : null;
        if (!(held instanceof LivingEntity victim) || !victim.isAlive()) {
            grabbedId = -1;
            return;
        }
        if (tick > start && tick < start + HOLD_TICKS) {
            // Ergue a vitima na frente do rosto, chacoalhando.
            Vec3 hold = front(0.4, 0.7).add(0, Math.sin(tick * 1.3) * 0.15, 0);
            victim.teleportTo(hold.x, hold.y, hold.z);
            victim.setDeltaMovement(Vec3.ZERO);
            victim.fallDistance = 0;
            victim.hurtMarked = true;
            if (tick % 4 == 0) {
                GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 1.2f), hold, 6, 0.3, 0.05);
            }
        }
        if (tick == start + HOLD_TICKS) {
            Vec3 throwDir = forward().scale(2.4).add(0, 0.9, 0);
            victim.hurt(damageSources().mobAttack(this), attackDamage(0.7f));
            victim.setDeltaMovement(throwDir);
            victim.hurtMarked = true;
            applyHitEffects(victim);
            playSound(ModSounds.TITAN_THROW.get(), 2.2f, 0.8f);
            GeneticsFx.burst(level, ParticleTypes.CLOUD, victim.position(), 10, 0.4, 0.1);
            grabbedId = -1;
        }
    }

    private void horde(ServerLevel level, int tick) {
        if (tick == 2) {
            playSound(roarSound(), 3.5f, 0.75f);
            BossFx.shake(level, position(), 32, 0.8f, 20);
        }
        if (tick == HORDE.windup()) {
            int count = phase() >= 2 ? 4 : 3;
            spawnMinions(level, count, 6);
            for (LivingEntity minion : minions(8)) {
                if (minion.tickCount < 2) {
                    erupt(level, minion.position());
                }
            }
            groundCrack(level, position(), 3.0, 60);
            playSound(ModSounds.TITAN_ERUPT.get(), 2.5f, 0.6f);
        }
    }

    @Override
    protected void onAttackCancelled(BossAttack attack) {
        airborne = false;
        grabbedId = -1;
        riftHits.clear();
    }
}
