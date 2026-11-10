package com.kmod.atrocities.client.codex;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.codex.CodexProgress;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.network.CodexSyncPayload;
import com.kmod.atrocities.registry.ModItems;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Estado do Códex no cliente: o progresso recebido do servidor e as fichas cuja cena de revelação
 * já foi vista (guardadas em {@code atrocities/codex_revealed.txt}, para tocar a cena só na primeira leitura).
 */
public final class ClientCodex {

    private static CodexProgress progress = CodexProgress.EMPTY;
    private static final Set<ResourceLocation> REVEALED = new HashSet<>();
    private static boolean revealedLoaded;

    private ClientCodex() {
    }

    public static CodexProgress progress() {
        return progress;
    }

    public static void reset() {
        progress = CodexProgress.EMPTY;
    }

    public static void onSync(CodexSyncPayload payload) {
        progress = payload.progress();
        List<String> traits = new ArrayList<>();
        for (String unlock : payload.unlocked()) {
            int split = unlock.indexOf('|');
            if (split < 0) {
                continue;
            }
            String kind = unlock.substring(0, split);
            ResourceLocation id = ResourceLocation.tryParse(unlock.substring(split + 1));
            if (id == null) {
                continue;
            }
            switch (kind) {
                case CodexSyncPayload.ENCOUNTER -> toast("codex.atrocities.toast.encounter", bossName(id), bossIcon(id), 0xE3262F);
                case CodexSyncPayload.DEFEAT -> toast("codex.atrocities.toast.defeat", bossName(id), bossIcon(id), 0xF2A51A);
                case CodexSyncPayload.TRAIT -> traits.add(id.toString());
                default -> {
                }
            }
        }
        if (traits.size() == 1) {
            ResourceLocation id = ResourceLocation.parse(traits.get(0));
            toast("codex.atrocities.toast.trait", traitName(id), new ItemStack(ModItems.GENE_SAMPLE.get()), 0x4CC24A);
        } else if (traits.size() > 1) {
            toast("codex.atrocities.toast.traits", Component.translatable("codex.atrocities.toast.traits.count", traits.size()),
                    new ItemStack(ModItems.GENE_SAMPLE.get()), 0x4CC24A);
        }
    }

    private static void toast(String title, Component subject, ItemStack icon, int color) {
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().addToast(new CodexToast(Component.translatable(title), subject, icon, color));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.6f, 1.2f));
    }

    public static Component bossName(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> type.getDescription())
                .orElse(Component.literal(id.toString()));
    }

    private static ItemStack bossIcon(ResourceLocation id) {
        return CodexData.book().entries().values().stream().flatMap(List::stream)
                .filter(entry -> entry.boss() != null
                        && BuiltInRegistries.ENTITY_TYPE.getKey(entry.boss().entity()).equals(id))
                .map(CodexData.Entry::icon).findFirst()
                .orElseGet(() -> new ItemStack(ModItems.CODEX.get()));
    }

    public static Component traitName(ResourceLocation id) {
        ResourceKey<Trait> key = ResourceKey.create(GeneticsRegistries.TRAIT, id);
        Minecraft mc = Minecraft.getInstance();
        int color = mc.level == null ? 0xFFFFFF : GeneticsRegistries.trait(mc.level.registryAccess(), key)
                .map(Trait::color).orElse(0xFFFFFF);
        return Trait.displayName(key).withColor(color);
    }

    // ------------------------------------------------------------------ cenas de revelação já vistas

    public static boolean revealed(ResourceLocation entry) {
        loadRevealed();
        return REVEALED.contains(entry);
    }

    public static void markRevealed(ResourceLocation entry) {
        loadRevealed();
        if (REVEALED.add(entry)) {
            try {
                Path file = revealedFile();
                Files.createDirectories(file.getParent());
                Files.write(file, REVEALED.stream().map(ResourceLocation::toString).sorted().toList(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                Atrocities.LOGGER.warn("Não foi possível salvar o progresso de leitura do Códex: {}", e.getMessage());
            }
        }
    }

    private static void loadRevealed() {
        if (revealedLoaded) {
            return;
        }
        revealedLoaded = true;
        Path file = revealedFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                ResourceLocation id = ResourceLocation.tryParse(line.trim());
                if (id != null) {
                    REVEALED.add(id);
                }
            }
        } catch (IOException e) {
            Atrocities.LOGGER.warn("Não foi possível ler o progresso de leitura do Códex: {}", e.getMessage());
        }
    }

    private static Path revealedFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(Atrocities.MODID).resolve("codex_revealed.txt");
    }
}
