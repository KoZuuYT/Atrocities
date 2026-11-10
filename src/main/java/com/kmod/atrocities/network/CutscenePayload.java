package com.kmod.atrocities.network;

import com.kmod.atrocities.Atrocities;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Servidor -> cliente: inicia (ou, com {@code duration} 0, encerra) a cutscene de um chefe.
 *
 * @param bossId   id de rede do chefe que dirige a cena
 * @param kind     {@link #INTRO} ou {@link #PHASE}
 * @param phase    fase em que o chefe entra (1 na entrada)
 * @param duration duracao em ticks
 * @param radius   raio da arena congelada
 * @param center   centro da arena
 */
public record CutscenePayload(int bossId, int kind, int phase, int duration, float radius, Vec3 center)
        implements CustomPacketPayload {

    public static final int INTRO = 0;
    public static final int PHASE = 1;

    public static final Type<CutscenePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "cutscene"));

    private static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);

    public static final StreamCodec<ByteBuf, CutscenePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CutscenePayload::bossId,
            ByteBufCodecs.VAR_INT, CutscenePayload::kind,
            ByteBufCodecs.VAR_INT, CutscenePayload::phase,
            ByteBufCodecs.VAR_INT, CutscenePayload::duration,
            ByteBufCodecs.FLOAT, CutscenePayload::radius,
            VEC3, CutscenePayload::center,
            CutscenePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
