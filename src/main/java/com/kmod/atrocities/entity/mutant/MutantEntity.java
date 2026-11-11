package com.kmod.atrocities.entity.mutant;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.entity.boss.BossAttack;
import com.kmod.atrocities.genetics.Archetype;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Mutante: criatura vanilla levada ao extremo pelo Soro Mutagenico.
 * <p>
 * Usa o mesmo motor de golpes dos chefes (telegrafos, animacoes GeckoLib, tremor de camera), mas luta sem
 * cutscenes e tem duas fases: a segunda, a <b>Furia</b>, comeca na metade da vida.
 * <p>
 * Um mutante <b>Alfa</b> (soro volatil na ultima dose) tem tier 2: 50% a mais de vida, 30% a mais de dano e
 * fica 15% maior.
 */
public abstract class MutantEntity extends AtrocityBoss {

    public static final int INTRO_TICKS = 80;
    public static final float ENRAGE_RATIO = 0.5f;

    protected MutantEntity(EntityType<? extends MutantEntity> type, Level level, BossEvent.BossBarColor color) {
        super(type, level, color);
        this.xpReward = 120;
    }

    /** Atributos base de todo mutante. */
    protected static AttributeSupplier.Builder mutantAttributes(double speed, double armor, double knockbackResistance) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, knockbackResistance)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.STEP_HEIGHT, 1.25D);
    }

    // ------------------------------------------------------------------ contrato

    /** Mob vanilla que deu origem a este mutante (para o Codex e as mensagens). */
    public abstract EntityType<?> origin();

    /** Vida do mutante comum (o Alfa tem 50% a mais). */
    protected abstract double mutantHealth();

    /** Dano base do mutante comum (o Alfa tem 30% a mais). */
    protected abstract double mutantDamage();

    /** Cor do soro/sangue do mutante, usada nas particulas. */
    protected abstract int mutantColor();

    protected abstract MutantSounds sounds();

    // ------------------------------------------------------------------ ganchos do motor dos chefes

    @Override
    public Archetype archetype() {
        return Archetype.COALESCED;
    }

    @Override
    protected boolean cinematic() {
        return false;
    }

    @Override
    public int maxPhase() {
        return 2;
    }

    @Override
    protected float phaseRatio(int phase) {
        return ENRAGE_RATIO;
    }

    @Override
    public int introTicks() {
        return INTRO_TICKS;
    }

    @Override
    protected double baseHealth(EmbryoGenome genome) {
        return mutantHealth() * (genome.tier() >= 2 ? 1.5 : 1.0);
    }

    @Override
    protected double baseDamage(int tier) {
        return mutantDamage() * (tier >= 2 ? 1.3 : 1.0);
    }

    @Override
    protected float tierScale(int tier) {
        return tier >= 2 ? 1.15f : 1.0f;
    }

    public boolean isAlpha() {
        return tier() >= 2;
    }

    @Override
    protected Component bossBarSuffix() {
        return isAlpha()
                ? Component.literal(" · ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("entity.atrocities.mutant.alpha").withStyle(ChatFormatting.GOLD))
                : Component.empty();
    }

    @Override
    public Component codexSubtitle() {
        return Component.translatable("codex.atrocities.mutant.subtitle", origin().getDescription());
    }

    @Override
    public Component codexHealth() {
        int health = (int) Math.round(mutantHealth());
        return Component.translatable("codex.atrocities.mutant.health", health, Math.round(health * 1.5));
    }

    @Override
    protected Component phaseMessage(int phase) {
        return Component.translatable("message.atrocities.mutant.enraged", getDisplayName())
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    }

    @Override
    public int fluidColor() {
        return mutantColor();
    }

    /** Inicializa um mutante recem-nascido (comum = tier 1, Alfa = tier 2). */
    public void initMutant(boolean alpha) {
        initFromGenome(new EmbryoGenome(archetype(), alpha ? 2 : 1, 100, List.of(), List.of(), 0));
    }

    @Override
    public void tick() {
        if (!level().isClientSide && genome() == null) {
            initMutant(false);
        }
        super.tick();
        if (level().isClientSide && isAlive() && phase() >= 2 && random.nextInt(3) == 0) {
            // Furia: vapor colorido saindo do corpo.
            level().addParticle(GeneticsFx.dust(mutantColor(), 1.1f),
                    getRandomX(0.6), getY() + random.nextDouble() * getBbHeight(), getRandomZ(0.6), 0, 0.05, 0);
        }
    }

    // ------------------------------------------------------------------ apoio

    /** Ponto a frente do mutante, na altura indicada (fracao da altura). */
    protected Vec3 front(double distance, double heightFraction) {
        return position().add(forward().scale(getBbWidth() * 0.5 + distance)).add(0, getBbHeight() * heightFraction, 0);
    }

    /** Salto em arco ate um ponto (usado por investidas aereas). */
    protected void leapTowards(Vec3 to, double height) {
        Vec3 delta = to.subtract(position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double vy = Math.sqrt(2 * 0.08 * Math.max(0.5, height + Math.max(0, delta.y)));
        double airTime = vy / 0.08 + Math.sqrt(2 * Math.max(0.5, height) / 0.08);
        double speed = Math.min(2.2, horizontal / Math.max(1, airTime) / 0.91);
        Vec3 dir = horizontal < 1.0E-3 ? forward() : new Vec3(delta.x / horizontal, 0, delta.z / horizontal);
        setDeltaMovement(dir.x * speed, vy, dir.z * speed);
        hasImpulse = true;
    }

    /** Explosao sem quebrar blocos: dano do golpe, empurrao e efeitos. */
    protected List<LivingEntity> blast(ServerLevel level, Vec3 at, double radius, float multiplier, double push) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        GeneticsFx.burst(level, GeneticsFx.dust(mutantColor(), 2.5f), at, 50, radius * 0.4, 0.2);
        GeneticsFx.burst(level, ParticleTypes.LARGE_SMOKE, at, 20, radius * 0.3, 0.05);
        level.playSound(null, at.x, at.y, at.z, ModSounds.MUTANT_BLAST.get(), getSoundSource(), 3f,
                0.8f + random.nextFloat() * 0.2f);
        com.kmod.atrocities.fx.BossFx.shake(level, at, radius * 4, 1.2f, 16);
        return strike(at, radius, multiplier, push, 0.6, null);
    }

    /** Carga volatil (projetil) que tocou em algo. */
    public void chargeBlast(ServerLevel level, Vec3 at) {
        blast(level, at, 2.6, 0.6f, 1.0);
    }

    @Override
    protected boolean canStartAttack(BossAttack attack, LivingEntity target) {
        return hasLineOfSight(target);
    }

    // ------------------------------------------------------------------ sons

    @Override
    protected SoundEvent roarSound() {
        return sounds().roar().get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return sounds().ambient().get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return sounds().hurt().get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return sounds().death().get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.0f;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Nullable
    @Override
    protected EntityType<?> fallbackMinion() {
        return origin();
    }
}
