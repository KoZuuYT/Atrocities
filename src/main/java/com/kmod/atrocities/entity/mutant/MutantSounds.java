package com.kmod.atrocities.entity.mutant;

import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Sons de voz de um mutante. */
public record MutantSounds(DeferredHolder<SoundEvent, SoundEvent> ambient,
                           DeferredHolder<SoundEvent, SoundEvent> hurt,
                           DeferredHolder<SoundEvent, SoundEvent> death,
                           DeferredHolder<SoundEvent, SoundEvent> roar) {
}
