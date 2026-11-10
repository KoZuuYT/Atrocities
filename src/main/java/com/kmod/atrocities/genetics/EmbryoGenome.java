package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Data Component do Embriao Instavel: o genoma que a Incubadora transforma em chefe.
 *
 * @param archetype linhagem dominante (define qual chefe nasce)
 * @param tier      1 a 4; define a vida e o arsenal do chefe
 * @param stability 0 a 100; embrioes instaveis podem abortar ou mutar durante a incubacao
 * @param traits    tracos combinados, ja limitados a potencia maxima de cada um
 * @param lineage   especies que doaram material, em ordem
 * @param mutations quantas mutacoes o embriao sofreu na incubacao
 */
public record EmbryoGenome(Archetype archetype, int tier, int stability, List<TraitRoll> traits,
                           List<ResourceKey<Species>> lineage, int mutations) {

    public static final int MAX_TIER = 4;
    /** Vida base do chefe por tier (I a IV), pensada para o meta de ~50 de dano por hit. */
    private static final int[] TIER_HEALTH = {1_500, 2_500, 3_500, 4_500};
    /** Teto de vida do chefe ja com os bonus dos tracos (antes do multiplicador da configuracao). */
    public static final int MAX_BOSS_HEALTH = 5_000;

    /** Instabilidade extra por par de tracos incompativeis. */
    private static final float CONFLICT_PENALTY = 15f;
    /** Instabilidade extra por amostra de arquetipo diferente do dominante. */
    private static final float HYBRID_PENALTY = 12f;
    /** Instabilidade por ponto de impureza media. */
    private static final float IMPURITY_PENALTY = 0.2f;

    public static final Codec<EmbryoGenome> CODEC = RecordCodecBuilder.create(i -> i.group(
            Archetype.CODEC.fieldOf("archetype").forGetter(EmbryoGenome::archetype),
            Codec.intRange(1, MAX_TIER).fieldOf("tier").forGetter(EmbryoGenome::tier),
            Codec.intRange(0, 100).fieldOf("stability").forGetter(EmbryoGenome::stability),
            TraitRoll.CODEC.listOf().fieldOf("traits").forGetter(EmbryoGenome::traits),
            ResourceKey.codec(GeneticsRegistries.SPECIES).listOf().fieldOf("lineage").forGetter(EmbryoGenome::lineage),
            Codec.intRange(0, 64).optionalFieldOf("mutations", 0).forGetter(EmbryoGenome::mutations)
    ).apply(i, EmbryoGenome::new));

    public static final StreamCodec<ByteBuf, EmbryoGenome> STREAM_CODEC = StreamCodec.composite(
            Archetype.STREAM_CODEC, EmbryoGenome::archetype,
            ByteBufCodecs.VAR_INT, EmbryoGenome::tier,
            ByteBufCodecs.VAR_INT, EmbryoGenome::stability,
            TraitRoll.STREAM_CODEC.apply(ByteBufCodecs.list()), EmbryoGenome::traits,
            ResourceKey.streamCodec(GeneticsRegistries.SPECIES).apply(ByteBufCodecs.list()), EmbryoGenome::lineage,
            ByteBufCodecs.VAR_INT, EmbryoGenome::mutations,
            EmbryoGenome::new);

    public EmbryoGenome {
        traits = List.copyOf(traits);
        lineage = List.copyOf(lineage);
    }

    /** Vida maxima base do chefe gerado por este genoma. */
    public int bossBaseHealth() {
        return TIER_HEALTH[Mth.clamp(tier, 1, MAX_TIER) - 1];
    }

    public int totalPotency() {
        return traits.stream().mapToInt(TraitRoll::potency).sum();
    }

    /**
     * Combina amostras num genoma (usado pelo Sequenciador).
     * <ul>
     *   <li>Arquetipo: o mais presente, ponderado pela pureza de cada amostra.</li>
     *   <li>Tracos: potencias somadas e escaladas pela pureza, limitadas ao maximo de cada traco.</li>
     *   <li>Estabilidade: 100 menos a instabilidade dos tracos, conflitos, hibridizacao e impureza.</li>
     *   <li>Tier: pela soma das potencias (ate 3 = I, ate 6 = II, ate 10 = III, acima = IV).</li>
     * </ul>
     *
     * @return vazio se nao houver amostras
     */
    public static Optional<EmbryoGenome> sequence(List<GeneSample> samples, HolderLookup.Provider registries) {
        if (samples.isEmpty()) {
            return Optional.empty();
        }

        Map<Archetype, Float> archetypeWeight = new EnumMap<>(Archetype.class);
        List<Archetype> sampleArchetypes = new ArrayList<>();
        Map<ResourceKey<Trait>, Float> potency = new LinkedHashMap<>();
        Set<ResourceKey<Species>> lineage = new LinkedHashSet<>();
        float puritySum = 0f;

        for (GeneSample sample : samples) {
            float purityFactor = sample.purity() / (float) GeneSample.MAX_PURITY;
            puritySum += sample.purity();
            lineage.add(sample.species());
            GeneticsRegistries.species(registries, sample.species()).ifPresent(species -> {
                archetypeWeight.merge(species.archetype(), purityFactor, Float::sum);
                sampleArchetypes.add(species.archetype());
            });
            for (TraitRoll roll : sample.traits()) {
                potency.merge(roll.trait(), roll.potency() * purityFactor, Float::sum);
            }
        }

        Archetype dominant = archetypeWeight.entrySet().stream()
                .max(Map.Entry.<Archetype, Float>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(Archetype.HEMOPHAGE);

        List<TraitRoll> traits = new ArrayList<>();
        float instability = 0f;
        for (Map.Entry<ResourceKey<Trait>, Float> entry : potency.entrySet()) {
            Optional<Trait> trait = GeneticsRegistries.trait(registries, entry.getKey());
            if (trait.isEmpty()) {
                continue; // traco removido por datapack: descartado no sequenciamento
            }
            int level = Mth.clamp(Math.round(entry.getValue()), 1, trait.get().maxPotency());
            traits.add(new TraitRoll(entry.getKey(), level));
            instability += trait.get().instability() * level;
        }
        traits.sort(Comparator.comparingInt(TraitRoll::potency).reversed()
                .thenComparing(roll -> roll.trait().location()));

        for (int a = 0; a < traits.size(); a++) {
            for (int b = a + 1; b < traits.size(); b++) {
                if (conflicts(traits.get(a).trait(), traits.get(b).trait(), registries)) {
                    instability += CONFLICT_PENALTY;
                }
            }
        }
        long hybrids = sampleArchetypes.stream().filter(arch -> arch != dominant).count();
        instability += hybrids * HYBRID_PENALTY;
        instability += (GeneSample.MAX_PURITY - puritySum / samples.size()) * IMPURITY_PENALTY;

        int stability = Mth.clamp(Math.round(100f - instability), 0, 100);
        int power = traits.stream().mapToInt(TraitRoll::potency).sum();

        return Optional.of(new EmbryoGenome(dominant, tierFor(power), stability, traits, List.copyOf(lineage), 0));
    }

    /** Tier pela soma das potencias: ate 3 = I, ate 6 = II, ate 10 = III, acima = IV. */
    public static int tierFor(int power) {
        return power <= 3 ? 1 : power <= 6 ? 2 : power <= 10 ? 3 : 4;
    }

    /** Embriao com a estabilidade final da incubacao (fome e mutacoes descontadas). */
    public EmbryoGenome withStability(int value) {
        return new EmbryoGenome(archetype, tier, Mth.clamp(value, 0, 100), traits, lineage, mutations);
    }

    /** Resultado de uma mutacao: o novo genoma e o traco que mudou (vazio se nada pode mudar). */
    public record Mutation(EmbryoGenome genome, Optional<ResourceKey<Trait>> trait) {
    }

    /**
     * Mutacao da incubacao: fortalece um traco existente ou desperta um traco latente de uma das
     * especies da linhagem. Cada mutacao custa 5 de estabilidade; o tier nunca cai.
     */
    public Mutation mutate(HolderLookup.Provider registries, RandomSource random) {
        List<TraitRoll> next = new ArrayList<>(traits);
        List<Integer> upgradable = new ArrayList<>();
        for (int idx = 0; idx < next.size(); idx++) {
            TraitRoll roll = next.get(idx);
            int max = GeneticsRegistries.trait(registries, roll.trait()).map(Trait::maxPotency).orElse(roll.potency());
            if (roll.potency() < max) {
                upgradable.add(idx);
            }
        }
        Set<ResourceKey<Trait>> present = new LinkedHashSet<>();
        next.forEach(roll -> present.add(roll.trait()));
        List<ResourceKey<Trait>> latent = new ArrayList<>();
        for (ResourceKey<Species> key : lineage) {
            GeneticsRegistries.species(registries, key).ifPresent(species -> species.traits().forEach(entry -> {
                if (!present.contains(entry.trait()) && !latent.contains(entry.trait())
                        && GeneticsRegistries.trait(registries, entry.trait()).isPresent()) {
                    latent.add(entry.trait());
                }
            }));
        }

        ResourceKey<Trait> changed;
        if (!upgradable.isEmpty() && (latent.isEmpty() || random.nextBoolean())) {
            int idx = upgradable.get(random.nextInt(upgradable.size()));
            TraitRoll roll = next.get(idx);
            next.set(idx, new TraitRoll(roll.trait(), roll.potency() + 1));
            changed = roll.trait();
        } else if (!latent.isEmpty()) {
            changed = latent.get(random.nextInt(latent.size()));
            next.add(new TraitRoll(changed, 1));
        } else {
            return new Mutation(withStability(stability - 5), Optional.empty());
        }
        next.sort(Comparator.comparingInt(TraitRoll::potency).reversed()
                .thenComparing(roll -> roll.trait().location()));
        int power = next.stream().mapToInt(TraitRoll::potency).sum();
        EmbryoGenome mutated = new EmbryoGenome(archetype, Math.max(tier, tierFor(power)),
                Mth.clamp(stability - 5, 0, 100), next, lineage, mutations + 1);
        return new Mutation(mutated, Optional.of(changed));
    }

    private static boolean conflicts(ResourceKey<Trait> a, ResourceKey<Trait> b, HolderLookup.Provider registries) {
        return GeneticsRegistries.trait(registries, a).map(t -> t.conflictsWith(b)).orElse(false)
                || GeneticsRegistries.trait(registries, b).map(t -> t.conflictsWith(a)).orElse(false);
    }
}
