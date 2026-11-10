package com.kmod.atrocities.registry;

import com.kmod.atrocities.Atrocities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Eventos de som. Os arquivos e variacoes ficam em {@code assets/atrocities/sounds.json}. */
public final class ModSounds {

    private ModSounds() {
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> SYRINGE_PIERCE = register("item.syringe.pierce");
    public static final DeferredHolder<SoundEvent, SoundEvent> SYRINGE_DRAW = register("item.syringe.draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSULE_CAPTURE = register("item.capsule.capture");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSULE_SEAL = register("item.capsule.seal");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPSULE_RELEASE = register("item.capsule.release");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENETICS_REJECT = register("item.genetics.reject");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModRegistries.SOUND_EVENTS.register(name, () ->
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, name)));
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que os sons sejam registrados.
    }
}
