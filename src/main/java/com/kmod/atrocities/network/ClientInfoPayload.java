package com.kmod.atrocities.network;

import com.kmod.atrocities.Atrocities;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor, resposta ao {@link ServerInfoPayload}: versao do Atrocities instalada no cliente. */
public record ClientInfoPayload(String version) implements CustomPacketPayload {

    public static final Type<ClientInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "client_info"));

    public static final StreamCodec<ByteBuf, ClientInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ClientInfoPayload::version,
            ClientInfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
