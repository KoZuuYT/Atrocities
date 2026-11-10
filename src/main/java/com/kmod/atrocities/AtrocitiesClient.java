package com.kmod.atrocities;

import com.kmod.atrocities.client.entity.EntitiesClient;
import com.kmod.atrocities.client.fx.CameraShake;
import com.kmod.atrocities.client.fx.DamageNumbers;
import com.kmod.atrocities.client.genetics.GeneticsClient;
import com.kmod.atrocities.client.machine.MachinesClient;
import com.kmod.atrocities.client.tooltip.ModTooltips;
import com.kmod.atrocities.client.versiongate.ClientVersionGate;
import com.kmod.atrocities.config.AtrocitiesConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Entrada do lado cliente. Nao e carregada em servidores dedicados. */
@Mod(value = Atrocities.MODID, dist = Dist.CLIENT)
public class AtrocitiesClient {

    public AtrocitiesClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, AtrocitiesConfig.CLIENT_SPEC);
        // Tela de configuracao em Mods > Atrocities > Config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(AtrocitiesClient::onClientSetup);
        ClientVersionGate.register();
        GeneticsClient.register(modBus);
        MachinesClient.register(modBus);
        EntitiesClient.register(modBus);
        ModTooltips.register(modBus);
        CameraShake.register();
        DamageNumbers.register();
        com.kmod.atrocities.client.cinematic.CinematicDirector.register();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        ClientVersionGate.startUpdateCheck();
    }
}
