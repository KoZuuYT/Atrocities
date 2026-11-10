package com.kmod.atrocities.client.genetics;

import com.kmod.atrocities.genetics.CapturedSpecimen;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Species;
import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.genetics.TraitRoll;
import com.kmod.atrocities.item.ExtractionSyringeItem;
import com.kmod.atrocities.item.StasisCapsuleItem;
import com.kmod.atrocities.machine.BiologicalIncubatorBlockEntity;
import com.kmod.atrocities.machine.MachineBlock;
import com.kmod.atrocities.machine.MachineKind;
import com.kmod.atrocities.registry.ModEntities;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Tooltips das ferramentas e itens geneticos. As linhas entram logo abaixo do nome do item;
 * detalhes dos tracos aparecem segurando Shift.
 */
final class GeneticsTooltips {

    private GeneticsTooltips() {
    }

    private static final int BAR_SEGMENTS = 10;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final int LABEL = 0x9A9A9A;

    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        HolderLookup.Provider registries = event.getContext().registries();
        if (registries == null) {
            registries = GeneticsClient.clientRegistries();
        }
        if (registries == null) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        boolean details = Screen.hasShiftDown();

        GeneSample sample = stack.get(ModDataComponents.GENE_SAMPLE.get());
        EmbryoGenome genome = stack.get(ModDataComponents.EMBRYO_GENOME.get());
        CapturedSpecimen specimen = stack.get(ModDataComponents.CAPTURED_SPECIMEN.get());

