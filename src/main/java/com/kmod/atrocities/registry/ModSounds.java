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

    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_REFUEL = register("block.machine.refuel");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXTRACTOR_GRIND = register("block.extractor.grind");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXTRACTOR_OUTPUT = register("block.extractor.output");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_SPIN = register("block.centrifuge.spin");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_COMPLETE =
            register("block.centrifuge.complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_PROCESS = register("block.sequencer.process");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEQUENCER_COMPLETE =
            register("block.sequencer.complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> INCUBATOR_HEARTBEAT =
            register("block.incubator.heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> INCUBATOR_HATCH = register("block.incubator.hatch");
    public static final DeferredHolder<SoundEvent, SoundEvent> INCUBATOR_MUTATE = register("block.incubator.mutate");
    public static final DeferredHolder<SoundEvent, SoundEvent> INCUBATOR_ABORT = register("block.incubator.abort");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModRegistries.SOUND_EVENTS.register(name, () ->
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, name)));
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que os sons sejam registrados.
    }
}
