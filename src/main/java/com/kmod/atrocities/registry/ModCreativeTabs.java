package com.kmod.atrocities.registry;

import com.kmod.atrocities.genetics.Archetype;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Aba criativa "Atrocities": recebe automaticamente todo item registrado pelo mod.
 * Itens que so fazem sentido com dados (amostras, embrioes) aparecem como exemplos gerados do datapack.
 */
public final class ModCreativeTabs {

    private ModCreativeTabs() {
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            ModRegistries.CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.atrocities"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.SAMPLE_VIAL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        Set<Item> dataDriven = Set.of(ModItems.GENE_SAMPLE.get(), ModItems.UNSTABLE_EMBRYO.get(),
                                ModItems.MATURED_EMBRYO.get(), ModItems.WEB_GLOB.get(), ModItems.VOLATILE_CHARGE.get(),
                                ModItems.ETHER_ORB.get(), ModItems.RIFT_SHARD.get(), ModItems.GOO_GLOB.get());
                        ModRegistries.ITEMS.getEntries().stream()
                                .map(DeferredHolder::get)
                                .filter(item -> !dataDriven.contains(item))
                                .forEach(output::accept);
                        addGeneticExamples(parameters.holders(), output);
                    })
                    .build());

    /** Uma amostra de referencia por especie e um embriao de exemplo (instavel e maduro) por arquetipo. */
    private static void addGeneticExamples(HolderLookup.Provider registries, CreativeModeTab.Output output) {
        GeneticsRegistries.allSpecies(registries)
                .forEach(species -> output.accept(ModItems.geneSample(GeneSample.reference(species))));
        for (Archetype archetype : Archetype.values()) {
            List<GeneSample> samples = GeneticsRegistries.allSpecies(registries)
                    .filter(species -> species.value().archetype() == archetype)
                    .limit(2)
                    .map(GeneSample::reference)
                    .toList();
            EmbryoGenome.sequence(samples, registries).ifPresent(genome -> {
                output.accept(ModItems.embryo(genome));
                output.accept(ModItems.maturedEmbryo(genome));
            });
        }
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que a aba seja registrada.
    }
}
