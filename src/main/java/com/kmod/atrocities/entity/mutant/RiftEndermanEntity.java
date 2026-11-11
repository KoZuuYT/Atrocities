package com.kmod.atrocities.entity.mutant;

import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.entity.boss.BossAttack;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Enderman Fendedor: enderman esticado ate quatro metros, com bracos-lamina e uma fenda roxa aberta no peito.
 * <ul>
 *   <li>Garra: golpe largo a frente.</li>
 *   <li>Golpe Fantasma: some e reaparece atras do alvo, golpeando em seguida.</li>
 *   <li>Estilhacos da Fenda: atira cristais que teleportam quem acertam (5 na Furia).</li>
 *   <li>Vortice: suga todos para perto por 2 s e implode.</li>
 *   <li>Tempestade de Fendas (Furia): tres teleportes seguidos, cada um com um golpe em area.</li>
 * </ul>
 * Desvia de 35% dos projeteis com um teleporte. <b>Agua</b> (balde, chuva de pocao de agua) o deixa atordoado.
 */
public class RiftEndermanEntity extends MutantEntity {

    private static final BossAttack CLAW = new BossAttack("claw", 8, 20, 30, 0f, 3.6f, 5, 1);
    private static final BossAttack BLINK_STRIKE = new BossAttack("blink_strike", 10, 30, 90, 4f, 24f, 4, 1);
    private static final BossAttack SHARDS = new BossAttack("shards", 14, 30, 110, 4f, 28f, 3, 1);
    private static final BossAttack VORTEX = new BossAttack("vortex", 40, 60, 240, 0f, 10f, 3, 1);
    private static final BossAttack RIFT_STORM = new BossAttack("rift_storm", 16, 64, 200, 0f, 24f, 3, 2);
    private static final List<BossAttack> ATTACKS = List.of(CLAW, BLINK_STRIKE, SHARDS, VORTEX, RIFT_STORM);

    public static final int COLOR = 0xFF4FD8;
    private static final double VORTEX_PULL_RADIUS = 11.0;
    private static final double VORTEX_RADIUS = 4.5;
    private static final float DODGE_CHANCE = 0.35f;
    private static final int WATER_STUN = 70;
    private static final int WATER_STUN_COOLDOWN = 300;

    private int waterCooldown;

