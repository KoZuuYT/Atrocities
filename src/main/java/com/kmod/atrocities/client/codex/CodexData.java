package com.kmod.atrocities.client.codex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.codex.CodexProgress;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo do Códex, carregado de resource packs:
 * <ul>
 *   <li>{@code assets/<ns>/codex/chapters/<id>.json}: capítulos (ordem, ícone, título, descrição, cor).</li>
 *   <li>{@code assets/<ns>/codex/entries/<capitulo>/<id>.json}: verbetes, com páginas montadas a partir de
 *       elementos ({@code heading}, {@code text}, {@code items}, {@code recipe}, {@code traits}, {@code species},
 *       {@code page_break}) ou uma ficha de chefe ({@code boss}).</li>
 * </ul>
 * Qualquer mod ou resource pack pode acrescentar capítulos e verbetes.
 */
public final class CodexData extends SimplePreparableReloadListener<CodexData.Book> {

    public static final CodexData INSTANCE = new CodexData();
    private static Book book = new Book(List.of(), Map.of());

    private CodexData() {
    }

    public static Book book() {
        return book;
    }

    // ------------------------------------------------------------------ modelo

    public record Book(List<Chapter> chapters, Map<ResourceLocation, List<Entry>> entries) {

        public List<Entry> entries(Chapter chapter) {
            return entries.getOrDefault(chapter.id(), List.of());
        }

        public Optional<Chapter> chapter(ResourceLocation id) {
            return chapters.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        public Optional<Entry> entry(ResourceLocation id) {
            return entries.values().stream().flatMap(List::stream).filter(e -> e.id().equals(id)).findFirst();
        }
    }

    public record Chapter(ResourceLocation id, int order, ItemStack icon, String title, String description, int color) {
    }

    public record Entry(ResourceLocation id, ResourceLocation chapter, int order, ItemStack icon, String title,
                        Unlock unlock, List<Element> elements, @Nullable Boss boss) {

        public boolean unlocked(CodexProgress progress) {
            return unlock.test(progress);
        }
    }

    /** Condição para o verbete aparecer. */
    public record Unlock(Kind kind, @Nullable ResourceLocation target) {

        public static final Unlock ALWAYS = new Unlock(Kind.ALWAYS, null);

        public enum Kind { ALWAYS, ENCOUNTER, DEFEAT, TRAIT }

        public boolean test(CodexProgress progress) {
            return switch (kind) {
                case ALWAYS -> true;
                case ENCOUNTER -> progress.hasEncountered(target);
                case DEFEAT -> progress.hasDefeated(target);
                case TRAIT -> progress.knowsTrait(target);
            };
        }

        /** Chave da dica mostrada enquanto o verbete está trancado. */
        public String hintKey() {
            return "codex.atrocities.locked." + kind.name().toLowerCase();
        }
    }

    public sealed interface Element permits Heading, Text, Items, Recipe, Traits, Species, PageBreak {
    }

    public record Heading(String key) implements Element {
    }

    public record Text(String key) implements Element {
    }

    public record Items(List<ItemStack> items, @Nullable String caption) implements Element {
    }

    public record Recipe(ResourceLocation id) implements Element {
    }

    public record Traits() implements Element {
    }

    public record Species() implements Element {
    }

    public record PageBreak() implements Element {
    }

    /** Ficha de chefe: visualizador animado, fases, golpes, espólios e lore. */
    public record Boss(EntityType<?> entity, String epithet, List<String> lore, String dissection,
                       List<Phase> phases, List<Attack> attacks, List<Drop> drops, float viewScale, int color) {
    }

    public record Phase(String name, String description) {
    }

    /**
     * @param animation animação GeckoLib do controlador "action" (vazia = sem animação de golpe)
     * @param phase     fase a partir da qual o golpe aparece
     */
    public record Attack(String name, String description, String animation, int phase) {
    }

    /** @param amount texto da quantidade (ex.: "1–4"); @param note chave opcional de observação */
    public record Drop(ItemStack stack, String amount, @Nullable String note) {
    }

    // ------------------------------------------------------------------ carregamento

    @Override
    protected Book prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<Chapter> chapters = new ArrayList<>();
        Map<ResourceLocation, List<Entry>> entries = new HashMap<>();
        manager.listResources("codex/chapters", path -> path.getPath().endsWith(".json")).forEach((file, resource) -> {
            ResourceLocation id = idOf(file, "codex/chapters/");
            try {
                chapters.add(parseChapter(id, read(resource)));
            } catch (Exception e) {
                Atrocities.LOGGER.error("Capítulo do Códex inválido {}: {}", file, e.getMessage());
            }
        });
        manager.listResources("codex/entries", path -> path.getPath().endsWith(".json")).forEach((file, resource) -> {
            ResourceLocation id = idOf(file, "codex/entries/");
            try {
                Entry entry = parseEntry(id, read(resource));
                entries.computeIfAbsent(entry.chapter(), key -> new ArrayList<>()).add(entry);
            } catch (Exception e) {
                Atrocities.LOGGER.error("Verbete do Códex inválido {}: {}", file, e.getMessage());
            }
        });
        chapters.sort(Comparator.comparingInt(Chapter::order).thenComparing(Chapter::id));
        entries.values().forEach(list -> list.sort(Comparator.comparingInt(Entry::order).thenComparing(Entry::id)));
        entries.replaceAll((key, list) -> List.copyOf(list));
        return new Book(List.copyOf(chapters), Map.copyOf(entries));
    }

