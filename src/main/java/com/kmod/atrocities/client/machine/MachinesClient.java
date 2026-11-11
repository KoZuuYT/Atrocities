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
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(ModBlockEntities.BIOLOGICAL_INCUBATOR.get(), IncubatorRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.BIOMASS_EXTRACTOR.get(),
                    context -> new MachineGlowRenderer<>(context, 0x9BE03A, MachineGlowRenderer.Pattern.BUBBLES));
            event.registerBlockEntityRenderer(ModBlockEntities.GENETIC_CENTRIFUGE.get(),
                    context -> new MachineGlowRenderer<>(context, 0x4AD8FF, MachineGlowRenderer.Pattern.ROTOR));
            event.registerBlockEntityRenderer(ModBlockEntities.GENE_SEQUENCER.get(),
                    context -> new MachineGlowRenderer<>(context, 0xFF4FD8, MachineGlowRenderer.Pattern.READOUT));
            event.registerBlockEntityRenderer(ModBlockEntities.IMBUING_TERMINAL.get(),
                    context -> new MachineGlowRenderer<>(context, 0xB06CFF, MachineGlowRenderer.Pattern.ORBIT));
        });
    }
}
