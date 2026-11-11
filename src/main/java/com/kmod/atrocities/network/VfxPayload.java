package com.kmod.atrocities.network;

import com.kmod.atrocities.Atrocities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> cliente: um efeito visual renderizado (ondas de choque, colunas de luz, telegrafos,
 * auras de carga, clarões de tela). O cliente desenha a geometria; particulas so complementam.
 *
 * @param kind     ordinal de {@link com.kmod.atrocities.fx.Vfx.Kind}
 * @param x        posicao do efeito
 * @param radius   tamanho principal (raio, comprimento ou forca, depende do tipo)
 * @param color    cor RGB
 * @param duration duracao em ticks
 * @param entity   id da entidade seguida (-1 = nenhuma)
 * @param target   id da entidade alvo (linhas; -1 = nenhuma)
 * @param extra    parametro livre (angulo, altura, intensidade)
 */
public record VfxPayload(int kind, double x, double y, double z, float radius, int color, int duration, int entity,
                         int target, float extra) implements CustomPacketPayload {

    public static final Type<VfxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "vfx"));

    public static final StreamCodec<FriendlyByteBuf, VfxPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.radius);
                buf.writeInt(p.color);
                buf.writeVarInt(p.duration);
                buf.writeVarInt(p.entity + 1);
                buf.writeVarInt(p.target + 1);
                buf.writeFloat(p.extra);
            },
            buf -> new VfxPayload(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                    buf.readInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
