package com.kmod.atrocities.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/** Itens do Atrocities. */
public final class ModItems {

    private ModItems() {
    }

    /**
     * Frasco de Amostra: recipiente de vidro reforcado. Componente base de toda a cadeia genetica
     * (seringas, capsulas de estase e amostras isoladas sao feitas a partir dele).
     */
    public static final DeferredItem<Item> SAMPLE_VIAL =
            ModRegistries.ITEMS.registerSimpleItem("sample_vial", new Item.Properties().stacksTo(16));

    static void bootstrap() {
        // Forca o carregamento da classe para que os campos acima sejam registrados.
    }
}
