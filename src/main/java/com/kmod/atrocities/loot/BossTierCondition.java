package com.kmod.atrocities.loot;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.registry.ModLoot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * Condicao de loot {@code atrocities:boss_tier}: passa se a entidade morta e um chefe com tier entre
 * {@code min} e {@code max}. Permite que datapacks escalem o loot pelo tier do genoma.
 */
public record BossTierCondition(int min, int max) implements LootItemCondition {

    public static final MapCodec<BossTierCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, EmbryoGenome.MAX_TIER).optionalFieldOf("min", 1).forGetter(BossTierCondition::min),
            Codec.intRange(1, EmbryoGenome.MAX_TIER).optionalFieldOf("max", EmbryoGenome.MAX_TIER).forGetter(BossTierCondition::max)
    ).apply(i, BossTierCondition::new));

    @Override
    public LootItemConditionType getType() {
        return ModLoot.BOSS_TIER.get();
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof AtrocityBoss boss)) {
            return false;
        }
        int tier = boss.tier();
        return tier >= min && tier <= max;
    }
}
