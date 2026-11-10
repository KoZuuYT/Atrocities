package com.kmod.atrocities.machine;

import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Species;
import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModBlockEntities;
import com.kmod.atrocities.registry.ModDataComponents;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Incubadora Biologica: gesta um Embriao Instavel ate virar Embriao Maduro.
 * <ul>
 *   <li>Duracao por tier: 1, 2, 3 e 5 minutos. Gasta 1 nutriente a cada 2 ticks.</li>
 *   <li>Sem nutrientes o embriao passa fome: o progresso para e ele perde 1 de estabilidade a cada 5 s.</li>
 *   <li>Ao fim, abaixo de {@value #ABORT_THRESHOLD}% de estabilidade pode abortar (2% por ponto abaixo,
 *       ate 70%): o embriao se perde e crias da linhagem rastejam para fora.</li>
 *   <li>Se nao abortar, chance de mutacao = (100 - estabilidade) / 2 %.</li>
 * </ul>
 */
public class BiologicalIncubatorBlockEntity extends MachineBlockEntity {

    private static final int[] INCUBATION_TICKS = {1200, 2400, 3600, 6000};
    public static final int ABORT_THRESHOLD = 35;
    private static final int STARVE_INTERVAL = 100;
    private static final int MAX_ABORTED_SPAWN = 3;
    private static final double NOTIFY_RADIUS = 16;

    private static final int EMBRYO = 0;
    private static final int OUTPUT = 2;

    private int stabilityLoss;
    private int starveTicks;
    private boolean starving;

