package com.kmod.atrocities.client.machine;

import com.kmod.atrocities.machine.MachineKind;
import com.kmod.atrocities.registry.ModBlockEntities;
import com.kmod.atrocities.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Telas e renderizadores das maquinas (somente cliente). */
public final class MachinesClient {

    private MachinesClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            for (MachineKind kind : MachineKind.values()) {
                event.register(ModMenus.type(kind), MachineScreen::new);
            }
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event ->
                event.registerBlockEntityRenderer(ModBlockEntities.BIOLOGICAL_INCUBATOR.get(), IncubatorRenderer::new));
    }
}
