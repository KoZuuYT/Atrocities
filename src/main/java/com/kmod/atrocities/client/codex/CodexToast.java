package com.kmod.atrocities.client.codex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Aviso "Códex atualizado": moldura de carne com veia pulsante na cor da descoberta. */
public class CodexToast implements Toast {

    private static final long DURATION = 5000L;

    private final Component title;
    private final Component subject;
    private final ItemStack icon;
    private final int color;

    public CodexToast(Component title, Component subject, ItemStack icon, int color) {
        this.title = title;
        this.subject = subject;
        this.icon = icon;
        this.color = color;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent component, long time) {
        Font font = Minecraft.getInstance().font;
        int w = width();
        int h = height();
        g.fill(0, 0, w, h, 0xF0140A0C);
        g.fill(1, 1, w - 1, h - 1, 0xF0241014);
        float pulse = 0.5f + 0.5f * Mth.sin(time / 180f);
        int border = CodexStyle.withAlpha(color, (int) (120 + 120 * pulse));
        g.renderOutline(0, 0, w, h, border);
        // Veia que corre pela borda inferior enquanto o aviso está na tela.
        int vein = (int) (Mth.clamp(time / (float) DURATION, 0f, 1f) * (w - 4));
        g.fill(2, h - 3, 2 + vein, h - 2, CodexStyle.withAlpha(color, 200));
        g.fill(5, 7, 25, 27, 0x60000000);
        g.renderOutline(5, 7, 20, 20, CodexStyle.withAlpha(color, 140));
        g.renderItem(icon, 7, 9);
        g.drawString(font, title, 30, 7, color, false);
        g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(subject, w - 34)),
                30, 18, 0xE8DCCB, false);
        return time >= DURATION * component.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
