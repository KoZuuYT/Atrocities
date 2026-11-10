package com.kmod.atrocities.network;

import com.kmod.atrocities.Atrocities;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> cliente: treme a camera do jogador (impactos, rugidos, despertar dos chefes).
 *
 * @param intensity forca do tremor (0.1 = leve, 1 = forte, ate 3)
 * @param duration  duracao em ticks
 */
public record ScreenShakePayload(float intensity, int duration) implements CustomPacketPayload {

    public static final Type<ScreenShakePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "screen_shake"));

    public static final StreamCodec<ByteBuf, ScreenShakePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ScreenShakePayload::intensity,
            ByteBufCodecs.VAR_INT, ScreenShakePayload::duration,
            ScreenShakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
