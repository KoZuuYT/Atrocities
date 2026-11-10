package com.kmod.atrocities.registry;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.machine.BiomassYield;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Data maps do Atrocities (valores por item definidos em JSON, sincronizados com o cliente). */
public final class ModDataMaps {

    private ModDataMaps() {
    }

    /** {@code data/<ns>/data_maps/item/biomass_yield.json}: pontos de biomassa por item no Extrator. */
    public static final DataMapType<Item, BiomassYield> BIOMASS_YIELD = DataMapType
            .builder(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "biomass_yield"), Registries.ITEM,
                    BiomassYield.CODEC)
            .synced(BiomassYield.CODEC, false)
            .build();

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterDataMapTypesEvent.class, event -> event.register(BIOMASS_YIELD));
    }
}
