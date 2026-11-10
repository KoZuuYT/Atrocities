package com.kmod.atrocities.codex;

import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.genetics.TraitRoll;
import com.kmod.atrocities.network.CodexSyncPayload;
import com.kmod.atrocities.registry.ModDataComponents;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModRegistries;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lado servidor do Códex Atrocitas: guarda o que cada jogador já descobriu e sincroniza com o cliente.
 * <ul>
 *   <li>Enfrentar um chefe (estar perto dele) revela a página secreta dele.</li>
 *   <li>Abater um chefe libera as notas de dissecação e conta abates e o maior tier vencido.</li>
 *   <li>Ter uma amostra ou embrião no inventário cataloga os traços que ele carrega.</li>
 * </ul>
 */
public final class Codex {

    private static final int SCAN_INTERVAL = 40;

    public static final Supplier<AttachmentType<CodexProgress>> PROGRESS =
            ModRegistries.ATTACHMENT_TYPES.register("codex", () -> AttachmentType.builder(() -> CodexProgress.EMPTY)
                    .serialize(CodexProgress.CODEC)
                    .copyOnDeath()
                    .build());

    private Codex() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                onLogin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % SCAN_INTERVAL == 0) {
                scanInventory(player);
            }
        });
    }

    public static CodexProgress progress(ServerPlayer player) {
        return player.getData(PROGRESS);
    }

    /** O jogador está enfrentando o chefe: revela a página dele (uma vez). */
    public static void encounter(ServerPlayer player, EntityType<?> boss) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(boss);
        CodexProgress progress = progress(player);
        if (progress.hasEncountered(id)) {
            return;
        }
        update(player, progress.withEncounter(id), List.of(CodexSyncPayload.unlock(CodexSyncPayload.ENCOUNTER, id)));
    }

    /** O jogador participou do abate do chefe. */
    public static void defeat(ServerPlayer player, EntityType<?> boss, int tier) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(boss);
        CodexProgress progress = progress(player);
        List<String> unlocked = new ArrayList<>();
        if (!progress.hasEncountered(id)) {
            unlocked.add(CodexSyncPayload.unlock(CodexSyncPayload.ENCOUNTER, id));
        }
        if (!progress.hasDefeated(id)) {
            unlocked.add(CodexSyncPayload.unlock(CodexSyncPayload.DEFEAT, id));
        }
        update(player, progress.withKill(id, tier), unlocked);
    }

    private static void onLogin(ServerPlayer player) {
        CodexProgress progress = progress(player);
        if (!progress.received() && AtrocitiesConfig.GIVE_CODEX_ON_JOIN.get()) {
            ItemStack book = new ItemStack(ModItems.CODEX.get());
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
            progress = progress.withReceived();
            player.setData(PROGRESS, progress);
        }
        sync(player, progress, List.of());
    }

    /** Cataloga os traços das amostras e embriões que o jogador carrega. */
    private static void scanInventory(ServerPlayer player) {
        CodexProgress progress = progress(player);
        Set<ResourceLocation> found = new HashSet<>();
        for (ItemStack stack : player.getInventory().items) {
            collectTraits(stack, progress, found);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            collectTraits(stack, progress, found);
        }
        if (found.isEmpty()) {
            return;
        }
        List<String> unlocked = found.stream().sorted()
                .map(id -> CodexSyncPayload.unlock(CodexSyncPayload.TRAIT, id)).toList();
        update(player, progress.withTraits(found), unlocked);
    }

    private static void collectTraits(ItemStack stack, CodexProgress progress, Set<ResourceLocation> found) {
        if (stack.isEmpty()) {
            return;
        }
        List<TraitRoll> traits = List.of();
        GeneSample sample = stack.get(ModDataComponents.GENE_SAMPLE.get());
        if (sample != null) {
            traits = sample.traits();
        } else {
            EmbryoGenome genome = stack.get(ModDataComponents.EMBRYO_GENOME.get());
            if (genome != null) {
                traits = genome.traits();
            }
        }
        for (TraitRoll roll : traits) {
            ResourceLocation id = roll.trait().location();
            if (!progress.knowsTrait(id)) {
                found.add(id);
            }
        }
    }

    private static void update(ServerPlayer player, CodexProgress progress, List<String> unlocked) {
        player.setData(PROGRESS, progress);
        sync(player, progress, unlocked);
    }

    private static void sync(ServerPlayer player, CodexProgress progress, List<String> unlocked) {
        if (player.connection != null && player.connection.hasChannel(CodexSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new CodexSyncPayload(progress, unlocked));
        }
    }
}
