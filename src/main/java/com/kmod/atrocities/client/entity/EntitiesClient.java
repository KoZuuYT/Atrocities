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
        modBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            // Arco Balista: mesmas animacoes de puxar do arco vanilla.
            var bow = com.kmod.atrocities.registry.ModItems.BALLISTA_BOW.get();
            net.minecraft.client.renderer.item.ItemProperties.register(bow,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                            entity == null || entity.getUseItem() != stack ? 0f
                                    : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20f);
            net.minecraft.client.renderer.item.ItemProperties.register(bow,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
        }));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(ModEntities.CHITINOUS_COLOSSUS.get(),
                    context -> new BossRenderer<>(context, "chitinous_colossus", 2.2f));
            event.registerEntityRenderer(ModEntities.HEMOPHAGE_MATRIARCH.get(),
                    context -> new BossRenderer<>(context, "hemophage_matriarch", 1.4f));
            event.registerEntityRenderer(ModEntities.COALESCED_LEGION.get(),
                    context -> new BossRenderer<>(context, "coalesced_legion", 2.0f));
            event.registerEntityRenderer(ModEntities.BOSS_ORGAN.get(), OrganRenderer::new);
            event.registerEntityRenderer(ModEntities.ZOMBIE_TITAN.get(),
                    context -> new BossRenderer<>(context, "zombie_titan", 1.0f));
            event.registerEntityRenderer(ModEntities.SKELETON_BALLISTA.get(),
                    context -> new BossRenderer<>(context, "skeleton_ballista", 0.7f));
            event.registerEntityRenderer(ModEntities.CREEPER_DETONATOR.get(),
                    context -> new BossRenderer<>(context, "creeper_detonator", 0.9f));
            event.registerEntityRenderer(ModEntities.WEAVER_SPIDER.get(),
                    context -> new BossRenderer<>(context, "weaver_spider", 1.4f));
            event.registerEntityRenderer(ModEntities.PHANTOM_RAY.get(),
                    context -> new BossRenderer<>(context, "phantom_ray", 1.8f));
            event.registerEntityRenderer(ModEntities.BIO_PROJECTILE.get(),
                    context -> new ThrownItemRenderer<>(context, 1.4f, true));
        });
    }
}
