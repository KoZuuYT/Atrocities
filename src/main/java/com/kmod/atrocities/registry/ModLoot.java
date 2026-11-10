package com.kmod.atrocities.registry;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.loot.BossTierCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Tipos de condicao de loot do mod. */
public final class ModLoot {

    private ModLoot() {
    }

    public static final DeferredRegister<LootItemConditionType> CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Atrocities.MODID);

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> BOSS_TIER =
            CONDITIONS.register("boss_tier", () -> new LootItemConditionType(BossTierCondition.CODEC));

    static void bootstrap() {
        // Forca o carregamento da classe.
    }
}
