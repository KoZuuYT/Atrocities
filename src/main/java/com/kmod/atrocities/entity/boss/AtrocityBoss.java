package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.combat.DamageMitigation;
import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.genetics.Archetype;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Species;
import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.genetics.TraitRoll;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base dos chefes nascidos da incubacao.
 * <ul>
 *   <li><b>Genoma</b>: tier define vida, dano e tamanho; cada traco aplica os {@code boss_attributes} do datapack
 *   e um efeito especial ({@link BossTraits}).</li>
 *   <li><b>Despertar</b>: 3 s emergindo do chao, invulneravel.</li>
 *   <li><b>Golpes</b>: maquina de estados com telegrafo ({@code windup}), impacto e recarga; cada golpe dispara
 *   a animacao de mesmo nome no controlador "action".</li>
 *   <li><b>Fase 2</b>: a 50% de vida o chefe ruge, repele todos e libera golpes novos, mais rapido.</li>
 *   <li><b>Atordoamento</b>: janelas de vulnerabilidade em que o dano ignora o soft cap.</li>
 * </ul>
 */
public abstract class AtrocityBoss extends Monster implements GeoEntity {

    public static final String MINION_TAG = "atrocities.minion";
    public static final int EMERGE_TICKS = 60;
    public static final int DEATH_TICKS = 70;
    public static final int TRANSITION_TICKS = 50;
    public static final float PHASE_TWO_RATIO = 0.5f;

