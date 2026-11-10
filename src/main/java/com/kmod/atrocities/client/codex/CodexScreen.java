package com.kmod.atrocities.client.codex;

import com.kmod.atrocities.codex.CodexProgress;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * O livro Códex Atrocitas: índice, capítulos, verbetes paginados e as fichas dos chefes
 * (visualizador 3D animado à esquerda, abas de ficha, fases, golpes e espólios à direita).
 * Lembra a última página aberta.
 */
public class CodexScreen extends Screen {

    private static final int LEFT_X = 22;
    private static final int RIGHT_X = 186;
    private static final int PAGE_Y = 18;
    private static final int PAGE_W = 144;
    private static final int PAGE_H = 172;
    private static final int FOOTER_Y = 196;
    private static final int VIEWER_H = 116;
    private static final int TAB_H = 14;

    private enum View { INDEX, CHAPTER, ENTRY }

    private enum BossTab {
        PROFILE("profile"), PHASES("phases"), ATTACKS("attacks"), DROPS("drops");

        private final String key;

        BossTab(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable("codex.atrocities.tab." + key);
        }
    }

    // Última posição de leitura (sobrevive ao fechar e reabrir o livro).
    private static View lastView = View.INDEX;
    @Nullable
    private static ResourceLocation lastChapter;
    @Nullable
    private static ResourceLocation lastEntry;
    private static int lastSpread;
    private static BossTab lastTab = BossTab.PROFILE;

    private View view;
    @Nullable
    private CodexData.Chapter chapter;
    @Nullable
    private CodexData.Entry entry;
    private int spread;
    private BossTab tab;

    private int left;
    private int top;
    private int ticks;
    private int turnTicks;
    private CodexProgress builtFor = ClientCodex.progress();
    private List<List<CodexLayout.Block>> pages = List.of();
    private List<CodexLayout.Block> tabBlocks = List.of();
    private float scroll;
    @Nullable
    private BossViewer viewer;
    private boolean draggingViewer;
    private final List<CodexLayout.Hotspot> hotspots = new ArrayList<>();

    /** Abre o livro direto num capítulo ou verbete (null = índice / lista do capítulo). */
    static CodexScreen openAt(@Nullable ResourceLocation chapterId, @Nullable ResourceLocation entryId) {
        lastChapter = chapterId;
        lastEntry = entryId;
        lastView = entryId != null ? View.ENTRY : chapterId != null ? View.CHAPTER : View.INDEX;
        lastSpread = 0;
        lastTab = BossTab.PROFILE;
        return new CodexScreen();
    }

    public CodexScreen() {
        super(Component.translatable("item.atrocities.codex_atrocitas"));
        CodexData.Book book = CodexData.book();
        this.view = lastView;
        this.chapter = lastChapter == null ? null : book.chapter(lastChapter).orElse(null);
        this.entry = lastEntry == null ? null : book.entry(lastEntry).orElse(null);
        this.spread = lastSpread;
        this.tab = lastTab;
        if (view == View.CHAPTER && chapter == null || view == View.ENTRY && (entry == null || chapter == null
                || !entry.unlocked(ClientCodex.progress()) && entry.boss() == null)) {
            view = View.INDEX;
        }
    }

