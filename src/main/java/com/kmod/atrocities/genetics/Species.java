package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;

/**
 * Especie-base de onde se extrai material genetico. Definida por datapack em
 * {@code data/<ns>/atrocities/species/<id>.json}; qualquer entidade (inclusive de outros mods) pode virar especie.
 *
 * @param entity     entidade viva que representa a especie
 * @param archetype  linhagem dominante
 * @param color      cor do fluido genetico (amostras, capsulas, particulas)
 * @param extraction regras de extracao e captura
 * @param traits     tracos que a especie pode doar, com peso e faixa de potencia
 */
public record Species(EntityType<?> entity, Archetype archetype, int color, Extraction extraction,
                      List<TraitEntry> traits) {

    public static final Codec<Species> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(Species::entity),
            Archetype.CODEC.fieldOf("archetype").forGetter(Species::archetype),
            HexColor.CODEC.fieldOf("color").forGetter(Species::color),
            Extraction.CODEC.optionalFieldOf("extraction", Extraction.DEFAULT).forGetter(Species::extraction),
            TraitEntry.CODEC.listOf().fieldOf("traits").forGetter(Species::traits)
    ).apply(i, Species::new));

    /**
     * @param syringeHealthRatio fracao maxima de vida para a Seringa de Extracao funcionar
     * @param captureHealthRatio fracao maxima de vida para a Capsula de Estase capturar
     * @param basePurity         pureza media das amostras (0-100)
     */
    public record Extraction(float syringeHealthRatio, float captureHealthRatio, int basePurity) {

        public static final Extraction DEFAULT = new Extraction(0.5f, 0.25f, 40);

        public static final Codec<Extraction> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.01f, 1f).optionalFieldOf("syringe_health_ratio", DEFAULT.syringeHealthRatio)
                        .forGetter(Extraction::syringeHealthRatio),
                Codec.floatRange(0.01f, 1f).optionalFieldOf("capture_health_ratio", DEFAULT.captureHealthRatio)
                        .forGetter(Extraction::captureHealthRatio),
                Codec.intRange(1, 100).optionalFieldOf("base_purity", DEFAULT.basePurity)
                        .forGetter(Extraction::basePurity)
        ).apply(i, Extraction::new));
    }

    /** Traco que a especie pode doar. Potencia sorteada entre {@code minPotency} e {@code maxPotency}. */
    public record TraitEntry(ResourceKey<Trait> trait, int weight, int minPotency, int maxPotency) {

        public static final Codec<TraitEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(GeneticsRegistries.TRAIT).fieldOf("trait").forGetter(TraitEntry::trait),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(TraitEntry::weight),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("min_potency", 1).forGetter(TraitEntry::minPotency),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_potency", 1).forGetter(TraitEntry::maxPotency)
        ).apply(i, TraitEntry::new));
    }
}
