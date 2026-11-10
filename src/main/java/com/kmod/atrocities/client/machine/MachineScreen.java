package com.kmod.atrocities.client.machine;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.machine.BiologicalIncubatorBlockEntity;
import com.kmod.atrocities.machine.BiomassExtractorBlockEntity;
import com.kmod.atrocities.machine.GeneSequencerBlockEntity;
import com.kmod.atrocities.machine.GeneticCentrifugeBlockEntity;
import com.kmod.atrocities.machine.MachineBlockEntity;
import com.kmod.atrocities.machine.MachineKind;
import com.kmod.atrocities.machine.MachineMenu;
import com.kmod.atrocities.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * Tela generica das maquinas: fundo por maquina, barras de progresso/nutrientes desenhadas em gradiente
 * e previsoes do resultado (pureza da centrifuga, tier/estabilidade do sequenciador, risco da incubadora).
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    private static final int TITLE_COLOR = 0xE6D2D2;
    private static final int LABEL_COLOR = 0xA89494;
    private static final int GOOD = 0x7FD46B;
    private static final int WARN = 0xE0B040;
    private static final int BAD = 0xE05050;

    private final MachineKind kind;
    private final ResourceLocation texture;

    public MachineScreen(MachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.kind = menu.kind();
        this.texture = ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "textures/gui/" + kind.id() + ".png");
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderBarTooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(texture, x, y, 0, 0, imageWidth, imageHeight);

        MachineKind.Rect bar = kind.progressBar();
        int filled = Math.round(bar.width() * menu.progressRatio());
        if (filled > 0) {
            int[] colors = progressColors();
            graphics.fillGradient(x + bar.x(), y + bar.y(), x + bar.x() + filled, y + bar.y() + bar.height(),
                    colors[0], colors[1]);
            // brilho pulsante na ponta da barra
            int glow = (int) (120 + 100 * Math.sin(Util.getMillis() / 120.0));
            graphics.fill(x + bar.x() + filled - 1, y + bar.y(), x + bar.x() + filled, y + bar.y() + bar.height(),
                    (Mth.clamp(glow, 0, 255) << 24) | 0xFFFFFF);
        }

        if (kind.usesNutrients()) {
            MachineKind.Rect n = MachineKind.NUTRIENT_BAR;
            int h = Math.round(n.height() * Math.min(1f, menu.nutrients() / (float) MachineBlockEntity.MAX_NUTRIENTS));
            if (h > 0) {
                graphics.fillGradient(x + n.x(), y + n.y() + n.height() - h, x + n.x() + n.width(),
                        y + n.y() + n.height(), 0xFFB5D34F, 0xFF4E7A1E);
            }
        }
        if (kind == MachineKind.BIOMASS_EXTRACTOR) {
            MachineKind.Rect b = MachineKind.BIOMASS_BAR;
            int buffer = Math.min(menu.extra(0), BiomassExtractorBlockEntity.POINTS_PER_BIOMASS);
            int h = Math.round(b.height() * buffer / (float) BiomassExtractorBlockEntity.POINTS_PER_BIOMASS);
            if (h > 0) {
                graphics.fillGradient(x + b.x(), y + b.y() + b.height() - h, x + b.x() + b.width(),
                        y + b.y() + b.height(), 0xFFC0453A, 0xFF5A1010);
            }
        }
    }

    private int[] progressColors() {
        return switch (kind) {
            case BIOMASS_EXTRACTOR -> new int[]{0xFFD04848, 0xFF6E1414};
            case GENETIC_CENTRIFUGE -> new int[]{0xFF6FD3F0, 0xFF2A6F8A};
            case GENE_SEQUENCER -> new int[]{0xFFC79BF0, 0xFF5B2E8A};
            case BIOLOGICAL_INCUBATOR -> new int[]{0xFFA8E060, 0xFF3F7A1E};
        };
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
        switch (kind) {
            case GENETIC_CENTRIFUGE -> centrifugePreview(graphics);
            case GENE_SEQUENCER -> sequencerPreview(graphics);
            case BIOLOGICAL_INCUBATOR -> incubatorStatus(graphics);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ previsoes

    private @Nullable GeneSample sample(int slot) {
        return menu.machineItem(slot).get(ModDataComponents.GENE_SAMPLE.get());
    }

    private void centrifugePreview(GuiGraphics graphics) {
        GeneSample a = sample(0);
        GeneSample b = sample(1);
        if (a == null) {
            return;
        }
        Component line;
        if (GeneticCentrifugeBlockEntity.canProcess(a, b)) {
            String result;
            if (b != null) {
                result = GeneticCentrifugeBlockEntity.mergedPurity(a, b) + "%";
            } else {
                int lo = Math.min(GeneticCentrifugeBlockEntity.REFINE_CAP, a.purity() + GeneticCentrifugeBlockEntity.REFINE_MIN);
                int hi = Math.min(GeneticCentrifugeBlockEntity.REFINE_CAP, a.purity() + GeneticCentrifugeBlockEntity.REFINE_MAX);
                result = lo == hi ? lo + "%" : lo + "-" + hi + "%";
            }
            line = Component.literal(a.purity() + "% → ").withColor(LABEL_COLOR)
                    .append(Component.literal(result).withColor(GOOD));
        } else if (b != null) {
            line = Component.translatable("gui.atrocities.centrifuge.mismatch").withColor(BAD);
        } else {
            line = Component.translatable("gui.atrocities.centrifuge.capped", GeneticCentrifugeBlockEntity.REFINE_CAP)
                    .withColor(WARN);
        }
        graphics.drawString(font, line, 66, 63, 0xFFFFFF, false);
    }

    private void sequencerPreview(GuiGraphics graphics) {
        List<GeneSample> samples = GeneSequencerBlockEntity.samples(menu::machineItem);
        if (samples.size() < GeneSequencerBlockEntity.MIN_SAMPLES || minecraft == null || minecraft.level == null) {
            graphics.drawString(font, Component.translatable("gui.atrocities.sequencer.need_samples",
                    GeneSequencerBlockEntity.MIN_SAMPLES), 30, 60, LABEL_COLOR, false);
            return;
        }
        EmbryoGenome.sequence(samples, minecraft.level.registryAccess()).ifPresent(genome -> {
            int stabilityColor = stabilityColor(genome.stability());
            Component line = Component.translatable("gui.atrocities.sequencer.preview")
                    .withColor(LABEL_COLOR)
                    .append(Component.literal(" " + roman(genome.tier())).withColor(tierColor(genome.tier()))
                            .withStyle(ChatFormatting.BOLD))
                    .append(Component.literal(" · ").withColor(LABEL_COLOR))
                    .append(Component.literal(genome.stability() + "%").withColor(stabilityColor));
            graphics.drawString(font, line, 30, 60, 0xFFFFFF, false);
        });
    }

    private void incubatorStatus(GuiGraphics graphics) {
        int stability = menu.extra(0);
        if (stability < 0) {
            graphics.drawString(font, Component.translatable("gui.atrocities.incubator.empty"), 30, 52,
                    LABEL_COLOR, false);
            return;
        }
        graphics.drawString(font, Component.translatable("gui.atrocities.incubator.stability")
                .withColor(LABEL_COLOR)
                .append(Component.literal(" " + stability + "%").withColor(stabilityColor(stability))), 30, 52,
                0xFFFFFF, false);

        Component second;
        if (menu.extra(1) == 1) {
            boolean blink = (Util.getMillis() / 400) % 2 == 0;
            second = Component.translatable("gui.atrocities.incubator.starving")
                    .withColor(blink ? BAD : 0x8A2A2A).withStyle(ChatFormatting.BOLD);
        } else if (stability < BiologicalIncubatorBlockEntity.ABORT_THRESHOLD) {
            int risk = Math.round(BiologicalIncubatorBlockEntity.abortChance(stability) * 100);
            second = Component.translatable("gui.atrocities.incubator.abort_risk", risk).withColor(BAD);
        } else if (menu.maxProgress() > 0) {
            int seconds = (menu.maxProgress() - menu.progress()) / 20;
            second = Component.translatable("gui.atrocities.incubator.remaining",
                    String.format("%d:%02d", seconds / 60, seconds % 60)).withColor(LABEL_COLOR);
        } else {
            return;
        }
        graphics.drawString(font, second, 30, 62, 0xFFFFFF, false);
    }

    private void renderBarTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        if (kind.usesNutrients() && MachineKind.NUTRIENT_BAR.contains(mx, my)) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.atrocities.nutrients", menu.nutrients(),
                            MachineBlockEntity.MAX_NUTRIENTS).withColor(0xB5D34F),
                    Component.translatable("gui.atrocities.nutrients.hint").withStyle(ChatFormatting.DARK_GRAY)),
                    mouseX, mouseY);
        } else if (kind == MachineKind.BIOMASS_EXTRACTOR && MachineKind.BIOMASS_BAR.contains(mx, my)) {
            graphics.renderTooltip(font, Component.translatable("gui.atrocities.biomass_buffer", menu.extra(0),
                    BiomassExtractorBlockEntity.POINTS_PER_BIOMASS), mouseX, mouseY);
        } else if (kind.progressBar().contains(mx, my) && menu.maxProgress() > 0) {
            graphics.renderTooltip(font, Component.translatable("gui.atrocities.progress",
                    Math.round(menu.progressRatio() * 100)), mouseX, mouseY);
        }
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(value);
        };
    }

    private static int stabilityColor(int stability) {
        return stability >= 70 ? GOOD : stability >= BiologicalIncubatorBlockEntity.ABORT_THRESHOLD ? WARN : BAD;
    }

    private static int tierColor(int tier) {
        return switch (tier) {
            case 1 -> 0xB0B0B0;
            case 2 -> 0x7FD46B;
            case 3 -> 0xB07CE0;
            default -> 0xFF5555;
        };
    }
}