    private static final int[] TIER_DAMAGE = {14, 18, 23, 28};
    private static final float[] TIER_SCALE = {0.85f, 1.0f, 1.12f, 1.25f};
    private static final ResourceLocation PHASE_TWO_SPEED =
            ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "phase_two_speed");

    protected static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    protected static final RawAnimation EMERGE = RawAnimation.begin().thenPlayAndHold("emerge");
    protected static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    protected static final RawAnimation STUNNED = RawAnimation.begin().thenLoop("stunned");
    protected static final String ROAR = "roar";

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(AtrocityBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EMERGE =
            SynchedEntityData.defineId(AtrocityBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_STUNNED =
            SynchedEntityData.defineId(AtrocityBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(AtrocityBoss.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent;
    private final Map<ResourceKey<Trait>, Integer> potencies = new HashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();

    @Nullable
    private EmbryoGenome genome;
    @Nullable
    protected BossAttack currentAttack;
    protected int attackTick;
    private int globalCooldown = 40;
    private int stunTicks;
    private int transitionTicks;
    private int traitClock;
    private int sporeCooldown;
    private int blinkCooldown;
    private int lastHurtTick = -1000;

    protected AtrocityBoss(EntityType<? extends AtrocityBoss> type, Level level, BossEvent.BossBarColor color) {
        super(type, level);
        this.bossEvent = new ServerBossEvent(type.getDescription(), color, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setVisible(false);
        this.xpReward = 250;
        this.setPersistenceRequired();
    }

    /** Atributos base de todo chefe; cada especie ajusta armadura e velocidade. */
    protected static AttributeSupplier.Builder bossAttributes(double speed, double armor, double toughness) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1_500.0D)
                .add(Attributes.ATTACK_DAMAGE, TIER_DAMAGE[0])
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ARMOR_TOUGHNESS, toughness)
                .add(Attributes.STEP_HEIGHT, 1.5D);
    }

    // ------------------------------------------------------------------ contrato das especies

    /** Linhagem que este chefe representa. */
    public abstract Archetype archetype();

    /** Repertorio de golpes. Deve ser uma lista constante. */
    protected abstract List<BossAttack> attacks();

    /** Executa um tick do golpe atual ({@code tick} vai de 0 a duration - 1). */
    protected abstract void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target);

    /** Distancia que o chefe tenta manter do alvo enquanto nao ataca. */
    protected abstract double preferredRange();

    /** Chamado uma vez ao entrar na fase 2 (durante o rugido). */
    protected void onPhaseTwo(ServerLevel level) {
    }

    /** Golpe interrompido (atordoamento, fase 2, morte): desfaca estados temporarios. */
    protected void onAttackCancelled(BossAttack attack) {
    }

    protected abstract SoundEvent roarSound();

    /** Cor das particulas de sangue/fluido do chefe. */
    public int fluidColor() {
        return archetype().color();
    }

    /** Criatura usada quando a linhagem nao tem especies validas. */
    protected abstract EntityType<?> fallbackMinion();

    // ------------------------------------------------------------------ dados sincronizados

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_EMERGE, 0);
        builder.define(DATA_STUNNED, false);
        builder.define(DATA_TIER, 1);
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    public int tier() {
        return entityData.get(DATA_TIER);
    }

    public int emergeTicks() {
        return entityData.get(DATA_EMERGE);
    }

    public boolean isEmerging() {
        return emergeTicks() > 0;
    }

    public boolean isStunned() {
        return entityData.get(DATA_STUNNED);
    }

    public boolean isAttacking() {
        return currentAttack != null;
    }

    /** Pode se mover e atacar (nao esta emergindo, atordoado, rugindo nem morrendo). */
    public boolean canAct() {
        return isAlive() && !isEmerging() && !isStunned() && transitionTicks <= 0;
    }

    @Nullable
    public EmbryoGenome genome() {
        return genome;
    }

    public int potency(ResourceKey<Trait> trait) {
        return potencies.getOrDefault(trait, 0);
    }

    // ------------------------------------------------------------------ genoma

    /** Aplica o genoma do Embriao Maduro: vida, dano, tamanho e tracos. */
    public void initFromGenome(EmbryoGenome genome) {
        this.genome = genome;
        applyGenome(true);
    }

    /** Chamado pelo Embriao Maduro: o chefe sai do chao nos proximos 3 s. */
    public void startEmerging() {
        entityData.set(DATA_EMERGE, EMERGE_TICKS);
    }

    private void applyGenome(boolean resetHealth) {
        if (genome == null) {
            return;
        }
        potencies.clear();
        for (TraitRoll roll : genome.traits()) {
            potencies.merge(roll.trait(), roll.potency(), Math::max);
        }
        int tier = Mth.clamp(genome.tier(), 1, EmbryoGenome.MAX_TIER);
        entityData.set(DATA_TIER, tier);
        xpReward = 250 * tier;

        setBase(Attributes.MAX_HEALTH, genome.bossBaseHealth() * AtrocitiesConfig.BOSS_HEALTH_MULTIPLIER.get());
        setBase(Attributes.ATTACK_DAMAGE, TIER_DAMAGE[tier - 1] * AtrocitiesConfig.BOSS_DAMAGE_MULTIPLIER.get());
        setBase(Attributes.SCALE, TIER_SCALE[tier - 1]);

        potencies.forEach((key, potency) -> GeneticsRegistries.trait(registryAccess(), key).ifPresent(trait -> {
            List<Trait.BossAttribute> modifiers = trait.bossAttributes();
            for (int i = 0; i < modifiers.size(); i++) {
                Trait.BossAttribute modifier = modifiers.get(i);
                AttributeInstance instance = getAttribute(modifier.attribute());
                if (instance == null) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Atrocities.MODID,
                        "trait/" + key.location().getNamespace() + "/" + key.location().getPath() + "/" + i);
                instance.addOrReplacePermanentModifier(
                        new AttributeModifier(id, modifier.perLevel() * potency, modifier.operation()));
            }
        }));
        capMaxHealth();
        if (resetHealth) {
            setHealth(getMaxHealth());
        } else {
            setHealth(Math.min(getHealth(), getMaxHealth()));
        }
        refreshDimensions();
        updateBossBarName();
    }

    /** Garante o teto de vida: se os tracos passarem do limite, a vida base e reduzida para compensar. */
    private void capMaxHealth() {
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        double cap = EmbryoGenome.MAX_BOSS_HEALTH * AtrocitiesConfig.BOSS_HEALTH_MULTIPLIER.get();
        double value = health.getValue();
        if (value > cap) {
            health.setBaseValue(health.getBaseValue() * cap / value);
        }
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                         double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void updateBossBarName() {
        bossEvent.setName(getDisplayName().copy().append(Component.literal(" · " + roman(tier()))
                .withStyle(ChatFormatting.GRAY)));
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        updateBossBarName();
    }

    // ------------------------------------------------------------------ IA

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChaseGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && genome == null) {
            initFromGenome(new EmbryoGenome(archetype(), 1, 100, List.of(), List.of(), 0));
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        bossEvent.setProgress(getHealth() / getMaxHealth());

        if (isEmerging()) {
            tickEmerging(level);
            return;
        }
        bossEvent.setVisible(true);
        cooldowns.replaceAll((id, ticks) -> Math.max(0, ticks - 1));
        if (globalCooldown > 0) {
            globalCooldown--;
        }
        tickTraits(level);

        if (transitionTicks > 0) {
            tickTransition(level);
            return;
        }
        if (stunTicks > 0) {
            getNavigation().stop();
            if (tickCount % 5 == 0) {
                GeneticsFx.burst(level, ParticleTypes.CRIT, position().add(0, getBbHeight() + 0.3, 0), 6, 0.4, 0.1);
            }
            if (--stunTicks <= 0) {
                entityData.set(DATA_STUNNED, false);
            }
            return;
        }
        if (phase() == 1 && getHealth() <= getMaxHealth() * PHASE_TWO_RATIO) {
            enterPhaseTwo(level);
            return;
        }

        LivingEntity target = getTarget();
        if (currentAttack != null) {
            BossAttack attack = currentAttack;
            if (target != null && attackTick < attack.windup()) {
                faceTowards(target, 25f);
            }
            tickAttack(level, attack, attackTick, target);
            if (currentAttack == attack && ++attackTick >= attack.duration()) {
                finishAttack(attack);
            }
        } else if (target != null && target.isAlive() && globalCooldown <= 0) {
            chooseAttack(target);
        }
    }

    private void chooseAttack(LivingEntity target) {
        double distance = Math.max(0, distanceTo(target) - getBbWidth() * 0.5);
        List<BossAttack> options = new ArrayList<>();
        int totalWeight = 0;
        for (BossAttack attack : attacks()) {
            if (attack.minPhase() <= phase() && cooldowns.getOrDefault(attack.id(), 0) <= 0
                    && attack.inRange(distance) && canStartAttack(attack, target)) {
                options.add(attack);
                totalWeight += attack.weight();
            }
        }
        if (options.isEmpty()) {
            return;
        }
        int roll = random.nextInt(totalWeight);
        for (BossAttack attack : options) {
            roll -= attack.weight();
            if (roll < 0) {
                startAttack(attack);
                return;
            }
        }
    }

    /** Condicao extra para um golpe (ex.: linha de visao). */
    protected boolean canStartAttack(BossAttack attack, LivingEntity target) {
        return hasLineOfSight(target);
    }

    protected void startAttack(BossAttack attack) {
        currentAttack = attack;
        attackTick = 0;
        getNavigation().stop();
        triggerAnim("action", attack.id());
    }

    private void finishAttack(BossAttack attack) {
        currentAttack = null;
        cooldowns.put(attack.id(), Math.round(attack.cooldown() * cooldownMultiplier()));
        globalCooldown = phase() >= 2 ? 8 : 16;
    }

    /** Interrompe o golpe atual sem aplicar o restante. */
    protected void cancelAttack() {
        if (currentAttack != null) {
            BossAttack attack = currentAttack;
            stopTriggeredAnim("action", attack.id());
            cooldowns.put(attack.id(), Math.round(attack.cooldown() * cooldownMultiplier()));
            currentAttack = null;
            onAttackCancelled(attack);
        }
        globalCooldown = Math.max(globalCooldown, 20);
    }

    private float cooldownMultiplier() {
        float multiplier = phase() >= 2 ? 0.75f : 1f;
        multiplier -= 0.05f * potency(BossTraits.ADRENAL_SURGE);
        return Math.max(0.45f, multiplier);
    }

    /** Atordoa o chefe: interrompe o golpe e abre uma janela de vulnerabilidade (sem soft cap). */
    public void stun(int ticks) {
        cancelAttack();
        stunTicks = ticks;
        entityData.set(DATA_STUNNED, true);
        getNavigation().stop();
        playSound(ModSounds.BOSS_STUNNED.get(), 2f, 0.9f);
    }

    protected void faceTowards(Entity target, float maxTurn) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        setYRot(Mth.approachDegrees(getYRot(), yaw, maxTurn));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /** Vetor horizontal para onde o chefe esta virado. */
    protected Vec3 forward() {
        return Vec3.directionFromRotation(0, getYRot());
    }

    // ------------------------------------------------------------------ despertar e fase 2

    private void tickEmerging(ServerLevel level) {
        int left = emergeTicks() - 1;
        entityData.set(DATA_EMERGE, left);
        getNavigation().stop();
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
        BlockState ground = level.getBlockState(blockPosition().below());
        double radius = getBbWidth() * 0.8;
        if (left % 2 == 0) {
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(),
                        12, radius, 0.1, radius, 0.15);
            }
            GeneticsFx.burst(level, GeneticsFx.dust(fluidColor(), 2.2f), position().add(0, 0.3, 0), 6, radius, 0.02);
        }
        if (left % 12 == 0) {
            playSound(ModSounds.BOSS_EMERGE.get(), 2.5f, 0.6f + random.nextFloat() * 0.2f);
        }
        if (left <= 0) {
            bossEvent.setVisible(true);
            playSound(roarSound(), 4f, 0.8f);
            shockwave(level, 10, 0f, 1.2, 0.5);
        }
    }

    private void enterPhaseTwo(ServerLevel level) {
        cancelAttack();
        entityData.set(DATA_PHASE, 2);
        transitionTicks = TRANSITION_TICKS;
        triggerAnim("action", ROAR);
        playSound(roarSound(), 5f, 0.6f);
        bossEvent.setDarkenScreen(true);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrReplacePermanentModifier(new AttributeModifier(PHASE_TWO_SPEED, 0.15,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48))) {
            player.displayClientMessage(Component.translatable("message.atrocities.boss.phase_two", getDisplayName())
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        }
        onPhaseTwo(level);
    }

    private void tickTransition(ServerLevel level) {
        transitionTicks--;
        getNavigation().stop();
        if (transitionTicks == TRANSITION_TICKS - 22) {
            shockwave(level, 14, 0.3f, 2.2, 0.7);
        }
        if (transitionTicks % 3 == 0) {
            GeneticsFx.burst(level, GeneticsFx.dust(fluidColor(), 2.5f), position().add(0, getBbHeight() * 0.6, 0),
                    10, getBbWidth() * 0.6, 0.05);
        }
    }

    /** Onda de choque: dano (fracao do ataque), repulsao e anel de detritos. */
    protected void shockwave(ServerLevel level, double radius, float damageMultiplier, double push, double lift) {
        playSound(ModSounds.BOSS_SHOCKWAVE.get(), 3f, 0.7f);
        for (int ring = 1; ring <= 3; ring++) {
            ring(level, ParticleTypes.CLOUD, position().add(0, 0.2, 0), radius * ring / 3.0, 24 * ring);
        }
        groundCrack(level, position(), radius * 0.6, 40);
        strike(position(), radius, damageMultiplier, push, lift, null);
    }

    // ------------------------------------------------------------------ combate

    /** Dano de um golpe: atributo de ataque x multiplicador do golpe (+15% na fase 2). */
    protected float attackDamage(float multiplier) {
        float base = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier;
        return phase() >= 2 ? base * 1.15f : base;
    }

    /**
     * Acerta todos os alvos validos em uma esfera.
     *
     * @param multiplier fracao do dano de ataque (0 = so empurra)
     * @param push       empurrao horizontal a partir do centro
     * @param lift       impulso vertical
     * @param filter     filtro extra (ex.: cone a frente)
     * @return alvos atingidos
     */
    protected List<LivingEntity> strike(Vec3 center, double radius, float multiplier, double push, double lift,
                                        @Nullable Predicate<LivingEntity> filter) {
        List<LivingEntity> hit = new ArrayList<>();
        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area, this::isValidVictim)) {
            double reach = radius + target.getBbWidth() * 0.5;
            if (target.position().distanceToSqr(center) > reach * reach || (filter != null && !filter.test(target))) {
                continue;
            }
            boolean damaged = multiplier <= 0 || target.hurt(damageSources().mobAttack(this), attackDamage(multiplier));
            if (!damaged) {
                continue;
            }
            Vec3 away = target.position().subtract(center).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? forward() : away.normalize();
            double resist = 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.6;
            target.push(away.x * push * resist, lift * resist, away.z * push * resist);
            target.hurtMarked = true;
            if (multiplier > 0) {
                applyHitEffects(target);
            }
            hit.add(target);
        }
        return hit;
    }

    /** Alvo esta dentro de um cone a frente do chefe. */
    protected boolean inFront(LivingEntity target, double halfAngleDegrees) {
        Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0E-4) {
            return true;
        }
        double cos = forward().dot(to.normalize());
        return cos >= Math.cos(Math.toRadians(halfAngleDegrees));
    }

    protected boolean isValidVictim(LivingEntity target) {
        if (target == this || !target.isAlive() || target instanceof AtrocityBoss || target.getTags().contains(MINION_TAG)) {
            return false;
        }
        return !(target instanceof Player player) || (!player.isSpectator() && !player.isCreative());
    }

    /** Efeitos dos tracos ofensivos em cada golpe que acerta. */
    public void applyHitEffects(LivingEntity target) {
        int hemotoxin = potency(BossTraits.HEMOTOXIN);
        if (hemotoxin > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 80 + 40 * hemotoxin, hemotoxin - 1), this);
        }
        int anticoagulant = potency(BossTraits.ANTICOAGULANT);
        if (anticoagulant > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60 + 20 * anticoagulant, anticoagulant - 1), this);
        }
        int bile = potency(BossTraits.TOXIC_BILE);
        if (bile > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0), this);
            target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, bile - 1), this);
        }
        int thermal = potency(BossTraits.THERMAL_ORGANS);
        if (thermal > 0) {
            target.igniteForSeconds(2f * thermal);
        }
        int nocturnal = potency(BossTraits.NOCTURNAL_MEMBRANES);
        if (nocturnal > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * nocturnal + 20, 0), this);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof AtrocityBoss || (attacker != null && attacker.getTags().contains(MINION_TAG))) {
            return false;
        }
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && (isEmerging() || transitionTicks > 0)) {
            if (level() instanceof ServerLevel server) {
                GeneticsFx.burst(server, ParticleTypes.ENCHANTED_HIT, source.getSourcePosition() != null
                        ? source.getSourcePosition() : position().add(0, getBbHeight() * 0.5, 0), 8, 0.3, 0.2);
            }
            playSound(ModSounds.BOSS_DEFLECT.get(), 1.2f, 1f);
            return false;
        }
        float mitigated = bypass ? amount : DamageMitigation.apply(amount, isStunned());
        boolean result = super.hurt(source, mitigated);
        if (result && level() instanceof ServerLevel server) {
            lastHurtTick = tickCount;
            onHurtTraits(server, source, mitigated);
            if (random.nextInt(3) == 0) {
                GeneticsFx.burst(server, GeneticsFx.dust(fluidColor(), 1.6f),
                        position().add(0, getBbHeight() * 0.55, 0), 8, getBbWidth() * 0.3, 0.05);
            }
        }
        return result;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL)
                || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING);
    }

    @Override
    public boolean fireImmune() {
        return super.fireImmune() || potency(BossTraits.THERMAL_ORGANS) > 0;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.WITHER) && !effect.is(MobEffects.LEVITATION)
                && super.canBeAffected(effect);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------ tracos

    private void tickTraits(ServerLevel level) {
        traitClock++;
        if (sporeCooldown > 0) {
            sporeCooldown--;
        }
        if (blinkCooldown > 0) {
            blinkCooldown--;
        }
        int regen = potency(BossTraits.REGENERATIVE_TISSUE);
        if (regen > 0 && traitClock % 20 == 0 && getHealth() < getMaxHealth()) {
            float rate = tickCount - lastHurtTick < 60 ? 0.00025f : 0.0005f;
            heal(getMaxHealth() * rate * regen);
            GeneticsFx.burst(level, ParticleTypes.HEART, position().add(0, getBbHeight(), 0), 1, 0.4, 0);
        }
        int nocturnal = potency(BossTraits.NOCTURNAL_MEMBRANES);
        if (nocturnal > 0 && traitClock % 60 == 0 && (level.isNight() || level.getMaxLocalRawBrightness(blockPosition()) < 8)) {
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(16 + 4 * nocturnal))) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0), this);
            }
        }
        int hive = potency(BossTraits.HIVE_MIND);
        if (hive > 0 && getTarget() != null && traitClock % Math.max(200, 400 - 60 * hive) == 0) {
            spawnMinions(level, 1 + hive / 2, 4 + hive);
        }
        int thermal = potency(BossTraits.THERMAL_ORGANS);
        if (thermal > 0 && traitClock % 4 == 0) {
            GeneticsFx.burst(level, ParticleTypes.FLAME, position().add(0, getBbHeight() * 0.5, 0), 2, getBbWidth() * 0.4, 0.01);
        }
        int strands = potency(BossTraits.VOID_STRANDS);
        LivingEntity target = getTarget();
        if (strands > 0 && blinkCooldown <= 0 && target != null && currentAttack == null && distanceTo(target) > 14) {
            blinkCooldown = Math.max(80, 200 - 40 * strands);
            blinkNear(level, target.position(), 4);
        }
        int spores = potency(BossTraits.SPORE_SACS);
        if (spores > 0 && phase() >= 2 && traitClock % 120 == 0) {
            sporeCloud(level, position(), 2.5f + spores, 100);
        }
    }

    private void onHurtTraits(ServerLevel level, DamageSource source, float dealt) {
        int spines = potency(BossTraits.CHITIN_SPINES);
        if (spines > 0 && source.isDirect() && source.getEntity() instanceof LivingEntity attacker
                && attacker.distanceTo(this) < getBbWidth() + 3) {
            attacker.hurt(damageSources().thorns(this), Math.min(30f, dealt * 0.08f * spines));
            GeneticsFx.burst(level, ParticleTypes.CRIT, attacker.position().add(0, attacker.getBbHeight() * 0.6, 0), 6, 0.2, 0.2);
        }
        int spores = potency(BossTraits.SPORE_SACS);
        if (spores > 0 && sporeCooldown <= 0 && random.nextFloat() < 0.2f) {
            sporeCooldown = 60;
            sporeCloud(level, position(), 2f + spores, 80);
        }
        int strands = potency(BossTraits.VOID_STRANDS);
        if (strands > 0 && blinkCooldown <= 0 && source.is(DamageTypeTags.IS_PROJECTILE) && random.nextFloat() < 0.4f) {
            blinkCooldown = 60;
            blinkNear(level, position(), 8);
        }
    }

    /** Nuvem de esporos venenosos (traco Bolsas de Esporos e golpes da Legiao). */
    public void sporeCloud(ServerLevel level, Vec3 at, float radius, int duration) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setOwner(this);
        cloud.setRadius(radius);
        cloud.setRadiusOnUse(-0.2f);
        cloud.setWaitTime(5);
        cloud.setDuration(duration);
        cloud.setRadiusPerTick(-radius / (duration * 1.5f));
        cloud.setParticle(GeneticsFx.dust(0x6E8F2A, 1.4f));
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
        level.addFreshEntity(cloud);
        playSound(ModSounds.BOSS_SPORES.get(), 1.5f, 0.8f + random.nextFloat() * 0.3f);
    }

    /** Teleporte curto (Filamentos do Vazio) perto de um ponto. */
    protected boolean blinkNear(ServerLevel level, Vec3 around, double spread) {
        Vec3 from = position();
        for (int attempt = 0; attempt < 12; attempt++) {
            double x = around.x + (random.nextDouble() - 0.5) * 2 * spread;
            double z = around.z + (random.nextDouble() - 0.5) * 2 * spread;
            double y = around.y + random.nextInt(5) - 2;
            if (randomTeleport(x, y, z, false)) {
                GeneticsFx.burst(level, ParticleTypes.REVERSE_PORTAL, from.add(0, getBbHeight() * 0.5, 0), 60, getBbWidth() * 0.5, 0.1);
                GeneticsFx.burst(level, ParticleTypes.PORTAL, position().add(0, getBbHeight() * 0.5, 0), 60, getBbWidth() * 0.5, 0.5);
                playSound(ModSounds.BOSS_BLINK.get(), 2f, 0.7f);
                return true;
            }
        }
        return false;
    }

    /**
     * Chama crias da linhagem do genoma (ou a criatura padrao do arquetipo).
     *
     * @return quantas surgiram
     */
    protected int spawnMinions(ServerLevel level, int count, int cap) {
        List<LivingEntity> existing = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(32),
                entity -> entity.getTags().contains(MINION_TAG));
        int allowed = Math.min(count, cap - existing.size());
        List<EntityType<?>> pool = new ArrayList<>();
        if (genome != null) {
            for (ResourceKey<Species> key : genome.lineage()) {
                GeneticsRegistries.species(level.registryAccess(), key).ifPresent(species -> pool.add(species.entity()));
            }
        }
        if (pool.isEmpty()) {
            pool.add(fallbackMinion());
        }
        int spawned = 0;
        for (int i = 0; i < allowed; i++) {
            EntityType<?> type = pool.get(random.nextInt(pool.size()));
            Entity entity = type.create(level);
            if (entity == null) {
                continue;
            }
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = getBbWidth() * 0.6 + 1.5;
            BlockPos pos = BlockPos.containing(getX() + Math.cos(angle) * distance, getY(), getZ() + Math.sin(angle) * distance);
            entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            entity.addTag(MINION_TAG);
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
                if (getTarget() != null) {
                    mob.setTarget(getTarget());
                }
            }
            level.addFreshEntity(entity);
            GeneticsFx.burst(level, GeneticsFx.dust(fluidColor(), 1.8f), entity.position().add(0, 0.5, 0), 20, 0.4, 0.05);
            level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.3, entity.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
            spawned++;
        }
        if (spawned > 0) {
            playSound(ModSounds.BOSS_SUMMON.get(), 2f, 0.8f);
        }
        return spawned;
    }

    // ------------------------------------------------------------------ efeitos visuais

    protected void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = i / (double) points * Mth.TWO_PI;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius,
                    1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** Detritos do bloco sob o ponto, espalhados em disco. */
    protected void groundCrack(ServerLevel level, Vec3 center, double radius, int count) {
        BlockState ground = level.getBlockState(BlockPos.containing(center).below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), center.x, center.y + 0.1, center.z,
                    count, radius, 0.1, radius, 0.2);
        }
    }

    // ------------------------------------------------------------------ morte

    @Override
    public void die(DamageSource source) {
        if (currentAttack != null) {
            stopTriggeredAnim("action", currentAttack.id());
            currentAttack = null;
        }
        super.die(source);
        if (level() instanceof ServerLevel level && !level.isClientSide) {
            bossEvent.setProgress(0);
            playSound(roarSound(), 4f, 0.45f);
            if (AtrocitiesConfig.BOSS_BROADCAST.get()) {
                Component killer = source.getEntity() != null ? source.getEntity().getDisplayName()
                        : Component.translatable("message.atrocities.boss.unknown_killer");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.atrocities.boss.slain", getDisplayName(), killer)
                                .withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = position().add(0, getBbHeight() * 0.5, 0);
        if (deathTime % 3 == 0) {
            GeneticsFx.burst(level, GeneticsFx.dust(fluidColor(), 2.4f), center, 12, getBbWidth() * 0.5, 0.05);
            GeneticsFx.burst(level, ParticleTypes.SMOKE, center, 6, getBbWidth() * 0.4, 0.02);
        }
        if (deathTime % 15 == 0) {
            playSound(ModSounds.BOSS_DEATH_SPASM.get(), 2f, 0.6f + random.nextFloat() * 0.3f);
        }
        if (deathTime >= DEATH_TICKS && !isRemoved()) {
            GeneticsFx.burst(level, ParticleTypes.EXPLOSION_EMITTER, center, 1, 0, 0);
            GeneticsFx.burst(level, GeneticsFx.dust(fluidColor(), 3f), center, 120, getBbWidth() * 0.7, 0.2);
            groundCrack(level, position(), getBbWidth(), 80);
            playSound(ModSounds.BOSS_BURST.get(), 4f, 0.7f);
            int volatileGlands = potency(BossTraits.VOLATILE_GLANDS);
            if (volatileGlands > 0) {
                level.explode(this, getX(), getY() + 1, getZ(), 2f + volatileGlands * 1.5f, Level.ExplosionInteraction.NONE);
            }
            level.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ barra de chefe

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    // ------------------------------------------------------------------ persistencia

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (genome != null) {
            EmbryoGenome.CODEC.encodeStart(NbtOps.INSTANCE, genome).result().ifPresent(nbt -> tag.put("genome", nbt));
        }
        tag.putInt("phase", phase());
        tag.putInt("emerge", emergeTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("genome")) {
            EmbryoGenome.CODEC.parse(NbtOps.INSTANCE, tag.get("genome")).result().ifPresent(value -> {
                genome = value;
                // Sem "Health" na NBT (ex.: /summon com genoma) o chefe nasce com a vida cheia.
                applyGenome(!tag.contains("Health"));
            });
        }
        entityData.set(DATA_PHASE, Mth.clamp(tag.getInt("phase"), 1, 2));
        entityData.set(DATA_EMERGE, Math.max(0, tag.getInt("emerge")));
        if (phase() >= 2) {
            bossEvent.setDarkenScreen(true);
        }
        updateBossBarName();
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, this::mainAnimation));
        AnimationController<AtrocityBoss> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
        for (BossAttack attack : attacks()) {
            action.triggerableAnim(attack.id(), RawAnimation.begin().thenPlay(attack.id()));
        }
        action.triggerableAnim(ROAR, RawAnimation.begin().thenPlay(ROAR));
        controllers.add(action);
    }

    private PlayState mainAnimation(AnimationState<AtrocityBoss> state) {
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (isEmerging()) {
            return state.setAndContinue(EMERGE);
        }
        if (isStunned()) {
            return state.setAndContinue(STUNNED);
        }
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ------------------------------------------------------------------ objetivo de perseguicao

    /** Persegue o alvo ate a distancia preferida; parado enquanto golpeia, emerge ou esta atordoado. */
    private static final class ChaseGoal extends Goal {

        private final AtrocityBoss boss;
        private int repath;

        ChaseGoal(AtrocityBoss boss) {
            this.boss = boss;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = boss.getTarget();
            return target != null && target.isAlive() && !boss.isEmerging();
        }

        @Override
        public void stop() {
            boss.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = boss.getTarget();
            if (target == null) {
                return;
            }
            if (!boss.canAct() || boss.isAttacking()) {
                boss.getNavigation().stop();
                return;
            }
            boss.getLookControl().setLookAt(target, 30f, 30f);
            if (--repath <= 0) {
                repath = 8;
                double distance = boss.distanceTo(target) - boss.getBbWidth() * 0.5;
                if (distance > boss.preferredRange()) {
                    boss.getNavigation().moveTo(target, 1.0D);
                } else {
                    boss.getNavigation().stop();
                    boss.faceTowards(target, 15f);
                }
            }
        }
    }
}
