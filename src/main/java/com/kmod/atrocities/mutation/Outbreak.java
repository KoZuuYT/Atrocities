package com.kmod.atrocities.mutation;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.entity.mutant.MutantEntity;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Surto Mutagenico: evento noturno em que um foco de soro contamina mobs perto de um jogador.
 * <ol>
 *   <li>Tres ondas de mobs <b>infectados</b> (mais fortes, brilhando) surgem em volta do foco.</li>
 *   <li>Vencidas as ondas, o foco entra em metamorfose e nasce o mutante daquela linhagem (25% de chance de Alfa).</li>
 *   <li>Derrotar o mutante contem o surto e deixa uma recompensa extra (escamas e soros).</li>
 * </ol>
 * O surto se dissipa se todos os jogadores se afastarem por um minuto ou se passar do tempo limite.
 */
public final class Outbreak {

    public static final String INFECTED_TAG = "atrocities.infected";
    private static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "infected_health");
    private static final ResourceLocation DAMAGE = ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "infected_damage");
    private static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "infected_speed");

    public static final int WAVES = 3;
    private static final int HATCH_TICKS = 70;
    private static final int WAVE_TIMEOUT = 20 * 75;
    private static final int ABANDON_TICKS = 20 * 60;
    private static final int MAX_TICKS = 20 * 60 * 15;
    private static final double AREA = 64.0;
    private static final float ALPHA_CHANCE = 0.25f;

    private static final List<Outbreak> ACTIVE = new ArrayList<>();
    private static final Set<Long> CHECKED_NIGHTS = new HashSet<>();

    private enum Stage { WAVES, HATCHING, MUTANT, OVER }

    private final ServerLevel level;
    private final MutationRules.Rule rule;
    private final Vec3 center;
    private final ServerBossEvent bar;
    private final Set<UUID> alive = new HashSet<>();
    private Stage stage = Stage.WAVES;
    private int wave;
    private int waveSize;
    private int waveTicks;
    private int hatchTicks;
    private int ticks;
    private int abandoned;
    private int missing;
    @Nullable
    private UUID mutant;

    private Outbreak(ServerLevel level, MutationRules.Rule rule, Vec3 center) {
        this.level = level;
        this.rule = rule;
        this.center = center;
        this.bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setDarkenScreen(true);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                tickLevel(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class, event -> {
            Entity entity = event.getEntity();
            if (entity.tickCount % 8 == 0 && entity.level() instanceof ServerLevel level
                    && entity.getTags().contains(INFECTED_TAG) && entity.isAlive()) {
                int color = entity.getPersistentData().getInt("atrocities.infected_color");
                level.sendParticles(GeneticsFx.dust(color == 0 ? 0x8CFF4F : color, 1.2f), entity.getX(),
                        entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 2, entity.getBbWidth() * 0.4,
                        entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4, 0.01);
            }
        });
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, event -> {
            if (event.getEntity().level() instanceof ServerLevel) {
                for (Outbreak outbreak : ACTIVE) {
                    outbreak.onDeath(event.getEntity());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> {
            ACTIVE.forEach(outbreak -> outbreak.bar.removeAllPlayers());
            ACTIVE.clear();
            CHECKED_NIGHTS.clear();
        });
    }

    // ------------------------------------------------------------------ inicio

    /** Sorteio noturno: uma vez por noite, no comeco dela, em cada mundo com ciclo de dia. */
    private static void tickLevel(ServerLevel level) {
        Iterator<Outbreak> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Outbreak outbreak = it.next();
            if (outbreak.level == level && !outbreak.tick()) {
                outbreak.bar.removeAllPlayers();
                it.remove();
            }
        }
        if (!AtrocitiesConfig.OUTBREAKS_ENABLED.get() || level.dimensionType().hasFixedTime()
                || level.getGameTime() % 20 != 0) {
            return;
        }
        long time = level.getDayTime() % 24000L;
        if (time < 13000L || time > 14000L) {
            return;
        }
        long night = (level.getDayTime() / 24000L) * 31 + level.dimension().location().hashCode();
        if (!CHECKED_NIGHTS.add(night)) {
            return;
        }
        if (level.random.nextDouble() >= AtrocitiesConfig.OUTBREAK_CHANCE.get()) {
            return;
        }
        List<ServerPlayer> candidates = level.players().stream()
                .filter(player -> !player.isSpectator() && !player.isCreative() && near(player.position()) == null)
                .toList();
        if (!candidates.isEmpty()) {
            start(level, candidates.get(level.random.nextInt(candidates.size())), null);
        }
    }

    @Nullable
    private static Outbreak near(Vec3 pos) {
        for (Outbreak outbreak : ACTIVE) {
            if (outbreak.center.distanceToSqr(pos) < AREA * AREA * 4) {
                return outbreak;
            }
        }
        return null;
    }

    /**
     * Comeca um surto perto do jogador.
     *
     * @param ruleId regra de mutacao (null = sorteada)
     * @return o surto criado, ou vazio se nao houver regra/lugar valido
     */
    public static Optional<Outbreak> start(ServerLevel level, ServerPlayer player, @Nullable ResourceLocation ruleId) {
        List<MutationRules.Rule> rules = MutationRules.all().stream()
                .filter(rule -> ruleId == null || rule.id().equals(ruleId))
                .filter(rule -> !rule.sources().isEmpty())
                .toList();
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        MutationRules.Rule rule = rules.get(level.random.nextInt(rules.size()));
        double angle = level.random.nextDouble() * Mth.TWO_PI;
        double distance = 18 + level.random.nextDouble() * 8;
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - player.getY()) > 12) {
            y = player.getBlockY();
        }
        Outbreak outbreak = new Outbreak(level, rule, new Vec3(x + 0.5, y, z + 0.5));
        ACTIVE.add(outbreak);
        outbreak.announce();
        Atrocities.LOGGER.info("Surto mutagenico ({}) em {} {} {}", rule.id(), x, y, z);
        return Optional.of(outbreak);
    }

    private Component mutantName() {
        return rule.result().getDescription();
    }

    private void announce() {
        Component message = Component.translatable("message.atrocities.outbreak.start", mutantName())
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        for (ServerPlayer player : playersNear(AREA * 2)) {
            player.displayClientMessage(message, false);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    ModSounds.OUTBREAK_START,
                    net.minecraft.sounds.SoundSource.HOSTILE, player.getX(), player.getY(), player.getZ(), 1.5f, 1f,
                    level.random.nextLong()));
        }
        GeneticsFx.burst(level, GeneticsFx.dust(rule.color(), 3f), center.add(0, 1, 0), 80, 1.5, 0.3);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
        BossFx.shake(level, center, 48, 0.8f, 20);
    }

    // ------------------------------------------------------------------ andamento

    /** @return false quando o surto acabou e deve ser removido */
    private boolean tick() {
        ticks++;
        List<ServerPlayer> players = playersNear(AREA);
        updateBar(players);
        if (players.isEmpty()) {
            if (++abandoned > ABANDON_TICKS) {
                dissipate("message.atrocities.outbreak.faded");
                return false;
            }
        } else {
            abandoned = 0;
        }
        if (ticks > MAX_TICKS && stage != Stage.MUTANT) {
            dissipate("message.atrocities.outbreak.faded");
            return false;
        }
        if (ticks % 10 == 0) {
            // O foco pulsa enquanto o surto dura.
            GeneticsFx.burst(level, GeneticsFx.dust(rule.color(), 2f), center.add(0, 0.6, 0), 8, 0.6, 0.05);
            level.sendParticles(ParticleTypes.ITEM_SLIME, center.x, center.y + 0.3, center.z, 3, 0.5, 0.1, 0.5, 0.05);
        }
        switch (stage) {
            case WAVES -> tickWaves(players);
            case HATCHING -> tickHatching();
            case MUTANT -> {
                // Sumiu sem morrer (descarregado ou removido por comando): o surto se desfaz.
                missing = mutant == null || level.getEntity(mutant) == null ? missing + 1 : 0;
                if (missing > 200) {
                    dissipate("message.atrocities.outbreak.faded");
                    return false;
                }
            }
            case OVER -> {
                return false;
            }
        }
        return stage != Stage.OVER;
    }

    private void tickWaves(List<ServerPlayer> players) {
        alive.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity living) || !living.isAlive());
        waveTicks++;
        boolean cleared = alive.isEmpty() || waveTicks > WAVE_TIMEOUT;
        if (wave > 0 && !cleared) {
            return;
        }
        if (wave >= WAVES) {
            stage = Stage.HATCHING;
            hatchTicks = 0;
            tell("message.atrocities.outbreak.hatching", ChatFormatting.GOLD);
            return;
        }
        spawnWave(players);
    }

    private void spawnWave(List<ServerPlayer> players) {
        wave++;
        waveTicks = 0;
        int count = Math.min(10, 3 + wave + 2 * Math.max(0, players.size() - 1));
        waveSize = 0;
        for (int i = 0; i < count; i++) {
            EntityType<?> type = rule.sources().get(level.random.nextInt(rule.sources().size()));
            Mob mob = infect(type, players);
            if (mob != null) {
                alive.add(mob.getUUID());
                waveSize++;
            }
        }
        GeneticsFx.sound(level, center, ModSounds.OUTBREAK_WAVE.get(), 3f, 0.8f + wave * 0.1f);
        tell(Component.translatable("message.atrocities.outbreak.wave", wave, WAVES), ChatFormatting.GREEN);
    }

    @Nullable
    private Mob infect(EntityType<?> type, List<ServerPlayer> players) {
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        double angle = level.random.nextDouble() * Mth.TWO_PI;
        double distance = 4 + level.random.nextDouble() * 6;
        double x = center.x + Math.cos(angle) * distance;
        double z = center.z + Math.sin(angle) * distance;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (Math.abs(y - center.y) > 6) {
            y = Mth.floor(center.y);
        }
        boolean flying = type == EntityType.PHANTOM;
        mob.moveTo(x, y + (flying ? 8 : 0), z, level.random.nextFloat() * 360f, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.EVENT, null);
        if (mob instanceof Slime slime) {
            slime.setSize(2, true);
        }
        mob.addTag(INFECTED_TAG);
        mob.getPersistentData().putInt("atrocities.infected_color", rule.color());
        mob.setPersistenceRequired();
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addOrReplacePermanentModifier(new AttributeModifier(HEALTH, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        var damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.addOrReplacePermanentModifier(new AttributeModifier(DAMAGE, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrReplacePermanentModifier(new AttributeModifier(SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        mob.setHealth(mob.getMaxHealth());
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 5, 0, false, false));
        if (!players.isEmpty()) {
            mob.setTarget(players.get(level.random.nextInt(players.size())));
        }
        level.addFreshEntity(mob);
        GeneticsFx.burst(level, GeneticsFx.dust(rule.color(), 2f), mob.position().add(0, 0.6, 0), 24, 0.4, 0.1);
        level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.3, mob.getZ(), 10, 0.3, 0.2, 0.3, 0.02);
        return mob;
    }

    private void tickHatching() {
        hatchTicks++;
        float progress = hatchTicks / (float) HATCH_TICKS;
        int interval = Math.max(3, 14 - Math.round(progress * 11));
        if (hatchTicks % interval == 0) {
            GeneticsFx.sound(level, center, ModSounds.MUTATION_PULSE.get(), 2f, 0.7f + progress * 0.6f);
            GeneticsFx.burst(level, GeneticsFx.dust(rule.color(), 2.5f), center.add(0, 1, 0), 20, 1.0, 0.15);
            BossFx.shake(level, center, 24, 0.2f + progress * 0.5f, 6);
        }
        if (hatchTicks < HATCH_TICKS) {
            return;
        }
        Entity created = rule.result().create(level);
        if (!(created instanceof MutantEntity entity)) {
            Atrocities.LOGGER.error("Surto {}: resultado nao e um mutante", rule.id());
            stage = Stage.OVER;
            return;
        }
        boolean alpha = level.random.nextFloat() < ALPHA_CHANCE;
        entity.moveTo(center.x, center.y, center.z, level.random.nextFloat() * 360f, 0f);
        entity.initMutant(alpha);
        entity.addTag(INFECTED_TAG);
        entity.startEmerging();
        level.addFreshEntity(entity);
        mutant = entity.getUUID();
        stage = Stage.MUTANT;
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
        GeneticsFx.burst(level, GeneticsFx.dust(rule.color(), 3f), center.add(0, 1, 0), 140, 1.5, 0.35);
        GeneticsFx.sound(level, center, ModSounds.MUTATION_BURST.get(), 3f, alpha ? 0.7f : 0.85f);
        BossFx.shake(level, center, 40, 1.4f, 24);
        tell(Component.translatable(alpha ? "message.atrocities.mutation.born_alpha" : "message.atrocities.mutation.born",
                entity.getDisplayName()), alpha ? ChatFormatting.GOLD : ChatFormatting.GREEN);
    }

    private void onDeath(LivingEntity entity) {
        if (entity.level() != level) {
            return;
        }
        alive.remove(entity.getUUID());
        if (stage == Stage.MUTANT && entity.getUUID().equals(mutant)) {
            // Surto contido: recompensa extra no lugar da morte.
            Vec3 at = entity.position().add(0, 0.5, 0);
            boolean alpha = entity instanceof MutantEntity m && m.isAlpha();
            drop(at, new ItemStack(ModItems.MUTANT_SCALE.get(), 4 + level.random.nextInt(5) + (alpha ? 4 : 0)));
            drop(at, new ItemStack(ModItems.MUTAGEN_SERUM.get(), 2));
            if (alpha) {
                drop(at, new ItemStack(ModItems.VOLATILE_SERUM.get(), 1));
            }
            GeneticsFx.sound(level, center, ModSounds.OUTBREAK_CLEARED.get(), 3f, 1f);
            tell("message.atrocities.outbreak.cleared", ChatFormatting.GOLD);
            stage = Stage.OVER;
        }
    }

    private void drop(Vec3 at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    /** Termina sem vencedor: infectados restantes viram fumaca. */
    private void dissipate(String key) {
        for (UUID id : alive) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.5, entity.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                entity.discard();
            }
        }
        alive.clear();
        tell(key, ChatFormatting.GRAY);
        stage = Stage.OVER;
    }

    /** Encerra o surto na hora (comando). */
    public static int stopAll(ServerLevel level) {
        int count = 0;
        Iterator<Outbreak> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Outbreak outbreak = it.next();
            if (outbreak.level == level) {
                outbreak.dissipate("message.atrocities.outbreak.faded");
                outbreak.bar.removeAllPlayers();
                it.remove();
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ barra e mensagens

    private void updateBar(List<ServerPlayer> players) {
        if (ticks % 20 == 1) {
            Set<ServerPlayer> current = new HashSet<>(bar.getPlayers());
            for (ServerPlayer player : players) {
                if (!current.remove(player)) {
                    bar.addPlayer(player);
                }
            }
            current.forEach(bar::removePlayer);
        }
        Component title = Component.translatable("bossbar.atrocities.outbreak", mutantName());
        switch (stage) {
            case WAVES -> {
                bar.setName(title.copy().append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable("bossbar.atrocities.outbreak.wave", wave, WAVES)));
                bar.setProgress(waveSize <= 0 ? 1f : Mth.clamp(alive.size() / (float) waveSize, 0f, 1f));
                bar.setColor(BossEvent.BossBarColor.GREEN);
            }
            case HATCHING -> {
                bar.setName(title.copy().append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable("bossbar.atrocities.outbreak.hatching")));
                bar.setProgress(Mth.clamp(hatchTicks / (float) HATCH_TICKS, 0f, 1f));
                bar.setColor(BossEvent.BossBarColor.YELLOW);
            }
            default -> bar.setVisible(false);
        }
    }

    private List<ServerPlayer> playersNear(double radius) {
        return level.getPlayers(player -> !player.isSpectator() && player.position().distanceToSqr(center) <= radius * radius);
    }

    private void tell(String key, ChatFormatting color) {
        tell(Component.translatable(key, mutantName()), color);
    }

    private void tell(Component message, ChatFormatting color) {
        Component styled = message.copy().withStyle(color, ChatFormatting.BOLD);
        for (ServerPlayer player : playersNear(AREA * 1.5)) {
            player.displayClientMessage(styled, true);
        }
    }

    public static List<Outbreak> active() {
        return List.copyOf(ACTIVE);
    }

    public Vec3 center() {
        return center;
    }

    public Level level() {
        return level;
    }
}
