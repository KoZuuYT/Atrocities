package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.genetics.Archetype;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Colosso Quitinoso: tanque blindado da linhagem quitinosa.
 * <p>Golpes:</p>
 * <ul>
 *   <li>Esmagamento: bracos sobem e caem na frente (area).</li>
 *   <li>Varredura: golpe lateral em arco que arremessa longe.</li>
 *   <li>Investida: dispara em linha reta; se bater numa parede ou num Pilar fica atordoado.</li>
 *   <li>Saraivada de Espinhos: leque de espinhos das costas.</li>
 *   <li>Terremoto (fase 2+): tres aneis de choque que so atingem quem esta no chao.</li>
 *   <li>Debandada (fase 3): tres investidas seguidas, mirando de novo a cada uma.</li>
 * </ul>
 * <p>Mecanicas:</p>
 * <ul>
 *   <li><b>Fase 1 - Couraca Viva</b>: golpes pela frente causam 40% do dano; pelas costas, 115%.</li>
 *   <li><b>Fase 2 - Pilares de Quitina</b>: tres pilares surgem na arena e, enquanto existirem, o Colosso
 *   recebe so 60% do dano. Quebre-os ou faca o Colosso investir contra eles (o pilar estoura e ele fica tonto).</li>
 *   <li><b>Fase 3 - Nucleo Exposto</b>: a carapaca explode. Sem armadura nem couraca, mais rapido, e os
 *   atordoamentos duram 50% a mais.</li>
 * </ul>
 */
public class ChitinousColossusEntity extends AtrocityBoss {

    private static final BossAttack SLAM = new BossAttack("slam", 18, 36, 60, 0f, 5.5f, 4, 1);
    private static final BossAttack SWEEP = new BossAttack("sweep", 12, 26, 50, 0f, 4.5f, 3, 1);
    private static final BossAttack CHARGE = new BossAttack("charge", 20, 46, 160, 6f, 26f, 3, 1);
    private static final BossAttack SPINE_VOLLEY = new BossAttack("spine_volley", 16, 30, 120, 6f, 32f, 2, 1);
    private static final BossAttack QUAKE = new BossAttack("quake", 24, 50, 200, 0f, 12f, 3, 2);
    private static final BossAttack RAMPAGE = new BossAttack("rampage", 16, 16 + 3 * RampageStep.LENGTH, 240,
            5f, 26f, 4, 3, "charge");
    private static final List<BossAttack> ATTACKS = List.of(SLAM, SWEEP, CHARGE, SPINE_VOLLEY, QUAKE, RAMPAGE);

