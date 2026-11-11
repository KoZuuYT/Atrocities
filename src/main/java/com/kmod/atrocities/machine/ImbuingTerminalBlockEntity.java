package com.kmod.atrocities.machine;

import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.imbue.ImbuedGenes;
import com.kmod.atrocities.imbue.Imbuing;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModBlockEntities;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Terminal de Imbuicao: grava os tracos de uma Amostra Genetica nos tanques de uma arma ou armadura,
 * usando um Soro Mutagenico como catalisador. Com um Frasco de Amostra vazio no lugar da amostra,
 * purga o ultimo gene do item.
 */
public class ImbuingTerminalBlockEntity extends MachineBlockEntity {

    public static final int DURATION = 300;
    public static final int PURGE_DURATION = 100;

    public static final int EQUIPMENT = 0;
    public static final int GENE = 1;
    public static final int CATALYST = 2;
    private static final int OUTPUT = 4;
    private static final int BYPRODUCT = 5;

    public ImbuingTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IMBUING_TERMINAL.get(), MachineKind.IMBUING_TERMINAL, pos, state);
    }

    /** O que o terminal faria com os itens atuais (compartilhado com a previsao da tela). */
    public enum Mode { NONE, PURGE, NEED_CATALYST, IMBUE }

    public static Mode mode(ItemStack equipment, ItemStack gene, ItemStack catalyst) {
        if (Imbuing.kindOf(equipment) == null) {
            return Mode.NONE;
        }
        if (gene.is(ModItems.SAMPLE_VIAL.get())) {
            return Imbuing.genes(equipment).isEmpty() ? Mode.NONE : Mode.PURGE;
        }
        if (GeneticCentrifugeBlockEntity.sample(gene) == null) {
            return Mode.NONE;
        }
        return isCatalyst(catalyst) ? Mode.IMBUE : Mode.NEED_CATALYST;
    }

    public static boolean isCatalyst(ItemStack stack) {
        return stack.is(ModItems.MUTAGEN_SERUM.get()) || stack.is(ModItems.VOLATILE_SERUM.get());
    }

    public static @Nullable Imbuing.Plan plan(ItemStack equipment, ItemStack gene, ItemStack catalyst,
                                              HolderLookup.Provider registries) {
        GeneSample sample = GeneticCentrifugeBlockEntity.sample(gene);
        if (sample == null) {
            return null;
        }
        return Imbuing.plan(equipment, sample, catalyst.is(ModItems.VOLATILE_SERUM.get()), registries);
    }

    @Override
    protected int processDuration(ServerLevel level) {
        if (!inventory.getStackInSlot(OUTPUT).isEmpty()) {
            return 0;
        }
        ItemStack equipment = inventory.getStackInSlot(EQUIPMENT);
        ItemStack gene = inventory.getStackInSlot(GENE);
        ItemStack catalyst = inventory.getStackInSlot(CATALYST);
        return switch (mode(equipment, gene, catalyst)) {
            case PURGE -> PURGE_DURATION;
            case IMBUE -> {
                Imbuing.Plan plan = plan(equipment, gene, catalyst, level.registryAccess());
                yield plan != null && plan.ok() && canOutput(BYPRODUCT, new ItemStack(ModItems.SAMPLE_VIAL.get()))
                        ? DURATION : 0;
            }
            default -> 0;
        };
    }

    @Override
    protected void workingTick(ServerLevel level) {
        if (progress % 25 == 1) {
            playSound(level, ModSounds.IMBUE_PROCESS.get(), 0.5f, 0.8f + progress / (float) maxProgress * 0.7f);
        }
        if (progress % 6 == 0) {
            Vec3 top = center().add(0, 0.6, 0);
            GeneticsFx.burst(level, ParticleTypes.ENCHANT, top, 3, 0.3, 0.3);
        }
    }

    @Override
    protected void finish(ServerLevel level) {
        ItemStack equipment = inventory.getStackInSlot(EQUIPMENT).copy();
        ItemStack gene = inventory.getStackInSlot(GENE);
        ItemStack catalyst = inventory.getStackInSlot(CATALYST);
        Vec3 top = center().add(0, 0.6, 0);

        Mode mode = mode(equipment, gene, catalyst);
        if (mode == Mode.PURGE) {
            Imbuing.setGenes(equipment, Imbuing.purged(Imbuing.genes(equipment)));
            inventory.setStackInSlot(EQUIPMENT, ItemStack.EMPTY);
            output(OUTPUT, equipment);
            playSound(level, SoundEvents.GRINDSTONE_USE, 0.8f, 1.1f);
            GeneticsFx.burst(level, ParticleTypes.SMOKE, top, 14, 0.25, 0.03);
            return;
        }
        if (mode != Mode.IMBUE) {
            return;
        }
        Imbuing.Plan plan = plan(equipment, gene, catalyst, level.registryAccess());
        if (plan == null || !plan.ok()) {
            return;
        }
        boolean volatileSerum = catalyst.is(ModItems.VOLATILE_SERUM.get());
        inventory.setStackInSlot(GENE, ItemStack.EMPTY);
        inventory.extractItem(CATALYST, 1, false);
        output(BYPRODUCT, new ItemStack(ModItems.SAMPLE_VIAL.get()));
        inventory.setStackInSlot(EQUIPMENT, ItemStack.EMPTY);

        if (volatileSerum && level.random.nextFloat() < Imbuing.VOLATILE_FAIL_CHANCE) {
            output(OUTPUT, equipment);
            playSound(level, SoundEvents.FIRE_EXTINGUISH, 1f, 0.8f);
            GeneticsFx.burst(level, ParticleTypes.LARGE_SMOKE, top, 20, 0.3, 0.05);
            return;
        }
        ImbuedGenes result = plan.result();
        Imbuing.setGenes(equipment, result);
        output(OUTPUT, equipment);

        int color = plan.added().isEmpty() ? 0xD070F0 : GeneticsRegistries.trait(level.registryAccess(),
                plan.added().getFirst().trait()).map(Trait::color).orElse(0xD070F0);
        playSound(level, ModSounds.IMBUE_COMPLETE.get(), 1f, 1f);
        GeneticsFx.spiral(level, GeneticsFx.dust(color, 1.3f), top, 0.6, 1.2, 32);
        GeneticsFx.burst(level, ParticleTypes.TOTEM_OF_UNDYING, top.add(0, 0.4, 0), 20, 0.3, 0.3);
    }
}
