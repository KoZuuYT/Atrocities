package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Linhagem biologica dominante de uma especie ou embriao. Define qual chefe nasce da incubacao.
 */
public enum Archetype implements StringRepresentable {
    /** Carapacas, placas e espinhos. Origina o Colosso Quitinoso. */
    CHITINOUS("chitinous", 0x8B7D3A),
    /** Sangue, toxinas e regeneracao. Origina a Matriarca Hemofaga. */
    HEMOPHAGE("hemophage", 0x8A0F1A),
    /** Colmeia, esporos e massa compartilhada. Origina a Legiao Coalescida. */
    COALESCED("coalesced", 0x5E8C31);

    public static final Codec<Archetype> CODEC = StringRepresentable.fromEnum(Archetype::values);
    public static final StreamCodec<ByteBuf, Archetype> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> values()[i], Archetype::ordinal);

    private final String name;
    private final int color;

    Archetype(String name, int color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("archetype.atrocities." + name).withColor(color);
    }
}
