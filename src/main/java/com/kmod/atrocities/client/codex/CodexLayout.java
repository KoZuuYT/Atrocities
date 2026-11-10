package com.kmod.atrocities.client.codex;

import com.kmod.atrocities.codex.CodexProgress;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Species;
import com.kmod.atrocities.genetics.Trait;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

/**
 * Monta e pagina o conteúdo dos verbetes: cada elemento vira uma sequência de blocos de altura fixa,
 * distribuídos em páginas. Texto quebra linha a linha entre páginas; títulos nunca ficam sozinhos no fim.
 */
public final class CodexLayout {

    public static final int LINE = 10;

    private CodexLayout() {
    }

    // ------------------------------------------------------------------ contexto de desenho

    /** Estado de um quadro: mouse, tempo, dicas flutuantes e áreas clicáveis registradas pelos blocos. */
    public static final class Ctx {
        public final GuiGraphics g;
        public final Font font;
        public final int mouseX;
        public final int mouseY;
        public final float time;
        @Nullable
        public ItemStack hoverStack;
        @Nullable
        public List<Component> hoverText;
        public final List<Hotspot> hotspots;
        /** Área visível (rolagem): blocos fora dela não registram cliques nem dicas. */
        public int clipTop = Integer.MIN_VALUE;
        public int clipBottom = Integer.MAX_VALUE;

        public Ctx(GuiGraphics g, Font font, int mouseX, int mouseY, float time, List<Hotspot> hotspots) {
            this.g = g;
            this.font = font;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.time = time;
            this.hotspots = hotspots;
        }

        public boolean hovered(int x, int y, int w, int h) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                    && mouseY >= clipTop && mouseY < clipBottom;
        }

        public void item(ItemStack stack, int x, int y) {
            g.renderItem(stack, x, y);
            g.renderItemDecorations(font, stack, x, y);
            if (hovered(x - 1, y - 1, 18, 18)) {
                g.fill(x, y, x + 16, y + 16, 0x40FFFFFF);
                hoverStack = stack;
            }
        }

