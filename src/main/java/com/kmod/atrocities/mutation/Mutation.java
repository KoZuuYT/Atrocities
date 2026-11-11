package com.kmod.atrocities.mutation;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.entity.mutant.MutantEntity;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Mutacao em estagios de mobs vanilla.
 * <ol>
 *   <li>Cada dose de Soro Mutagenico sobe um estagio: o mob cresce, ganha vida, dano, velocidade e
 *   resistencia, e sara por completo (e fica bravo com quem aplicou).</li>
 *   <li>Na terceira dose comeca a <b>metamorfose</b>: o mob trava num casulo de carne pulsante por 3,5 s,
 *   invulneravel, e explode no mutante da sua especie.</li>
 *   <li>Se a ultima dose for de Soro Volatil, nasce um mutante <b>Alfa</b>. Toda dose volatil tem 15% de
 *   chance de romper o mob (ele explode em biomassa).</li>
 * </ol>
 * O estado fica nos dados persistentes do mob, entao sobrevive a reinicios do servidor.
 */
public final class Mutation {

    public static final int FINAL_STAGE = 3;
    public static final int METAMORPH_TICKS = 70;
    public static final float RUPTURE_CHANCE = 0.15f;

    /** Tag rapida (sem consultar NBT a cada tick) de mobs com algum estagio de mutacao. */
    public static final String MUTATED_TAG = "atrocities.mutated";
    private static final String STAGE = "atrocities.mutation_stage";
    private static final String ALPHA = "atrocities.mutation_alpha";
    private static final String COCOON = "atrocities.metamorphosis";

    private static final ResourceLocation SCALE = id("scale");
    private static final ResourceLocation HEALTH = id("health");
    private static final ResourceLocation DAMAGE = id("damage");
    private static final ResourceLocation SPEED = id("speed");
    private static final ResourceLocation KNOCKBACK = id("knockback");
    private static final ResourceLocation ARMOR = id("armor");
    private static final ResourceLocation SWELL = id("swell");

    public enum Result {
        INCOMPATIBLE, BUSY, MUTATED, TRANSFORMING, RUPTURED;

        public boolean consumed() {
            return this == MUTATED || this == TRANSFORMING || this == RUPTURED;
        }
    }

