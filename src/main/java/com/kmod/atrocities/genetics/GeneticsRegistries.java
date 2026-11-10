package com.kmod.atrocities.genetics;

import com.kmod.atrocities.Atrocities;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Registros de datapack da genetica ({@code atrocities:species} e {@code atrocities:trait}).
 * Sao sincronizados com os clientes para que os tooltips mostrem nomes e cores.
 */
public final class GeneticsRegistries {

    private GeneticsRegistries() {
    }

    public static final ResourceKey<Registry<Trait>> TRAIT =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "trait"));
    public static final ResourceKey<Registry<Species>> SPECIES =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "species"));

    public static void register(IEventBus modBus) {
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, event -> {
            event.dataPackRegistry(TRAIT, Trait.CODEC, Trait.CODEC);
            event.dataPackRegistry(SPECIES, Species.CODEC, Species.CODEC);
        });
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> validate(event.getServer().registryAccess()));
    }

    /** Registra no log o que foi carregado e avisa sobre especies que apontam para tracos inexistentes. */
    private static void validate(HolderLookup.Provider registries) {
        long traits = registries.lookup(TRAIT).map(lookup -> lookup.listElements().count()).orElse(0L);
        long species = allSpecies(registries).count();
        Atrocities.LOGGER.info("Genetica carregada: {} especies, {} tracos.", species, traits);
        allSpecies(registries).forEach(ref -> ref.value().traits().forEach(entry -> {
            if (trait(registries, entry.trait()).isEmpty()) {
                Atrocities.LOGGER.warn("Especie {} referencia o traco inexistente {}.",
                        ref.key().location(), entry.trait().location());
            }
        }));
    }

    /** Todas as especies, em ordem estavel de id. */
    public static Stream<Holder.Reference<Species>> allSpecies(HolderLookup.Provider registries) {
        return registries.lookup(SPECIES).stream()
                .flatMap(HolderLookup::listElements)
                .sorted(Comparator.comparing(ref -> ref.key().location()));
    }

    /** Especie cuja entidade e {@code type}, se houver. */
    public static Optional<Holder.Reference<Species>> speciesFor(HolderLookup.Provider registries, EntityType<?> type) {
        return allSpecies(registries).filter(ref -> ref.value().entity() == type).findFirst();
    }

    public static Optional<Species> species(HolderLookup.Provider registries, ResourceKey<Species> key) {
        return registries.lookup(SPECIES).flatMap(lookup -> lookup.get(key)).map(Holder::value);
    }

    public static Optional<Trait> trait(HolderLookup.Provider registries, ResourceKey<Trait> key) {
        return registries.lookup(TRAIT).flatMap(lookup -> lookup.get(key)).map(Holder::value);
    }
}
