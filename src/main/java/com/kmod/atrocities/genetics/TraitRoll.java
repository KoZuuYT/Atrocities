package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/**
 * Um traco presente numa amostra ou genoma, com sua potencia.
 * Guarda apenas a chave do traco: se um datapack remover o traco, o item continua valido.
 */
public record TraitRoll(ResourceKey<Trait> trait, int potency) {

    public static final Codec<TraitRoll> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(GeneticsRegistries.TRAIT).fieldOf("trait").forGetter(TraitRoll::trait),
            ExtraCodecs.POSITIVE_INT.fieldOf("potency").forGetter(TraitRoll::potency)
    ).apply(i, TraitRoll::new));

    public static final StreamCodec<ByteBuf, TraitRoll> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(GeneticsRegistries.TRAIT), TraitRoll::trait,
            ByteBufCodecs.VAR_INT, TraitRoll::potency,
            TraitRoll::new);
}