    private static final int CHITIN = 0xC9A15A;
    private static final int DASH_TICKS = 20;
    private static final int CHARGE_STUN = 70;
    private static final float FRONT_FACTOR = 0.4f;
    private static final float BACK_FACTOR = 1.15f;
    private static final float PYLON_FACTOR = 0.6f;
    private static final ResourceLocation SHED_ARMOR =
            ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "colossus_shed_armor");

    /** Tempos de cada investida da Debandada. */
    private static final class RampageStep {
        static final int DASH = 16;
        static final int PAUSE = 12;
        static final int LENGTH = DASH + PAUSE;
    }

    private Vec3 chargeDirection = Vec3.ZERO;
    private final Set<UUID> chargeHits = new HashSet<>();
    private final List<Vec3> pylonSites = new ArrayList<>();

    public ChitinousColossusEntity(EntityType<? extends ChitinousColossusEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(0.24D, 8.0D, 3.0D);
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

    private int stunTicks(int base) {
        return phase() >= MAX_PHASE ? Math.round(base * 1.5f) : base;
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "slam" -> slam(level, tick);
            case "sweep" -> sweep(level, tick);
            case "charge" -> charge(level, tick, target);
            case "spine_volley" -> spineVolley(level, tick, target);
            case "quake" -> quake(level, tick);
            case "rampage" -> rampage(level, tick, target);
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
            aimCharge(target);
            playSound(ModSounds.BOSS_CHARGE.get(), 3f, 0.7f);
        }
        if (tick < windup + DASH_TICKS) {
            dash(level, tick > windup + 2, phase() >= 2 ? 1.05 : 0.9);
        } else {
            setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
        }
    }

    private void aimCharge(@Nullable LivingEntity target) {
        Vec3 aim = target != null ? target.position().subtract(position()).multiply(1, 0, 1) : forward();
        chargeDirection = aim.lengthSqr() < 1.0E-4 ? forward() : aim.normalize();
        chargeHits.clear();
    }

    /**
     * Um tick de investida: empurra o Colosso, atropela quem estiver na frente e verifica colisoes.
     *
     * @return {@code true} se a investida terminou numa parede ou num pilar (o golpe foi interrompido)
     */
    private boolean dash(ServerLevel level, boolean canCollide, double speed) {
        setDeltaMovement(chargeDirection.x * speed, getDeltaMovement().y, chargeDirection.z * speed);
        setYRot((float) (Mth.atan2(chargeDirection.z, chargeDirection.x) * Mth.RAD_TO_DEG) - 90f);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        Vec3 front = position().add(chargeDirection.scale(getBbWidth() * 0.5));
        strike(front, getBbWidth() * 0.5 + 0.8, 1.2f, 2.2, 0.6, victim -> chargeHits.add(victim.getUUID()));
        groundCrack(level, position(), getBbWidth() * 0.4, 6);
        if (!canCollide) {
            return false;
        }
        // Pilar no caminho: o Colosso o despedaca e fica tonto com o impacto.
        AABB ahead = getBoundingBox().move(chargeDirection.scale(1.2)).inflate(0.4);
        for (BossOrganEntity pylon : organs(BossOrganEntity.Variant.PYLON)) {
            if (pylon.getBoundingBox().intersects(ahead)) {
                Vec3 at = pylon.position().add(0, pylon.getBbHeight() * 0.5, 0);
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.COLOSSUS_CARAPACE.get())),
                        at.x, at.y, at.z, 40, 0.6, 1.0, 0.6, 0.2);
                pylon.hurt(damageSources().genericKill(), Float.MAX_VALUE);
                crash(level, at);
                hint(level, "colossus.pylon_rammed", ChatFormatting.GOLD);
                stun(stunTicks(CHARGE_STUN + 30));
                return true;
            }
        }
        if (horizontalCollision) {
            crash(level, position().add(chargeDirection.scale(getBbWidth() * 0.6)).add(0, getBbHeight() * 0.4, 0));
            stun(stunTicks(CHARGE_STUN));
            return true;
        }
        return false;
    }

    private void crash(ServerLevel level, Vec3 impact) {
        GeneticsFx.burst(level, ParticleTypes.EXPLOSION, impact, 3, 0.6, 0);
        groundCrack(level, impact, 1.5, 50);
        playSound(ModSounds.BOSS_SLAM.get(), 3.5f, 0.5f);
        setDeltaMovement(chargeDirection.scale(-0.4));
    }

    private void rampage(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = RAMPAGE.windup();
        if (tick < start) {
            if (tick % 3 == 0) {
                groundCrack(level, position().add(forward().scale(-getBbWidth() * 0.4)), 1.0, 12);
                playSound(ModSounds.BOSS_STOMP.get(), 1.8f, 0.7f);
            }
            return;
        }
        int local = (tick - start) % RampageStep.LENGTH;
        if (local == 0) {
            aimCharge(target);
            if (tick > start) {
                replayAnimation(RAMPAGE.animation());
            }
            playSound(ModSounds.BOSS_CHARGE.get(), 3f, 0.75f + (tick - start) / (float) RAMPAGE.duration() * 0.4f);
            BossFx.shake(level, position(), 24, 0.4f, 6);
        }
        if (local < RampageStep.DASH) {
            dash(level, local > 2, 1.15);
        } else {
            setDeltaMovement(getDeltaMovement().multiply(0.4, 1, 0.4));
            if (target != null) {
                faceTowards(target, 20f);
                // Aviso da proxima investida: linha tracejada ate o alvo.
                if (local % 2 == 0) {
                    Vec3 from = position().add(0, 0.2, 0);
                    Vec3 to = target.position().add(0, 0.2, 0);
                    for (int i = 0; i < 16; i++) {
                        Vec3 p = from.lerp(to, i / 16.0);
                        level.sendParticles(GeneticsFx.dust(0xFF2A1E, 1.3f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
                    }
                }
            }
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

    // ------------------------------------------------------------------ mecanicas

    @Override
    protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float amount) {
        float factor = 1f;
        Vec3 from = source.getSourcePosition();
        if (phase() < MAX_PHASE && !isStunned() && from != null) {
            Vec3 to = from.subtract(position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 1.0E-4) {
                double cos = forward().dot(to.normalize());
                if (cos > 0.5) {
                    // Pela frente: a couraca absorve a maior parte.
                    factor *= FRONT_FACTOR;
                    deflectFeedback(level, source, 1.2f);
                    hint(level, "colossus.front", ChatFormatting.YELLOW);
                } else if (cos < -0.5) {
                    // Pelas costas: o ponto fraco brilha.
                    factor *= BACK_FACTOR;
                    Vec3 back = position().add(forward().scale(-getBbWidth() * 0.5)).add(0, getBbHeight() * 0.6, 0);
                    level.sendParticles(GeneticsFx.dust(0xFF9A2A, 2f), back.x, back.y, back.z, 14, 0.3, 0.3, 0.3, 0.05);
                    level.sendParticles(ParticleTypes.CRIT, back.x, back.y, back.z, 8, 0.3, 0.3, 0.3, 0.3);
                }
            }
        }
        if (phase() == 2 && !organs(BossOrganEntity.Variant.PYLON).isEmpty()) {
            factor *= PYLON_FACTOR;
            hint(level, "colossus.pylons", ChatFormatting.YELLOW);
            if (factor >= FRONT_FACTOR) {
                deflectFeedback(level, source, 0.8f);
            }
        }
        return amount * factor;
    }

    @Override
    protected void tickPhaseMechanics(ServerLevel level) {
        if (phase() < MAX_PHASE && tickCount % 8 == 0) {
            // Brilho alaranjado nas costas: o ponto fraco.
            Vec3 back = position().add(forward().scale(-getBbWidth() * 0.48)).add(0, getBbHeight() * 0.65, 0);
            level.sendParticles(GeneticsFx.dust(0xFF9A2A, 1.4f), back.x, back.y, back.z, 3, 0.25, 0.25, 0.25, 0.01);
        }
        if (phase() >= MAX_PHASE && tickCount % 4 == 0) {
            // Nucleo exposto pulsando.
            Vec3 core = position().add(0, getBbHeight() * 0.6, 0);
            level.sendParticles(GeneticsFx.dust(0xFFB040, 1.6f), core.x, core.y, core.z, 4, getBbWidth() * 0.3, 0.3,
                    getBbWidth() * 0.3, 0.02);
            if (tickCount % 20 == 0) {
                level.sendParticles(ParticleTypes.LAVA, core.x, core.y, core.z, 2, 0.4, 0.4, 0.4, 0);
            }
        }
    }

    @Override
    protected void onPhaseStart(ServerLevel level, int phase) {
        if (phase == 2) {
            pylonSites.clear();
            pylonSites.addAll(pointsAround(level, 3, 9.0, Mth.PI / 3));
        }
    }

    @Override
    protected void onIntroTick(ServerLevel level, int tick) {
        // Rachaduras em anel se espalhando enquanto o Colosso rompe o chao.
        if (tick < 60 && tick % 10 == 0) {
            double radius = 2 + tick / 10.0 * 1.4;
            for (int i = 0; i < 10; i++) {
                double angle = i / 10.0 * Mth.TWO_PI + tick;
                groundCrack(level, position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 0.5, 8);
            }
            playSound(ModSounds.BOSS_STOMP.get(), 2.5f, 0.5f);
        }
        if (tick == 60) {
            GeneticsFx.burst(level, ParticleTypes.CRIT, position().add(0, getBbHeight(), 0), 80, getBbWidth() * 0.6, 0.5);
        }
    }

    @Override
    protected void onTransitionTick(ServerLevel level, int phase, int tick) {
        if (phase == 2) {
            // Os pilares anunciam onde vao surgir.
            float progress = tick / (float) (TRANSITION_TICKS - TRANSITION_CLIMAX_AT);
            if (progress <= 1f && tick % 2 == 0) {
                for (Vec3 site : pylonSites) {
                    eruptionWarning(level, site, CHITIN, progress);
                }
            }
            if (tick % 8 == 0) {
                playSound(ModSounds.BOSS_STOMP.get(), 2.5f, 0.5f);
            }
        } else if (phase == MAX_PHASE && tick < TRANSITION_TICKS - TRANSITION_CLIMAX_AT) {
            // A carapaca racha: faiscas e lascas cada vez mais intensas.
            float progress = tick / (float) (TRANSITION_TICKS - TRANSITION_CLIMAX_AT);
            Vec3 body = position().add(0, getBbHeight() * 0.6, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, body.x, body.y, body.z, 2 + Math.round(progress * 8),
                    getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.1);
            if (tick % 6 == 0) {
                playSound(ModSounds.BOSS_ARMOR_CLANG.get(), 2f, 0.5f + progress * 0.4f);
            }
        }
    }

    @Override
    protected void onPhaseClimax(ServerLevel level, int phase) {
        if (phase == 2) {
            for (Vec3 site : pylonSites) {
                spawnOrgan(level, BossOrganEntity.Variant.PYLON, site, 40f, 20f);
            }
            pylonSites.clear();
        } else if (phase == MAX_PHASE) {
            // A carapaca explode: sem armadura a partir daqui.
            for (BossOrganEntity pylon : organs(BossOrganEntity.Variant.PYLON)) {
                pylon.hurt(damageSources().genericKill(), Float.MAX_VALUE);
            }
            Vec3 body = position().add(0, getBbHeight() * 0.6, 0);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.COLOSSUS_CARAPACE.get())),
                    body.x, body.y, body.z, 120, getBbWidth() * 0.6, getBbHeight() * 0.4, getBbWidth() * 0.6, 0.4);
            GeneticsFx.burst(level, ParticleTypes.EXPLOSION, body, 4, getBbWidth() * 0.5, 0);
            playSound(ModSounds.BOSS_BURST.get(), 4f, 0.8f);
            shedArmor();
        }
    }

    private void shedArmor() {
        for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS)) {
            AttributeInstance instance = getAttribute(attribute);
            if (instance != null) {
                instance.addOrReplacePermanentModifier(new AttributeModifier(SHED_ARMOR, -1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (phase() >= MAX_PHASE) {
            shedArmor();
        }
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
