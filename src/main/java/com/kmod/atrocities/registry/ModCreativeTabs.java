package com.kmod.atrocities.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Aba criativa "Atrocities": recebe automaticamente todo item registrado pelo mod. */
public final class ModCreativeTabs {

    private ModCreativeTabs() {
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            ModRegistries.CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.atrocities"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.SAMPLE_VIAL.get().getDefaultInstance())
                    .displayItems((parameters, output) ->
                            ModRegistries.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                    .build());

    static void bootstrap() {
        // Forca o carregamento da classe para que a aba seja registrada.
    }
}