    @Override
    protected void apply(Book loaded, ResourceManager manager, ProfilerFiller profiler) {
        book = loaded;
        int count = loaded.entries().values().stream().mapToInt(List::size).sum();
        Atrocities.LOGGER.info("Códex Atrocitas: {} capítulos, {} verbetes.", loaded.chapters().size(), count);
    }

    private static JsonObject read(Resource resource) throws Exception {
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static ResourceLocation idOf(ResourceLocation file, String prefix) {
        String path = file.getPath().substring(prefix.length(), file.getPath().length() - ".json".length());
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
    }

    private static Chapter parseChapter(ResourceLocation id, JsonObject json) {
        return new Chapter(id, GsonHelper.getAsInt(json, "order", 0), item(GsonHelper.getAsString(json, "icon")),
                GsonHelper.getAsString(json, "title"), GsonHelper.getAsString(json, "description", ""),
                color(json, "color", 0x7A0E14));
    }

    private static Entry parseEntry(ResourceLocation id, JsonObject json) {
        ResourceLocation chapter = ResourceLocation.parse(GsonHelper.getAsString(json, "chapter"));
        Unlock unlock = Unlock.ALWAYS;
        if (json.has("unlock")) {
            JsonObject u = GsonHelper.getAsJsonObject(json, "unlock");
            unlock = new Unlock(Unlock.Kind.valueOf(GsonHelper.getAsString(u, "type").toUpperCase()),
                    ResourceLocation.parse(GsonHelper.getAsString(u, "target")));
        }
        List<Element> elements = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "pages", new JsonArray())) {
            elements.add(parseElement(element.getAsJsonObject()));
        }
        Boss boss = json.has("boss") ? parseBoss(GsonHelper.getAsJsonObject(json, "boss")) : null;
        return new Entry(id, chapter, GsonHelper.getAsInt(json, "order", 0), item(GsonHelper.getAsString(json, "icon")),
                GsonHelper.getAsString(json, "title"), unlock, List.copyOf(elements), boss);
    }

    private static Element parseElement(JsonObject json) {
        String type = GsonHelper.getAsString(json, "type");
        return switch (type) {
            case "heading" -> new Heading(GsonHelper.getAsString(json, "text"));
            case "text" -> new Text(GsonHelper.getAsString(json, "text"));
            case "items" -> new Items(items(GsonHelper.getAsJsonArray(json, "items")),
                    GsonHelper.getAsString(json, "caption", null));
            case "recipe" -> new Recipe(ResourceLocation.parse(GsonHelper.getAsString(json, "recipe")));
            case "traits" -> new Traits();
            case "species" -> new Species();
            case "page_break" -> new PageBreak();
            default -> throw new IllegalArgumentException("tipo de elemento desconhecido: " + type);
        };
    }

    private static Boss parseBoss(JsonObject json) {
        ResourceLocation entityId = ResourceLocation.parse(GsonHelper.getAsString(json, "entity"));
        EntityType<?> entity = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId)
                .orElseThrow(() -> new IllegalArgumentException("entidade desconhecida: " + entityId));
        List<String> lore = new ArrayList<>();
        GsonHelper.getAsJsonArray(json, "lore").forEach(e -> lore.add(e.getAsString()));
        List<Phase> phases = new ArrayList<>();
        GsonHelper.getAsJsonArray(json, "phases").forEach(e -> {
            JsonObject p = e.getAsJsonObject();
            phases.add(new Phase(GsonHelper.getAsString(p, "name"), GsonHelper.getAsString(p, "description")));
        });
        List<Attack> attacks = new ArrayList<>();
        GsonHelper.getAsJsonArray(json, "attacks").forEach(e -> {
            JsonObject a = e.getAsJsonObject();
            attacks.add(new Attack(GsonHelper.getAsString(a, "name"), GsonHelper.getAsString(a, "description"),
                    GsonHelper.getAsString(a, "animation", ""), GsonHelper.getAsInt(a, "phase", 1)));
        });
        List<Drop> drops = new ArrayList<>();
        GsonHelper.getAsJsonArray(json, "drops").forEach(e -> {
            JsonObject d = e.getAsJsonObject();
            drops.add(new Drop(item(GsonHelper.getAsString(d, "item")), GsonHelper.getAsString(d, "amount", ""),
                    GsonHelper.getAsString(d, "note", null)));
        });
        return new Boss(entity, GsonHelper.getAsString(json, "epithet"), List.copyOf(lore),
                GsonHelper.getAsString(json, "dissection"), List.copyOf(phases), List.copyOf(attacks),
                List.copyOf(drops), GsonHelper.getAsFloat(json, "view_scale", 1f), color(json, "color", 0x7A0E14));
    }

    private static List<ItemStack> items(JsonArray array) {
        List<ItemStack> stacks = new ArrayList<>();
        array.forEach(e -> stacks.add(item(e.getAsString())));
        return List.copyOf(stacks);
    }

    private static ItemStack item(String id) {
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(net.minecraft.world.item.Items.BARRIER);
        return new ItemStack(item);
    }

    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        String value = GsonHelper.getAsString(json, key);
        return Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16);
    }
}
