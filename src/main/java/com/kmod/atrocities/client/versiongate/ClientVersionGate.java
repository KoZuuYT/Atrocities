package com.kmod.atrocities.client.versiongate;

import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.network.ClientInfoPayload;
import com.kmod.atrocities.network.ServerInfoPayload;
import com.kmod.atrocities.versiongate.ModVersion;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Lado cliente do VersionGate:
 * <ul>
 *   <li>responde ao servidor com a versao instalada;</li>
 *   <li>se as versoes diferem, abre {@link ModUpdateScreen} assim que o mundo carregar
 *       (ou por cima da tela de desconexao, se o servidor exigir a versao exata);</li>
 *   <li>no menu principal, mostra um botao quando ha uma Release mais nova no GitHub.</li>
 * </ul>
 */
public final class ClientVersionGate {

    /** Atualizacao a mostrar quando o jogador estiver no mundo sem nenhuma tela aberta. */
    @Nullable
    private static volatile UpdateInfo pendingInGame;
    /** Atualizacao a mostrar no lugar da tela de desconexao. */
    @Nullable
    private static volatile UpdateInfo pendingOnDisconnect;

    private ClientVersionGate() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientVersionGate::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientVersionGate::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientVersionGate::onScreenInit);
    }

    /** Chamado no setup do cliente. */
    public static void startUpdateCheck() {
        if (AtrocitiesConfig.CHECK_FOR_UPDATES.get()) {
            GithubReleaseChecker.checkAsync(ModVersion.repo());
        }
    }

    /** Handler do {@link ServerInfoPayload} (thread de rede). */
    public static void onServerInfo(ServerInfoPayload payload, IPayloadContext context) {
        String current = ModVersion.current();
        context.reply(new ClientInfoPayload(current));
        if (payload.version().equals(current)) {
            return;
        }
        if (payload.requireExactVersion()) {
            pendingOnDisconnect = UpdateInfo.fromServer(payload.version(), payload.githubRepo(), UpdateInfo.Reason.SERVER_REQUIRED);
        } else {
            pendingInGame = UpdateInfo.fromServer(payload.version(), payload.githubRepo(), UpdateInfo.Reason.SERVER_MISMATCH);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        UpdateInfo info = pendingInGame;
        Minecraft mc = Minecraft.getInstance();
        if (info != null && mc.player != null && mc.screen == null) {
            pendingInGame = null;
            mc.setScreen(new ModUpdateScreen(null, info));
        }
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        UpdateInfo info = pendingOnDisconnect;
        if (info != null && event.getNewScreen() instanceof DisconnectedScreen disconnected) {
            pendingOnDisconnect = null;
            pendingInGame = null;
            event.setNewScreen(new ModUpdateScreen(disconnected, info));
        }
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title) || !AtrocitiesConfig.TITLE_SCREEN_NOTICE.get()) {
            return;
        }
        GithubReleaseChecker.newerRelease().ifPresent(info -> event.addListener(Button.builder(
                        Component.translatable("screen.atrocities.update.title_button", info.version()).withStyle(ChatFormatting.RED),
                        b -> Minecraft.getInstance().setScreen(new ModUpdateScreen(title, info)))
                .bounds(4, 4, 170, 20)
                .build()));
    }
}
