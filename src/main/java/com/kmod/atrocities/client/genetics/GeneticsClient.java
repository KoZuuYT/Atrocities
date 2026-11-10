package com.kmod.atrocities.client.genetics;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.genetics.CapturedSpecimen;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Species;
import com.kmod.atrocities.registry.ModDataComponents;
import com.kmod.atrocities.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Visual dos itens geneticos no cliente: cores por especie/arquetipo, modelo da capsula cheia e tooltips. */
public final class GeneticsClient {

    private GeneticsClient() {
    }

    /** Cor do fluido quando a especie nao existe mais no datapack. */
    private static final int FALLBACK_FLUID = 0x781A22;
    public static final ResourceLocation FILLED_PROPERTY =
            ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "filled");

    public static void register(IEventBus modBus) {
        modBus.addListener(GeneticsClient::onClientSetup);
        modBus.addListener(GeneticsClient::onItemColors);
        NeoForge.EVENT_BUS.addListener(GeneticsTooltips::onTooltip);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.STASIS_CAPSULE.get(), FILLED_PROPERTY,
                (stack, level, entity, seed) -> stack.has(ModDataComponents.CAPTURED_SPECIMEN.get()) ? 1f : 0f));
    }

    /** Camada 1 (layer1) de cada item recebe a cor genetica; a camada 0 fica intacta. */
    private static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            GeneSample sample = stack.get(ModDataComponents.GENE_SAMPLE.get());
            return tintIndex == 1 ? opaque(sample == null ? FALLBACK_FLUID : speciesColor(sample.species())) : -1;
        }, ModItems.GENE_SAMPLE.get());

        event.register((stack, tintIndex) -> {
            CapturedSpecimen specimen = stack.get(ModDataComponents.CAPTURED_SPECIMEN.get());
            return tintIndex == 1 && specimen != null ? opaque(speciesColor(specimen.species())) : -1;
        }, ModItems.STASIS_CAPSULE.get());

        event.register((stack, tintIndex) -> {
            EmbryoGenome genome = stack.get(ModDataComponents.EMBRYO_GENOME.get());
            return tintIndex == 1 ? opaque(genome == null ? FALLBACK_FLUID : genome.archetype().color()) : -1;
        }, ModItems.UNSTABLE_EMBRYO.get());
    }

    static int speciesColor(ResourceKey<Species> key) {
        HolderLookup.Provider registries = clientRegistries();
        if (registries == null) {
            return FALLBACK_FLUID;
        }
        return GeneticsRegistries.species(registries, key).map(Species::color).orElse(FALLBACK_FLUID);
    }

    static HolderLookup.Provider clientRegistries() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }
}
