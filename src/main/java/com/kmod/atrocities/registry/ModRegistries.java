package com.kmod.atrocities.registry;

import com.kmod.atrocities.Atrocities;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Todos os {@link DeferredRegister} do mod em um so lugar.
 * <p>
 * As classes de conteudo ({@link ModItems}, {@link ModCreativeTabs}, ...) apenas declaram entradas
 * nestes registros; {@link #register(IEventBus)} liga tudo ao barramento do mod.
 */
public final class ModRegistries {

    private ModRegistries() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Atrocities.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Atrocities.MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Atrocities.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Atrocities.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Atrocities.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, Atrocities.MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Atrocities.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Atrocities.MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Atrocities.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Atrocities.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Atrocities.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Atrocities.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Atrocities.MODID);

    /** Carrega as classes de conteudo (inicializando seus campos estaticos) e registra tudo no barramento. */
    public static void register(IEventBus modBus) {
        ModDataComponents.bootstrap();
        ModSounds.bootstrap();
        ModBlocks.bootstrap();
        ModItems.bootstrap();
        ModBlockEntities.bootstrap();
        ModMenus.bootstrap();
        ModEntities.bootstrap();
        ModCreativeTabs.bootstrap();
        ModLoot.bootstrap();

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        SOUND_EVENTS.register(modBus);
        PARTICLE_TYPES.register(modBus);
        MOB_EFFECTS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
        ModLoot.CONDITIONS.register(modBus);

        ModDataMaps.register(modBus);
        ModBlockEntities.registerCapabilities(modBus);
        ModEntities.registerAttributes(modBus);
    }
}
