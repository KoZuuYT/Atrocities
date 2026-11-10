package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Data Component da Amostra Genetica: material extraido de uma especie viva.
 *
 * @param species especie de origem
 * @param purity  pureza de 1 a 100; amplifica a potencia dos tracos no sequenciamento
 * @param traits  tracos isolados na amostra
 */
public record GeneSample(ResourceKey<Species> species, int purity, List<TraitRoll> traits) {

    public static final int MAX_PURITY = 100;
    /** Chance de a seringa isolar um segundo traco. */
    private static final float SECOND_TRAIT_CHANCE = 0.35f;

    public static final Codec<GeneSample> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(GeneticsRegistries.SPECIES).fieldOf("species").forGetter(GeneSample::species),
            Codec.intRange(1, MAX_PURITY).fieldOf("purity").forGetter(GeneSample::purity),
            TraitRoll.CODEC.listOf().fieldOf("traits").forGetter(GeneSample::traits)
    ).apply(i, GeneSample::new));

    public static final StreamCodec<ByteBuf, GeneSample> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(GeneticsRegistries.SPECIES), GeneSample::species,
            ByteBufCodecs.VAR_INT, GeneSample::purity,
            TraitRoll.STREAM_CODEC.apply(ByteBufCodecs.list()), GeneSample::traits,
            GeneSample::new);

    public GeneSample {
        traits = List.copyOf(traits);
    }

    /**
     * Sorteia uma amostra extraida de um especime.
     *
     * @param healthRatio vida atual / vida maxima do especime; quanto mais enfraquecido, mais pura a amostra
     */
    public static GeneSample extract(Holder.Reference<Species> species, float healthRatio, RandomSource random) {
        Species sp = species.value();
        int weaknessBonus = Math.round((1f - Mth.clamp(healthRatio, 0f, 1f)) * 20f);
        int purity = Mth.clamp(sp.extraction().basePurity() + weaknessBonus + random.nextIntBetweenInclusive(-10, 10),
                1, MAX_PURITY);
        int count = random.nextFloat() < SECOND_TRAIT_CHANCE ? 2 : 1;
        return new GeneSample(species.key(), purity, rollTraits(sp, count, random));
    }

    /** Amostra de referencia com pureza e potencia maximas (aba criativa). */
    public static GeneSample reference(Holder.Reference<Species> species) {
        List<TraitRoll> traits = species.value().traits().stream()
                .map(entry -> new TraitRoll(entry.trait(), entry.maxPotency()))
                .toList();
        return new GeneSample(species.key(), MAX_PURITY, traits);
    }

    private static List<TraitRoll> rollTraits(Species species, int count, RandomSource random) {
        List<Species.TraitEntry> pool = new ArrayList<>(species.traits());
        List<TraitRoll> rolled = new ArrayList<>();
        while (rolled.size() < count && !pool.isEmpty()) {
            int total = pool.stream().mapToInt(Species.TraitEntry::weight).sum();
            int pick = random.nextInt(total);
            for (int idx = 0; idx < pool.size(); idx++) {
                Species.TraitEntry entry = pool.get(idx);
                pick -= entry.weight();
                if (pick < 0) {
                    int lo = Math.min(entry.minPotency(), entry.maxPotency());
                    int hi = Math.max(entry.minPotency(), entry.maxPotency());
                    rolled.add(new TraitRoll(entry.trait(), random.nextIntBetweenInclusive(lo, hi)));
                    pool.remove(idx);
                    break;
                }
            }
        }
        rolled.sort(Comparator.comparing((TraitRoll roll) -> roll.trait().location()));
        return rolled;
    }
}