    public BiologicalIncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIOLOGICAL_INCUBATOR.get(), MachineKind.BIOLOGICAL_INCUBATOR, pos, state);
    }

    public static int incubationTicks(int tier) {
        return INCUBATION_TICKS[Mth.clamp(tier, 1, INCUBATION_TICKS.length) - 1];
    }

    /** Chance de aborto (0 a 1) para uma estabilidade final. */
    public static float abortChance(int stability) {
        return stability >= ABORT_THRESHOLD ? 0f : Math.min(0.7f, (ABORT_THRESHOLD - stability) * 0.02f);
    }

    /** Chance de mutacao (0 a 1) para uma estabilidade final. */
    public static float mutationChance(int stability) {
        return Mth.clamp((100 - stability) / 200f, 0f, 0.5f);
    }

    private @Nullable EmbryoGenome genome() {
        return inventory.getStackInSlot(EMBRYO).get(ModDataComponents.EMBRYO_GENOME.get());
    }

    /** Item exibido flutuando dentro do tanque (cliente). */
    public ItemStack displayStack() {
        ItemStack embryo = inventory.getStackInSlot(EMBRYO);
        return embryo.isEmpty() ? inventory.getStackInSlot(OUTPUT) : embryo;
    }

    @Override
    protected int processDuration(ServerLevel level) {
        EmbryoGenome genome = genome();
        if (genome == null || !inventory.getStackInSlot(OUTPUT).isEmpty()) {
            return 0;
        }
        return incubationTicks(genome.tier());
    }

    @Override
    protected int nutrientCost(int currentProgress) {
        return currentProgress % 2 == 0 ? 1 : 0;
    }

    @Override
    protected void workingTick(ServerLevel level) {
        starving = false;
        float ratio = progress / (float) Math.max(1, maxProgress);
        int interval = Math.round(Mth.lerp(ratio, 40f, 14f));
        if (progress % interval == 0) {
            playSound(level, ModSounds.INCUBATOR_HEARTBEAT.get(), 0.6f + ratio * 0.3f, 0.85f + ratio * 0.35f);
        }
    }

    @Override
    protected void starvingTick(ServerLevel level) {
        starving = true;
        starveTicks++;
        if (starveTicks % STARVE_INTERVAL == 0) {
            stabilityLoss = Math.min(100, stabilityLoss + 1);
            playSound(level, ModSounds.INCUBATOR_HEARTBEAT.get(), 0.35f, 0.55f);
            GeneticsFx.burst(level, ParticleTypes.SMOKE, center().add(0, 0.55, 0), 4, 0.2, 0.01);
        }
    }

    @Override
    protected void onInputChanged() {
        stabilityLoss = 0;
        starveTicks = 0;
        starving = false;
    }

    @Override
    protected void finish(ServerLevel level) {
        EmbryoGenome genome = genome();
        if (genome == null) {
            return;
        }
        int stability = Mth.clamp(genome.stability() - stabilityLoss, 0, 100);
        stabilityLoss = 0;
        starveTicks = 0;

        if (level.random.nextFloat() < abortChance(stability)) {
            abort(level, genome);
            return;
        }

        EmbryoGenome result = genome.withStability(stability);
        Component mutationNote = null;
        if (level.random.nextFloat() < mutationChance(stability)) {
            EmbryoGenome.Mutation mutation = result.mutate(level.registryAccess(), level.random);
            result = mutation.genome();
            mutationNote = mutation.trait()
                    .map(trait -> Component.translatable("message.atrocities.incubator.mutated",
                            Trait.displayName(trait).withStyle(ChatFormatting.LIGHT_PURPLE)))
                    .orElseGet(() -> Component.translatable("message.atrocities.incubator.mutated_unstable"));
        }

        inventory.setStackInSlot(EMBRYO, ItemStack.EMPTY);
        output(OUTPUT, ModItems.maturedEmbryo(result));

        Vec3 top = center().add(0, 0.5, 0);
        playSound(level, ModSounds.INCUBATOR_HATCH.get(), 1f, 0.8f);
        GeneticsFx.burst(level, GeneticsFx.dust(result.archetype().color(), 1.6f), top, 40, 0.4, 0);
        GeneticsFx.burst(level, ParticleTypes.SCULK_SOUL, top, 8, 0.3, 0.03);
        notifyNearby(level, Component.translatable("message.atrocities.incubator.matured")
                .withStyle(ChatFormatting.GREEN));
        if (mutationNote != null) {
            playSound(level, ModSounds.INCUBATOR_MUTATE.get(), 1f, 1f);
            GeneticsFx.spiral(level, ParticleTypes.WITCH, top.add(0, -0.4, 0), 0.6, 1.5, 30);
            notifyNearby(level, mutationNote.copy().withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    /** O embriao se desfaz; ate 3 crias da linhagem rastejam para fora, hostis. */
    private void abort(ServerLevel level, EmbryoGenome genome) {
        inventory.setStackInSlot(EMBRYO, ItemStack.EMPTY);
        Vec3 top = center().add(0, 0.6, 0);
        playSound(level, ModSounds.INCUBATOR_ABORT.get(), 1.2f, 0.7f);
        GeneticsFx.burst(level, GeneticsFx.dust(0x7A0E14, 2f), top, 60, 0.6, 0);
        GeneticsFx.burst(level, ParticleTypes.EXPLOSION, top, 1, 0, 0);
        GeneticsFx.burst(level, ParticleTypes.DAMAGE_INDICATOR, top, 10, 0.4, 0.2);

        BlockPos spawnAt = level.getBlockState(worldPosition.above()).getCollisionShape(level, worldPosition.above())
                .isEmpty() ? worldPosition.above() : worldPosition.relative(getBlockState().getValue(MachineBlock.FACING));
        int spawned = 0;
        for (ResourceKey<Species> key : genome.lineage()) {
            if (spawned >= MAX_ABORTED_SPAWN) {
                break;
            }
            Species species = GeneticsRegistries.species(level.registryAccess(), key).orElse(null);
            if (species == null) {
                continue;
            }
            Entity entity = species.entity().create(level);
            if (entity == null) {
                continue;
            }
            entity.moveTo(spawnAt.getX() + 0.5, spawnAt.getY(), spawnAt.getZ() + 0.5,
                    level.random.nextFloat() * 360f, 0f);
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnAt), MobSpawnType.MOB_SUMMONED, null);
            }
            level.addFreshEntity(entity);
            spawned++;
        }
        notifyNearby(level, Component.translatable("message.atrocities.incubator.aborted")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    private void notifyNearby(ServerLevel level, Component message) {
        AABB area = new AABB(worldPosition).inflate(NOTIFY_RADIUS);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            player.displayClientMessage(message, false);
        }
    }

    /** extra 0: estabilidade efetiva (-1 sem embriao); extra 1: passando fome (0/1). */
    @Override
    protected int extraData(int index) {
        if (index == 0) {
            EmbryoGenome genome = genome();
            return genome == null ? -1 : Mth.clamp(genome.stability() - stabilityLoss, 0, 100);
        }
        return index == 1 && starving ? 1 : 0;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("stability_loss", stabilityLoss);
        tag.putInt("starve_ticks", starveTicks);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        stabilityLoss = tag.getInt("stability_loss");
        starveTicks = tag.getInt("starve_ticks");
    }
}
