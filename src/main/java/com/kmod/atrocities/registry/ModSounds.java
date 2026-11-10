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

    public static final DeferredHolder<SoundEvent, SoundEvent> EMBRYO_AWAKEN = register("item.matured_embryo.awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_EMERGE = register("entity.boss.emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_WINDUP = register("entity.boss.windup");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SLAM = register("entity.boss.slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SWING = register("entity.boss.swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_STOMP = register("entity.boss.stomp");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SHOCKWAVE = register("entity.boss.shockwave");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_DEFLECT = register("entity.boss.deflect");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_STUNNED = register("entity.boss.stunned");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SPORES = register("entity.boss.spores");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_BLINK = register("entity.boss.blink");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SUMMON = register("entity.boss.summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_DEATH_SPASM = register("entity.boss.death_spasm");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_BURST = register("entity.boss.burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_CHARGE = register("entity.boss.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SPINE_SHOT = register("entity.boss.spine_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SPINE_IMPACT = register("entity.boss.spine_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_BILE_SPLASH = register("entity.boss.bile_splash");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_AMBIENT = register("entity.chitinous_colossus.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_HURT = register("entity.chitinous_colossus.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_DEATH = register("entity.chitinous_colossus.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLOSSUS_ROAR = register("entity.chitinous_colossus.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATRIARCH_AMBIENT = register("entity.hemophage_matriarch.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATRIARCH_HURT = register("entity.hemophage_matriarch.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATRIARCH_DEATH = register("entity.hemophage_matriarch.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATRIARCH_ROAR = register("entity.hemophage_matriarch.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATRIARCH_BITE = register("entity.hemophage_matriarch.bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATRIARCH_LEAP = register("entity.hemophage_matriarch.leap");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATRIARCH_SCREECH = register("entity.hemophage_matriarch.screech");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATRIARCH_SPRAY = register("entity.hemophage_matriarch.spray");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATRIARCH_DRAIN = register("entity.hemophage_matriarch.drain");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGION_AMBIENT = register("entity.coalesced_legion.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGION_HURT = register("entity.coalesced_legion.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGION_DEATH = register("entity.coalesced_legion.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGION_ROAR = register("entity.coalesced_legion.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGION_GURGLE = register("entity.coalesced_legion.gurgle");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGION_ENGULF = register("entity.coalesced_legion.engulf");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGION_PULL = register("entity.coalesced_legion.pull");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGION_SPIT = register("entity.coalesced_legion.spit");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModRegistries.SOUND_EVENTS.register(name, () ->
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, name)));
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que os sons sejam registrados.
    }
}
