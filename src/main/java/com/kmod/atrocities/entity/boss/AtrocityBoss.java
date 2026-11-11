package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.cinematic.ArenaCinematics;
import com.kmod.atrocities.combat.DamageMitigation;
import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.genetics.Archetype;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Species;
import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.genetics.TraitRoll;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.network.CutscenePayload;
import com.kmod.atrocities.registry.ModEntities;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 *   <li><b>Despertar</b>: cutscene de 5,5 s (emerge do chao e ruge) com a arena congelada.</li>
 *   <li><b>Golpes</b>: maquina de estados com telegrafo ({@code windup}), impacto e recarga; cada golpe dispara
 *   a animacao de mesmo nome no controlador "action".</li>
 *   <li><b>Fases</b>: a 66% e 33% de vida o chefe entra nas fases 2 e 3 com uma cutscene de transicao; cada
 *   fase traz uma mecanica propria (orgaos, couraca, divisao...) e golpes novos.</li>
 *   <li><b>Atordoamento</b>: janelas de vulnerabilidade em que o dano ignora o soft cap.</li>
 * </ul>
 */
public abstract class AtrocityBoss extends Monster implements GeoEntity {

    public static final String MINION_TAG = "atrocities.minion";
    /** Entrada completa (cutscene): 60 ticks emergindo do chao + 50 ticks de rugido. */
    public static final int EMERGE_TICKS = 110;
    public static final int EMERGE_ROAR_AT = 50;
    public static final int DEATH_TICKS = 70;
    /** Transicao de fase (cutscene); o "climax" (ruptura) acontece 30 ticks antes do fim. */
    public static final int TRANSITION_TICKS = 70;
    public static final int TRANSITION_CLIMAX_AT = 30;
    public static final int MAX_PHASE = 3;
    /** Vida (fracao) em que o chefe entra nas fases 2 e 3. */
    public static final float[] PHASE_RATIOS = {0.66f, 0.33f};

    private static final int[] TIER_DAMAGE = {11, 14, 17, 21};
    private static final float[] TIER_SCALE = {0.85f, 1.0f, 1.12f, 1.25f};
    private static final ResourceLocation PHASE_SPEED =
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
    /** Progresso do telegrafo do golpe atual (0 = sem golpe carregando, 1 = impacto). */
    private static final EntityDataAccessor<Float> DATA_CHARGE =
            SynchedEntityData.defineId(AtrocityBoss.class, EntityDataSerializers.FLOAT);
    private static final int DANGER = 0xFF2A1E;

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
    private int lastHintTick = -1000;
    private boolean introStarted;

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
                .add(Attributes.MAX_HEALTH, 900.0D)
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

    // ------------------------------------------------------------------ ganchos (chefes x mutantes)

    /** Entrada e trocas de fase viram cutscenes que congelam a arena (os mutantes lutam sem pausa). */
    protected boolean cinematic() {
        return true;
    }

    /** Numero de fases (os chefes tem 3; os mutantes, 2: a segunda e a furia). */
    public int maxPhase() {
        return MAX_PHASE;
    }

    /** Fracao da vida em que a fase {@code phase + 1} comeca. */
    protected float phaseRatio(int phase) {
        return PHASE_RATIOS[Mth.clamp(phase, 1, PHASE_RATIOS.length) - 1];
    }

    /** Duracao da entrada; os ultimos {@link #EMERGE_ROAR_AT} ticks sao o rugido. */
    public int introTicks() {
        return EMERGE_TICKS;
    }

    /** Vida base antes dos tracos. */
    protected double baseHealth(EmbryoGenome genome) {
        return genome.bossBaseHealth();
    }

    /** Dano base por tier. */
    protected double baseDamage(int tier) {
        return TIER_DAMAGE[Mth.clamp(tier, 1, TIER_DAMAGE.length) - 1];
    }

    /** Escala do modelo por tier. */
    protected float tierScale(int tier) {
        return TIER_SCALE[Mth.clamp(tier, 1, TIER_SCALE.length) - 1];
    }

