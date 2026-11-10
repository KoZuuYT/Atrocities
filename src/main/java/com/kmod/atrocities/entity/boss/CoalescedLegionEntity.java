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
 * Legiao Coalescida: massa de corpos fundidos que controla o campo com esporos e crias.
 * <ul>
 *   <li>Engolfar: bracos fecham sobre quem esta perto, puxando e lentificando.</li>
 *   <li>Explosao de Esporos: nuvem venenosa e repulsao ao redor.</li>
 *   <li>Atracao: tentaculos puxam todos de longe ate a massa.</li>
 *   <li>Brotamento: a massa expele crias e fica exposta (vulneravel) por alguns segundos.</li>
 *   <li>Morteiro de Bile: bolotas em arco que viram nuvens toxicas.</li>
 *   <li>Fissao (fase 2): ao rugir, se parte em varias crias de uma vez.</li>
 * </ul>
 */
public class CoalescedLegionEntity extends AtrocityBoss {

    private static final BossAttack ENGULF = new BossAttack("engulf", 16, 32, 50, 0f, 4.5f, 4, 1);
    private static final BossAttack SPORE_BURST = new BossAttack("spore_burst", 20, 40, 160, 0f, 8f, 3, 1);
    private static final BossAttack PULL = new BossAttack("pull", 18, 40, 180, 6f, 20f, 3, 1);
    private static final BossAttack BUD = new BossAttack("bud", 20, 40, 360, 0f, 40f, 2, 1);
    private static final BossAttack BILE_MORTAR = new BossAttack("bile_mortar", 14, 30, 100, 6f, 30f, 3, 1);
    private static final List<BossAttack> ATTACKS = List.of(ENGULF, SPORE_BURST, PULL, BUD, BILE_MORTAR);

    private static final int SPORE = 0x5E8C31;

    public CoalescedLegionEntity(EntityType<? extends CoalescedLegionEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(0.22D, 2.0D, 0.0D);
    }

    @Override
    public Archetype archetype() {
        return Archetype.COALESCED;
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
        return EntityType.SLIME;
    }

