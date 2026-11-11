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
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_TELEGRAPH = register("entity.boss.telegraph");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_RELEASE = register("entity.boss.release");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_HIT_FLESH = register("entity.boss.hit_flesh");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_CRITICAL = register("entity.boss.critical");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUTSCENE_STING = register("entity.boss.cutscene_sting");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE_SHIFT = register("entity.boss.phase_shift");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORGAN_SPAWN = register("entity.boss_organ.spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORGAN_PULSE = register("entity.boss_organ.pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> ORGAN_SHATTER = register("entity.boss_organ.shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_ARMOR_CLANG = register("entity.boss.armor_clang");
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

    // ------------------------------------------------------------------ mutantes (Fase 9)
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTANT_BLAST = register("entity.mutant.blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTANT_LEAP = register("entity.mutant.leap");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTANT_LAND = register("entity.mutant.land");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_GRAB = register("entity.zombie_titan.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_THROW = register("entity.zombie_titan.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> TITAN_ERUPT = register("entity.zombie_titan.erupt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BALLISTA_VOLLEY = register("entity.skeleton_ballista.volley");
    public static final DeferredHolder<SoundEvent, SoundEvent> BALLISTA_SHOT = register("entity.skeleton_ballista.ballista");
    public static final DeferredHolder<SoundEvent, SoundEvent> BALLISTA_DRAW = register("entity.skeleton_ballista.draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> BALLISTA_RATTLE = register("entity.skeleton_ballista.rattle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR_FUSE = register("entity.creeper_detonator.fuse");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR_DEFUSED = register("entity.creeper_detonator.defused");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAVER_WEB = register("entity.weaver_spider.web");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAVER_SPRAY = register("entity.weaver_spider.spray");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAY_SHRIEK = register("entity.phantom_ray.shriek");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAY_GUST = register("entity.phantom_ray.gust");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAY_SWOOP = register("entity.phantom_ray.swoop");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAY_ORB = register("entity.phantom_ray.orb");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTAGEN_INJECT = register("item.mutagen.inject");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTAGEN_THROW = register("item.mutagen.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTAGEN_SHATTER = register("item.mutagen.shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTATION_STAGE = register("entity.mutation.stage");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTATION_PULSE = register("entity.mutation.pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTATION_BURST = register("entity.mutation.burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUTATION_RUPTURE = register("entity.mutation.rupture");

    public static final DeferredHolder<SoundEvent, SoundEvent> IMBUE_PROCESS = register("block.imbuing_terminal.process");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMBUE_COMPLETE = register("block.imbuing_terminal.complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENE_EVOLVE = register("item.imbued.evolve");
    public static final com.kmod.atrocities.entity.mutant.MutantSounds ZOMBIE_TITAN = voices("zombie_titan");
    public static final com.kmod.atrocities.entity.mutant.MutantSounds SKELETON_BALLISTA = voices("skeleton_ballista");
    public static final com.kmod.atrocities.entity.mutant.MutantSounds CREEPER_DETONATOR = voices("creeper_detonator");
    public static final com.kmod.atrocities.entity.mutant.MutantSounds WEAVER_SPIDER = voices("weaver_spider");
    public static final com.kmod.atrocities.entity.mutant.MutantSounds PHANTOM_RAY = voices("phantom_ray");

    // ------------------------------------------------------------------ Fase 11
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_BLINK = register("entity.rift_enderman.blink");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_VORTEX = register("entity.rift_enderman.vortex");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_SQUISH = register("entity.devourer_slime.squish");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_ENGULF = register("entity.devourer_slime.engulf");
    public static final DeferredHolder<SoundEvent, SoundEvent> WITCH_BREW = register("entity.cauldron_witch.brew");
    public static final DeferredHolder<SoundEvent, SoundEvent> WITCH_DRINK = register("entity.cauldron_witch.drink");
    public static final DeferredHolder<SoundEvent, SoundEvent> WITCH_THROW = register("entity.cauldron_witch.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> OUTBREAK_START = register("event.outbreak.start");
    public static final DeferredHolder<SoundEvent, SoundEvent> OUTBREAK_WAVE = register("event.outbreak.wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> OUTBREAK_CLEARED = register("event.outbreak.cleared");
    public static final com.kmod.atrocities.entity.mutant.MutantSounds RIFT_ENDERMAN = voices("rift_enderman");
    public static final com.kmod.atrocities.entity.mutant.MutantSounds DEVOURER_SLIME = voices("devourer_slime");
    public static final com.kmod.atrocities.entity.mutant.MutantSounds CAULDRON_WITCH = voices("cauldron_witch");

    private static com.kmod.atrocities.entity.mutant.MutantSounds voices(String mob) {
        return new com.kmod.atrocities.entity.mutant.MutantSounds(register("entity." + mob + ".ambient"),
                register("entity." + mob + ".hurt"), register("entity." + mob + ".death"), register("entity." + mob + ".roar"));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return ModRegistries.SOUND_EVENTS.register(name, () ->
                SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, name)));
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que os sons sejam registrados.
    }
}