    /** Sufixo da barra de chefe (por padrao, o tier em romanos). */
    protected Component bossBarSuffix() {
        return Component.literal(" · " + roman(tier())).withStyle(ChatFormatting.GRAY);
    }

    /** Linha de vida da ficha no Codex. */
    public Component codexHealth() {
        return Component.translatable("codex.atrocities.boss.health");
    }

    /** Subtitulo da ficha no Codex. */
    public Component codexSubtitle() {
        return archetype().displayName();
    }

    /** Mensagem mostrada a quem luta quando o chefe entra na fase {@code phase}. */
    protected Component phaseMessage(int phase) {
        String key = phase >= MAX_PHASE ? "message.atrocities.boss.phase_three" : "message.atrocities.boss.phase_two";
        return Component.translatable(key, getDisplayName()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
    }

    /** Repertorio de golpes. Deve ser uma lista constante. */
    protected abstract List<BossAttack> attacks();

    /** Executa um tick do golpe atual ({@code tick} vai de 0 a duration - 1). */
    protected abstract void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target);

    /** Distancia que o chefe tenta manter do alvo enquanto nao ataca. */
    protected abstract double preferredRange();

    /** Chamado uma vez ao entrar em uma fase nova (inicio da cutscene de transicao). */
    protected void onPhaseStart(ServerLevel level, int phase) {
    }

    /** Cada tick da cutscene de entrada ({@code tick} de 0 a {@link #EMERGE_TICKS} - 1). */
    protected void onIntroTick(ServerLevel level, int tick) {
    }

    /** Cada tick da cutscene de transicao ({@code tick} de 0 a {@link #TRANSITION_TICKS} - 1). */
    protected void onTransitionTick(ServerLevel level, int phase, int tick) {
    }

    /** Ruptura da transicao: momento em que a mecanica da fase nova aparece (orgaos, carapaca quebrando...). */
    protected void onPhaseClimax(ServerLevel level, int phase) {
    }

    /** Mecanicas continuas da fase atual (rodam todo tick fora das cutscenes, mesmo atordoado). */
    protected void tickPhaseMechanics(ServerLevel level) {
    }