    @Override
    protected void init() {
        left = (width - CodexStyle.BOOK_W) / 2;
        top = (height - CodexStyle.BOOK_H) / 2;
        rebuild(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ navegação

    private void openIndex() {
        view = View.INDEX;
        entry = null;
        turn();
    }

    private void openChapter(CodexData.Chapter target) {
        view = View.CHAPTER;
        chapter = target;
        entry = null;
        turn();
    }

    private void openEntry(CodexData.Entry target) {
        view = View.ENTRY;
        entry = target;
        spread = 0;
        scroll = 0;
        tab = BossTab.PROFILE;
        rebuild(true);
        turn();
    }

    private void back() {
        if (view == View.ENTRY && chapter != null) {
            openChapter(chapter);
        } else if (view != View.INDEX) {
            openIndex();
        }
    }

    private void turnSpread(int delta) {
        int next = Mth.clamp(spread + delta, 0, Math.max(0, (pages.size() - 1) / 2));
        if (next != spread) {
            spread = next;
            turn();
        }
    }

    private void selectTab(BossTab next) {
        if (next != tab) {
            tab = next;
            scroll = 0;
            rebuildTab();
            click(1.3f);
        }
    }

    private void turn() {
        turnTicks = 5;
        draggingViewer = false;
        remember();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
    }

    private void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25f));
    }

    private void remember() {
        lastView = view;
        lastChapter = chapter == null ? null : chapter.id();
        lastEntry = entry == null ? null : entry.id();
        lastSpread = spread;
        lastTab = tab;
    }

    /** Remonta páginas e visualizador (verbete novo ou progresso atualizado pelo servidor). */
    private void rebuild(boolean fresh) {
        builtFor = ClientCodex.progress();
        viewer = null;
        pages = List.of();
        tabBlocks = List.of();
        if (view != View.ENTRY || entry == null) {
            return;
        }
        if (entry.boss() != null) {
            boolean locked = !entry.unlocked(builtFor);
            viewer = new BossViewer(entry.boss(), locked);
            if (!locked && !ClientCodex.revealed(entry.id())) {
                viewer.startReveal();
                ClientCodex.markRevealed(entry.id());
            }
            rebuildTab();
        } else {
            pages = CodexLayout.paginate(CodexLayout.build(entry, font, PAGE_W, builtFor), PAGE_H);
            spread = Mth.clamp(spread, 0, Math.max(0, (pages.size() - 1) / 2));
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (turnTicks > 0) {
            turnTicks--;
        }
        if (viewer != null) {
            viewer.tick();
        }
        if (ClientCodex.progress() != builtFor) {
            boolean wasLocked = entry != null && !entry.unlock().test(builtFor);
            rebuild(false);
            if (wasLocked && entry != null && entry.unlocked(builtFor) && viewer != null) {
                viewer.startReveal();
                ClientCodex.markRevealed(entry.id());
            }
        }
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hotspots.clear();
        float time = ticks + partialTick;
        CodexLayout.Ctx ctx = new CodexLayout.Ctx(g, font, mouseX, mouseY, time, hotspots);

        float open = Mth.clamp((ticks + partialTick) / 6f, 0f, 1f);
        float ease = 1f - (1f - open) * (1f - open);
        g.pose().pushPose();
        g.pose().translate(width / 2f, height / 2f, 0);
        g.pose().scale(0.94f + 0.06f * ease, 0.94f + 0.06f * ease, 1f);
        g.pose().translate(-width / 2f, -height / 2f, 0);

        renderRibbons(ctx);
        g.blit(CodexStyle.BOOK, left, top, 0, 0, CodexStyle.BOOK_W, CodexStyle.BOOK_H, CodexStyle.TEX_W, CodexStyle.TEX_H);

        switch (view) {
            case INDEX -> renderIndex(ctx);
            case CHAPTER -> renderChapter(ctx);
            case ENTRY -> {
                if (entry != null && entry.boss() != null) {
                    renderBoss(ctx);
                } else {
                    renderEntry(ctx);
                }
            }
        }

        if (turnTicks > 0) {
            int alpha = (int) (turnTicks / 5f * 200);
            g.pose().pushPose();
            g.pose().translate(0, 0, 450);
            g.fill(left + LEFT_X - 4, top + PAGE_Y - 4, left + LEFT_X + PAGE_W + 4, top + FOOTER_Y + 10,
                    CodexStyle.withAlpha(0xD9C9A6, alpha));
            g.fill(left + RIGHT_X - 4, top + PAGE_Y - 4, left + RIGHT_X + PAGE_W + 4, top + FOOTER_Y + 10,
                    CodexStyle.withAlpha(0xD9C9A6, alpha));
            g.pose().popPose();
        }
        g.pose().popPose();

        if (ctx.hoverStack != null && !ctx.hoverStack.isEmpty()) {
            g.renderTooltip(font, ctx.hoverStack, mouseX, mouseY);
        } else if (ctx.hoverText != null) {
            g.renderComponentTooltip(font, ctx.hoverText, mouseX, mouseY);
        }
    }

    /** Marcadores de capítulo presos na borda direita do livro. */
    private void renderRibbons(CodexLayout.Ctx ctx) {
        GuiGraphics g = ctx.g;
        List<CodexData.Chapter> chapters = CodexData.book().chapters();
        int x = left + CodexStyle.BOOK_W - 10;
        for (int i = -1; i < chapters.size(); i++) {
            int y = top + 14 + (i + 1) * 22;
            CodexData.Chapter target = i < 0 ? null : chapters.get(i);
            boolean selected = i < 0 ? view == View.INDEX : chapter != null && chapter.id().equals(target.id()) && view != View.INDEX;
            boolean hovered = ctx.hovered(x, y, 34, 19);
            int length = selected ? 34 : hovered ? 31 : 28;
            int color = target == null ? 0x3A2A22 : target.color();
            g.fill(x, y, x + length, y + 19, CodexStyle.withAlpha(CodexStyle.darken(color, 0.55f), 255));
            g.fill(x, y + 1, x + length - 1, y + 18, CodexStyle.withAlpha(color, 255));
            g.fill(x, y + 1, x + length - 1, y + 3, CodexStyle.withAlpha(CodexStyle.lighten(color, 0.25f), 255));
            // Corte em "V" na ponta da fita.
            g.fill(x + length - 4, y + 7, x + length, y + 12, CodexStyle.withAlpha(CodexStyle.darken(color, 0.4f), 255));
            g.renderItem(target == null ? new ItemStack(ModItems.CODEX.get()) : target.icon(), x + length - 22, y + 1);
            if (hovered) {
                ctx.hoverText = List.of(target == null ? Component.translatable("codex.atrocities.index")
                        : Component.translatable(target.title()).withColor(CodexStyle.lighten(target.color(), 0.4f)));
            }
            ctx.click(x, y, length, 19, () -> {
                if (target == null) {
                    openIndex();
                } else {
                    openChapter(target);
                }
            });
        }
    }

    private void renderIndex(CodexLayout.Ctx ctx) {
        GuiGraphics g = ctx.g;
        int lx = left + LEFT_X;
        int y = top + PAGE_Y;
        // Emblema pulsante.
        float pulse = 0.5f + 0.5f * Mth.sin(ctx.time * 0.08f);
        g.pose().pushPose();
        g.pose().translate(lx + PAGE_W / 2f, y + 4, 0);
        g.pose().scale(4f, 4f, 1f);
        Component emblem = Component.literal("☣");
        g.drawString(font, emblem, -font.width(emblem) / 2, 0,
                CodexStyle.withAlpha(CodexStyle.BLOOD, (int) (170 + 85 * pulse)), false);
        g.pose().popPose();
        Component title = Component.translatable("codex.atrocities.title").withStyle(Style.EMPTY.withBold(true));
        g.drawString(font, title, lx + (PAGE_W - font.width(title)) / 2, y + 44, CodexStyle.BLOOD, false);
        Component subtitle = Component.translatable("codex.atrocities.subtitle");
        CodexStyle.fit(g, font, subtitle, lx + PAGE_W / 2f, y + 56, PAGE_W, CodexStyle.INK_FADED, 1f);
        CodexStyle.rule(g, lx + 10, y + 68, PAGE_W - 20, CodexStyle.withAlpha(CodexStyle.BLOOD, 140));
        List<CodexLayout.Block> intro = new ArrayList<>();
        CodexLayout.text(intro, font, Component.translatable("codex.atrocities.index.intro"), PAGE_W, CodexStyle.INK);
        CodexLayout.renderAll(ctx, intro, lx, y + 76, PAGE_W);

        int rx = left + RIGHT_X;
        new CodexLayout.Heading(Component.translatable("codex.atrocities.chapters"), CodexStyle.BLOOD).render(ctx, rx, y, PAGE_W);
        int ry = y + 17;
        CodexProgress progress = ClientCodex.progress();
        for (CodexData.Chapter target : CodexData.book().chapters()) {
            List<CodexData.Entry> entries = CodexData.book().entries(target);
            long open = entries.stream().filter(e -> e.unlocked(progress)).count();
            boolean hovered = ctx.hovered(rx, ry, PAGE_W, 15);
            if (hovered) {
                g.fill(rx - 2, ry, rx + PAGE_W + 2, ry + 15, CodexStyle.withAlpha(target.color(), 50));
            }
            g.pose().pushPose();
            g.pose().translate(rx, ry + 1, 0);
            g.pose().scale(0.8125f, 0.8125f, 1f);
            g.renderItem(target.icon(), 0, 0);
            g.pose().popPose();
            g.drawString(font, Component.translatable(target.title()), rx + 17, ry + 4,
                    hovered ? CodexStyle.HIGHLIGHT : CodexStyle.INK, false);
            String count = open + "/" + entries.size();
            g.drawString(font, count, rx + PAGE_W - font.width(count), ry + 4, CodexStyle.INK_FADED, false);
            ctx.click(rx - 2, ry, PAGE_W + 4, 15, () -> openChapter(target));
            ry += 15;
        }

        ry += 3;
        new CodexLayout.Heading(Component.translatable("codex.atrocities.progress"), CodexStyle.BLOOD).render(ctx, rx, ry, PAGE_W);
        ry += 16;
        List<CodexData.Entry> bosses = CodexData.book().entries().values().stream().flatMap(List::stream)
                .filter(e -> e.boss() != null).toList();
        long seen = bosses.stream().filter(e -> progress.hasEncountered(id(e))).count();
        long slain = bosses.stream().filter(e -> progress.hasDefeated(id(e))).count();
        Minecraft mc = Minecraft.getInstance();
        int traits = mc.level == null ? 0 : mc.level.registryAccess().registry(GeneticsRegistries.TRAIT)
                .map(r -> r.keySet().size()).orElse(0);
        long known = mc.level == null ? 0 : mc.level.registryAccess().registry(GeneticsRegistries.TRAIT)
                .map(r -> r.keySet().stream().filter(progress::knowsTrait).count()).orElse(0L);
        ry = progressBar(g, rx, ry, Component.translatable("codex.atrocities.progress.catalogued"), seen, bosses.size(), 0xB0141F);
        ry = progressBar(g, rx, ry, Component.translatable("codex.atrocities.progress.slain"), slain, bosses.size(), 0xC08A1A);
        progressBar(g, rx, ry, Component.translatable("codex.atrocities.progress.traits"), known, traits, 0x4C9A3A);
    }

    private int progressBar(GuiGraphics g, int x, int y, Component label, long value, long max, int color) {
        g.drawString(font, label, x, y, CodexStyle.INK, false);
        String count = value + "/" + max;
        g.drawString(font, count, x + PAGE_W - font.width(count), y, CodexStyle.INK_FADED, false);
        g.fill(x, y + 9, x + PAGE_W, y + 12, 0x40301818);
        int fill = max <= 0 ? 0 : (int) (PAGE_W * value / (double) max);
        g.fill(x, y + 9, x + fill, y + 12, CodexStyle.withAlpha(color, 255));
        g.fill(x, y + 9, x + fill, y + 10, CodexStyle.withAlpha(CodexStyle.lighten(color, 0.4f), 255));
        return y + 15;
    }

    private void renderChapter(CodexLayout.Ctx ctx) {
        if (chapter == null) {
            return;
        }
        GuiGraphics g = ctx.g;
        int lx = left + LEFT_X;
        int y = top + PAGE_Y;
        List<CodexLayout.Block> blocks = new ArrayList<>();
        blocks.add(new CodexLayout.Title(chapter.icon(), Component.translatable(chapter.title()), chapter.color()));
        if (!chapter.description().isEmpty()) {
            CodexLayout.text(blocks, font, Component.translatable(chapter.description()), PAGE_W, CodexStyle.INK);
        }
        CodexLayout.renderAll(ctx, blocks, lx, y, PAGE_W);

        int rx = left + RIGHT_X;
        new CodexLayout.Heading(Component.translatable("codex.atrocities.entries"), chapter.color()).render(ctx, rx, y, PAGE_W);
        int ry = y + 17;
        CodexProgress progress = ClientCodex.progress();
        for (CodexData.Entry target : CodexData.book().entries(chapter)) {
            boolean unlocked = target.unlocked(progress);
            boolean hovered = ctx.hovered(rx, ry, PAGE_W, 18);
            boolean clickable = unlocked || target.boss() != null;
            if (hovered && clickable) {
                g.fill(rx - 2, ry, rx + PAGE_W + 2, ry + 18, CodexStyle.withAlpha(chapter.color(), 50));
            }
            if (unlocked) {
                g.renderItem(target.icon(), rx, ry + 1);
                g.drawString(font, font.substrByWidth(Component.translatable(target.title()), PAGE_W - 30).getString(),
                        rx + 20, ry + 5, hovered ? CodexStyle.HIGHLIGHT : CodexStyle.INK, false);
                if (target.boss() != null && progress.hasDefeated(id(target))) {
                    drawSeal(g, rx + PAGE_W - 8, ry + 5, 0xC08A1A);
                } else if (target.boss() != null && !ClientCodex.revealed(target.id())) {
                    drawSeal(g, rx + PAGE_W - 8, ry + 5, 0xE3262F);
                }
            } else {
                drawLock(g, rx + 3, ry + 3);
                g.drawString(font, "???", rx + 20, ry + 5, CodexStyle.INK_FADED, false);
                if (hovered) {
                    ctx.hoverText = List.of(Component.translatable(target.unlock().hintKey(), unlockSubject(target.unlock()))
                            .withColor(0xD8C8B0));
                }
            }
            if (clickable) {
                ctx.click(rx - 2, ry, PAGE_W + 4, 18, () -> openEntry(target));
            }
            ry += 19;
        }
    }

    private Component unlockSubject(CodexData.Unlock unlock) {
        if (unlock.target() == null) {
            return Component.empty();
        }
        return unlock.kind() == CodexData.Unlock.Kind.TRAIT ? ClientCodex.traitName(unlock.target())
                : ClientCodex.bossName(unlock.target());
    }

    private void renderEntry(CodexLayout.Ctx ctx) {
        GuiGraphics g = ctx.g;
        int first = spread * 2;
        if (first < pages.size()) {
            CodexLayout.renderAll(ctx, pages.get(first), left + LEFT_X, top + PAGE_Y, PAGE_W);
        }
        if (first + 1 < pages.size()) {
            CodexLayout.renderAll(ctx, pages.get(first + 1), left + RIGHT_X, top + PAGE_Y, PAGE_W);
        }
        int total = pages.size();
        pageNumber(g, first + 1, total, true);
        if (first + 2 <= total) {
            pageNumber(g, first + 2, total, false);
        }
        if (spread > 0) {
            navArrow(ctx, left + LEFT_X, top + FOOTER_Y - 1, -1, () -> turnSpread(-1));
        }
        if (first + 2 < total) {
            navArrow(ctx, left + RIGHT_X + PAGE_W - 6, top + FOOTER_Y - 1, 1, () -> turnSpread(1));
        }
        backLink(ctx);
    }

    // ------------------------------------------------------------------ ficha de chefe

    private void renderBoss(CodexLayout.Ctx ctx) {
        if (entry == null || entry.boss() == null || viewer == null) {
            return;
        }
        GuiGraphics g = ctx.g;
        CodexData.Boss boss = entry.boss();
        boolean locked = !entry.unlocked(builtFor);
        int vx = left + LEFT_X;
        int vy = top + PAGE_Y;
        CodexStyle.specimenFrame(g, vx, vy, PAGE_W, VIEWER_H, locked ? 0x3A2A22 : boss.color());
        viewer.render(g, vx, vy, PAGE_W, VIEWER_H);
        if (!locked && ctx.hovered(vx, vy, PAGE_W, VIEWER_H) && !viewer.revealing()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 420);
            Component hint = Component.translatable("codex.atrocities.viewer.drag");
            g.drawString(font, hint, vx + PAGE_W - font.width(hint) - 3, vy + 3, 0x90F2E6D8, false);
            g.pose().popPose();
        }

        int ny = vy + VIEWER_H + 5;
        if (locked) {
            drawCentered(g, Component.literal("? ? ?").withStyle(Style.EMPTY.withBold(true)), vx + PAGE_W / 2, ny, CodexStyle.BLOOD);
            drawCentered(g, Component.translatable("codex.atrocities.viewer.uncatalogued"), vx + PAGE_W / 2, ny + 11, CodexStyle.INK_FADED);
        } else {
            drawCentered(g, boss.entity().getDescription().copy().withStyle(Style.EMPTY.withBold(true)),
                    vx + PAGE_W / 2, ny, CodexStyle.darken(boss.color(), 0.8f));
            drawCentered(g, Component.translatable(boss.epithet()).withStyle(Style.EMPTY.withItalic(true)),
                    vx + PAGE_W / 2, ny + 11, CodexStyle.INK_FADED);
            int cy = ny + 24;
            int chipW = 34;
            int gap = (PAGE_W - chipW * 4) / 3;
            chip(ctx, vx, cy, chipW, Component.translatable("codex.atrocities.viewer.replay"), viewer.revealing(), viewer::startReveal);
            chip(ctx, vx + (chipW + gap), cy, chipW, Component.translatable("codex.atrocities.viewer.roar_button"), false, viewer::roar);
            chip(ctx, vx + (chipW + gap) * 2, cy, chipW, Component.translatable("codex.atrocities.viewer.walk"),
                    "walk".equals(viewer.pose()), () -> viewer.togglePose("walk"));
            chip(ctx, vx + (chipW + gap) * 3, cy, chipW, Component.translatable("codex.atrocities.viewer.stunned"),
                    "stunned".equals(viewer.pose()), () -> viewer.togglePose("stunned"));
        }
        backLink(ctx);

        // Página direita: abas.
        int rx = left + RIGHT_X;
        int ry = top + PAGE_Y;
        int tabW = PAGE_W / BossTab.values().length;
        for (BossTab candidate : BossTab.values()) {
            int tx = rx + candidate.ordinal() * tabW;
            boolean selected = candidate == tab;
            boolean hovered = !locked && ctx.hovered(tx, ry, tabW - 1, TAB_H);
            int bg = locked ? 0x30201010 : selected ? CodexStyle.withAlpha(boss.color(), 200)
                    : hovered ? CodexStyle.withAlpha(boss.color(), 90) : 0x30201010;
            g.fill(tx, ry, tx + tabW - 1, ry + TAB_H, bg);
            Component label = locked ? Component.literal("???") : candidate.label();
            int color = selected && !locked ? 0xF2E6D8 : CodexStyle.INK;
            CodexStyle.fit(g, font, label, tx + (tabW - 1) / 2f, ry + 3, tabW - 4, color, 1f);
            if (!locked) {
                ctx.click(tx, ry, tabW - 1, TAB_H, () -> selectTab(candidate));
            }
        }
        g.fill(rx, ry + TAB_H, rx + PAGE_W, ry + TAB_H + 1, CodexStyle.withAlpha(locked ? 0x3A2A22 : boss.color(), 220));

        int areaTop = ry + TAB_H + 5;
        int areaH = top + FOOTER_Y + 8 - areaTop;
        int contentH = CodexLayout.height(tabBlocks);
        float maxScroll = Math.max(0, contentH - areaH);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(rx - 2, areaTop, rx + PAGE_W + 4, areaTop + areaH);
        ctx.clipTop = areaTop;
        ctx.clipBottom = areaTop + areaH;
        CodexLayout.renderAll(ctx, tabBlocks, rx, areaTop - (int) scroll, PAGE_W - 4);
        ctx.clipTop = Integer.MIN_VALUE;
        ctx.clipBottom = Integer.MAX_VALUE;
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(12, (int) (areaH * areaH / (float) contentH));
            int barY = areaTop + (int) ((areaH - barH) * (scroll / maxScroll));
            g.fill(rx + PAGE_W + 1, areaTop, rx + PAGE_W + 3, areaTop + areaH, 0x30201010);
            g.fill(rx + PAGE_W + 1, barY, rx + PAGE_W + 3, barY + barH, CodexStyle.withAlpha(boss.color(), 220));
        }
    }

    private void rebuildTab() {
        if (entry == null || entry.boss() == null) {
            tabBlocks = List.of();
            return;
        }
        CodexData.Boss boss = entry.boss();
        ResourceLocation id = id(entry);
        int w = PAGE_W - 4;
        List<CodexLayout.Block> out = new ArrayList<>();
        if (!entry.unlocked(builtFor)) {
            out.add(new CodexLayout.Heading(Component.translatable("codex.atrocities.boss.classified"), CodexStyle.BLOOD));
            CodexLayout.redacted(out, 4, w, id.hashCode());
            out.add(new CodexLayout.Gap(6));
            CodexLayout.text(out, font, Component.translatable("codex.atrocities.locked.encounter", boss.entity().getDescription()
                    .copy().withStyle(s -> s.withObfuscated(true))), w, CodexStyle.INK_FADED);
            out.add(new CodexLayout.Gap(6));
            CodexLayout.redacted(out, 6, w, id.hashCode() * 7L);
            tabBlocks = out;
            return;
        }
        int color = CodexStyle.darken(boss.color(), 0.8f);
        switch (tab) {
            case PROFILE -> {
                CodexLayout.text(out, font, Component.translatable("codex.atrocities.boss.archetype", archetypeName(boss)), w, CodexStyle.INK);
                CodexLayout.text(out, font, Component.translatable("codex.atrocities.boss.health"), w, CodexStyle.INK);
                int kills = builtFor.killCount(id);
                CodexLayout.text(out, font, Component.translatable("codex.atrocities.boss.kills", kills), w, CodexStyle.INK);
                int best = builtFor.bestTier(id);
                CodexLayout.text(out, font, Component.translatable("codex.atrocities.boss.best_tier",
                        best <= 0 ? Component.literal("—") : Component.literal(roman(best))), w, CodexStyle.INK);
                out.add(new CodexLayout.Gap(6));
                out.add(new CodexLayout.Heading(Component.translatable("codex.atrocities.boss.lore"), color));
                for (String lore : boss.lore()) {
                    CodexLayout.text(out, font, Component.translatable(lore), w, CodexStyle.INK);
                    out.add(new CodexLayout.Gap(5));
                }
                out.add(new CodexLayout.Heading(Component.translatable("codex.atrocities.boss.dissection"), color));
                if (builtFor.hasDefeated(id)) {
                    CodexLayout.text(out, font, Component.translatable(boss.dissection()), w, CodexStyle.INK);
                } else {
                    CodexLayout.redacted(out, 5, w, id.hashCode() * 31L);
                    out.add(new CodexLayout.Gap(3));
                    CodexLayout.text(out, font, Component.translatable("codex.atrocities.locked.defeat", boss.entity().getDescription()),
                            w, CodexStyle.INK_FADED);
                }
            }
            case PHASES -> {
                String[] ranges = {"100–66%", "66–33%", "33–0%"};
                for (int i = 0; i < boss.phases().size(); i++) {
                    CodexData.Phase phase = boss.phases().get(i);
                    out.add(new CodexLayout.Heading(Component.translatable("codex.atrocities.boss.phase_title", roman(i + 1),
                            Component.translatable(phase.name())), color));
                    if (i < ranges.length) {
                        CodexLayout.text(out, font, Component.translatable("codex.atrocities.boss.phase_range", ranges[i]), w, CodexStyle.INK_FADED);
                    }
                    CodexLayout.text(out, font, Component.translatable(phase.description()), w, CodexStyle.INK);
                    out.add(new CodexLayout.Gap(7));
                }
            }
            case ATTACKS -> {
                CodexLayout.text(out, font, Component.translatable("codex.atrocities.boss.attacks_hint"), w, CodexStyle.INK_FADED);
                out.add(new CodexLayout.Gap(5));
                for (CodexData.Attack attack : boss.attacks()) {
                    out.add(new AttackRow(attack, boss.color()));
                    CodexLayout.text(out, font, Component.translatable(attack.description()), w, CodexStyle.INK);
                    out.add(new CodexLayout.Gap(6));
                }
            }
            case DROPS -> {
                CodexLayout.text(out, font, Component.translatable("codex.atrocities.boss.drops_hint"), w, CodexStyle.INK_FADED);
                out.add(new CodexLayout.Gap(5));
                for (CodexData.Drop drop : boss.drops()) {
                    out.add(new DropRow(drop));
                    if (drop.note() != null) {
                        CodexLayout.text(out, font, Component.translatable(drop.note()), w, CodexStyle.INK_FADED);
                    }
                    out.add(new CodexLayout.Gap(4));
                }
            }
        }
        tabBlocks = out;
    }

    /** Golpe clicável: encena a animação no visualizador. */
    private final class AttackRow implements CodexLayout.Block {
        private final CodexData.Attack attack;
        private final int color;

        AttackRow(CodexData.Attack attack, int color) {
            this.attack = attack;
            this.color = color;
        }

        @Override
        public int height() {
            return 14;
        }

        @Override
        public boolean keepWithNext() {
            return true;
        }

        @Override
        public void render(CodexLayout.Ctx ctx, int x, int y, int w) {
            boolean hovered = ctx.hovered(x - 2, y, w + 4, 13);
            ctx.g.fill(x - 2, y, x + w + 2, y + 13, CodexStyle.withAlpha(color, hovered ? 110 : 45));
            CodexStyle.arrow(ctx.g, x + 1, y + 2, 1, CodexStyle.withAlpha(hovered ? 0xF2E6D8 : CodexStyle.darken(color, 0.6f), 255));
            ctx.g.drawString(ctx.font, Component.translatable(attack.name()).withStyle(Style.EMPTY.withBold(true)), x + 9, y + 3,
                    hovered ? 0xF2E6D8 : CodexStyle.INK, false);
            if (attack.phase() > 1) {
                String badge = Component.translatable("codex.atrocities.boss.phase_badge", roman(attack.phase())).getString();
                int bw = ctx.font.width(badge) + 4;
                ctx.g.fill(x + w - bw, y + 2, x + w, y + 11, CodexStyle.withAlpha(CodexStyle.darken(color, 0.5f), 230));
                ctx.g.drawString(ctx.font, badge, x + w - bw + 2, y + 3, 0xF2E6D8, false);
            }
            ctx.click(x - 2, y, w + 4, 13, () -> {
                if (viewer != null) {
                    viewer.playAttack(attack, true);
                }
            });
        }
    }

    /** Espólio: item, quantidade e dica do item ao passar o mouse. */
    private static final class DropRow implements CodexLayout.Block {
        private final CodexData.Drop drop;

        DropRow(CodexData.Drop drop) {
            this.drop = drop;
        }

        @Override
        public int height() {
            return 20;
        }

        @Override
        public void render(CodexLayout.Ctx ctx, int x, int y, int w) {
            ctx.g.fill(x, y + 1, x + 18, y + 19, 0x24000000);
            ctx.g.renderOutline(x, y + 1, 18, 18, 0x50201010);
            ctx.item(drop.stack(), x + 1, y + 2);
            Component name = drop.stack().getHoverName();
            ctx.g.drawString(ctx.font, Minecraft.getInstance().font.substrByWidth(name, w - 60).getString(), x + 23, y + 6,
                    CodexStyle.INK, false);
            ctx.g.drawString(ctx.font, drop.amount(), x + w - ctx.font.width(drop.amount()), y + 6, CodexStyle.BLOOD, false);
        }
    }

    // ------------------------------------------------------------------ peças de interface

    private void chip(CodexLayout.Ctx ctx, int x, int y, int w, Component label, boolean active, Runnable action) {
        GuiGraphics g = ctx.g;
        boolean hovered = ctx.hovered(x, y, w, 12);
        int base = entry != null && entry.boss() != null ? entry.boss().color() : CodexStyle.BLOOD;
        g.fill(x, y, x + w, y + 12, CodexStyle.withAlpha(base, active ? 220 : hovered ? 140 : 60));
        g.renderOutline(x, y, w, 12, CodexStyle.withAlpha(CodexStyle.darken(base, 0.5f), 200));
        CodexStyle.fit(g, font, label, x + w / 2f, y + 2, w - 4, active || hovered ? 0xF2E6D8 : CodexStyle.INK, 1f);
        ctx.click(x, y, w, 12, () -> {
            action.run();
            click(1.1f);
        });
    }

    private void navArrow(CodexLayout.Ctx ctx, int x, int y, int dir, Runnable action) {
        boolean hovered = ctx.hovered(x - 3, y - 3, 12, 15);
        CodexStyle.arrow(ctx.g, x, y, dir, hovered ? CodexStyle.withAlpha(CodexStyle.HIGHLIGHT, 255)
                : CodexStyle.withAlpha(CodexStyle.BLOOD, 200));
        ctx.click(x - 3, y - 3, 12, 15, action);
    }

    private void backLink(CodexLayout.Ctx ctx) {
        Component label = Component.translatable("codex.atrocities.back");
        int x = left + LEFT_X + (PAGE_W - font.width(label)) / 2;
        int y = top + FOOTER_Y;
        boolean hovered = ctx.hovered(x - 2, y - 2, font.width(label) + 4, 12);
        ctx.g.drawString(font, label, x, y, hovered ? CodexStyle.HIGHLIGHT : CodexStyle.INK_FADED, false);
        ctx.click(x - 2, y - 2, font.width(label) + 4, 12, this::back);
    }

    private void pageNumber(GuiGraphics g, int page, int total, boolean leftPage) {
        String text = page + " / " + total;
        int x = leftPage ? left + LEFT_X + PAGE_W - font.width(text) - 4 : left + RIGHT_X + 6;
        g.drawString(font, text, x, top + FOOTER_Y, CodexStyle.INK_FADED, false);
    }

    private void drawCentered(GuiGraphics g, Component text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private static void drawSeal(GuiGraphics g, int x, int y, int color) {
        g.fill(x - 3, y - 2, x + 4, y + 5, CodexStyle.withAlpha(CodexStyle.darken(color, 0.5f), 255));
        g.fill(x - 2, y - 1, x + 3, y + 4, CodexStyle.withAlpha(color, 255));
        g.fill(x - 1, y, x + 1, y + 1, CodexStyle.withAlpha(CodexStyle.lighten(color, 0.5f), 255));
    }

    private static void drawLock(GuiGraphics g, int x, int y) {
        int dark = 0xFF3A2A22;
        g.fill(x + 2, y, x + 8, y + 1, dark);
        g.fill(x + 1, y + 1, x + 2, y + 5, dark);
        g.fill(x + 8, y + 1, x + 9, y + 5, dark);
        g.fill(x, y + 5, x + 10, y + 12, dark);
        g.fill(x + 1, y + 6, x + 9, y + 11, 0xFF6B5546);
        g.fill(x + 4, y + 7, x + 6, y + 10, dark);
    }

    private static ResourceLocation id(CodexData.Entry entry) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entry.boss().entity());
    }

    private static Component archetypeName(CodexData.Boss boss) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && boss.entity().create(mc.level) instanceof AtrocityBoss sample) {
            Component name = sample.archetype().displayName();
            sample.discard();
            return name;
        }
        return Component.literal("?");
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

    // ------------------------------------------------------------------ entrada

    private boolean inViewer(double mx, double my) {
        return view == View.ENTRY && viewer != null && mx >= left + LEFT_X && mx < left + LEFT_X + PAGE_W
                && my >= top + PAGE_Y && my < top + PAGE_Y + VIEWER_H;
    }

    private boolean inRightPage(double mx, double my) {
        return mx >= left + RIGHT_X - 4 && mx < left + RIGHT_X + PAGE_W + 6 && my >= top + PAGE_Y && my < top + FOOTER_Y + 10;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = hotspots.size() - 1; i >= 0; i--) {
                CodexLayout.Hotspot spot = hotspots.get(i);
                if (spot.contains(mx, my)) {
                    spot.action().run();
                    return true;
                }
            }
            if (inViewer(mx, my)) {
                draggingViewer = true;
                return true;
            }
        }
        if (button == 1 && view != View.INDEX) {
            back();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingViewer && viewer != null) {
            viewer.drag(dx, dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingViewer && viewer != null) {
            viewer.release();
        }
        draggingViewer = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (view == View.ENTRY && entry != null && entry.boss() != null) {
            if (inViewer(mx, my) && viewer != null) {
                viewer.zoom(scrollY);
                return true;
            }
            if (inRightPage(mx, my)) {
                scroll -= (float) scrollY * 12f;
                return true;
            }
        } else if (view == View.ENTRY) {
            turnSpread(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            turnSpread(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            turnSpread(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            back();
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        remember();
        super.onClose();
    }
}
