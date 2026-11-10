package com.kmod.atrocities.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Permite elevar o teto de atributos vanilla (ex.: max_health = 1024),
 * necessario para chefes com milhares de pontos de vida.
 */
@Mixin(RangedAttribute.class)
public interface RangedAttributeAccessor {

    @Mutable
    @Accessor("maxValue")
    void atrocities$setMaxValue(double maxValue);

    @Accessor("maxValue")
    double atrocities$getMaxValue();
}
