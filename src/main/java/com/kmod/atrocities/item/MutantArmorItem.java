package com.kmod.atrocities.item;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.registry.ModRegistries;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Carapaca Mutante: armadura feita de Escamas Mutantes sobre uma base de diamante.
 * <p>
 * Conjunto completo: 20% menos dano de mutantes e Aberracoes, e imunidade a veneno e nausea.
 */
public class MutantArmorItem extends ArmorItem {

    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, Atrocities.MODID);

    public static final Holder<ArmorMaterial> CARAPACE = MATERIALS.register("mutant_carapace", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                map.put(ArmorItem.Type.BOOTS, 3);
                map.put(ArmorItem.Type.LEGGINGS, 6);
                map.put(ArmorItem.Type.CHESTPLATE, 8);
                map.put(ArmorItem.Type.HELMET, 3);
                map.put(ArmorItem.Type.BODY, 11);
            }),
            15, SoundEvents.ARMOR_EQUIP_TURTLE,
            () -> Ingredient.of(com.kmod.atrocities.registry.ModItems.MUTANT_SCALE.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "mutant_carapace"))),
            3.0f, 0.05f));

    public static final int DURABILITY_FACTOR = 35;
    public static final float BOSS_REDUCTION = 0.2f;

    public MutantArmorItem(ArmorItem.Type type, Properties properties) {
        super(CARAPACE, type, properties.durability(type.getDurability(DURABILITY_FACTOR)));
    }

    /** Quantas pecas da Carapaca a entidade veste. */
    public static int piecesWorn(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            if (entity.getItemBySlot(slot).getItem() instanceof MutantArmorItem) {
                count++;
            }
        }
        return count;
    }

    public static boolean fullSet(LivingEntity entity) {
        return piecesWorn(entity) >= 4;
    }

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, event -> {
            if (event.getSource().getEntity() instanceof AtrocityBoss && fullSet(event.getEntity())) {
                event.setAmount(event.getAmount() * (1 - BOSS_REDUCTION));
            }
        });
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Applicable.class, event -> {
            var effect = event.getEffectInstance().getEffect();
            if ((effect.is(MobEffects.POISON) || effect.is(MobEffects.CONFUSION)) && fullSet(event.getEntity())) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Player player = net.neoforged.fml.loading.FMLEnvironment.dist.isClient() ? ClientAccess.player() : null;
        int worn = player != null ? piecesWorn(player) : 0;
        ChatFormatting color = worn >= 4 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
        tooltip.add(Component.translatable("tooltip.atrocities.mutant_armor.set", worn).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.atrocities.mutant_armor.bonus1").withStyle(color));
        tooltip.add(Component.translatable("tooltip.atrocities.mutant_armor.bonus2").withStyle(color));
    }

    /** Isola o acesso ao jogador do cliente (a classe so e carregada no cliente). */
    private static final class ClientAccess {
        static Player player() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
    }
}
