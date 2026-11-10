package com.kmod.atrocities.client.tooltip;

import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Tooltips customizadas dos itens do Atrocities:
 * <ul>
 *   <li>Moldura na cor da raridade, fundo escuro tingido e cantos ornamentados.</li>
 *   <li>Linha separadora sob o nome e uma fileira de selos (raridade, categoria e tier).</li>
 *   <li>Lendario: moldura cintilante e brilhos piscando. Artefato: moldura pulsando e sangue escorrendo.</li>
 * </ul>
 * A moldura e desenhada pelo componente de selos, que roda depois do fundo vanilla.
 */
public final class ModTooltips {

    private ModTooltips() {
    }

    /** Caixa da tooltip sendo desenhada agora (preenchida no evento de cor, consumida pelos selos). */
    @Nullable
    private static Frame frame;

    private record Frame(int x, int y, int width, int height, TooltipStyle style) {
    }

    /** Dado do componente de selos inserido logo abaixo do nome. */
    public record Badges(TooltipStyle style) implements TooltipComponent {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterClientTooltipComponentFactoriesEvent.class,
                event -> event.register(Badges.class, BadgeRow::new));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ItemTooltipEvent.class, ModTooltips::onTooltip);
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.GatherComponents.class, ModTooltips::onGather);
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Color.class, ModTooltips::onColor);
    }

    // ------------------------------------------------------------------ eventos

    /** Nome animado para as raridades altas (as linhas sao refeitas a cada quadro). */
    private static void onTooltip(ItemTooltipEvent event) {
        TooltipStyle style = TooltipStyle.of(event.getItemStack());
        if (style == null || !style.rarity().animated() || event.getToolTip().isEmpty()) {
            return;
        }
        int color = switch (style.rarity()) {
            case LEGENDARY -> lerp(0xF2A51A, 0xFFE58A, wave(900, 0));
            default -> lerp(0xC0141E, 0xFF5A4A, wave(650, 0));
        };
        Component name = event.getToolTip().get(0);
        event.getToolTip().set(0, name.copy().withColor(color));
    }

    private static void onGather(RenderTooltipEvent.GatherComponents event) {
        TooltipStyle style = TooltipStyle.of(event.getItemStack());
        if (style == null) {
            return;
        }
        List<Either<net.minecraft.network.chat.FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        elements.add(Math.min(1, elements.size()), Either.right(new Badges(style)));
    }

    private static void onColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        TooltipStyle style = TooltipStyle.of(stack);
        if (style == null || event.getComponents().stream().noneMatch(BadgeRow.class::isInstance)) {
            frame = null;
            return;
        }
        int color = style.rarity().color();
        int top = color;
        if (style.rarity() == TooltipStyle.Rarity.ARTIFACT) {
            top = lerp(color, 0xFF7A6A, wave(650, 0));
        } else if (style.rarity() == TooltipStyle.Rarity.LEGENDARY) {
            top = lerp(color, 0xFFE58A, wave(900, 0));
        }
        event.setBorderStart(0xFF000000 | top);
        event.setBorderEnd(0xFF000000 | darken(color, 0.45f));
        event.setBackgroundStart(0xF2000000 | lerp(0x0C0810, color, 0.16f));
        event.setBackgroundEnd(0xF2000000 | 0x050306);

        int width = 0;
        int height = event.getComponents().size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : event.getComponents()) {
            width = Math.max(width, component.getWidth(event.getFont()));
            height += component.getHeight();
        }
        frame = new Frame(event.getX(), event.getY(), width, height, style);
    }

    // ------------------------------------------------------------------ componente de selos

    private static final int BADGE_HEIGHT = 11;
    private static final int BADGE_GAP = 3;
    private static final int CATEGORY_COLOR = 0x3A4358;

    /** Fileira de selos; tambem desenha a moldura ornamentada da tooltip. */
    public static final class BadgeRow implements ClientTooltipComponent {

        private final TooltipStyle style;
        private final List<Badge> badges = new ArrayList<>();

        private record Badge(Component label, int color) {
        }

        BadgeRow(Badges data) {
            this.style = data.style();
            badges.add(new Badge(Component.translatable(style.rarity().key()), style.rarity().color()));
            badges.add(new Badge(Component.translatable(style.category().key()), CATEGORY_COLOR));
            if (style.tier() > 0) {
                badges.add(new Badge(Component.literal(roman(style.tier())), TooltipStyle.tierColor(style.tier())));
            }
        }

        @Override
        public int getHeight() {
            return BADGE_HEIGHT + 3;
        }

        @Override
        public int getWidth(Font font) {
            int width = 0;
            for (Badge badge : badges) {
                width += font.width(badge.label()) + 6 + BADGE_GAP;
            }
            return width - BADGE_GAP;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            Frame current = frame;
            if (current != null && current.style() == style) {
                drawFrame(graphics, current);
            }
            int cursor = x;
            for (Badge badge : badges) {
                int width = font.width(badge.label()) + 6;
                drawPill(graphics, cursor, y + 1, width, badge.color());
                graphics.drawString(font, badge.label(), cursor + 3, y + 3, 0xFFFFFF, true);
                cursor += width + BADGE_GAP;
            }
        }

        /** Selo em relevo: topo claro, base escura e sombra deslocada. */
        private static void drawPill(GuiGraphics graphics, int x, int y, int width, int color) {
            int h = BADGE_HEIGHT;
            graphics.fill(x + 1, y + 1, x + width + 1, y + h + 1, 0xC0000000);
            graphics.fill(x + 1, y, x + width - 1, y + h, 0xFF000000 | color);
            graphics.fill(x, y + 1, x + width, y + h - 1, 0xFF000000 | color);
            graphics.fill(x + 1, y, x + width - 1, y + 1, 0xFF000000 | lighten(color, 0.35f));
            graphics.fill(x + 1, y + h - 2, x + width - 1, y + h, 0xFF000000 | darken(color, 0.35f));
        }

        private static void drawFrame(GuiGraphics graphics, Frame f) {
            int color = 0xFF000000 | f.style().rarity().color();
            int x0 = f.x() - 3;
            int y0 = f.y() - 3;
            int x1 = f.x() + f.width() + 3;
            int y1 = f.y() + f.height() + 3;
            long time = Util.getMillis();

            // Linha separadora sob o nome: forte no centro, sumindo nas pontas.
            int sepY = f.y() + 10;
            int segments = 16;
            for (int i = 0; i < segments; i++) {
                float t = Math.abs((i + 0.5f) / segments - 0.5f) * 2f;
                int alpha = (int) (200 * (1f - t * t));
                int sx0 = f.x() + f.width() * i / segments;
                int sx1 = f.x() + f.width() * (i + 1) / segments;
                graphics.fill(sx0, sepY, sx1, sepY + 1, (alpha << 24) | f.style().rarity().color());
            }

            // Cantos em L por fora da moldura, com um ponto interno.
            corner(graphics, x0 - 2, y0 - 2, 1, 1, color);
            corner(graphics, x1 + 1, y0 - 2, -1, 1, color);
            corner(graphics, x0 - 2, y1 + 1, 1, -1, color);
            corner(graphics, x1 + 1, y1 + 1, -1, -1, color);

            switch (f.style().rarity()) {
                case LEGENDARY -> sparkles(graphics, x0, y0, x1, y1, time);
                case ARTIFACT -> drips(graphics, x0, y0, x1, y1, time);
                default -> {
                }
            }
        }

        private static void corner(GuiGraphics graphics, int x, int y, int dx, int dy, int color) {
            int length = 5;
            for (int i = 0; i < length; i++) {
                graphics.fill(x + i * dx, y, x + i * dx + 1, y + 1, color);
                graphics.fill(x, y + i * dy, x + 1, y + i * dy + 1, color);
            }
            graphics.fill(x + 2 * dx, y + 2 * dy, x + 2 * dx + 1, y + 2 * dy + 1, color);
        }

        /** Brilhos em cruz que acendem e apagam em tempos diferentes. */
        private static void sparkles(GuiGraphics graphics, int x0, int y0, int x1, int y1, long time) {
            int[][] points = {{x0 + 6, y0 + 5}, {x1 - 7, y1 - 6}, {x1 - 12, y0 + 4}, {x0 + 10, y1 - 5}};
            for (int i = 0; i < points.length; i++) {
                float glow = Math.max(0f, Mth.sin((time + i * 700L) / 420f));
                if (glow < 0.15f) {
                    continue;
                }
                int alpha = (int) (255 * glow);
                int c = (alpha << 24) | 0xFFF1B0;
                int px = points[i][0];
                int py = points[i][1];
                int arm = glow > 0.7f ? 2 : 1;
                graphics.fill(px - arm, py, px + arm + 1, py + 1, c);
                graphics.fill(px, py - arm, px + 1, py + arm + 1, c);
            }
        }

        /** Gotas de sangue escorrendo da borda superior. */
        private static void drips(GuiGraphics graphics, int x0, int y0, int x1, int y1, long time) {
            int width = x1 - x0;
            int[] offsets = {width / 5, width / 2 + 3, width * 4 / 5};
            for (int i = 0; i < offsets.length; i++) {
                long cycle = 2600L + i * 530L;
                float t = ((time + i * 977L) % cycle) / (float) cycle;
                int maxLength = 9 + i * 2;
                int length = (int) (maxLength * Math.min(1f, t * 1.6f));
                int x = x0 + offsets[i];
                int top = y0 + 1;
                for (int j = 0; j < length; j++) {
                    int alpha = (int) (220 * (1f - j / (float) (maxLength + 4)));
                    graphics.fill(x, top + j, x + 1, top + j + 1, (alpha << 24) | 0x9E0F1F);
                }
                // A gota se solta e cai no fim do ciclo.
                if (t > 0.62f) {
                    int fall = (int) ((t - 0.62f) / 0.38f * (y1 - y0 - maxLength - 6));
                    int dropY = top + maxLength + fall;
                    int alpha = (int) (230 * (1f - (t - 0.62f) / 0.38f));
                    graphics.fill(x, dropY, x + 1, dropY + 2, (alpha << 24) | 0xC21A2C);
                } else if (length > 0) {
                    graphics.fill(x - 1, top + length, x + 2, top + length + 1, 0xCC9E0F1F);
                }
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static float wave(long periodMs, long offset) {
        return (Mth.sin((Util.getMillis() + offset) / (float) periodMs * Mth.TWO_PI) + 1f) * 0.5f;
    }

    private static int lerp(int from, int to, float t) {
        return FastColor.ARGB32.lerp(t, from, to) & 0xFFFFFF;
    }

    private static int darken(int rgb, float amount) {
        return lerp(rgb, 0x000000, amount);
    }

    private static int lighten(int rgb, float amount) {
        return lerp(rgb, 0xFFFFFF, amount);
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }
}
