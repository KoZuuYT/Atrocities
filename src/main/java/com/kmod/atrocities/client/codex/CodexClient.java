package com.kmod.atrocities.client.codex;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Ligações do Códex no cliente: carregamento do conteúdo, limpeza ao sair e abertura do livro. */
public final class CodexClient {

    private CodexClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterClientReloadListenersEvent.class,
                event -> event.registerReloadListener(CodexData.INSTANCE));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            ClientCodex.reset();
            BossViewer.clear();
        });
    }

    public static void openBook() {
        Minecraft.getInstance().setScreen(new CodexScreen());
    }
}
