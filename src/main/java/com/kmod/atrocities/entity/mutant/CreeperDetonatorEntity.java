package com.kmod.atrocities.entity.mutant;

import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.entity.boss.BossAttack;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Creeper Detonador: quadrupede com bolsas de polvora pulsando nas costas.
 * <ul>
 *   <li>Cabecada: empurrao fortissimo a frente.</li>
 *   <li>Bote Explosivo: salta sobre o alvo e explode ao cair.</li>
 *   <li>Cargas Volateis: cospe bolotas que explodem ao tocar em algo (5 na Furia).</li>
 *   <li>Supernova: incha por 2,5 s e explode num raio enorme. <b>Desarme</b>: cause 12% da vida dele em
 *   dano durante a carga e ele apaga, atordoado.</li>
 *   <li>Ultimo Suspiro: explode ao morrer.</li>
 * </ul>
 */
public class CreeperDetonatorEntity extends MutantEntity {

    private static final BossAttack HEADBUTT = new BossAttack("headbutt", 8, 22, 40, 0f, 3.2f, 5, 1);
    private static final BossAttack POUNCE = new BossAttack("pounce", 12, 50, 120, 5f, 18f, 4, 1);
    private static final BossAttack CHARGES = new BossAttack("charges", 14, 34, 150, 4f, 24f, 3, 1);
    private static final BossAttack SUPERNOVA = new BossAttack("supernova", 50, 70, 260, 0f, 8f, 3, 1);
    private static final List<BossAttack> ATTACKS = List.of(HEADBUTT, POUNCE, CHARGES, SUPERNOVA);

    private static final int COLOR = 0x6BFF3A;
    private static final double SUPERNOVA_RADIUS = 7.0;
    private static final float DEFUSE_FRACTION = 0.12f;
    private static final int DEFUSE_STUN = 90;

    private boolean airborne;
    private float supernovaDamage;

    public CreeperDetonatorEntity(EntityType<? extends CreeperDetonatorEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return mutantAttributes(0.29D, 3.0D, 0.8D);
    }

    @Override
    public EntityType<?> origin() {
        return EntityType.CREEPER;
    }

    @Override
    protected double mutantHealth() {
        return 520;
    }

    @Override
    protected double mutantDamage() {
        return 14;
    }

    @Override
    protected int mutantColor() {
        return COLOR;
    }

