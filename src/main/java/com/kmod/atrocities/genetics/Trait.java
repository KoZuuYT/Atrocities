package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;

/**
 * Traco genetico definido por datapack em {@code data/<ns>/atrocities/trait/<id>.json}.
 *
 * @param category     grupo funcional (ofensa, defesa, mobilidade, especial)
 * @param maxPotency   nivel maximo que o traco atinge num embriao
 * @param instability  instabilidade somada ao genoma por nivel de potencia
 * @param color        cor usada nos tooltips
 * @param incompatible tracos que entram em conflito com este (penalidade extra de instabilidade)
 */
public record Trait(TraitCategory category, int maxPotency, float instability, int color,
                    List<ResourceKey<Trait>> incompatible) {

    public static final Codec<Trait> CODEC = RecordCodecBuilder.create(i -> i.group(
            TraitCategory.CODEC.fieldOf("category").forGetter(Trait::category),
            Codec.intRange(1, 10).fieldOf("max_potency").forGetter(Trait::maxPotency),
            Codec.floatRange(0f, 100f).fieldOf("instability").forGetter(Trait::instability),
            HexColor.CODEC.fieldOf("color").forGetter(Trait::color),
            ResourceKey.codec(GeneticsRegistries.TRAIT).listOf().optionalFieldOf("incompatible", List.of())
                    .forGetter(Trait::incompatible)
    ).apply(i, Trait::new));

    public boolean conflictsWith(ResourceKey<Trait> other) {
        return incompatible.contains(other);
    }

    public static String translationKey(ResourceKey<Trait> key) {
        return "trait." + key.location().getNamespace() + "." + key.location().getPath();
    }

    public static MutableComponent displayName(ResourceKey<Trait> key) {
        return Component.translatable(translationKey(key));
    }

    public static MutableComponent description(ResourceKey<Trait> key) {
        return Component.translatable(translationKey(key) + ".desc");
    }
}
