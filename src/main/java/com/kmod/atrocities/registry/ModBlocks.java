package com.kmod.atrocities.registry;

import com.kmod.atrocities.machine.MachineBlock;
import com.kmod.atrocities.machine.MachineKind;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;

/** Blocos do Atrocities e seus itens. */
public final class ModBlocks {

    private ModBlocks() {
    }

    private static final Map<MachineKind, DeferredBlock<MachineBlock>> MACHINES = new EnumMap<>(MachineKind.class);

    public static final DeferredBlock<MachineBlock> BIOMASS_EXTRACTOR =
            machine(MachineKind.BIOMASS_EXTRACTOR, 4, false, Rarity.COMMON);
    public static final DeferredBlock<MachineBlock> GENETIC_CENTRIFUGE =
            machine(MachineKind.GENETIC_CENTRIFUGE, 5, false, Rarity.UNCOMMON);
    public static final DeferredBlock<MachineBlock> GENE_SEQUENCER =
            machine(MachineKind.GENE_SEQUENCER, 7, false, Rarity.RARE);
    public static final DeferredBlock<MachineBlock> BIOLOGICAL_INCUBATOR =
            machine(MachineKind.BIOLOGICAL_INCUBATOR, 9, true, Rarity.RARE);
    public static final DeferredBlock<MachineBlock> IMBUING_TERMINAL =
            machine(MachineKind.IMBUING_TERMINAL, 8, false, Rarity.EPIC);

    private static DeferredBlock<MachineBlock> machine(MachineKind kind, int litLight, boolean glass, Rarity rarity) {
        BlockBehaviour.Properties base = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5f, 6f)
                .requiresCorrectToolForDrops()
                .sound(glass ? SoundType.GLASS : SoundType.NETHERITE_BLOCK)
                .lightLevel(state -> state.getValue(MachineBlock.LIT) ? litLight : 0);
        BlockBehaviour.Properties properties = glass
                ? base.noOcclusion().isViewBlocking((state, level, pos) -> false)
                : base;
        DeferredBlock<MachineBlock> block = ModRegistries.BLOCKS.register(kind.id(),
                () -> new MachineBlock(kind, properties));
        ModRegistries.ITEMS.registerItem(kind.id(), props -> new BlockItem(block.get(), props),
                new Item.Properties().rarity(rarity));
        MACHINES.put(kind, block);
        return block;
    }

    public static MachineBlock machine(MachineKind kind) {
        return MACHINES.get(kind).get();
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que os blocos sejam registrados.
    }
}
