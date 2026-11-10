package com.kmod.atrocities.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Traco genetico definido por datapack em {@code data/<ns>/atrocities/trait/<id>.json}.
 *
 * @param category     grupo funcional (ofensa, defesa, mobilidade, especial)
 * @param maxPotency   nivel maximo que o traco atinge num embriao
 * @param instability  instabilidade somada ao genoma por nivel de potencia
 * @param color        cor usada nos tooltips
 * @param incompatible tracos que entram em conflito com este (penalidade extra de instabilidade)
 * @param bossAttributes modificadores de atributo que o traco aplica no chefe, por nivel de potencia
 */
public record Trait(TraitCategory category, int maxPotency, float instability, int color,
                    List<ResourceKey<Trait>> incompatible, List<BossAttribute> bossAttributes) {

    public static final Codec<Trait> CODEC = RecordCodecBuilder.create(i -> i.group(
            TraitCategory.CODEC.fieldOf("category").forGetter(Trait::category),
            Codec.intRange(1, 10).fieldOf("max_potency").forGetter(Trait::maxPotency),
            Codec.floatRange(0f, 100f).fieldOf("instability").forGetter(Trait::instability),
            HexColor.CODEC.fieldOf("color").forGetter(Trait::color),
            ResourceKey.codec(GeneticsRegistries.TRAIT).listOf().optionalFieldOf("incompatible", List.of())
                    .forGetter(Trait::incompatible),
            BossAttribute.CODEC.listOf().optionalFieldOf("boss_attributes", List.of())
                    .forGetter(Trait::bossAttributes)
    ).apply(i, Trait::new));

    /**
     * Modificador aplicado no chefe: {@code valor = per_level * potencia}.
     *
     * @param attribute atributo afetado (ex.: minecraft:generic.armor)
     * @param perLevel  valor por nivel de potencia
     * @param operation add_value, add_multiplied_base ou add_multiplied_total
     */
    public record BossAttribute(Holder<Attribute> attribute, double perLevel, AttributeModifier.Operation operation) {

        public static final Codec<BossAttribute> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(BossAttribute::attribute),
                Codec.DOUBLE.fieldOf("per_level").forGetter(BossAttribute::perLevel),
                AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE)
                        .forGetter(BossAttribute::operation)
        ).apply(i, BossAttribute::new));
    }

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
