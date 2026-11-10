package com.kmod.atrocities.client.versiongate;

import com.kmod.atrocities.versiongate.ModVersion;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Tela de atualizacao do Atrocities: explica a diferenca de versao e oferece
 * download direto do GitHub, a pagina da Release e, depois do download, sair do jogo para concluir.
 */
public class ModUpdateScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int RED = 0xFFB3202A;
    private static final int BAR_BG = 0xFF2A0E10;

    @Nullable
    private final Screen lastScreen;
    private final UpdateInfo info;
    private MultiLineLabel message = MultiLineLabel.EMPTY;
    private Button installButton;
    private Button quitButton;

    public ModUpdateScreen(@Nullable Screen lastScreen, UpdateInfo info) {
        super(Component.translatable("screen.atrocities.update.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        this.lastScreen = lastScreen;
        this.info = info;
    }

    @Override
    protected void init() {
        String current = ModVersion.current();
        Component body = switch (info.reason()) {
            case SERVER_REQUIRED -> Component.translatable("screen.atrocities.update.server_required", info.version(), current);
            case SERVER_MISMATCH -> Component.translatable("screen.atrocities.update.server_mismatch", info.version(), current);
            case NEWER_RELEASE -> Component.translatable("screen.atrocities.update.newer_release", info.version(), current);
        };
        message = MultiLineLabel.create(font, body, width - 60);

        int x = (width - BUTTON_WIDTH) / 2;
        int y = height / 2 + 10;

        installButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.atrocities.update.install", info.version()),
                        b -> ModUpdateInstaller.get().start(info))
                .bounds(x, y, BUTTON_WIDTH, 20).build());
        quitButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.atrocities.update.quit"),
                        b -> minecraft.stop())
                .bounds(x, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.atrocities.update.open_page"),
                        b -> Util.getPlatform().openUri(info.pageUrl()))
                .bounds(x, y + 24, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable(info.reason() == UpdateInfo.Reason.SERVER_REQUIRED ? "gui.back" : "screen.atrocities.update.later"),
                        b -> onClose())
                .bounds(x, y + 48, BUTTON_WIDTH, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        ModUpdateInstaller installer = ModUpdateInstaller.get();
        boolean ready = installer.state() == ModUpdateInstaller.State.READY && installer.stagedVersion().equals(info.version());
        installButton.visible = !ready;
        installButton.active = ModUpdateInstaller.canSelfUpdate() && installer.state() != ModUpdateInstaller.State.DOWNLOADING;
        quitButton.visible = ready;
    }

    @Override
    public void tick() {
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 90, 0xFFFFFF);
        message.renderCentered(graphics, width / 2, height / 2 - 70);
        renderStatus(graphics);
    }

    private void renderStatus(GuiGraphics graphics) {
        ModUpdateInstaller installer = ModUpdateInstaller.get();
        int y = height / 2 - 8;
        Component status = switch (installer.state()) {
            case IDLE -> ModUpdateInstaller.canSelfUpdate()
                    ? Component.empty()
                    : Component.translatable("screen.atrocities.update.dev_env").withStyle(ChatFormatting.GRAY);
            case DOWNLOADING -> Component.translatable("screen.atrocities.update.downloading").withStyle(ChatFormatting.YELLOW);
            case READY -> Component.translatable("screen.atrocities.update.ready", installer.stagedVersion()).withStyle(ChatFormatting.GREEN);
            case FAILED -> Component.translatable("screen.atrocities.update.failed", installer.error()).withStyle(ChatFormatting.RED);
        };
        graphics.drawCenteredString(font, status, width / 2, y, 0xFFFFFF);

        if (installer.state() == ModUpdateInstaller.State.DOWNLOADING) {
            int barWidth = BUTTON_WIDTH;
            int barX = (width - barWidth) / 2;
            int barY = y - 6;
            float progress = installer.progress();
            graphics.fill(barX, barY, barX + barWidth, barY + 3, BAR_BG);
            if (progress >= 0F) {
                graphics.fill(barX, barY, barX + Math.round(barWidth * progress), barY + 3, RED);
            } else {
                // Tamanho desconhecido: faixa pulsante.
                int pulse = (int) ((Util.getMillis() / 6) % barWidth);
                graphics.fill(barX + pulse, barY, Math.min(barX + barWidth, barX + pulse + 40), barY + 3, RED);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(lastScreen);
    }
}