    /**
     * Ajusta o dano recebido segundo a mecanica do chefe (antes do soft cap).
     *
     * @return dano ajustado
     */
    protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float amount) {
        return amount;
    }

    /** Chefe imune a dano no momento (ex.: Legiao dividida). O golpe ricocheteia. */
    public boolean isShielded() {
        return false;
    }

    /** Chefe inativo (nao se move nem ataca), ex.: enquanto os nucleos estao soltos. */
    protected boolean isDormant() {
        return false;
    }

    /** Um golpe do chefe acertou um alvo. */
    protected void onHitLanded(ServerLevel level, LivingEntity target, float damage) {
    }

    /** Um orgao do chefe foi destruido pelos jogadores. */
    public void onOrganDestroyed(ServerLevel level, BossOrganEntity organ) {
    }

    /** Um orgao do chefe chegou ate ele (nucleos da Legiao). */
    public void onOrganArrived(ServerLevel level, BossOrganEntity organ) {
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
        builder.define(DATA_CHARGE, 0f);
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

    /** Progresso (0 a 1) do golpe sendo carregado; usado pelo renderizador para tremer e inchar o chefe. */
    public float charge() {
        return entityData.get(DATA_CHARGE);
    }

    public boolean isAttacking() {
        return currentAttack != null;
    }

    /** Pode se mover e atacar (nao esta emergindo, atordoado, rugindo nem morrendo). */
    public boolean canAct() {
        return isAlive() && !isEmerging() && !isStunned() && transitionTicks <= 0 && !isDormant();
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
        entityData.set(DATA_EMERGE, introTicks());
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

        setBase(Attributes.MAX_HEALTH, baseHealth(genome) * AtrocitiesConfig.BOSS_HEALTH_MULTIPLIER.get());
        setBase(Attributes.ATTACK_DAMAGE, baseDamage(tier) * AtrocitiesConfig.BOSS_DAMAGE_MULTIPLIER.get());
        setBase(Attributes.SCALE, tierScale(tier));

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
        bossEvent.setName(getDisplayName().copy().append(bossBarSuffix()));
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
        if (tickCount % 20 == 0) {
            catalogWitnesses(level);
        }

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
        tickPhaseMechanics(level);
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
        if (phase() < maxPhase() && getHealth() <= getMaxHealth() * phaseRatio(phase())) {
            enterPhase(level, phase() + 1);
            return;
        }
        if (isDormant()) {
            getNavigation().stop();
            return;
        }

        LivingEntity target = getTarget();
        if (currentAttack != null) {
            BossAttack attack = currentAttack;
            if (target != null && attackTick < attack.windup()) {
                faceTowards(target, 25f);
            }
            tickTelegraph(level, attack, attackTick, target);
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
        triggerAnim("action", attack.animation());
    }

    /** Reinicia a animacao de um golpe (golpes com varias repeticoes, como a Debandada). */
    protected void replayAnimation(String animation) {
        stopTriggeredAnim("action", animation);
        triggerAnim("action", animation);
    }

    private void finishAttack(BossAttack attack) {
        currentAttack = null;
        entityData.set(DATA_CHARGE, 0f);
        cooldowns.put(attack.id(), Math.round(attack.cooldown() * cooldownMultiplier()));
        globalCooldown = switch (phase()) {
            case 1 -> 16;
            case 2 -> 10;
            default -> 7;
        };
    }

    /** Interrompe o golpe atual sem aplicar o restante. */
    protected void cancelAttack() {
        if (currentAttack != null) {
            BossAttack attack = currentAttack;
            stopTriggeredAnim("action", attack.animation());
            cooldowns.put(attack.id(), Math.round(attack.cooldown() * cooldownMultiplier()));
            currentAttack = null;
            entityData.set(DATA_CHARGE, 0f);
            onAttackCancelled(attack);
        }
        globalCooldown = Math.max(globalCooldown, 20);
    }

    private float cooldownMultiplier() {
        float multiplier = switch (phase()) {
            case 1 -> 1f;
            case 2 -> 0.85f;
            default -> 0.7f;
        };
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
        if (level() instanceof ServerLevel level) {
            ring(level, ParticleTypes.ENCHANTED_HIT, position().add(0, getBbHeight() + 0.4, 0), getBbWidth() * 0.6, 20);
            BossFx.shake(level, position(), 20, 0.4f, 8);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32))) {
                player.displayClientMessage(Component.translatable("message.atrocities.boss.vulnerable")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
    }

    // ------------------------------------------------------------------ telegrafo

    /** Forma do aviso de perigo desenhado durante a preparacao de um golpe. */
    protected enum Telegraph {
        /** Sem marca no chao (invocacoes); so o brilho de carga. */
        NONE,
        /** Circulo ao redor do chefe: um anel interno cresce ate encostar no anel externo no impacto. */
        AREA,
        /** Linha tracejada ate o alvo (investidas, puxoes, disparos). */
        LINE
    }

    /** Forma do telegrafo de cada golpe. Por padrao: longo alcance = linha, invocacao = nada, resto = area. */
    protected Telegraph telegraph(BossAttack attack) {
        if (attack.maxRange() >= 35f) {
            return Telegraph.NONE;
        }
        return attack.minRange() >= 5f ? Telegraph.LINE : Telegraph.AREA;
    }

    /** Raio da area de perigo de um golpe {@link Telegraph#AREA}. */
    protected double telegraphRadius(BossAttack attack) {
        return Math.min(attack.maxRange(), 12f) + getBbWidth() * 0.5;
    }

    /**
     * Feedback generico de todo golpe: particulas convergindo para o chefe e marca de perigo no chao enquanto
     * carrega (com som que sobe de tom), e um clarao com estalo no instante em que o golpe e liberado.
     */
    private void tickTelegraph(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        int windup = Math.max(1, attack.windup());
        if (tick < windup) {
            float progress = (tick + 1) / (float) windup;
            entityData.set(DATA_CHARGE, progress);
            Vec3 core = position().add(0, getBbHeight() * 0.55, 0);
            int count = 2 + Math.round(progress * 4);
            double shell = getBbWidth() * 1.4 + 1.0;
            for (int i = 0; i < count; i++) {
                Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize();
                Vec3 from = core.add(dir.scale(shell));
                Vec3 velocity = dir.scale(-1);
                level.sendParticles(GeneticsFx.dust(fluidColor(), 1.2f + progress), from.x, from.y, from.z, 0,
                        velocity.x, velocity.y, velocity.z, 0.35);
            }
            if (tick % 2 == 0) {
                drawDanger(level, attack, progress, target);
            }
            if (tick % 4 == 0) {
                playSound(ModSounds.BOSS_TELEGRAPH.get(), 1.4f, 0.6f + progress * 0.9f);
            }
        } else if (tick == windup) {
            entityData.set(DATA_CHARGE, 0f);
            Vec3 core = position().add(0, getBbHeight() * 0.55, 0);
            level.sendParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            GeneticsFx.burst(level, GeneticsFx.dust(fluidColor(), 2.4f), core, 24, getBbWidth() * 0.4, 0.25);
            playSound(ModSounds.BOSS_RELEASE.get(), 2f, 0.85f + random.nextFloat() * 0.2f);
            BossFx.shake(level, position(), 16, 0.25f, 6);
        }
    }

    private void drawDanger(ServerLevel level, BossAttack attack, float progress, @Nullable LivingEntity target) {
        ParticleOptions edge = GeneticsFx.dust(DANGER, 1.3f);
        switch (telegraph(attack)) {
            case AREA -> {
                double radius = telegraphRadius(attack);
                Vec3 ground = position().add(0, 0.15, 0);
                ring(level, edge, ground, radius, (int) Math.min(64, 10 + radius * 5));
                ring(level, GeneticsFx.dust(0xFF8A3A, 1.0f + progress), ground, radius * progress,
                        (int) Math.max(6, radius * progress * 5));
            }
            case LINE -> {
                if (target == null) {
                    return;
                }
                Vec3 from = position().add(0, 0.2, 0);
                Vec3 to = target.position().add(0, 0.2, 0);
                double length = from.distanceTo(to);
                int dashes = (int) Math.min(48, length * 1.5);
                // Os tracos correm em direcao ao alvo e avancam junto com a carga.
                double phase = (tickCount % 6) / 6.0;
                for (int i = 0; i < dashes; i++) {
                    double t = (i + phase) / dashes;
                    if (t > progress + 0.15) {
                        break;
                    }
                    Vec3 p = from.lerp(to, t);
                    level.sendParticles(edge, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
                }
                ring(level, edge, to, 1.2, 12);
            }
            default -> {
            }
        }
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
        if (!introStarted) {
            // Cutscene de entrada: congela a arena e assume a camera de quem esta perto.
            introStarted = true;
            if (cinematic()) {
                ArenaCinematics.start(this, CutscenePayload.INTRO, 1, emergeTicks());
                playSound(ModSounds.CUTSCENE_STING.get(), 4f, 0.8f);
            }
        }
        int left = emergeTicks() - 1;
        entityData.set(DATA_EMERGE, left);
        getNavigation().stop();
        onIntroTick(level, introTicks() - 1 - left);
        if (left == EMERGE_ROAR_AT) {
            triggerAnim("action", ROAR);
            playSound(roarSound(), 4f, 0.8f);
            bossEvent.setVisible(true);
            BossFx.shake(level, position(), 48, 1.6f, 30);
            GeneticsFx.burst(level, GeneticsFx.dust(fluidColor(), 3f), position().add(0, getBbHeight() * 0.7, 0), 80,
                    getBbWidth() * 0.7, 0.25);
            ring(level, ParticleTypes.CLOUD, position().add(0, 0.3, 0), getBbWidth() + 2, 40);
        }
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
        if (left > EMERGE_ROAR_AT && left % 12 == 0) {
            playSound(ModSounds.BOSS_EMERGE.get(), 2.5f, 0.6f + random.nextFloat() * 0.2f);
            BossFx.shake(level, position(), 28, 0.35f + (introTicks() - left) / (float) introTicks() * 0.6f, 12);
        }
        if (left <= 0) {
            // Fim da cena: libera os jogadores e os empurra com a onda do rugido.
            if (cinematic()) {
                ArenaCinematics.end(this);
            }
            bossEvent.setVisible(true);
            shockwave(level, 10, 0f, 1.2, 0.5);
            BossFx.shake(level, position(), 40, 1.2f, 20);
        }
    }

    private void enterPhase(ServerLevel level, int phase) {
        cancelAttack();
        entityData.set(DATA_PHASE, phase);
        transitionTicks = TRANSITION_TICKS;
        triggerAnim("action", ROAR);
        playSound(roarSound(), 5f, phase == maxPhase() ? 0.5f : 0.6f);
        if (cinematic()) {
            playSound(ModSounds.CUTSCENE_STING.get(), 4f, phase == MAX_PHASE ? 0.6f : 0.75f);
            bossEvent.setDarkenScreen(true);
            bossEvent.setCreateWorldFog(phase >= MAX_PHASE);
        }
        BossFx.shake(level, position(), 48, 0.8f, 20);
        applyPhaseSpeed();
        Component message = phaseMessage(phase);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48))) {
            player.displayClientMessage(message, true);
        }
        if (cinematic()) {
            ArenaCinematics.start(this, CutscenePayload.PHASE, phase, TRANSITION_TICKS);
        }
        onPhaseStart(level, phase);
    }

    private void applyPhaseSpeed() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(PHASE_SPEED);
            if (phase() > 1) {
                speed.addPermanentModifier(new AttributeModifier(PHASE_SPEED, 0.12 * (phase() - 1),
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    private void tickTransition(ServerLevel level) {
        transitionTicks--;
        getNavigation().stop();
        onTransitionTick(level, phase(), TRANSITION_TICKS - 1 - transitionTicks);
        if (transitionTicks % 3 == 0) {
            GeneticsFx.burst(level, GeneticsFx.dust(fluidColor(), 2.5f), position().add(0, getBbHeight() * 0.6, 0),
                    10, getBbWidth() * 0.6, 0.05);
        }
        if (transitionTicks == TRANSITION_CLIMAX_AT) {
            Vec3 core = position().add(0, getBbHeight() * 0.6, 0);
            level.sendParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 2, 0.2, 0.2, 0.2, 0);
            GeneticsFx.burst(level, GeneticsFx.dust(fluidColor(), 3f), core, 100, getBbWidth() * 0.8, 0.3);
            playSound(ModSounds.PHASE_SHIFT.get(), 4f, phase() >= maxPhase() ? 0.6f : 0.8f);
            BossFx.shake(level, position(), 48, 1.5f, 24);
            onPhaseClimax(level, phase());
        }
        if (transitionTicks <= 0) {
            if (cinematic()) {
                ArenaCinematics.end(this);
            }
            shockwave(level, 14, 0.25f, 2.2, 0.7);
        }
    }

    // ------------------------------------------------------------------ apoio as mecanicas

    /** Mensagem de dica na barra de acao para quem esta lutando (no maximo uma a cada 5 s). */
    protected void hint(ServerLevel level, String key, ChatFormatting color) {
        if (tickCount - lastHintTick < 100) {
            return;
        }
        lastHintTick = tickCount;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32))) {
            player.displayClientMessage(Component.translatable("message.atrocities.hint." + key)
                    .withStyle(color, ChatFormatting.BOLD), true);
        }
    }

    /** Golpe amortecido: faiscas no ponto de impacto e som metalico. */
    protected void deflectFeedback(ServerLevel level, DamageSource source, float volume) {
        Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition().add(0, 1, 0)
                : position().add(0, getBbHeight() * 0.5, 0);
        Vec3 center = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 surface = center.add(at.subtract(center).multiply(1, 0, 1).normalize().scale(getBbWidth() * 0.5));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, surface.x, surface.y, surface.z, 10, 0.2, 0.3, 0.2, 0.25);
        level.sendParticles(ParticleTypes.CRIT, surface.x, surface.y, surface.z, 6, 0.2, 0.3, 0.2, 0.2);
        playSound(ModSounds.BOSS_ARMOR_CLANG.get(), volume, 0.9f + random.nextFloat() * 0.2f);
    }

    /** Altura do chao perto de (x, z), procurando a partir da altura do chefe; usada para posicionar orgaos. */
    protected Vec3 groundNear(ServerLevel level, double x, double z) {
        BlockPos start = BlockPos.containing(x, getY() + 4, z);
        for (int dy = 0; dy < 12; dy++) {
            BlockPos pos = start.below(dy);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return new Vec3(x, pos.getY() + 1, z);
            }
        }
        return new Vec3(x, getY(), z);
    }

    /** Pontos em volta do chefe (angulo inicial = frente dele), ja no chao. */
    protected List<Vec3> pointsAround(ServerLevel level, int count, double radius, double angleOffset) {
        List<Vec3> points = new ArrayList<>();
        double base = (getYRot() + 90) * Mth.DEG_TO_RAD + angleOffset;
        for (int i = 0; i < count; i++) {
            double angle = base + i * Mth.TWO_PI / count;
            points.add(groundNear(level, getX() + Math.cos(angle) * radius, getZ() + Math.sin(angle) * radius));
        }
        return points;
    }

    /** Cria um orgao do chefe com vida proporcional ao tier. */
    protected BossOrganEntity spawnOrgan(ServerLevel level, BossOrganEntity.Variant variant, Vec3 at, float baseHealth,
                                        float perTier) {
        float health = (baseHealth + perTier * tier()) * AtrocitiesConfig.BOSS_HEALTH_MULTIPLIER.get().floatValue();
        BossOrganEntity organ = BossOrganEntity.create(ModEntities.BOSS_ORGAN.get(), level, this, variant, at, health);
        level.addFreshEntity(organ);
        GeneticsFx.burst(level, GeneticsFx.dust(variant.color(), 2.4f), at.add(0, 0.5, 0), 40, 0.8, 0.2);
        groundCrack(level, at, 1.4, 40);
        organ.playSound(ModSounds.ORGAN_SPAWN.get(), 2.5f, 0.8f);
        return organ;
    }

    /** Orgaos vivos deste chefe (opcionalmente so de uma variante). */
    public List<BossOrganEntity> organs(@Nullable BossOrganEntity.Variant variant) {
        return level().getEntitiesOfClass(BossOrganEntity.class, getBoundingBox().inflate(48),
                organ -> organ.isAlive() && organ.isOwnedBy(this) && (variant == null || organ.variant() == variant));
    }

    /** Coluna de particulas subindo do chao (aviso de onde algo vai surgir). */
    protected void eruptionWarning(ServerLevel level, Vec3 at, int color, float progress) {
        BlockState ground = level.getBlockState(BlockPos.containing(at).below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z,
                    4 + Math.round(progress * 8), 0.5, 0.05, 0.5, 0.1);
        }
        level.sendParticles(GeneticsFx.dust(color, 1.2f + progress), at.x, at.y + 0.3 + progress * 1.5, at.z,
                3, 0.3, 0.6 * progress + 0.1, 0.3, 0.02);
        ring(level, GeneticsFx.dust(DANGER, 1.1f), at.add(0, 0.15, 0), 1.2 + progress * 0.6, 12);
    }

    /** Onda de choque: dano (fracao do ataque), repulsao e anel de detritos. */
    protected void shockwave(ServerLevel level, double radius, float damageMultiplier, double push, double lift) {
        playSound(ModSounds.BOSS_SHOCKWAVE.get(), 3f, 0.7f);
        for (int ring = 1; ring <= 3; ring++) {
            ring(level, ParticleTypes.CLOUD, position().add(0, 0.2, 0), radius * ring / 3.0, 24 * ring);
        }
        groundCrack(level, position(), radius * 0.6, 40);
        BossFx.shake(level, position(), radius * 3, 0.9f, 16);
        strike(position(), radius, damageMultiplier, push, lift, null);
    }

    // ------------------------------------------------------------------ combate

    /** Dano de um golpe: atributo de ataque x multiplicador do golpe (+8% na fase 2, +15% na fase 3). */
    protected float attackDamage(float multiplier) {
        float base = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier;
        return switch (phase()) {
            case 1 -> base;
            case 2 -> base * 1.08f;
            default -> base * 1.15f;
        };
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
            float damage = attackDamage(multiplier);
            boolean damaged = multiplier <= 0 || target.hurt(damageSources().mobAttack(this), damage);
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
                if (level() instanceof ServerLevel server) {
                    onHitLanded(server, target, damage);
                }
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
        if (!bypass && (isEmerging() || transitionTicks > 0 || isShielded())) {
            if (isShielded() && level() instanceof ServerLevel server) {
                onShieldedHit(server, source);
            }
            if (level() instanceof ServerLevel server) {
                GeneticsFx.burst(server, ParticleTypes.ENCHANTED_HIT, source.getSourcePosition() != null
                        ? source.getSourcePosition() : position().add(0, getBbHeight() * 0.5, 0), 8, 0.3, 0.2);
            }
            playSound(ModSounds.BOSS_DEFLECT.get(), 1.2f, 1f);
            return false;
        }
        if (!bypass && level() instanceof ServerLevel server) {
            amount = modifyIncomingDamage(server, source, amount);
            if (amount <= 0) {
                return false;
            }
        }
        float mitigated = bypass ? amount : DamageMitigation.apply(amount, isStunned());
        boolean result = super.hurt(source, mitigated);
        if (result && level() instanceof ServerLevel server) {
            lastHurtTick = tickCount;
            onHurtTraits(server, source, mitigated);
            hurtFeedback(server, source, mitigated);
        }
        return result;
    }

    /** Golpe contra o chefe enquanto ele esta protegido ({@link #isShielded()}). */
    protected void onShieldedHit(ServerLevel level, DamageSource source) {
    }

    /** Respingo de fluido no ponto do golpe, som de carne e, se vulneravel, um estalo critico. */
    private void hurtFeedback(ServerLevel level, DamageSource source, float dealt) {
        Vec3 center = position().add(0, getBbHeight() * 0.55, 0);
        Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : center.add(forward());
        Vec3 side = from.subtract(center).multiply(1, 0, 1);
        side = side.lengthSqr() < 1.0E-4 ? forward() : side.normalize();
        // O respingo sai na superficie do chefe, do lado de quem bateu.
        Vec3 surface = center.add(side.scale(getBbWidth() * 0.45));
        int amount = Mth.clamp(Math.round(dealt * 0.6f), 4, 30);
        level.sendParticles(GeneticsFx.dust(fluidColor(), 1.5f), surface.x, surface.y, surface.z, amount,
                0.25, 0.25, 0.25, 0.08);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, surface.x, surface.y, surface.z,
                Math.min(6, 1 + Math.round(dealt / 10f)), 0.2, 0.2, 0.2, 0.1);
        playSound(ModSounds.BOSS_HIT_FLESH.get(), 1.2f, 0.75f + random.nextFloat() * 0.3f);
        if (isStunned()) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, surface.x, surface.y, surface.z, 14, 0.3, 0.3, 0.3, 0.3);
            level.sendParticles(ParticleTypes.CRIT, surface.x, surface.y, surface.z, 10, 0.3, 0.3, 0.3, 0.4);
            playSound(ModSounds.BOSS_CRITICAL.get(), 1.6f, 0.9f + random.nextFloat() * 0.2f);
        }
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
        return spawnMinionsAt(level, position(), count, cap);
    }

    /** Crias que nao sao orgaos, perto do chefe. */
    public List<LivingEntity> minions(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius),
                entity -> entity.isAlive() && entity.getTags().contains(MINION_TAG) && !(entity instanceof BossOrganEntity));
    }

    /**
     * Chama crias perto de um ponto (o chefe ou um Casulo de Sangue).
     *
     * @return quantas surgiram
     */
    public int spawnMinionsAt(ServerLevel level, Vec3 origin, int count, int cap) {
        List<LivingEntity> existing = minions(32);
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
            double distance = origin.distanceToSqr(position()) < 1 ? getBbWidth() * 0.6 + 1.5 : 1.5;
            BlockPos pos = BlockPos.containing(origin.x + Math.cos(angle) * distance, origin.y,
                    origin.z + Math.sin(angle) * distance);
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
        // Todo impacto que racha o chao tambem treme a tela (proporcional ao tamanho do impacto).
        BossFx.shake(level, center, 10 + radius * 5, (float) Math.min(1.4, 0.25 + radius * 0.22), 10);
        BlockState ground = level.getBlockState(BlockPos.containing(center).below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), center.x, center.y + 0.1, center.z,
                    count, radius, 0.1, radius, 0.2);
        }
    }

    // ------------------------------------------------------------------ Codex

    /** Raio em que os jogadores "enfrentam" o chefe e tem a pagina dele revelada no Codex. */
    private static final double CODEX_RADIUS = 40;

    /** Quem esta na arena passa a conhecer o chefe (pagina secreta do Codex). */
    private void catalogWitnesses(ServerLevel level) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(CODEX_RADIUS),
                p -> p.isAlive() && !p.isSpectator())) {
            com.kmod.atrocities.codex.Codex.encounter(player, getType());
        }
    }

    /** Usado so no cliente pelo visualizador do Codex: substitui a animacao principal. */
    @Nullable
    public RawAnimation codexPose;
    /** Instancia de vitrine do Codex (anima mesmo com o jogo pausado). */
    public boolean codexDummy;

    @Override
    public boolean shouldPlayAnimsWhileGamePaused() {
        return codexDummy;
    }

    // ------------------------------------------------------------------ morte

    @Override
    public void die(DamageSource source) {
        if (currentAttack != null) {
            stopTriggeredAnim("action", currentAttack.animation());
            currentAttack = null;
        }
        super.die(source);
        if (level() instanceof ServerLevel level && !level.isClientSide) {
            ArenaCinematics.stop(this);
            java.util.Set<ServerPlayer> slayers = new java.util.HashSet<>(level.getEntitiesOfClass(ServerPlayer.class,
                    getBoundingBox().inflate(CODEX_RADIUS + 8), p -> !p.isSpectator()));
            if (source.getEntity() instanceof ServerPlayer killer) {
                slayers.add(killer);
            }
            slayers.forEach(player -> com.kmod.atrocities.codex.Codex.defeat(player, getType(), tier()));
            for (BossOrganEntity organ : organs(null)) {
                GeneticsFx.burst(level, GeneticsFx.dust(organ.variant().color(), 2f), organ.position().add(0, 0.6, 0),
                        30, 0.6, 0.1);
                organ.discard();
            }
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
            BossFx.shake(level, position(), 40, 2f, 30);
            int volatileGlands = potency(BossTraits.VOLATILE_GLANDS);
            if (volatileGlands > 0) {
                level.explode(this, getX(), getY() + 1, getZ(), 2f + volatileGlands * 1.5f, Level.ExplosionInteraction.NONE);
            }
            level.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && ArenaCinematics.isActive(this)) {
            ArenaCinematics.stop(this);
        }
        super.remove(reason);
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
        entityData.set(DATA_PHASE, Mth.clamp(tag.getInt("phase"), 1, maxPhase()));
        entityData.set(DATA_EMERGE, Math.max(0, tag.getInt("emerge")));
        bossEvent.setDarkenScreen(cinematic() && phase() >= 2);
        bossEvent.setCreateWorldFog(cinematic() && phase() >= MAX_PHASE);
        applyPhaseSpeed();
        updateBossBarName();
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, this::mainAnimation));
        AnimationController<AtrocityBoss> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
        LinkedHashSet<String> animations = new LinkedHashSet<>();
        attacks().forEach(attack -> animations.add(attack.animation()));
        for (String animation : animations) {
            action.triggerableAnim(animation, RawAnimation.begin().thenPlay(animation));
        }
        action.triggerableAnim(ROAR, RawAnimation.begin().thenPlay(ROAR));
        controllers.add(action);
    }

    private PlayState mainAnimation(AnimationState<AtrocityBoss> state) {
        if (codexPose != null) {
            return state.setAndContinue(codexPose);
        }
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
