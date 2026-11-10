package com.kmod.atrocities;

import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.mixin.RangedAttributeAccessor;
import com.kmod.atrocities.network.AtrocitiesNetwork;
import com.kmod.atrocities.registry.ModRegistries;
import com.kmod.atrocities.versiongate.ModVersion;
import com.kmod.atrocities.versiongate.ServerVersionGate;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Atrocities: bioengenharia sombria, mutacoes geneticas e chefes criados pelos jogadores.
 */
@Mod(Atrocities.MODID)
public class Atrocities {

    public static final String MODID = "atrocities";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Atrocities(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AtrocitiesConfig.COMMON_SPEC);

        ModRegistries.register(modBus);
        GeneticsRegistries.register(modBus);
        AtrocitiesNetwork.register(modBus);
        ServerVersionGate.register();
        com.kmod.atrocities.cinematic.ArenaCinematics.register();
        com.kmod.atrocities.item.SanguineCleaverItem.registerEvents();
        com.kmod.atrocities.codex.Codex.register();

        modBus.addListener(Atrocities::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Atrocities::raiseAttributeCaps);
        LOGGER.info("Atrocities {} carregado. A carne aguarda.", ModVersion.current());
    }

    /** Eleva o teto de max_health para permitir chefes com milhares de pontos de vida. */
    private static void raiseAttributeCaps() {
        if (Attributes.MAX_HEALTH.value() instanceof RangedAttribute maxHealth) {
            RangedAttributeAccessor accessor = (RangedAttributeAccessor) maxHealth;
            double cap = AtrocitiesConfig.MAX_HEALTH_CAP.get();
            if (accessor.atrocities$getMaxValue() < cap) {
                accessor.atrocities$setMaxValue(cap);
                LOGGER.info("Teto de max_health elevado para {}.", cap);
            }
        }
    }
}
