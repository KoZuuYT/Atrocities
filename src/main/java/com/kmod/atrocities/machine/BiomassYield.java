package com.kmod.atrocities.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Valor do data map {@code atrocities:biomass_yield}: quantos pontos de biomassa um item rende no
 * Extrator de Biomassa (100 pontos = 1 Biomassa). Arquivo em
 * {@code data/<ns>/data_maps/item/biomass_yield.json}; qualquer datapack pode adicionar itens.
 */
public record BiomassYield(int amount) {

    public static final Codec<BiomassYield> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1000).fieldOf("amount").forGetter(BiomassYield::amount)
    ).apply(i, BiomassYield::new));
}