        public void click(int x, int y, int w, int h, Runnable action) {
            if (y + h > clipTop && y < clipBottom) {
                hotspots.add(new Hotspot(x, Math.max(y, clipTop), w, Math.min(y + h, clipBottom) - Math.max(y, clipTop), action));
            }
        }
    }

    public record Hotspot(int x, int y, int w, int h, Runnable action) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // ------------------------------------------------------------------ blocos

    public interface Block {
        int height();

        void render(Ctx ctx, int x, int y, int w);

        /** Título: vai para a página seguinte se o bloco depois dele não couber junto. */
        default boolean keepWithNext() {
            return false;
        }
    }

    public record Gap(int height) implements Block {
        @Override
        public void render(Ctx ctx, int x, int y, int w) {
        }
    }

    /** Força uma página nova. */
    public record Break() implements Block {
        @Override
        public int height() {
            return 0;
        }

        @Override
        public void render(Ctx ctx, int x, int y, int w) {
        }
    }

    public record Line(FormattedCharSequence text, int color) implements Block {
        @Override
        public int height() {
            return LINE;
        }

        @Override
        public void render(Ctx ctx, int x, int y, int w) {
            ctx.g.drawString(ctx.font, text, x, y, color, false);
        }
    }

    /** Título de seção: texto em sangue seco com fio tracejado. */
    public record Heading(Component text, int color) implements Block {
        @Override
        public int height() {
            return 15;
        }

        @Override
        public boolean keepWithNext() {
            return true;
        }

        @Override
        public void render(Ctx ctx, int x, int y, int w) {
            ctx.g.drawString(ctx.font, text, x, y + 2, color, false);
            CodexStyle.rule(ctx.g, x, y + 12, w, CodexStyle.withAlpha(color, 140));
        }
    }

    /** Cabeçalho do verbete: ícone em moldura de espécime e título. */
    public record Title(ItemStack icon, Component text, int color) implements Block {
        @Override
        public int height() {
            return 28;
        }

        @Override
        public void render(Ctx ctx, int x, int y, int w) {
            ctx.g.fill(x + 1, y + 1, x + 19, y + 19, 0x30000000);
            CodexStyle.specimenFrame(ctx.g, x + 1, y + 1, 18, 18, color);
            ctx.g.renderItem(icon, x + 2, y + 2);
            List<FormattedCharSequence> lines = ctx.font.split(text.copy().withStyle(Style.EMPTY.withBold(true)), w - 28);
            int ty = y + (lines.size() > 1 ? 1 : 6);
            for (FormattedCharSequence line : lines.subList(0, Math.min(2, lines.size()))) {
                ctx.g.drawString(ctx.font, line, x + 26, ty, color, false);
                ty += 10;
            }
            CodexStyle.rule(ctx.g, x, y + 24, w, CodexStyle.withAlpha(color, 160));
        }
    }

    /** Fileira de itens com dica ao passar o mouse. */
    public record Items(List<ItemStack> items) implements Block {
        @Override
        public int height() {
            return 22;
        }

        @Override
        public void render(Ctx ctx, int x, int y, int w) {
            int total = items.size() * 20;
            int sx = x + Math.max(0, (w - total) / 2);
            for (int i = 0; i < items.size(); i++) {
                int ix = sx + i * 20;
                ctx.g.fill(ix, y + 1, ix + 18, y + 19, 0x24000000);
                ctx.g.renderOutline(ix, y + 1, 18, 18, 0x50201010);
                ctx.item(items.get(i), ix + 1, y + 2);
            }
        }
    }

    /** Receita de bancada 3x3 com ingredientes alternando a cada segundo. */
    public record RecipeGrid(RecipeHolder<?> holder) implements Block {
        @Override
        public int height() {
            return 62;
        }

        @Override
        public void render(Ctx ctx, int x, int y, int w) {
            Recipe<?> recipe = holder.value();
            List<Ingredient> ingredients = recipe.getIngredients();
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            int ox = x + (w - 94) / 2;
            int oy = y + 2;
            ctx.g.fill(ox - 2, oy - 2, ox + 56, oy + 56, 0x22000000);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int sx = ox + col * 18;
                    int sy = oy + row * 18;
                    ctx.g.renderOutline(sx, sy, 18, 18, 0x60301818);
                }
            }
            for (int i = 0; i < ingredients.size(); i++) {
                ItemStack[] options = ingredients.get(i).getItems();
                if (options.length == 0) {
                    continue;
                }
                ItemStack shown = options[(int) (ctx.time / 20f) % options.length];
                int col = i % width;
                int row = i / width;
                ctx.item(shown, ox + col * 18 + 1, oy + row * 18 + 1);
            }
            CodexStyle.arrow(ctx.g, ox + 62, oy + 22, 1, CodexStyle.withAlpha(CodexStyle.BLOOD, 200));
            CodexStyle.arrow(ctx.g, ox + 67, oy + 22, 1, CodexStyle.withAlpha(CodexStyle.BLOOD, 120));
            int rx = ox + 76;
            int ry = oy + 18;
            CodexStyle.specimenFrame(ctx.g, rx, ry, 18, 18, CodexStyle.BLOOD);
            Minecraft mc = Minecraft.getInstance();
            ItemStack result = mc.level == null ? ItemStack.EMPTY : recipe.getResultItem(mc.level.registryAccess());
            ctx.item(result, rx + 1, ry + 1);
        }
    }

    /** Linha com marcador colorido (traços, espécies, golpes). */
    public record Bullet(Component text, int color) implements Block {
        @Override
        public int height() {
            return 12;
        }

        @Override
        public void render(Ctx ctx, int x, int y, int w) {
            ctx.g.fill(x, y + 3, x + 4, y + 7, CodexStyle.withAlpha(color, 255));
            ctx.g.fill(x + 1, y + 4, x + 3, y + 6, CodexStyle.withAlpha(CodexStyle.lighten(color, 0.5f), 255));
            ctx.g.drawString(ctx.font, net.minecraft.locale.Language.getInstance()
                    .getVisualOrder(ctx.font.substrByWidth(text, w - 8)), x + 7, y + 1, CodexStyle.INK, false);
        }
    }

    /** Linha de texto censurado (informação ainda não descoberta). */
    public record Redacted(int width, long seed) implements Block {
        @Override
        public int height() {
            return LINE;
        }

        @Override
        public void render(Ctx ctx, int x, int y, int w) {
            CodexStyle.redaction(ctx.g, x, y, Math.min(w, width), seed);
        }
    }

    // ------------------------------------------------------------------ construção

    /** Quebra um texto traduzido em linhas; {@code \n} separa parágrafos. */
    public static void text(List<Block> out, Font font, Component text, int width, int color) {
        String[] paragraphs = text.getString().split("\n");
        for (int p = 0; p < paragraphs.length; p++) {
            if (paragraphs[p].isBlank()) {
                continue;
            }
            for (FormattedCharSequence line : font.split(Component.literal(paragraphs[p]).withStyle(text.getStyle()), width)) {
                out.add(new Line(line, color));
            }
            if (p < paragraphs.length - 1) {
                out.add(new Gap(4));
            }
        }
    }

    /** Linhas censuradas no lugar de um texto (mesmo número aproximado de linhas). */
    public static void redacted(List<Block> out, int lines, int width, long seed) {
        RandomSourceLite random = new RandomSourceLite(seed);
        for (int i = 0; i < lines; i++) {
            out.add(new Redacted(i == lines - 1 ? width / 2 + random.next(width / 3) : width - random.next(18), seed + i));
        }
    }

    public static List<Block> build(CodexData.Entry entry, Font font, int width, CodexProgress progress) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Title(entry.icon(), Component.translatable(entry.title()), CodexStyle.BLOOD));
        for (CodexData.Element element : entry.elements()) {
            switch (element) {
                case CodexData.Heading heading -> blocks.add(new Heading(Component.translatable(heading.key()), CodexStyle.BLOOD));
                case CodexData.Text text -> {
                    text(blocks, font, Component.translatable(text.key()), width, CodexStyle.INK);
                    blocks.add(new Gap(6));
                }
                case CodexData.Items items -> {
                    for (int i = 0; i < items.items().size(); i += Math.max(1, width / 20)) {
                        blocks.add(new Items(items.items().subList(i, Math.min(items.items().size(), i + width / 20))));
                    }
                    if (items.caption() != null) {
                        text(blocks, font, Component.translatable(items.caption()), width, CodexStyle.INK_FADED);
                    }
                    blocks.add(new Gap(4));
                }
                case CodexData.Recipe recipe -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null) {
                        mc.level.getRecipeManager().byKey(recipe.id()).ifPresent(holder -> {
                            blocks.add(new RecipeGrid(holder));
                            blocks.add(new Gap(4));
                        });
                    }
                }
                case CodexData.Traits traits -> traits(blocks, font, width, progress);
                case CodexData.Species species -> species(blocks, font, width, progress);
                case CodexData.PageBreak pageBreak -> blocks.add(new Break());
            }
        }
        return blocks;
    }

    private static void traits(List<Block> out, Font font, int width, CodexProgress progress) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RegistryAccess access = mc.level.registryAccess();
        Registry<Trait> registry = access.registry(GeneticsRegistries.TRAIT).orElse(null);
        if (registry == null) {
            return;
        }
        List<Map.Entry<ResourceKey<Trait>, Trait>> all = new ArrayList<>(registry.entrySet());
        all.sort(Comparator.<Map.Entry<ResourceKey<Trait>, Trait>, Integer>comparing(e -> e.getValue().category().ordinal())
                .thenComparing(e -> e.getKey().location()));
        long known = all.stream().filter(e -> progress.knowsTrait(e.getKey().location())).count();
        text(out, font, Component.translatable("codex.atrocities.traits.progress", known, all.size()), width, CodexStyle.INK_FADED);
        out.add(new Gap(6));
        for (Map.Entry<ResourceKey<Trait>, Trait> entry : all) {
            Trait trait = entry.getValue();
            boolean discovered = progress.knowsTrait(entry.getKey().location());
            if (discovered) {
                out.add(new Bullet(Trait.displayName(entry.getKey()).withColor(CodexStyle.darken(trait.color(), 0.7f))
                        .withStyle(Style.EMPTY.withBold(true)), trait.color()));
                text(out, font, Component.translatable("codex.atrocities.traits.line",
                        trait.category().displayName(), trait.maxPotency(), Mth.floor(trait.instability())), width, CodexStyle.INK_FADED);
                text(out, font, Trait.description(entry.getKey()), width, CodexStyle.INK);
            } else {
                out.add(new Bullet(Component.translatable("codex.atrocities.traits.unknown"), 0x5A4A40));
                redacted(out, 1, width - 20, entry.getKey().location().hashCode());
            }
            out.add(new Gap(5));
        }
    }

    private static void species(List<Block> out, Font font, int width, CodexProgress progress) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RegistryAccess access = mc.level.registryAccess();
        GeneticsRegistries.allSpecies(access).sorted(Comparator
                        .<net.minecraft.core.Holder.Reference<Species>, Integer>comparing(ref -> ref.value().archetype().ordinal())
                        .thenComparing(ref -> ref.key().location()))
                .forEach(ref -> {
                    Species species = ref.value();
                    out.add(new Bullet(species.entity().getDescription().copy().withStyle(Style.EMPTY.withBold(true)),
                            species.archetype().color()));
                    text(out, font, Component.translatable("codex.atrocities.species.line", species.archetype().displayName(),
                            Math.round(species.extraction().syringeHealthRatio() * 100),
                            Math.round(species.extraction().captureHealthRatio() * 100)), width, CodexStyle.INK_FADED);
                    List<Component> names = new ArrayList<>();
                    for (Species.TraitEntry trait : species.traits()) {
                        names.add(progress.knowsTrait(trait.trait().location())
                                ? Trait.displayName(trait.trait())
                                : Component.translatable("codex.atrocities.traits.unknown_short"));
                    }
                    Component joined = names.stream().reduce((a, b) -> a.copy().append(", ").append(b)).orElse(Component.empty());
                    text(out, font, Component.translatable("codex.atrocities.species.traits", joined), width, CodexStyle.INK);
                    out.add(new Gap(5));
                });
    }

    // ------------------------------------------------------------------ paginação

    public static List<List<Block>> paginate(List<Block> blocks, int pageHeight) {
        List<List<Block>> pages = new ArrayList<>();
        List<Block> page = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (block instanceof Break) {
                if (!page.isEmpty()) {
                    pages.add(page);
                    page = new ArrayList<>();
                    used = 0;
                }
                continue;
            }
            if (page.isEmpty() && block instanceof Gap) {
                continue;
            }
            int needed = block.height();
            if (block.keepWithNext() && i + 1 < blocks.size() && !(blocks.get(i + 1) instanceof Break)) {
                needed += blocks.get(i + 1).height();
            }
            if (used + needed > pageHeight && !page.isEmpty()) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
                if (block instanceof Gap) {
                    continue;
                }
            }
            page.add(block);
            used += block.height();
        }
        if (!page.isEmpty()) {
            pages.add(page);
        }
        return pages;
    }

    public static int height(List<Block> blocks) {
        return blocks.stream().mapToInt(Block::height).sum();
    }

    public static void renderAll(Ctx ctx, List<Block> blocks, int x, int y, int w) {
        int cy = y;
        for (Block block : blocks) {
            if (cy + block.height() >= ctx.clipTop && cy <= ctx.clipBottom) {
                block.render(ctx, x, cy, w);
            }
            cy += block.height();
        }
    }

    /** Gerador determinístico simples (tarjas de censura estáveis entre quadros). */
    private static final class RandomSourceLite {
        private long state;

        RandomSourceLite(long seed) {
            this.state = seed * 0x5DEECE66DL + 11;
        }

        int next(int bound) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            return bound <= 0 ? 0 : (int) ((state >>> 33) % bound);
        }
    }
}
