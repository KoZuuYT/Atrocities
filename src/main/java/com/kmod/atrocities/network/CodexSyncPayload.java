package com.kmod.atrocities.network;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.codex.CodexProgress;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> cliente: estado completo do Códex do jogador.
 *
 * @param progress progresso atual
 * @param unlocked descobertas novas desde o último envio, no formato {@code tipo|id}
 *                 ({@code encounter}, {@code defeat} ou {@code trait}); o cliente mostra um aviso para cada uma
 */
public record CodexSyncPayload(CodexProgress progress, List<String> unlocked) implements CustomPacketPayload {

    public static final String ENCOUNTER = "encounter";
    public static final String DEFEAT = "defeat";
    public static final String TRAIT = "trait";

    public static final Type<CodexSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "codex_sync"));

    public static final StreamCodec<ByteBuf, CodexSyncPayload> STREAM_CODEC = StreamCodec.composite(
            CodexProgress.STREAM_CODEC, CodexSyncPayload::progress,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), CodexSyncPayload::unlocked,
            CodexSyncPayload::new);

    public static String unlock(String kind, ResourceLocation id) {
        return kind + "|" + id;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
