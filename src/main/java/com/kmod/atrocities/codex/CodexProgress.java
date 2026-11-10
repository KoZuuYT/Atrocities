package com.kmod.atrocities.codex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Progresso do Códex Atrocitas de um jogador (anexado ao jogador e copiado na morte).
 *
 * @param encountered chefes enfrentados (id da entidade): libera a página secreta
 * @param kills       abates por chefe
 * @param bestTier    maior tier abatido por chefe
 * @param traits      traços genéticos já catalogados (vistos em amostras ou embriões)
 * @param received    o jogador já recebeu o livro ao entrar pela primeira vez
 */
public record CodexProgress(Set<ResourceLocation> encountered, Map<ResourceLocation, Integer> kills,
                            Map<ResourceLocation, Integer> bestTier, Set<ResourceLocation> traits, boolean received) {

    public static final CodexProgress EMPTY = new CodexProgress(Set.of(), Map.of(), Map.of(), Set.of(), false);

    public static final Codec<CodexProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("encountered", List.of())
                    .forGetter(p -> List.copyOf(p.encountered)),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("kills", Map.of())
                    .forGetter(CodexProgress::kills),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("best_tier", Map.of())
                    .forGetter(CodexProgress::bestTier),
            ResourceLocation.CODEC.listOf().optionalFieldOf("traits", List.of())
                    .forGetter(p -> List.copyOf(p.traits)),
            Codec.BOOL.optionalFieldOf("received", false).forGetter(CodexProgress::received)
    ).apply(i, (encountered, kills, best, traits, received) ->
            new CodexProgress(Set.copyOf(encountered), kills, best, Set.copyOf(traits), received)));

    public static final StreamCodec<ByteBuf, CodexProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(HashSet::new, ResourceLocation.STREAM_CODEC), p -> new HashSet<>(p.encountered),
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT), p -> new HashMap<>(p.kills),
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT), p -> new HashMap<>(p.bestTier),
            ByteBufCodecs.collection(HashSet::new, ResourceLocation.STREAM_CODEC), p -> new HashSet<>(p.traits),
            ByteBufCodecs.BOOL, CodexProgress::received,
            CodexProgress::new);

    public CodexProgress {
        encountered = Set.copyOf(encountered);
        kills = Map.copyOf(kills);
        bestTier = Map.copyOf(bestTier);
        traits = Set.copyOf(traits);
    }

    public boolean hasEncountered(ResourceLocation boss) {
        return encountered.contains(boss);
    }

    public int killCount(ResourceLocation boss) {
        return kills.getOrDefault(boss, 0);
    }

    public boolean hasDefeated(ResourceLocation boss) {
        return killCount(boss) > 0;
    }

    public int bestTier(ResourceLocation boss) {
        return bestTier.getOrDefault(boss, 0);
    }

    public boolean knowsTrait(ResourceLocation trait) {
        return traits.contains(trait);
    }

    public CodexProgress withEncounter(ResourceLocation boss) {
        Set<ResourceLocation> next = new HashSet<>(encountered);
        next.add(boss);
        return new CodexProgress(next, kills, bestTier, traits, received);
    }

    public CodexProgress withKill(ResourceLocation boss, int tier) {
        Set<ResourceLocation> seen = new HashSet<>(encountered);
        seen.add(boss);
        Map<ResourceLocation, Integer> nextKills = new HashMap<>(kills);
        nextKills.merge(boss, 1, Integer::sum);
        Map<ResourceLocation, Integer> nextBest = new HashMap<>(bestTier);
        nextBest.merge(boss, tier, Math::max);
        return new CodexProgress(seen, nextKills, nextBest, traits, received);
    }

    public CodexProgress withTraits(Set<ResourceLocation> discovered) {
        Set<ResourceLocation> next = new HashSet<>(traits);
        next.addAll(discovered);
        return new CodexProgress(encountered, kills, bestTier, next, received);
    }

    public CodexProgress withReceived() {
        return new CodexProgress(encountered, kills, bestTier, traits, true);
    }
}
