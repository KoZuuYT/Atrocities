package com.kmod.atrocities.machine;

import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModBlockEntities;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Extrator de Biomassa: tritura materia organica (data map {@code atrocities:biomass_yield}) num acumulador;
 * cada 100 pontos viram 1 Biomassa, o combustivel das outras maquinas. Nao consome nutrientes.
 */
public class BiomassExtractorBlockEntity extends MachineBlockEntity {

    public static final int DURATION = 40;
    public static final int POINTS_PER_BIOMASS = 100;
    private static final int INPUT = 0;
    private static final int OUTPUT = 1;

    private int buffer;

    public BiomassExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIOMASS_EXTRACTOR.get(), MachineKind.BIOMASS_EXTRACTOR, pos, state);
    }

    @Override
    protected void beforeProcess(ServerLevel level) {
        ItemStack biomass = new ItemStack(ModItems.BIOMASS.get());
        boolean produced = false;
        while (buffer >= POINTS_PER_BIOMASS && canOutput(OUTPUT, biomass)) {
            output(OUTPUT, biomass);
            buffer -= POINTS_PER_BIOMASS;
            produced = true;
        }
        if (produced) {
            playSound(level, ModSounds.EXTRACTOR_OUTPUT.get(), 0.7f, 0.8f + level.random.nextFloat() * 0.2f);
            GeneticsFx.burst(level, GeneticsFx.dust(0x6E1B1B, 1.2f), center().add(0, 0.55, 0), 10, 0.25, 0);
            setChanged();
        }
    }

    @Override
    protected int processDuration(ServerLevel level) {
        int yield = MachineKind.biomassYield(inventory.getStackInSlot(INPUT));
        return yield > 0 && buffer < POINTS_PER_BIOMASS ? DURATION : 0;
    }

    @Override
    protected int nutrientCost(int currentProgress) {
        return 0;
    }

    @Override
    protected void workingTick(ServerLevel level) {
        if (progress % 10 == 1) {
            playSound(level, ModSounds.EXTRACTOR_GRIND.get(), 0.45f, 0.7f + level.random.nextFloat() * 0.25f);
            ItemStack input = inventory.getStackInSlot(INPUT);
            if (!input.isEmpty()) {
                Vec3 top = center().add(0, 0.55, 0);
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, input.copyWithCount(1)),
                        top.x, top.y, top.z, 6, 0.15, 0.05, 0.15, 0.08);
            }
        }
    }

    @Override
    protected void finish(ServerLevel level) {
        buffer += MachineKind.biomassYield(inventory.getStackInSlot(INPUT));
        inventory.extractItem(INPUT, 1, false);
    }

    @Override
    protected int extraData(int index) {
        return index == 0 ? buffer : 0;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("buffer", buffer);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        buffer = tag.getInt("buffer");
    }
}