    private Mutation() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "mutation/" + path);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, event -> event.addListener(MutationRules.INSTANCE));
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, Mutation::onEntityTick);
    }

    public static int stage(Entity entity) {
        return entity.getTags().contains(MUTATED_TAG) ? entity.getPersistentData().getInt(STAGE) : 0;
    }

    public static boolean isTransforming(Entity entity) {
        return entity.getTags().contains(MUTATED_TAG) && entity.getPersistentData().getInt(COCOON) > 0;
    }

    /** O mob aceita Soro Mutagenico (tem regra de mutacao e nao e cria de chefe). */
    public static boolean isMutable(Entity entity) {
        return entity instanceof Mob && !(entity instanceof AtrocityBoss) && entity.isAlive()
                && !entity.getTags().contains(AtrocityBoss.MINION_TAG)
                && MutationRules.forSource(entity.getType()).isPresent();
    }

    // ------------------------------------------------------------------ injecao

    /**
     * Aplica uma dose de soro.
     *
     * @param injector quem aplicou (recebe as mensagens e vira alvo do mob)
     */
    public static Result inject(ServerLevel level, Mob mob, @Nullable Player injector, boolean volatileSerum) {
        Optional<MutationRules.Rule> found = MutationRules.forSource(mob.getType());
        if (found.isEmpty() || !isMutable(mob)) {
            tell(injector, Component.translatable("message.atrocities.mutation.incompatible", mob.getDisplayName())
                    .withStyle(ChatFormatting.RED));
            return Result.INCOMPATIBLE;
        }
        if (isTransforming(mob)) {
            tell(injector, Component.translatable("message.atrocities.mutation.busy", mob.getDisplayName())
                    .withStyle(ChatFormatting.RED));
            return Result.BUSY;
        }
        MutationRules.Rule rule = found.get();
        Vec3 center = mob.position().add(0, mob.getBbHeight() * 0.6, 0);
        GeneticsFx.sound(level, center, ModSounds.MUTAGEN_INJECT.get(), 1f, 1f);

        if (volatileSerum && level.random.nextFloat() < RUPTURE_CHANCE) {
            rupture(level, mob, rule.color());
            tell(injector, Component.translatable("message.atrocities.mutation.ruptured", mob.getDisplayName())
                    .withStyle(ChatFormatting.GOLD));
            return Result.RUPTURED;
        }

        int stage = Math.min(FINAL_STAGE, stage(mob) + 1);
        mob.addTag(MUTATED_TAG);
        CompoundTag data = mob.getPersistentData();
        data.putInt(STAGE, stage);
        mob.setPersistenceRequired();
        applyStage(mob, stage);
        mob.setHealth(mob.getMaxHealth());

        GeneticsFx.burst(level, GeneticsFx.dust(rule.color(), 1.6f + stage * 0.4f), center, 30 + stage * 15,
                mob.getBbWidth() * 0.6, 0.12);
        GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, center, 10 + stage * 5, mob.getBbWidth() * 0.4, 0.15);
        GeneticsFx.spiral(level, GeneticsFx.dust(rule.color(), 1.2f), mob.position(), mob.getBbWidth() + 0.4,
                mob.getBbHeight() + 0.5, 30);
        GeneticsFx.sound(level, center, ModSounds.MUTATION_STAGE.get(), 1.2f, 0.7f + 0.2f * stage);
        BossFx.shake(level, center, 10, 0.2f + 0.1f * stage, 8);

        if (injector != null && !injector.getAbilities().instabuild) {
            mob.setTarget(injector);
        }
        if (stage >= FINAL_STAGE) {
            data.putBoolean(ALPHA, volatileSerum);
            beginMetamorphosis(mob);
            tell(injector, Component.translatable(volatileSerum
                            ? "message.atrocities.mutation.metamorphosis_alpha" : "message.atrocities.mutation.metamorphosis",
                    mob.getDisplayName()).withStyle(volatileSerum ? ChatFormatting.GOLD : ChatFormatting.GREEN,
                    ChatFormatting.BOLD));
            return Result.TRANSFORMING;
        }
        tell(injector, Component.translatable("message.atrocities.mutation.stage", mob.getDisplayName(), stage, FINAL_STAGE)
                .withStyle(ChatFormatting.GREEN));
        return Result.MUTATED;
    }

    private static void tell(@Nullable Player player, Component message) {
        if (player != null) {
            GeneticsFx.notify(player, message);
        }
    }

    /** Bonus de cada estagio: cresce 15%, +75% de vida, +35% de dano, +6% de velocidade, armadura e firmeza. */
    private static void applyStage(Mob mob, int stage) {
        modify(mob, Attributes.SCALE, SCALE, 0.15 * stage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.MAX_HEALTH, HEALTH, 0.75 * stage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.ATTACK_DAMAGE, DAMAGE, 0.35 * stage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.MOVEMENT_SPEED, SPEED, 0.06 * stage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, 0.15 * stage, AttributeModifier.Operation.ADD_VALUE);
        modify(mob, Attributes.ARMOR, ARMOR, 2.0 * stage, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void modify(Mob mob, Holder<Attribute> attribute, ResourceLocation id, double amount,
                               AttributeModifier.Operation operation) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /** Soro volatil demais: o mob estoura numa chuva de biomassa. */
    private static void rupture(ServerLevel level, Mob mob, int color) {
        Vec3 center = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
        GeneticsFx.burst(level, GeneticsFx.dust(color, 2.4f), center, 80, mob.getBbWidth() * 0.6, 0.25);
        GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, center, 30, mob.getBbWidth() * 0.5, 0.3);
        GeneticsFx.burst(level, ParticleTypes.EXPLOSION, center, 2, 0.3, 0);
        GeneticsFx.sound(level, center, ModSounds.MUTATION_RUPTURE.get(), 1.6f, 0.9f);
        BossFx.shake(level, center, 14, 0.6f, 10);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(3),
                entity -> entity != mob && entity.isAlive())) {
            nearby.hurt(mob.damageSources().explosion(mob, null), 6f);
            Vec3 push = nearby.position().subtract(mob.position()).multiply(1, 0, 1);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : push.normalize().scale(0.8);
            nearby.push(push.x, 0.35, push.z);
            nearby.hurtMarked = true;
        }
        mob.spawnAtLocation(new ItemStack(ModItems.BIOMASS.get(), 2 + level.random.nextInt(3)));
        mob.discard();
    }

    // ------------------------------------------------------------------ metamorfose

    private static void beginMetamorphosis(Mob mob) {
        mob.getPersistentData().putInt(COCOON, METAMORPH_TICKS);
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!entity.getTags().contains(MUTATED_TAG) || !(entity instanceof Mob mob)
                || !(entity.level() instanceof ServerLevel level) || !mob.isAlive()) {
            return;
        }
        Optional<MutationRules.Rule> rule = MutationRules.forSource(mob.getType());
        int color = rule.map(MutationRules.Rule::color).orElse(0x7CD64B);
        CompoundTag data = mob.getPersistentData();
        int cocoon = data.getInt(COCOON);
        if (cocoon > 0) {
            tickMetamorphosis(level, mob, data, cocoon, color, rule.orElse(null));
            return;
        }
        int stage = data.getInt(STAGE);
        if (stage > 0 && mob.tickCount % 8 == 0) {
            // Veias brilhando: particulas na cor do soro, mais densas a cada estagio.
            for (int i = 0; i < stage; i++) {
                level.sendParticles(GeneticsFx.dust(color, 0.8f + stage * 0.3f), mob.getRandomX(0.7),
                        mob.getY() + level.random.nextDouble() * mob.getBbHeight(), mob.getRandomZ(0.7), 1, 0, 0.02, 0, 0);
            }
            if (stage >= 2 && mob.tickCount % 80 == 0) {
                GeneticsFx.sound(level, mob.position(), ModSounds.MUTATION_PULSE.get(), 0.5f, 1.3f);
            }
        }
    }

    private static void tickMetamorphosis(ServerLevel level, Mob mob, CompoundTag data, int cocoon, int color,
                                          @Nullable MutationRules.Rule rule) {
        int tick = METAMORPH_TICKS - cocoon;
        float progress = tick / (float) METAMORPH_TICKS;
        data.putInt(COCOON, cocoon - 1);
        mob.setDeltaMovement(Vec3.ZERO);

        // Convulsoes: o corpo gira aos trancos e incha em pulsos cada vez mais fortes.
        float jerk = (level.random.nextFloat() - 0.5f) * 40f * progress;
        mob.setYRot(mob.getYRot() + jerk);
        mob.yBodyRot = mob.getYRot();
        mob.yHeadRot = mob.getYRot() + (level.random.nextFloat() - 0.5f) * 60f * progress;
        double swell = 0.4 * progress + 0.08 * Math.sin(tick * (0.6 + progress))
                * (0.5 + progress);
        modify(mob, Attributes.SCALE, SWELL, swell, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        Vec3 center = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
        if (tick % 2 == 0) {
            GeneticsFx.spiral(level, GeneticsFx.dust(color, 1.0f + progress), mob.position(),
                    mob.getBbWidth() * 0.6 + 0.8 * (1 - progress), mob.getBbHeight() + 0.6, 12 + Math.round(progress * 12));
            level.sendParticles(ParticleTypes.ITEM_SLIME, center.x, center.y, center.z, 2 + Math.round(progress * 4),
                    mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.05);
        }
        int interval = Math.max(3, 14 - Math.round(progress * 11));
        if (tick % interval == 0) {
            GeneticsFx.sound(level, center, ModSounds.MUTATION_PULSE.get(), 1.4f, 0.8f + progress * 0.6f);
            GeneticsFx.burst(level, GeneticsFx.dust(color, 2.0f), center, 12, mob.getBbWidth() * 0.5, 0.1);
            BossFx.shake(level, center, 16, 0.15f + progress * 0.4f, 6);
        }
        if (cocoon - 1 <= 0) {
            hatch(level, mob, data, color, rule);
        }
    }

    private static void hatch(ServerLevel level, Mob mob, CompoundTag data, int color, @Nullable MutationRules.Rule rule) {
        boolean alpha = data.getBoolean(ALPHA);
        Entity created = rule != null ? rule.result().create(level) : null;
        if (!(created instanceof MutantEntity mutant)) {
            Atrocities.LOGGER.error("Regra de mutacao de {} nao aponta para um mutante valido",
                    mob.getType().getDescriptionId());
            data.remove(COCOON);
            mob.setNoAi(false);
            mob.setInvulnerable(false);
            return;
        }
        mutant.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0f);
        mutant.yBodyRot = mob.getYRot();
        mutant.yHeadRot = mob.getYRot();
        mutant.initMutant(alpha);
        if (mob.hasCustomName()) {
            mutant.setCustomName(mob.getCustomName());
        }
        mutant.startEmerging();
        level.addFreshEntity(mutant);

        Vec3 center = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        GeneticsFx.burst(level, GeneticsFx.dust(color, 3f), center, 140, mob.getBbWidth() + 0.5, 0.35);
        GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, center, 50, mob.getBbWidth() * 0.8, 0.4);
        GeneticsFx.sound(level, center, ModSounds.MUTATION_BURST.get(), 3f, alpha ? 0.7f : 0.85f);
        BossFx.shake(level, center, 36, 1.4f, 24);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(5),
                entity -> entity != mob && entity != mutant && entity.isAlive())) {
            Vec3 push = nearby.position().subtract(mob.position()).multiply(1, 0, 1);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : push.normalize();
            nearby.push(push.x * 1.2, 0.5, push.z * 1.2);
            nearby.hurtMarked = true;
        }
        Component message = Component.translatable(alpha ? "message.atrocities.mutation.born_alpha"
                        : "message.atrocities.mutation.born", mutant.getDisplayName())
                .withStyle(alpha ? ChatFormatting.GOLD : ChatFormatting.GREEN, ChatFormatting.BOLD);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(48))) {
            player.displayClientMessage(message, true);
        }
        mob.discard();
    }

    /** Usado pelo Codex e pelas dicas: progresso (0 a 1) da metamorfose. */
    public static float metamorphosisProgress(Entity entity) {
        int cocoon = entity.getPersistentData().getInt(COCOON);
        return cocoon <= 0 ? 0f : Mth.clamp(1f - cocoon / (float) METAMORPH_TICKS, 0f, 1f);
    }
}
