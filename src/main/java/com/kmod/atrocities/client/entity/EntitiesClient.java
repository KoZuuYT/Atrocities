package com.kmod.atrocities.client.entity;

import com.kmod.atrocities.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderizadores das entidades (somente cliente). */
public final class EntitiesClient {

    private EntitiesClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(ModEntities.CHITINOUS_COLOSSUS.get(),
                    context -> new BossRenderer<>(context, "chitinous_colossus", 2.2f));
            event.registerEntityRenderer(ModEntities.HEMOPHAGE_MATRIARCH.get(),
                    context -> new BossRenderer<>(context, "hemophage_matriarch", 1.4f));
            event.registerEntityRenderer(ModEntities.COALESCED_LEGION.get(),
                    context -> new BossRenderer<>(context, "coalesced_legion", 2.0f));
            event.registerEntityRenderer(ModEntities.BIO_PROJECTILE.get(),
                    context -> new ThrownItemRenderer<>(context, 1.4f, true));
        });
    }
}