    @Override
    protected MutantSounds sounds() {
        return ModSounds.CREEPER_DETONATOR;
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
            case "pounce", "charges" -> Telegraph.LINE;
            default -> Telegraph.AREA;
        };
    }

    @Override
    protected double telegraphRadius(BossAttack attack) {
        return attack == SUPERNOVA ? SUPERNOVA_RADIUS : 3.0;
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "headbutt" -> headbutt(level, tick);
            case "pounce" -> pounce(level, tick, target);
            case "charges" -> charges(level, tick, target);
            case "supernova" -> supernova(level, tick);
            default -> {
            }
        }
    }

    private void headbutt(ServerLevel level, int tick) {
        if (tick == HEADBUTT.windup()) {
            Vec3 at = front(1.0, 0.35);
            strike(at, 2.4, 0.9f, 2.4, 0.55, null);
            GeneticsFx.burst(level, ParticleTypes.CLOUD, at, 10, 0.4, 0.1);
            GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 1.5f), at, 12, 0.4, 0.1);
            playSound(ModSounds.BOSS_SWING.get(), 2f, 1.1f);
        }
    }

    private void pounce(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = POUNCE.windup();
        if (tick == 3) {
            playSound(ModSounds.DETONATOR_FUSE.get(), 1.4f, 1.3f);
        }
        if (tick == start) {
            leapTowards(target != null ? target.position() : position().add(forward().scale(8)), 3.0);
            airborne = true;
            playSound(ModSounds.MUTANT_LEAP.get(), 2f, 1.0f);
        }
        if (airborne && tick > start + 3 && onGround()) {
            airborne = false;
            blast(level, position().add(0, 0.5, 0), 3.6, 1.0f, 1.4);
            groundCrack(level, position(), 2.4, 60);
        } else if (airborne && tick == POUNCE.duration() - 1) {
            airborne = false;
        }
    }

    private void charges(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick == 2) {
            playSound(ModSounds.DETONATOR_FUSE.get(), 1.6f, 1.6f);
        }
        if (tick != CHARGES.windup() || target == null) {
            return;
        }
        int count = phase() >= 2 ? 5 : 3;
        Vec3 origin = position().add(0, getBbHeight() * 0.8, 0);
        for (int i = 0; i < count; i++) {
            Vec3 spot = target.position().add((random.nextDouble() - 0.5) * 5, 0, (random.nextDouble() - 0.5) * 5);
            if (i == 0) {
                spot = target.position();
            }
            Vec3 delta = spot.subtract(origin);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            BioProjectile charge = new BioProjectile(level, this, BioProjectile.Kind.BLAST, 0f);
            charge.setPos(origin.x, origin.y, origin.z);
            // Arco: sobe mais quanto mais longe for o alvo.
            charge.shoot(delta.x, delta.y + horizontal * 0.45, delta.z, (float) Math.min(1.6, 0.5 + horizontal * 0.06), 2f);
            level.addFreshEntity(charge);
        }
        GeneticsFx.burst(level, ParticleTypes.LARGE_SMOKE, origin, 12, 0.4, 0.05);
        playSound(ModSounds.WEAVER_WEB.get(), 2f, 0.6f);
    }

    private void supernova(ServerLevel level, int tick) {
        int fire = SUPERNOVA.windup();
        if (tick == 0) {
            supernovaDamage = 0;
            hint(level, "detonator.supernova", ChatFormatting.YELLOW);
        }
        if (tick < fire) {
            if (tick % 10 == 0) {
                playSound(ModSounds.DETONATOR_FUSE.get(), 2.5f, 0.6f + tick / (float) fire * 0.8f);
            }
            if (tick % 3 == 0) {
                GeneticsFx.burst(level, ParticleTypes.SMOKE, position().add(0, getBbHeight(), 0), 4, 0.5, 0.02);
            }
            return;
        }
        if (tick == fire) {
            blast(level, position().add(0, 0.8, 0), SUPERNOVA_RADIUS, 2.0f, 2.2);
            groundCrack(level, position(), SUPERNOVA_RADIUS * 0.7, 120);
            for (int i = 1; i <= 3; i++) {
                ring(level, ParticleTypes.FLAME, position().add(0, 0.3, 0), SUPERNOVA_RADIUS * i / 3.0, 30 * i);
            }
            if (phase() >= 2) {
                // Furia: a explosao deixa cargas espalhadas.
                for (int i = 0; i < 4; i++) {
                    double angle = i * Math.PI / 2 + random.nextDouble() * 0.5;
                    BioProjectile charge = new BioProjectile(level, this, BioProjectile.Kind.BLAST, 0f);
                    charge.setPos(getX(), getY() + getBbHeight(), getZ());
                    charge.shoot(Math.cos(angle), 1.2, Math.sin(angle), 0.8f, 3f);
                    level.addFreshEntity(charge);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float before = getHealth();
        boolean result = super.hurt(source, amount);
        if (result && currentAttack == SUPERNOVA && attackTick < SUPERNOVA.windup() && level() instanceof ServerLevel level) {
            supernovaDamage += Math.max(0, before - getHealth());
            if (supernovaDamage >= getMaxHealth() * DEFUSE_FRACTION) {
                // Desarmado: a carga apaga e ele fica tonto.
                GeneticsFx.burst(level, ParticleTypes.LARGE_SMOKE, position().add(0, getBbHeight() * 0.6, 0), 40, 0.8, 0.05);
                playSound(ModSounds.DETONATOR_DEFUSED.get(), 2.5f, 0.8f);
                hint(level, "detonator.defused", ChatFormatting.GOLD);
                stun(DEFUSE_STUN);
            }
        }
        return result;
    }

    @Override
    protected void tickDeath() {
        if (deathTime == DEATH_TICKS - 1 && level() instanceof ServerLevel level) {
            // Ultimo Suspiro.
            blast(level, position().add(0, 0.8, 0), 5.0, 1.2f, 2.0);
        }
        super.tickDeath();
    }

    @Override
    protected void onAttackCancelled(BossAttack attack) {
        airborne = false;
        supernovaDamage = 0;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.RAVAGER_STEP, 0.6f, 1.3f);
    }
}
