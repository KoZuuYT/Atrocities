package com.kmod.atrocities.item;

import com.kmod.atrocities.registry.ModItems;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * Material das armas feitas com restos de chefes. Mais duravel que netherite e reparado com Coracao Aberrante.
 * O dano de cada arma e definido na propria arma (meta de ~50 por hit em PvP).
 */
public final class BioforgedTier {

    private BioforgedTier() {
    }

    public static final Tier BIOFORGED = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2600, 9.0f, 0.0f, 18, () -> Ingredient.of(ModItems.ABERRANT_HEART.get()));
}
