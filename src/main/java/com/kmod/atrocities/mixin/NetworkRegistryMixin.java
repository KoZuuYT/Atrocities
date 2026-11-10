package com.kmod.atrocities.mixin;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.network.ServerInfoPayload;
import com.kmod.atrocities.versiongate.ServerVersionGate;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryComponent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepta a negociacao de rede do NeoForge ANTES da verificacao de canais e da sincronizacao
 * de registros. Sem isso, um cliente sem o Atrocities seria derrubado com a mensagem generica
 * do NeoForge ("incompatible"/"missing channels") e nunca veria o link de download.
 */
@Mixin(value = NetworkRegistry.class, remap = false)
public abstract class NetworkRegistryMixin {

    /** Cliente NeoForge: verifica se ele anuncia o canal-marcador do Atrocities. */
    @Inject(method = "initializeNeoForgeConnection(Lnet/minecraft/network/protocol/configuration/ServerConfigurationPacketListener;Ljava/util/Map;)V",
            at = @At("HEAD"), cancellable = true)
    private static void atrocities$checkModdedClient(ServerConfigurationPacketListener listener,
                                                     Map<ConnectionProtocol, Set<ModdedNetworkQueryComponent>> clientChannels,
                                                     CallbackInfo ci) {
        if (!AtrocitiesConfig.KICK_CLIENTS_WITHOUT_MOD.get()) {
            return;
        }
        boolean hasAtrocities = clientChannels.values().stream()
                .flatMap(Set::stream)
                .anyMatch(component -> component.id().equals(ServerInfoPayload.ID));
        if (!hasAtrocities) {
            Atrocities.LOGGER.info("[VersionGate] Cliente NeoForge sem o Atrocities desconectado com link de download.");
            listener.disconnect(ServerVersionGate.missingModMessage());
            ci.cancel();
        }
    }

    /** Cliente vanilla (ou outro loader): nunca tem o mod. */
    @Inject(method = "initializeOtherConnection(Lnet/minecraft/network/protocol/configuration/ServerConfigurationPacketListener;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void atrocities$checkOtherClient(ServerConfigurationPacketListener listener,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (!AtrocitiesConfig.KICK_CLIENTS_WITHOUT_MOD.get()) {
            return;
        }
        Atrocities.LOGGER.info("[VersionGate] Cliente sem NeoForge desconectado com link de download.");
        listener.disconnect(ServerVersionGate.missingModMessage());
        cir.setReturnValue(false);
    }
}
