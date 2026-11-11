package com.kmod.atrocities.imbue;

import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.genetics.TraitRoll;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

/**
 * Data Component de armas e armaduras imbuidas: os genes ocupando os tanques do item
 * (a potencia de cada {@link TraitRoll} e o nivel do gene) e a evolucao acumulada.
 *
 * @param genes genes imbuidos, na ordem em que entraram (no maximo {@link Imbuing#MAX_GENES})
 * @param xp    evolucao acumulada rumo ao proximo nivel
 */
public record ImbuedGenes(List<TraitRoll> genes, int xp) {

    public static final ImbuedGenes EMPTY = new ImbuedGenes(List.of(), 0);

    public static final Codec<ImbuedGenes> CODEC = RecordCodecBuilder.create(i -> i.group(
            TraitRoll.CODEC.listOf().fieldOf("genes").forGetter(ImbuedGenes::genes),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(ImbuedGenes::xp)
    ).apply(i, ImbuedGenes::new));

    public static final StreamCodec<ByteBuf, ImbuedGenes> STREAM_CODEC = StreamCodec.composite(
            TraitRoll.STREAM_CODEC.apply(ByteBufCodecs.list()), ImbuedGenes::genes,
            ByteBufCodecs.VAR_INT, ImbuedGenes::xp,
            ImbuedGenes::new);

    public ImbuedGenes {
        genes = List.copyOf(genes);
        xp = Math.max(0, xp);
    }

    public boolean isEmpty() {
        return genes.isEmpty();
    }

    /** Nivel do gene no item, ou 0 se ausente. */
    public int level(ResourceKey<Trait> trait) {
        for (TraitRoll gene : genes) {
            if (gene.trait().equals(trait)) {
                return gene.potency();
            }
        }
        return 0;
    }

    public int totalLevels() {
        return genes.stream().mapToInt(TraitRoll::potency).sum();
    }
}