        if (sample != null) {
            describeSample(sample, registries, details, lines);
        } else if (genome != null) {
            describeGenome(genome, stack.is(ModItems.MATURED_EMBRYO.get()), registries, details, lines);
        } else if (stack.getItem() instanceof StasisCapsuleItem) {
            describeCapsule(specimen, registries, lines);
        } else if (stack.getItem() instanceof ExtractionSyringeItem) {
            lines.add(hint("item.atrocities.extraction_syringe.hint"));
            lines.add(hint("item.atrocities.extraction_syringe.hint2"));
        } else if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof MachineBlock machine) {
            lines.add(hint("item.atrocities." + machine.kind().id() + ".hint"));
        } else if (stack.is(ModItems.BIOMASS.get())) {
            lines.add(hint("item.atrocities.biomass.hint"));
        }
        if (!lines.isEmpty()) {
            event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
        }

        int yield = MachineKind.biomassYield(stack);
        if (yield > 0) {
            event.getToolTip().add(Component.translatable("tooltip.atrocities.biomass_yield", yield)
                    .withColor(0x8FB04A));
        }
    }

    // ------------------------------------------------------------------ amostra

    private static void describeSample(GeneSample sample, HolderLookup.Provider registries, boolean details,
                                       List<Component> lines) {
        Optional<Species> species = GeneticsRegistries.species(registries, sample.species());
        lines.add(labeled("species", speciesName(sample.species(), species)));
        species.ifPresent(sp -> lines.add(labeled("archetype", sp.archetype().displayName())));
        lines.add(labeled("purity", bar(sample.purity(), 100, purityColor(sample.purity()))
                .append(Component.literal(" " + sample.purity() + "%").withColor(purityColor(sample.purity())))));
        traitLines(sample.traits(), registries, details, lines);
    }

    // ------------------------------------------------------------------ embriao

    private static void describeGenome(EmbryoGenome genome, boolean matured, HolderLookup.Provider registries,
                                       boolean details, List<Component> lines) {
        lines.add(labeled("archetype", genome.archetype().displayName()));
        lines.add(labeled("boss", ModEntities.bossFor(genome.archetype()).getDescription().copy()
                .withColor(genome.archetype().color())));
        lines.add(labeled("tier", Component.literal(roman(genome.tier())).withColor(tierColor(genome.tier()))
                .withStyle(ChatFormatting.BOLD)));
        int stabilityColor = stabilityColor(genome.stability());
        lines.add(labeled("stability", bar(genome.stability(), 100, stabilityColor)
                .append(Component.literal(" " + genome.stability() + "%").withColor(stabilityColor))));
        lines.add(labeled("boss_health", Component.literal(String.format("%,d", genome.bossBaseHealth()))
                .withColor(0xE05050)));
        if (genome.mutations() > 0) {
            lines.add(labeled("mutations", Component.literal(String.valueOf(genome.mutations()))
                    .withColor(0xD070F0)));
        }
        if (!matured) {
            int seconds = BiologicalIncubatorBlockEntity.incubationTicks(genome.tier()) / 20;
            lines.add(labeled("incubation", Component.literal(String.format("%d:%02d", seconds / 60, seconds % 60))
                    .withColor(0xA8E060)));
        }
        traitLines(genome.traits(), registries, details, lines);
        if (details && !genome.lineage().isEmpty()) {
            MutableComponent lineage = Component.empty();
            for (int i = 0; i < genome.lineage().size(); i++) {
                ResourceKey<Species> key = genome.lineage().get(i);
                if (i > 0) {
                    lineage.append(Component.literal(" + ").withColor(LABEL));
                }
                lineage.append(speciesName(key, GeneticsRegistries.species(registries, key)));
            }
            lines.add(labeled("lineage", lineage));
        }
        if (matured) {
            lines.add(Component.translatable("tooltip.atrocities.matured_hint")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        } else if (genome.stability() < BiologicalIncubatorBlockEntity.ABORT_THRESHOLD) {
            lines.add(Component.translatable("tooltip.atrocities.unstable_warning")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        }
    }

    // ------------------------------------------------------------------ capsula

    private static void describeCapsule(CapturedSpecimen specimen, HolderLookup.Provider registries,
                                        List<Component> lines) {
        if (specimen == null) {
            lines.add(Component.translatable("tooltip.atrocities.capsule.empty").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(hint("item.atrocities.stasis_capsule.hint"));
            return;
        }
        Optional<Species> species = GeneticsRegistries.species(registries, specimen.species());
        int color = species.map(Species::color).orElse(0xFFFFFF);
        lines.add(labeled("specimen", specimen.entityType().getDescription().copy().withColor(lighten(color))));
        int healthColor = purityColor(Math.round(specimen.healthRatio() * 100));
        lines.add(labeled("health", Component.literal(String.format("❤ %.1f / %.1f",
                specimen.health(), specimen.maxHealth())).withColor(healthColor)));
        lines.add(hint("item.atrocities.stasis_capsule.release_hint"));
    }

    // ------------------------------------------------------------------ partes comuns

    private static void traitLines(List<TraitRoll> traits, HolderLookup.Provider registries, boolean details,
                                   List<Component> lines) {
        if (traits.isEmpty()) {
            return;
        }
        lines.add(Component.translatable("tooltip.atrocities.traits").withColor(LABEL));
        for (TraitRoll roll : traits) {
            Optional<Trait> trait = GeneticsRegistries.trait(registries, roll.trait());
            int color = trait.map(Trait::color).orElse(0xAAAAAA);
            lines.add(Component.literal(" ◆ ").withColor(color)
                    .append(Trait.displayName(roll.trait()).withColor(lighten(color)))
                    .append(Component.literal(" " + roman(roll.potency())).withColor(0xFFFFFF)));
            if (details) {
                trait.ifPresent(t -> lines.add(Component.literal("    ").append(t.category().displayName())));
                lines.add(Component.literal("    ").append(Trait.description(roll.trait()))
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        if (!details) {
            lines.add(Component.translatable("tooltip.atrocities.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component speciesName(ResourceKey<Species> key, Optional<Species> species) {
        return species.map(sp -> sp.entity().getDescription().copy().withColor(lighten(sp.color())))
                .orElseGet(() -> Component.literal(key.location().toString()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent labeled(String label, Component value) {
        return Component.translatable("tooltip.atrocities." + label).withColor(LABEL)
                .append(CommonComponents.SPACE).append(value);
    }

    private static Component hint(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    private static MutableComponent bar(int value, int max, int color) {
        int filled = Mth.clamp(Math.round(value / (float) max * BAR_SEGMENTS), 0, BAR_SEGMENTS);
        return Component.literal("█".repeat(filled)).withColor(color)
                .append(Component.literal("█".repeat(BAR_SEGMENTS - filled)).withColor(0x3A3A3A));
    }

    private static String roman(int value) {
        return value >= 1 && value <= ROMAN.length ? ROMAN[value - 1] : String.valueOf(value);
    }

    private static int purityColor(int purity) {
        return purity >= 85 ? 0xFFD54F : purity >= 60 ? 0x7FD46B : purity >= 35 ? 0xE0B040 : 0xD9534F;
    }

    private static int stabilityColor(int stability) {
        return stability >= 70 ? 0x7FD46B : stability >= 35 ? 0xE0B040 : 0xD9534F;
    }

    private static int tierColor(int tier) {
        return switch (tier) {
            case 1 -> 0xB0B0B0;
            case 2 -> 0x7FD46B;
            case 3 -> 0xB07CE0;
            default -> 0xFF5555;
        };
    }

    /** Clareia cores escuras de especie/traco para ficarem legiveis no fundo do tooltip. */
    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = r + (255 - r) * 2 / 5;
        g = g + (255 - g) * 2 / 5;
        b = b + (255 - b) * 2 / 5;
        return (r << 16) | (g << 8) | b;
    }
}
