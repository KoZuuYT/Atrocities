package com.kmod.atrocities.network;

import com.kmod.atrocities.Atrocities;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> cliente, enviado logo apos o login: versao do Atrocities no servidor e de onde baixa-la.
 * <p>
 * O canal deste payload tambem serve de marcador: se o cliente nao o anuncia durante a negociacao
 * de rede, ele nao tem o mod (ver {@code NetworkRegistryMixin}).
 */
public record ServerInfoPayload(String version, String githubRepo, boolean requireExactVersion) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "server_info");
    public static final Type<ServerInfoPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<ByteBuf, ServerInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ServerInfoPayload::version,
            ByteBufCodecs.STRING_UTF8, ServerInfoPayload::githubRepo,
            ByteBufCodecs.BOOL, ServerInfoPayload::requireExactVersion,
            ServerInfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
