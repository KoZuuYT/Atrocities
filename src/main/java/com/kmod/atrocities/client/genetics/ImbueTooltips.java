package com.kmod.atrocities.client.genetics;

import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.genetics.TraitRoll;
import com.kmod.atrocities.imbue.ImbuedGenes;
import com.kmod.atrocities.imbue.Imbuing;
import com.kmod.atrocities.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Tooltip de armas e armaduras imbuidas: tanques, genes, efeitos (Shift) e evolucao. */
final class ImbueTooltips {

    private ImbueTooltips() {
    }

    private static final int LABEL = 0x9A9A9A;
    private static final int TANK = 0x7FF0D8;
    private static final int EVOLUTION = 0xD070F0;
    private static final int SEGMENTS = 10;

    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ImbuedGenes genes = stack.get(ModDataComponents.IMBUED_GENES.get());
        Imbuing.Kind kind = Imbuing.kindOf(stack);
        if (genes == null || genes.isEmpty() || kind == null) {
            return;
        }
        HolderLookup.Provider registries = event.getContext().registries();
        if (registries == null) {
            registries = GeneticsClient.clientRegistries();
        }
        if (registries == null) {
            return;
        }
        boolean details = Screen.hasShiftDown();
        List<Component> lines = new ArrayList<>();

        MutableComponent tanks = Component.translatable("tooltip.atrocities.imbue.tanks").withColor(LABEL)
                .append(" ");
        for (int i = 0; i < Imbuing.MAX_GENES; i++) {
            tanks.append(Component.literal(i < genes.genes().size() ? "■" : "□").withColor(TANK));
        }
        lines.add(tanks);

        for (TraitRoll gene : genes.genes()) {
            int color = GeneticsRegistries.trait(registries, gene.trait()).map(Trait::color).orElse(0xB0B0B0);
            int max = Imbuing.maxLevel(registries, gene.trait());
            MutableComponent line = Component.literal(" ◆ ").withColor(color)
                    .append(Trait.displayName(gene.trait()).withColor(lighten(color)))
                    .append(Component.literal(" " + Imbuing.roman(gene.potency())).withColor(lighten(color)));
            if (gene.potency() >= max) {
                line.append(Component.literal(" ★").withColor(0xFFD54F));
            }
            lines.add(line);
            if (details) {
                String key = Trait.translationKey(gene.trait()) + (kind == Imbuing.Kind.WEAPON ? ".weapon" : ".armor");
                if (I18n.exists(key)) {
                    lines.add(Component.literal("    ").append(Component.translatable(key))
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            }
        }

        if (Imbuing.fullyEvolved(genes, registries)) {
            lines.add(Component.translatable("tooltip.atrocities.imbue.max").withColor(0xFFD54F));
        } else {
            int cost = Imbuing.evolutionCost(genes);
            int filled = Math.min(SEGMENTS, genes.xp() * SEGMENTS / Math.max(1, cost));
            lines.add(Component.translatable("tooltip.atrocities.imbue.evolution").withColor(LABEL)
                    .append(" ")
                    .append(Component.literal("▮".repeat(filled)).withColor(EVOLUTION))
                    .append(Component.literal("▮".repeat(SEGMENTS - filled)).withColor(0x3A3A3A))
                    .append(Component.literal(" " + genes.xp() + "/" + cost).withColor(LABEL)));
        }
        if (!details) {
            lines.add(Component.translatable("tooltip.atrocities.imbue.shift").withStyle(ChatFormatting.DARK_GRAY));
        }
        event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
    }

    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = r + (255 - r) / 2;
        g = g + (255 - g) / 2;
        b = b + (255 - b) / 2;
        return (r << 16) | (g << 8) | b;
    }
}