    @Override
    protected boolean canStartAttack(BossAttack attack, LivingEntity target) {
        return attack == BUD || attack == SPORE_BURST || hasLineOfSight(target);
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "engulf" -> engulf(level, tick);
            case "spore_burst" -> sporeBurst(level, tick);
            case "pull" -> pull(level, tick);
            case "bud" -> bud(level, tick);
            case "bile_mortar" -> bileMortar(level, tick, target);
            default -> {
            }
        }
    }

    private void engulf(ServerLevel level, int tick) {
        if (tick == 3) {
            playSound(ModSounds.LEGION_GURGLE.get(), 2f, 0.7f);
        }
        if (tick == ENGULF.windup()) {
            List<LivingEntity> hit = strike(position(), getBbWidth() * 0.5 + 2.5, 0.9f, -0.6, 0.1, null);
            hit.forEach(victim -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this));
            GeneticsFx.burst(level, GeneticsFx.dust(0x6B2A2A, 2f), position().add(0, 1, 0), 40, getBbWidth() * 0.6, 0.1);
            playSound(ModSounds.LEGION_ENGULF.get(), 2.5f, 0.8f);
        }
    }

    private void sporeBurst(ServerLevel level, int tick) {
        if (tick < SPORE_BURST.windup() && tick % 4 == 0) {
            GeneticsFx.burst(level, GeneticsFx.dust(SPORE, 1.5f), position().add(0, getBbHeight() * 0.7, 0), 10,
                    getBbWidth() * 0.4, 0.02);
            playSound(ModSounds.LEGION_GURGLE.get(), 1.2f, 1.2f);
        }
        if (tick == SPORE_BURST.windup()) {
            sporeCloud(level, position(), 5f, 120);
            strike(position(), 6, 0.4f, 1.5, 0.4, null);
            ring(level, GeneticsFx.dust(SPORE, 2.5f), position().add(0, 0.5, 0), 4, 40);
            GeneticsFx.burst(level, ParticleTypes.SPORE_BLOSSOM_AIR, position().add(0, getBbHeight(), 0), 60, 3, 0.1);
            playSound(ModSounds.BOSS_SPORES.get(), 3f, 0.6f);
        }
    }

    private void pull(ServerLevel level, int tick) {
        int start = PULL.windup();
        if (tick == 2) {
            playSound(ModSounds.LEGION_GURGLE.get(), 2.5f, 0.5f);
        }
        if (tick < start || tick > start + 14) {
            return;
        }
        Vec3 core = position().add(0, getBbHeight() * 0.5, 0);
        if (tick % 2 == 0) {
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(22),
                    victim -> isValidVictim(victim) && hasLineOfSight(victim))) {
                Vec3 to = core.subtract(victim.position());
                if (to.lengthSqr() > 22 * 22) {
                    continue;
                }
                Vec3 pull = to.normalize().scale(0.35);
                victim.push(pull.x, Math.max(0.05, pull.y * 0.3), pull.z);
                victim.hurtMarked = true;
                GeneticsFx.stream(level, GeneticsFx.dust(0x6B2A2A, 1.1f), victim.position().add(0, victim.getBbHeight() * 0.5, 0),
                        core, 12);
            }
        }
        if (tick == start) {
            playSound(ModSounds.LEGION_PULL.get(), 3f, 0.7f);
        }
        if (tick == start + 14) {
            strike(position(), getBbWidth() * 0.5 + 2, 0.5f, 0.8, 0.3, null);
            playSound(ModSounds.LEGION_ENGULF.get(), 2.5f, 0.6f);
        }
    }

    private void bud(ServerLevel level, int tick) {
        if (tick < BUD.windup() && tick % 2 == 0) {
            GeneticsFx.burst(level, GeneticsFx.dust(0x6B2A2A, 2f), position().add(0, getBbHeight() * 0.5, 0), 6,
                    getBbWidth() * 0.5, 0.03);
            if (tick % 6 == 0) {
                playSound(ModSounds.LEGION_GURGLE.get(), 1.8f, 0.6f);
            }
        }
        if (tick == BUD.windup()) {
            spawnMinions(level, phase() >= 2 ? 5 : 3, 8);
        }
        if (tick == BUD.duration() - 1) {
            stun(40);
        }
    }

    private void bileMortar(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick == 2) {
            playSound(ModSounds.LEGION_GURGLE.get(), 2f, 1.0f);
        }
        if (target == null || tick != BILE_MORTAR.windup()) {
            return;
        }
        int count = phase() >= 2 ? 5 : 3;
        Vec3 origin = position().add(0, getBbHeight() * 0.95, 0);
        for (int i = 0; i < count; i++) {
            Vec3 landing = target.position().add((random.nextDouble() - 0.5) * 5, 0, (random.nextDouble() - 0.5) * 5);
            Vec3 to = landing.subtract(origin);
            double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
            BioProjectile bile = new BioProjectile(level, this, BioProjectile.Kind.BILE, attackDamage(0.35f));
            bile.setPos(origin.x, origin.y, origin.z);
            float speed = (float) (0.75 + horizontal * 0.028);
            bile.shoot(to.x, to.y + horizontal * 0.55, to.z, speed, 3f);
            level.addFreshEntity(bile);
        }
        GeneticsFx.burst(level, GeneticsFx.dust(0x7FA02A, 2f), origin, 24, 0.6, 0.1);
        playSound(ModSounds.LEGION_SPIT.get(), 2.5f, 0.8f);
    }

    @Override
    protected void onPhaseTwo(ServerLevel level) {
        spawnMinions(level, 4, 10);
        sporeCloud(level, position(), 6f, 140);
    }

    @Override
    protected SoundEvent roarSound() {
        return ModSounds.LEGION_ROAR.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.LEGION_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.LEGION_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.LEGION_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }
}
