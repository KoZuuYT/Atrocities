package com.kmod.atrocities.mutation;

import com.google.gson.JsonElement;
import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.genetics.HexColor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

/**
 * Regras de mutacao, carregadas de {@code data/<ns>/atrocities/mutation/*.json}.
 * <pre>
 * {
 *   "sources": ["minecraft:zombie", "minecraft:husk"],
 *   "result": "atrocities:zombie_titan",
 *   "color": "#7CD64B"
 * }
 * </pre>
 * Cada mob listado em {@code sources} pode receber o Soro Mutagenico; na terceira dose vira {@code result}.
 * Datapacks podem apontar mobs de outros mods para os mutantes existentes.
 */
public final class MutationRules extends SimpleJsonResourceReloadListener {

    public record Rule(ResourceLocation id, List<EntityType<?>> sources, EntityType<?> result, int color) {

        static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf().fieldOf("sources").forGetter(Rule::sources),
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("result").forGetter(Rule::result),
                HexColor.CODEC.optionalFieldOf("color", 0x7CD64B).forGetter(Rule::color)
        ).apply(instance, (sources, result, color) -> new Rule(null, sources, result, color)));

        Rule withId(ResourceLocation id) {
            return new Rule(id, sources, result, color);
        }
    }

    public static final MutationRules INSTANCE = new MutationRules();

    private Map<EntityType<?>, Rule> bySource = Map.of();

    private MutationRules() {
        super(new com.google.gson.Gson(), "atrocities/mutation");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<EntityType<?>, Rule> map = new HashMap<>();
        files.forEach((id, json) -> Rule.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> Atrocities.LOGGER.error("Regra de mutacao invalida {}: {}", id, error))
                .ifPresent(rule -> rule.sources().forEach(source -> map.put(source, rule.withId(id)))));
        bySource = Map.copyOf(map);
        Atrocities.LOGGER.info("Carregadas {} regras de mutacao ({} mobs mutaveis)", files.size(), map.size());
    }

    public static Optional<Rule> forSource(EntityType<?> type) {
        return Optional.ofNullable(INSTANCE.bySource.get(type));
    }
}
