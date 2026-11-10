package com.kmod.atrocities.network;

import com.kmod.atrocities.client.versiongate.ClientVersionGate;
import com.kmod.atrocities.versiongate.ServerVersionGate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registro dos payloads de rede.
 * <p>
 * Os canais do VersionGate sao {@code optional()}: assim a negociacao do NeoForge nunca derruba
 * o jogador antes de o Atrocities conseguir mostrar a propria mensagem com o link de download.
 */
public final class AtrocitiesNetwork {

    /** Versao do protocolo de rede do mod. Mude apenas quando payloads forem alterados de forma incompativel. */
    public static final String PROTOCOL_VERSION = "1";

    private AtrocitiesNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AtrocitiesNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar versionGate = event.registrar(PROTOCOL_VERSION).optional();
        versionGate.playToClient(ServerInfoPayload.TYPE, ServerInfoPayload.STREAM_CODEC,
                (payload, context) -> ClientVersionGate.onServerInfo(payload, context));
        versionGate.playToServer(ClientInfoPayload.TYPE, ClientInfoPayload.STREAM_CODEC,
                ServerVersionGate::onClientInfo);

        PayloadRegistrar effects = event.registrar(PROTOCOL_VERSION).optional();
        effects.playToClient(ScreenShakePayload.TYPE, ScreenShakePayload.STREAM_CODEC,
                (payload, context) -> com.kmod.atrocities.client.fx.CameraShake.add(payload.intensity(), payload.duration()));
        effects.playToClient(CutscenePayload.TYPE, CutscenePayload.STREAM_CODEC,
                (payload, context) -> com.kmod.atrocities.client.cinematic.CinematicDirector.play(payload));
    }
}