    public RiftEndermanEntity(EntityType<? extends RiftEndermanEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.PINK);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return mutantAttributes(0.33D, 4.0D, 0.7D);
    }

    @Override
    public EntityType<?> origin() {
        return EntityType.ENDERMAN;
    }

    @Override
    protected double mutantHealth() {
        return 540;
    }

    @Override
    protected double mutantDamage() {
        return 15;
    }

    @Override
    protected int mutantColor() {
        return COLOR;
    }

    @Override
    protected MutantSounds sounds() {
        return ModSounds.RIFT_ENDERMAN;
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
    protected Telegraph telegraph(BossAttack attack) {
        return switch (attack.id()) {
            case "blink_strike", "shards" -> Telegraph.LINE;
            case "rift_storm" -> Telegraph.NONE;
            default -> Telegraph.AREA;
        };
    }

    @Override
    protected double telegraphRadius(BossAttack attack) {
        return attack == VORTEX ? VORTEX_RADIUS : 3.6;
    }

    @Override
    protected boolean canStartAttack(BossAttack attack, LivingEntity target) {
        // Teleportes nao precisam de linha de visao.
        return attack == BLINK_STRIKE || attack == RIFT_STORM || hasLineOfSight(target);
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "claw" -> claw(level, tick);
            case "blink_strike" -> blinkStrike(level, tick, target);
            case "shards" -> shards(level, tick, target);
            case "vortex" -> vortex(level, tick);
            case "rift_storm" -> riftStorm(level, tick, target);
            default -> {
            }
        }
    }

    private void claw(ServerLevel level, int tick) {
        if (tick == CLAW.windup()) {
            Vec3 at = front(1.2, 0.45);
            strike(position(), 3.6 + getBbWidth() * 0.5, 1.0f, 1.2, 0.3, target -> inFront(target, 75));
            GeneticsFx.burst(level, ParticleTypes.SWEEP_ATTACK, at, 3, 0.8, 0);
            GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 1.4f), at, 14, 0.8, 0.05);
            playSound(ModSounds.BOSS_SWING.get(), 2f, 1.25f);
        }
    }

    /** Some num estalo, reaparece atras do alvo e golpeia. */
    private void blinkStrike(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = BLINK_STRIKE.windup();
        if (tick == start && target != null) {
            Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(2.2));
            teleportTo(level, behind);
            faceTowards(target, 180f);
        }
        if (tick == start + 6) {
            strike(position(), 3.4 + getBbWidth() * 0.5, 1.2f, 1.4, 0.4, victim -> inFront(victim, 80));
            GeneticsFx.burst(level, ParticleTypes.SWEEP_ATTACK, front(1.0, 0.45), 4, 0.8, 0);
            playSound(ModSounds.BOSS_SWING.get(), 2f, 1.1f);
        }
    }

    private void shards(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick != SHARDS.windup() || target == null) {
            return;
        }
        int count = phase() >= 2 ? 5 : 3;
        Vec3 origin = position().add(0, getBbHeight() * 0.65, 0);
        Vec3 aim = target.getEyePosition().subtract(origin);
        for (int i = 0; i < count; i++) {
            double spread = (i - (count - 1) / 2.0) * 0.16;
            Vec3 dir = aim.yRot((float) spread);
            BioProjectile shard = new BioProjectile(level, this, BioProjectile.Kind.SHARD, attackDamage(0.7f));
            shard.setPos(origin.x, origin.y, origin.z);
            shard.shoot(dir.x, dir.y + aim.horizontalDistance() * 0.04, dir.z, 1.5f, 1f);
            level.addFreshEntity(shard);
        }
        GeneticsFx.burst(level, ParticleTypes.REVERSE_PORTAL, origin, 30, 0.4, 0.2);
        playSound(ModSounds.RIFT_BLINK.get(), 1.6f, 1.5f);
    }

    /** Suga todos por 2 s, implode e joga longe. */
    private void vortex(ServerLevel level, int tick) {
        int fire = VORTEX.windup();
        Vec3 core = position().add(0, getBbHeight() * 0.5, 0);
        if (tick == 0) {
            hint(level, "rift.vortex", ChatFormatting.LIGHT_PURPLE);
            playSound(ModSounds.RIFT_VORTEX.get(), 3f, 0.7f);
        }
        if (tick < fire) {
            float progress = tick / (float) fire;
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(VORTEX_PULL_RADIUS),
                    this::isValidVictim)) {
                Vec3 to = core.subtract(victim.position()).multiply(1, 0, 1);
                double distance = to.length();
                if (distance < 1.0 || distance > VORTEX_PULL_RADIUS) {
                    continue;
                }
                double strength = 0.06 + 0.06 * progress;
                victim.push(to.x / distance * strength, 0, to.z / distance * strength);
                victim.hurtMarked = true;
            }
            if (tick % 2 == 0) {
                double radius = VORTEX_PULL_RADIUS * (1 - progress * 0.7);
                double spin = tick * 0.35;
                for (int i = 0; i < 6; i++) {
                    double angle = spin + i * Math.PI / 3;
                    level.sendParticles(ParticleTypes.PORTAL, getX() + Math.cos(angle) * radius, getY() + 0.4,
                            getZ() + Math.sin(angle) * radius, 3, 0.1, 0.3, 0.1, 0.5);
                }
                level.sendParticles(GeneticsFx.dust(COLOR, 2f), core.x, core.y, core.z, 4, 0.3, 0.3, 0.3, 0.02);
            }
            return;
        }
        if (tick == fire) {
            level.sendParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            GeneticsFx.burst(level, ParticleTypes.REVERSE_PORTAL, core, 120, 1.0, 0.6);
            GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 3f), core, 60, 1.2, 0.3);
            strike(position(), VORTEX_RADIUS + getBbWidth() * 0.5, 1.8f, 2.4, 0.8, null);
            groundCrack(level, position(), VORTEX_RADIUS * 0.7, 80);
            playSound(ModSounds.MUTANT_BLAST.get(), 3f, 1.3f);
        }
    }

    /** Furia: tres teleportes ao redor do alvo, cada um seguido de um golpe em area. */
    private void riftStorm(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int step = (tick - RIFT_STORM.windup());
        if (step < 0 || target == null) {
            return;
        }
        int[] blinks = {0, 16, 32};
        for (int i = 0; i < blinks.length; i++) {
            if (step == blinks[i]) {
                double angle = random.nextDouble() * Math.PI * 2;
                Vec3 spot = target.position().add(Math.cos(angle) * 2.6, 0, Math.sin(angle) * 2.6);
                teleportTo(level, spot);
                faceTowards(target, 180f);
                replayAnimation(RIFT_STORM.animation());
                ring(level, GeneticsFx.dust(DANGER_COLOR, 1.3f), position().add(0, 0.15, 0), 3.4, 24);
            }
            if (step == blinks[i] + 9) {
                strike(position(), 3.4 + getBbWidth() * 0.5, 1.0f, 1.6, 0.5, null);
                ring(level, ParticleTypes.REVERSE_PORTAL, position().add(0, 0.4, 0), 3.0, 30);
                GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 2f), position().add(0, 1, 0), 20, 1.2, 0.1);
                playSound(ModSounds.BOSS_SWING.get(), 2f, 0.9f);
            }
        }
    }

    private static final int DANGER_COLOR = 0xFF2A2A;

    /** Teleporte com particulas nos dois pontos (procura chao perto do destino). */
    private void teleportTo(ServerLevel level, Vec3 to) {
        Vec3 from = position();
        Vec3 ground = groundNear(level, to.x, to.z);
        if (!randomTeleport(ground.x, ground.y, ground.z, false)) {
            blinkNear(level, to, 2.5);
        }
        com.kmod.atrocities.fx.Vfx.rift(level, from.add(0, getBbHeight() * 0.5, 0), getBbHeight() * 0.55f, COLOR, 14);
        com.kmod.atrocities.fx.Vfx.rift(level, position().add(0, getBbHeight() * 0.5, 0), getBbHeight() * 0.55f, COLOR, 14);
        GeneticsFx.burst(level, ParticleTypes.REVERSE_PORTAL, from.add(0, getBbHeight() * 0.5, 0), 50, 0.5, 0.1);
        GeneticsFx.burst(level, ParticleTypes.PORTAL, position().add(0, getBbHeight() * 0.5, 0), 60, 0.5, 0.6);
        GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 2f), position().add(0, getBbHeight() * 0.5, 0), 20, 0.5, 0.1);
        playSound(ModSounds.RIFT_BLINK.get(), 2f, 0.9f + random.nextFloat() * 0.2f);
    }

    // ------------------------------------------------------------------ agua e esquiva

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level() instanceof ServerLevel level && !isEmerging() && !isDeadOrDying()) {
            boolean splash = source.getDirectEntity() instanceof ThrownPotion potion
                    && potion.getItem().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY).is(net.minecraft.world.item.alchemy.Potions.WATER);
            if (source.is(DamageTypes.DROWN) || splash) {
                // A chuva so incomoda; balde de agua ou pocao de agua o atordoam.
                if ((splash || isInWaterOrBubble()) && waterCooldown <= 0) {
                    waterCooldown = WATER_STUN_COOLDOWN;
                    GeneticsFx.burst(level, ParticleTypes.SPLASH, position().add(0, getBbHeight() * 0.6, 0), 60, 0.6, 0.2);
                    hint(level, "rift.water", ChatFormatting.AQUA);
                    stun(WATER_STUN);
                }
                return splash && super.hurt(source, amount);
            }
            if (source.is(DamageTypeTags.IS_PROJECTILE) && !isStunned() && random.nextFloat() < DODGE_CHANCE
                    && getTarget() != null) {
                blinkNear(level, getTarget().position(), 6);
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (waterCooldown > 0) {
            waterCooldown--;
        }
        if (level().isClientSide && isAlive() && random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.PORTAL, getRandomX(0.6), getRandomY() - 0.25, getRandomZ(0.6),
                    (random.nextDouble() - 0.5) * 2, -random.nextDouble(), (random.nextDouble() - 0.5) * 2);
        }
    }

    @Override
    protected void onAttackCancelled(BossAttack attack) {
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT, 0.15f, 0.5f);
    }
}
