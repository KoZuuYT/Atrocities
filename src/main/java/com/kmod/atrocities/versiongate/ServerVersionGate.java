package com.kmod.atrocities.versiongate;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.network.ClientInfoPayload;
import com.kmod.atrocities.network.ServerInfoPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Lado servidor do VersionGate.
 * <ul>
 *   <li>Cliente sem o mod: desconectado durante a negociacao de rede com uma mensagem e o link do jar
 *       (ver {@code NetworkRegistryMixin}). As mensagens sao literais porque um cliente sem o mod
 *       nao tem as traducoes do Atrocities.</li>
 *   <li>Cliente com o mod: recebe {@link ServerInfoPayload} ao entrar; se a versao for diferente,
 *       o cliente mostra a tela de atualizacao com download direto.</li>
 *   <li>Com {@code requireExactVersion}, versoes diferentes sao desconectadas.</li>
 * </ul>
 */
public final class ServerVersionGate {

    private ServerVersionGate() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerVersionGate::onPlayerLoggedIn);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.connection.hasChannel(ServerInfoPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new ServerInfoPayload(
                    ModVersion.current(), ModVersion.repo(), AtrocitiesConfig.REQUIRE_EXACT_VERSION.get()));
        }
    }

    /** Resposta do cliente com a versao instalada. */
    public static void onClientInfo(ClientInfoPayload payload, IPayloadContext context) {
        String serverVersion = ModVersion.current();
        if (payload.version().equals(serverVersion)) {
            return;
        }
        String playerName = context.player().getGameProfile().getName();
        Atrocities.LOGGER.info("[VersionGate] {} usa Atrocities {} (servidor: {}).", playerName, payload.version(), serverVersion);
        if (AtrocitiesConfig.REQUIRE_EXACT_VERSION.get()) {
            context.disconnect(versionMismatchMessage(payload.version(), serverVersion, ModVersion.repo()));
        }
    }

    /** Mensagem para quem entra sem o mod instalado. */
    public static Component missingModMessage() {
        String version = ModVersion.current();
        String repo = ModVersion.repo();
        return header()
                .append(Component.literal("Este servidor exige o mod Atrocities " + version + ".\n")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("This server requires the Atrocities mod " + version + ".\n\n")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Requer NeoForge 1.21.1 + GeckoLib. Download direto:\n")
                        .withStyle(ChatFormatting.YELLOW))
                .append(link(ModVersion.jarUrl(repo, version)))
                .append(Component.literal("\n\nTodas as versoes / all versions:\n").withStyle(ChatFormatting.GRAY))
                .append(link(ModVersion.releasesUrl(repo)));
    }

    /** Mensagem para quem tem o mod em outra versao (somente com requireExactVersion). */
    public static Component versionMismatchMessage(String clientVersion, String serverVersion, String repo) {
        return header()
                .append(Component.literal("Versao incompativel do Atrocities.\n").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Voce: " + clientVersion + "  |  Servidor: " + serverVersion + "\n\n")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Baixe a versao do servidor:\n").withStyle(ChatFormatting.YELLOW))
                .append(link(ModVersion.jarUrl(repo, serverVersion)));
    }

    private static MutableComponent header() {
        return Component.literal("☣ ATROCITIES ☣\n\n").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
    }

    private static MutableComponent link(String url) {
        return Component.literal(url).withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
    }
}
