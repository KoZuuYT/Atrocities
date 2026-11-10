package com.kmod.atrocities.client.tooltip;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.item.BileLauncherItem;
import com.kmod.atrocities.item.BioThrowableItem;
import com.kmod.atrocities.item.ExtractionSyringeItem;
import com.kmod.atrocities.item.StasisCapsuleItem;
import com.kmod.atrocities.registry.ModDataComponents;
import com.kmod.atrocities.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.jetbrains.annotations.Nullable;

/**
 * Visual das tooltips dos itens do Atrocities: raridade (cor da moldura e animacao), categoria e tier.
 * Itens de outros mods nao recebem estilo.
 */
public record TooltipStyle(Rarity rarity, Category category, int tier) {

    /** Raridade visual. {@code animated} = moldura pulsante com efeitos extras. */
    public enum Rarity {
        COMMON(0x8E8E8E, false),
        UNCOMMON(0x4CC24A, false),
        RARE(0x3F82F0, false),
        EPIC(0xA35CF0, false),
        LEGENDARY(0xF2A51A, true),
        ARTIFACT(0xE3262F, true);

        private final int color;
        private final boolean animated;

        Rarity(int color, boolean animated) {
            this.color = color;
            this.animated = animated;
        }

        public int color() {
            return color;
        }

        public boolean animated() {
            return animated;
        }

        public String key() {
            return "tooltip.atrocities.rarity." + name().toLowerCase();
        }
    }

    /** Categoria mostrada no selo cinza. */
    public enum Category {
        WEAPON, TOOL, THROWABLE, ORGAN, MACHINE, SAMPLE, EMBRYO, TROPHY, MATERIAL;

        public String key() {
            return "tooltip.atrocities.category." + name().toLowerCase();
        }
    }

    /** Cores dos selos de tier (I a IV). */
    private static final int[] TIER_COLORS = {0x4CC24A, 0x3F82F0, 0xA35CF0, 0xE3262F};

    public static int tierColor(int tier) {
        return TIER_COLORS[Math.max(1, Math.min(TIER_COLORS.length, tier)) - 1];
    }

    @Nullable
    public static TooltipStyle of(ItemStack stack) {
        if (stack.isEmpty() || !Atrocities.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return null;
        }
        EmbryoGenome genome = stack.get(ModDataComponents.EMBRYO_GENOME.get());
        Category category = category(stack);
        int tier = genome != null ? genome.tier() : 0;
        return new TooltipStyle(rarity(stack, category, tier), category, tier);
    }

    private static Category category(ItemStack stack) {
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof BileLauncherItem) {
            return Category.WEAPON;
        }
        if (stack.getItem() instanceof ExtractionSyringeItem || stack.getItem() instanceof StasisCapsuleItem) {
            return Category.TOOL;
        }
        if (stack.getItem() instanceof BioThrowableItem) {
            return Category.THROWABLE;
        }
        if (stack.getItem() instanceof BlockItem) {
            return Category.MACHINE;
        }
        if (stack.is(ModItems.ABERRANT_HEART.get())) {
            return Category.ORGAN;
        }
        if (stack.is(ModItems.GENE_SAMPLE.get())) {
            return Category.SAMPLE;
        }
        if (stack.is(ModItems.UNSTABLE_EMBRYO.get()) || stack.is(ModItems.MATURED_EMBRYO.get())) {
            return Category.EMBRYO;
        }
        if (stack.is(ModItems.COLOSSUS_CARAPACE.get()) || stack.is(ModItems.MATRIARCH_FANG.get())
                || stack.is(ModItems.LEGION_CORE.get())) {
            return Category.TROPHY;
        }
        return Category.MATERIAL;
    }

    private static Rarity rarity(ItemStack stack, Category category, int tier) {
        if (category == Category.WEAPON) {
            return Rarity.ARTIFACT;
        }
        if (category == Category.TROPHY) {
            return Rarity.LEGENDARY;
        }
        if (category == Category.EMBRYO && tier > 0) {
            return switch (tier) {
                case 1 -> Rarity.RARE;
                case 2 -> Rarity.EPIC;
                case 3 -> Rarity.LEGENDARY;
                default -> Rarity.ARTIFACT;
            };
        }
        return switch (stack.getRarity()) {
            case UNCOMMON -> Rarity.UNCOMMON;
            case RARE -> Rarity.RARE;
            case EPIC -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }
}
