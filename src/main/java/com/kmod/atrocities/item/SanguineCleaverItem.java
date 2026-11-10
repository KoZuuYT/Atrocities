package com.kmod.atrocities.item;

import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Cutelo Sanguineo (trofeu da Matriarca Hemofaga): golpes rapidos que roubam vida.
 * Cura 15% do dano causado, ate 8 de vida por golpe.
 */
public class SanguineCleaverItem extends SwordItem {

    public static final float LIFESTEAL = 0.15f;
    public static final float MAX_HEAL = 8f;

    public SanguineCleaverItem(Properties properties) {
        super(BioforgedTier.BIOFORGED, properties.attributes(createAttributes(BioforgedTier.BIOFORGED, 39f, -2.8f)));
    }

    /** Liga o roubo de vida no barramento do jogo. */
    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, SanguineCleaverItem::onDamage);
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getDirectEntity() instanceof Player player)
                || !player.getMainHandItem().is(ModItems.SANGUINE_CLEAVER.get())
                || !(player.level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0 || player.getHealth() >= player.getMaxHealth()) {
            return;
        }
        float heal = Math.min(event.getNewDamage() * LIFESTEAL, MAX_HEAL);
        player.heal(heal);
        LivingEntity target = event.getEntity();
        Vec3 from = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 to = player.position().add(0, player.getBbHeight() * 0.6, 0);
        GeneticsFx.stream(level, GeneticsFx.dust(0x9E0F1F, 1.1f), from, to, 10);
        GeneticsFx.burst(level, GeneticsFx.dust(0xC21A2C, 1.4f), from, 8, 0.25, 0.02);
        GeneticsFx.sound(level, to, ModSounds.MATRIARCH_DRAIN.get(), 0.6f, 1.3f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.atrocities.sanguine_cleaver", (int) (LIFESTEAL * 100), (int) MAX_HEAL)
                .withStyle(ChatFormatting.GRAY));
    }
}
