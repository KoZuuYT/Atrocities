package com.kmod.atrocities.machine;

import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModBlockEntities;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sequenciador Genetico: combina de 2 a 4 amostras num Embriao Instavel
 * ({@link EmbryoGenome#sequence}). As amostras sao consumidas.
 */
public class GeneSequencerBlockEntity extends MachineBlockEntity {

    public static final int DURATION = 400;
    public static final int MIN_SAMPLES = 2;
    public static final int SAMPLE_SLOTS = 4;
    private static final int OUTPUT = 5;

    public GeneSequencerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENE_SEQUENCER.get(), MachineKind.GENE_SEQUENCER, pos, state);
    }

    /** Amostras presentes nos slots de entrada (usado tambem pela previsao da tela). */
    public static List<GeneSample> samples(IntFunction<ItemStack> slots) {
        List<GeneSample> samples = new ArrayList<>();
        for (int slot = 0; slot < SAMPLE_SLOTS; slot++) {
            GeneSample sample = GeneticCentrifugeBlockEntity.sample(slots.apply(slot));
            if (sample != null) {
                samples.add(sample);
            }
        }
        return samples;
    }

    @Override
    protected int processDuration(ServerLevel level) {
        if (!inventory.getStackInSlot(OUTPUT).isEmpty()) {
            return 0;
        }
        return samples(inventory::getStackInSlot).size() >= MIN_SAMPLES ? DURATION : 0;
    }

    @Override
    protected void workingTick(ServerLevel level) {
        if (progress % 40 == 1) {
            playSound(level, ModSounds.SEQUENCER_PROCESS.get(), 0.5f, 0.9f + progress / (float) maxProgress * 0.5f);
        }
    }

    @Override
    protected void finish(ServerLevel level) {
        List<GeneSample> samples = samples(inventory::getStackInSlot);
        EmbryoGenome.sequence(samples, level.registryAccess()).ifPresent(genome -> {
            for (int slot = 0; slot < SAMPLE_SLOTS; slot++) {
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
            output(OUTPUT, ModItems.embryo(genome));

            Vec3 top = center().add(0, 0.6, 0);
            playSound(level, ModSounds.SEQUENCER_COMPLETE.get(), 1f, 0.9f + genome.tier() * 0.05f);
            GeneticsFx.spiral(level, GeneticsFx.dust(genome.archetype().color(), 1.3f), top, 0.7, 1.4, 36);
            GeneticsFx.burst(level, ParticleTypes.ENCHANT, top.add(0, 0.4, 0), 40, 0.6, 0.6);
            GeneticsFx.burst(level, ParticleTypes.FLASH, top, 1, 0, 0);
        });
    }
}
