package com.kmod.atrocities.registry;

import com.kmod.atrocities.machine.BiologicalIncubatorBlockEntity;
import com.kmod.atrocities.machine.BiomassExtractorBlockEntity;
import com.kmod.atrocities.machine.GeneSequencerBlockEntity;
import com.kmod.atrocities.machine.GeneticCentrifugeBlockEntity;
import com.kmod.atrocities.machine.ImbuingTerminalBlockEntity;
import com.kmod.atrocities.machine.MachineBlockEntity;
import com.kmod.atrocities.machine.MachineKind;
import java.util.function.Supplier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Block entities das maquinas e a capability de itens (funis e tubos). */
public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static final Supplier<BlockEntityType<BiomassExtractorBlockEntity>> BIOMASS_EXTRACTOR =
            ModRegistries.BLOCK_ENTITY_TYPES.register("biomass_extractor", () -> BlockEntityType.Builder
                    .of(BiomassExtractorBlockEntity::new, ModBlocks.BIOMASS_EXTRACTOR.get()).build(null));
    public static final Supplier<BlockEntityType<GeneticCentrifugeBlockEntity>> GENETIC_CENTRIFUGE =
            ModRegistries.BLOCK_ENTITY_TYPES.register("genetic_centrifuge", () -> BlockEntityType.Builder
                    .of(GeneticCentrifugeBlockEntity::new, ModBlocks.GENETIC_CENTRIFUGE.get()).build(null));
    public static final Supplier<BlockEntityType<GeneSequencerBlockEntity>> GENE_SEQUENCER =
            ModRegistries.BLOCK_ENTITY_TYPES.register("gene_sequencer", () -> BlockEntityType.Builder
                    .of(GeneSequencerBlockEntity::new, ModBlocks.GENE_SEQUENCER.get()).build(null));
    public static final Supplier<BlockEntityType<BiologicalIncubatorBlockEntity>> BIOLOGICAL_INCUBATOR =
            ModRegistries.BLOCK_ENTITY_TYPES.register("biological_incubator", () -> BlockEntityType.Builder
                    .of(BiologicalIncubatorBlockEntity::new, ModBlocks.BIOLOGICAL_INCUBATOR.get()).build(null));
    public static final Supplier<BlockEntityType<ImbuingTerminalBlockEntity>> IMBUING_TERMINAL =
            ModRegistries.BLOCK_ENTITY_TYPES.register("imbuing_terminal", () -> BlockEntityType.Builder
                    .of(ImbuingTerminalBlockEntity::new, ModBlocks.IMBUING_TERMINAL.get()).build(null));

    public static BlockEntityType<? extends MachineBlockEntity> typeFor(MachineKind kind) {
        return switch (kind) {
            case BIOMASS_EXTRACTOR -> BIOMASS_EXTRACTOR.get();
            case GENETIC_CENTRIFUGE -> GENETIC_CENTRIFUGE.get();
            case GENE_SEQUENCER -> GENE_SEQUENCER.get();
            case BIOLOGICAL_INCUBATOR -> BIOLOGICAL_INCUBATOR.get();
            case IMBUING_TERMINAL -> IMBUING_TERMINAL.get();
        };
    }

    public static void registerCapabilities(IEventBus modBus) {
        modBus.addListener(RegisterCapabilitiesEvent.class, event -> {
            for (MachineKind kind : MachineKind.values()) {
                itemHandler(event, typeFor(kind));
            }
        });
    }

    private static <B extends MachineBlockEntity> void itemHandler(RegisterCapabilitiesEvent event,
                                                                   BlockEntityType<B> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (machine, side) -> machine.automation());
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que os tipos sejam registrados.
    }
}
