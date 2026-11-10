package com.kmod.atrocities.client.codex;

import com.kmod.atrocities.Atrocities;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Paleta e primitivas de desenho do Códex. */
public final class CodexStyle {

    public static final ResourceLocation BOOK =
            ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "textures/gui/codex/book.png");
    public static final int BOOK_W = 352;
    public static final int BOOK_H = 216;
    public static final int TEX_W = 512;
    public static final int TEX_H = 256;

    /** Texto principal (tinta ferrosa sobre pergaminho). */
    public static final int INK = 0x2E1C16;
    /** Texto secundário. */
    public static final int INK_FADED = 0x6B5546;
    /** Títulos e destaques (sangue seco). */
    public static final int BLOOD = 0x7A0E14;
    /** Realce de seleção. */
    public static final int HIGHLIGHT = 0xB0141F;

    private CodexStyle() {
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int darken(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    public static int lighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    /** Linha horizontal tracejada (separador de seções). */
    public static void rule(GuiGraphics g, int x, int y, int w, int color) {
        for (int i = 0; i < w; i += 4) {
            g.fill(x + i, y, x + Math.min(w, i + 2), y + 1, color);
        }
    }

    /** Seta triangular em pixels, para a esquerda ({@code dir < 0}) ou direita. */
    public static void arrow(GuiGraphics g, int x, int y, int dir, int color) {
        for (int i = 0; i < 5; i++) {
            int len = 9 - i * 2;
            int col = dir < 0 ? x + 4 - i : x + i;
            g.fill(col, y + i, col + 1, y + i + len, color);
        }
    }

    /** Texto centralizado em {@code centerX}, encolhido para caber em {@code maxWidth}. */
    public static void fit(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text,
                           float centerX, float y, int maxWidth, int color, float scale) {
        int width = font.width(text);
        float s = Math.min(scale, maxWidth / (float) Math.max(1, width));
        g.pose().pushPose();
        g.pose().translate(centerX, y + (1 - s / scale) * 4 * scale, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, -width / 2, 0, color, false);
        g.pose().popPose();
    }

    /** Moldura de "espécime": borda dupla com cantos reforçados. */
    public static void specimenFrame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, withAlpha(color, 255));
        g.renderOutline(x - 3, y - 3, w + 6, h + 6, withAlpha(color, 110));
        int c = 5;
        int col = withAlpha(darken(color, 0.6f), 255);
        g.fill(x - 3, y - 3, x - 3 + c, y - 1, col);
        g.fill(x - 3, y - 3, x - 1, y - 3 + c, col);
        g.fill(x + w + 3 - c, y - 3, x + w + 3, y - 1, col);
        g.fill(x + w + 1, y - 3, x + w + 3, y - 3 + c, col);
        g.fill(x - 3, y + h + 1, x - 3 + c, y + h + 3, col);
        g.fill(x - 3, y + h + 3 - c, x - 1, y + h + 3, col);
        g.fill(x + w + 3 - c, y + h + 1, x + w + 3, y + h + 3, col);
        g.fill(x + w + 1, y + h + 3 - c, x + w + 3, y + h + 3, col);
    }

    /** Tarja preta de texto censurado, com leve irregularidade. */
    public static void redaction(GuiGraphics g, int x, int y, int w, long seed) {
        int jitter = (int) (Math.abs(seed * 31L) % 5);
        g.fill(x, y + 1, x + w - jitter, y + 8, 0xE0140C0C);
        g.fill(x + 2, y, x + w - jitter - 3, y + 1, 0x90140C0C);
    }
}
