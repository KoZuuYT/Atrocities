package com.kmod.atrocities.registry;

import com.kmod.atrocities.machine.MachineKind;
import com.kmod.atrocities.machine.MachineMenu;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Menus (telas) das maquinas: um tipo por maquina, todos com o mesmo {@link MachineMenu}. */
public final class ModMenus {

    private ModMenus() {
    }

    private static final Map<MachineKind, DeferredHolder<MenuType<?>, MenuType<MachineMenu>>> MENUS =
            new EnumMap<>(MachineKind.class);

    static {
        for (MachineKind kind : MachineKind.values()) {
            MENUS.put(kind, ModRegistries.MENU_TYPES.register(kind.id(), () -> IMenuTypeExtension.create(
                    (containerId, inventory, buf) -> new MachineMenu(kind, containerId, inventory, buf))));
        }
    }

    public static MenuType<MachineMenu> type(MachineKind kind) {
        return MENUS.get(kind).get();
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que os menus sejam registrados.
    }
}
