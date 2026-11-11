package com.kmod.atrocities.registry;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.entity.boss.BossOrganEntity;
import com.kmod.atrocities.entity.boss.ChitinousColossusEntity;
import com.kmod.atrocities.entity.boss.CoalescedLegionEntity;
import com.kmod.atrocities.entity.boss.HemophageMatriarchEntity;
import com.kmod.atrocities.genetics.Archetype;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Entidades: os tres chefes (um por arquetipo) e seus projeteis. */
public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredHolder<EntityType<?>, EntityType<ChitinousColossusEntity>> CHITINOUS_COLOSSUS =
            ModRegistries.ENTITY_TYPES.register("chitinous_colossus", () -> EntityType.Builder
                    .of(ChitinousColossusEntity::new, MobCategory.MONSTER)
                    .sized(3.2f, 3.4f)
                    .eyeHeight(2.2f)
                    .clientTrackingRange(16)
                    .build("chitinous_colossus"));

    public static final DeferredHolder<EntityType<?>, EntityType<HemophageMatriarchEntity>> HEMOPHAGE_MATRIARCH =
            ModRegistries.ENTITY_TYPES.register("hemophage_matriarch", () -> EntityType.Builder
                    .of(HemophageMatriarchEntity::new, MobCategory.MONSTER)
                    .sized(2.0f, 3.0f)
                    .eyeHeight(2.6f)
                    .clientTrackingRange(16)
                    .build("hemophage_matriarch"));

    public static final DeferredHolder<EntityType<?>, EntityType<CoalescedLegionEntity>> COALESCED_LEGION =
            ModRegistries.ENTITY_TYPES.register("coalesced_legion", () -> EntityType.Builder
                    .of(CoalescedLegionEntity::new, MobCategory.MONSTER)
                    .sized(2.8f, 2.4f)
                    .eyeHeight(1.8f)
                    .clientTrackingRange(16)
                    .build("coalesced_legion"));

    public static final DeferredHolder<EntityType<?>, EntityType<BioProjectile>> BIO_PROJECTILE =
            ModRegistries.ENTITY_TYPES.register("bio_projectile", () -> EntityType.Builder
                    .<BioProjectile>of(BioProjectile::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build("bio_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<BossOrganEntity>> BOSS_ORGAN =
            ModRegistries.ENTITY_TYPES.register("boss_organ", () -> EntityType.Builder
                    .of(BossOrganEntity::new, MobCategory.MISC)
                    .sized(1.4f, 3.4f)
                    .clientTrackingRange(10)
                    .build("boss_organ"));

    // ------------------------------------------------------------------ mutantes (Fase 9)

    public static final DeferredHolder<EntityType<?>, EntityType<com.kmod.atrocities.entity.mutant.ZombieTitanEntity>> ZOMBIE_TITAN =
            ModRegistries.ENTITY_TYPES.register("zombie_titan", () -> EntityType.Builder
                    .of(com.kmod.atrocities.entity.mutant.ZombieTitanEntity::new, MobCategory.MONSTER)
                    .sized(2.0f, 2.9f)
                    .eyeHeight(2.4f)
                    .clientTrackingRange(12)
                    .build("zombie_titan"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.kmod.atrocities.entity.mutant.SkeletonBallistaEntity>> SKELETON_BALLISTA =
            ModRegistries.ENTITY_TYPES.register("skeleton_ballista", () -> EntityType.Builder
                    .of(com.kmod.atrocities.entity.mutant.SkeletonBallistaEntity::new, MobCategory.MONSTER)
                    .sized(1.3f, 3.2f)
                    .eyeHeight(2.8f)
                    .clientTrackingRange(12)
                    .build("skeleton_ballista"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.kmod.atrocities.entity.mutant.CreeperDetonatorEntity>> CREEPER_DETONATOR =
            ModRegistries.ENTITY_TYPES.register("creeper_detonator", () -> EntityType.Builder
                    .of(com.kmod.atrocities.entity.mutant.CreeperDetonatorEntity::new, MobCategory.MONSTER)
                    .sized(1.8f, 2.9f)
                    .eyeHeight(2.4f)
                    .clientTrackingRange(12)
                    .build("creeper_detonator"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.kmod.atrocities.entity.mutant.WeaverSpiderEntity>> WEAVER_SPIDER =
            ModRegistries.ENTITY_TYPES.register("weaver_spider", () -> EntityType.Builder
                    .of(com.kmod.atrocities.entity.mutant.WeaverSpiderEntity::new, MobCategory.MONSTER)
                    .sized(2.8f, 1.5f)
                    .eyeHeight(1.0f)
                    .clientTrackingRange(12)
                    .build("weaver_spider"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.kmod.atrocities.entity.mutant.PhantomRayEntity>> PHANTOM_RAY =
            ModRegistries.ENTITY_TYPES.register("phantom_ray", () -> EntityType.Builder
                    .of(com.kmod.atrocities.entity.mutant.PhantomRayEntity::new, MobCategory.MONSTER)
                    .sized(3.6f, 1.0f)
                    .eyeHeight(0.6f)
                    .clientTrackingRange(12)
                    .build("phantom_ray"));

    /** Chefe que nasce de um embriao do arquetipo dado. */
    public static EntityType<? extends AtrocityBoss> bossFor(Archetype archetype) {
        return switch (archetype) {
            case CHITINOUS -> CHITINOUS_COLOSSUS.get();
            case HEMOPHAGE -> HEMOPHAGE_MATRIARCH.get();
            case COALESCED -> COALESCED_LEGION.get();
        };
    }

    public static void registerAttributes(IEventBus modBus) {
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(CHITINOUS_COLOSSUS.get(), ChitinousColossusEntity.createAttributes().build());
            event.put(HEMOPHAGE_MATRIARCH.get(), HemophageMatriarchEntity.createAttributes().build());
            event.put(COALESCED_LEGION.get(), CoalescedLegionEntity.createAttributes().build());
            event.put(BOSS_ORGAN.get(), BossOrganEntity.createAttributes().build());
            event.put(ZOMBIE_TITAN.get(), com.kmod.atrocities.entity.mutant.ZombieTitanEntity.createAttributes().build());
            event.put(SKELETON_BALLISTA.get(), com.kmod.atrocities.entity.mutant.SkeletonBallistaEntity.createAttributes().build());
            event.put(CREEPER_DETONATOR.get(), com.kmod.atrocities.entity.mutant.CreeperDetonatorEntity.createAttributes().build());
            event.put(WEAVER_SPIDER.get(), com.kmod.atrocities.entity.mutant.WeaverSpiderEntity.createAttributes().build());
            event.put(PHANTOM_RAY.get(), com.kmod.atrocities.entity.mutant.PhantomRayEntity.createAttributes().build());
        });
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que as entidades sejam registradas.
    }
}
