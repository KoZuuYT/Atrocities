package com.kmod.atrocities.imbue;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.genetics.TraitRoll;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModDataComponents;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import org.jetbrains.annotations.Nullable;

/**
 * Regras da imbuicao genetica: que itens recebem genes, como uma amostra vira genes no item
 * e como os genes evoluem com o uso.
 */
public final class Imbuing {

    private Imbuing() {
    }

    /** Tanques geneticos por item. */
    public static final int MAX_GENES = 3;
    /** Chance de o Soro Volatil queimar o gene (o item volta intacto). */
    public static final float VOLATILE_FAIL_CHANCE = 0.2f;
    /** Evolucao ganha quando o gene ja esta no item com nivel igual ou maior. */
    public static final int REDUNDANT_XP_PER_LEVEL = 20;

    public static final TagKey<Item> IMBUABLE_WEAPONS = tag("imbuable_weapons");
    public static final TagKey<Item> IMBUABLE_ARMOR = tag("imbuable_armor");

    /** Onde o item atua: arma (golpes e flechas) ou armadura (quem veste). */
    public enum Kind { WEAPON, ARMOR }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, path));
    }

    public static @Nullable Kind kindOf(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() != 1) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof ArmorItem || stack.is(IMBUABLE_ARMOR)) {
            return Kind.ARMOR;
        }
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof ProjectileWeaponItem
                || item instanceof TridentItem || item instanceof MaceItem
                || stack.is(IMBUABLE_WEAPONS) || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
            return Kind.WEAPON;
        }
        return null;
    }

    public static ImbuedGenes genes(ItemStack stack) {
        ImbuedGenes genes = stack.get(ModDataComponents.IMBUED_GENES.get());
        return genes == null ? ImbuedGenes.EMPTY : genes;
    }

    /** Grava os genes no item; sem genes, o componente e o brilho saem. */
    public static void setGenes(ItemStack stack, ImbuedGenes genes) {
        if (genes.isEmpty()) {
            stack.remove(ModDataComponents.IMBUED_GENES.get());
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        } else {
            stack.set(ModDataComponents.IMBUED_GENES.get(), genes);
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
    }

    /** Nivel maximo de um gene: a potencia maxima do traco no datapack. */
    public static int maxLevel(HolderLookup.Provider registries, ResourceKey<Trait> trait) {
        return GeneticsRegistries.trait(registries, trait).map(Trait::maxPotency).orElse(5);
    }

    /** Nivel inicial: potencia da amostra ajustada pela pureza (50% a 100%), +1 com Soro Volatil. */
    public static int startLevel(TraitRoll roll, int purity, int max, boolean volatileSerum) {
        int level = Math.round(roll.potency() * (0.5f + purity / 200f));
        if (volatileSerum) {
            level++;
        }
        return Mth.clamp(level, 1, max);
    }

    /** Evolucao necessaria para o proximo nivel. */
    public static int evolutionCost(ImbuedGenes genes) {
        return 30 + 25 * genes.totalLevels();
    }

    public static boolean fullyEvolved(ImbuedGenes genes, HolderLookup.Provider registries) {
        return genes.genes().stream().allMatch(gene -> gene.potency() >= maxLevel(registries, gene.trait()));
    }

    // ------------------------------------------------------------------ plano da imbuicao

    /**
     * Resultado previsto de imbuir uma amostra num item.
     *
     * @param error  motivo de recusa, ou nulo se a imbuicao pode acontecer
     * @param result genes do item depois da imbuicao
     * @param added  genes novos ou fortalecidos (para a previsao da tela)
     */
    public record Plan(@Nullable Component error, ImbuedGenes result, List<TraitRoll> added) {
        public boolean ok() {
            return error == null;
        }
    }

    public static Plan plan(ItemStack equipment, GeneSample sample, boolean volatileSerum,
                            HolderLookup.Provider registries) {
        ImbuedGenes current = genes(equipment);
        List<TraitRoll> genes = new ArrayList<>(current.genes());
        List<TraitRoll> added = new ArrayList<>();
        int bonusXp = 0;
        for (TraitRoll roll : sample.traits()) {
            int max = maxLevel(registries, roll.trait());
            int level = startLevel(roll, sample.purity(), max, volatileSerum);
            int index = indexOf(genes, roll.trait());
            if (index >= 0) {
                int existing = genes.get(index).potency();
                if (level > existing) {
                    genes.set(index, new TraitRoll(roll.trait(), level));
                    added.add(new TraitRoll(roll.trait(), level));
                } else {
                    bonusXp += REDUNDANT_XP_PER_LEVEL * level;
                }
                continue;
            }
            Optional<ResourceKey<Trait>> conflict = conflict(genes, roll.trait(), registries);
            if (conflict.isPresent()) {
                return new Plan(Component.translatable("gui.atrocities.imbue.conflict",
                        Trait.displayName(roll.trait()), Trait.displayName(conflict.get())), current, List.of());
            }
            if (genes.size() >= MAX_GENES) {
                return new Plan(Component.translatable("gui.atrocities.imbue.full", MAX_GENES), current, List.of());
            }
            genes.add(new TraitRoll(roll.trait(), level));
            added.add(new TraitRoll(roll.trait(), level));
        }
        return new Plan(null, new ImbuedGenes(genes, current.xp() + bonusXp), added);
    }

    private static int indexOf(List<TraitRoll> genes, ResourceKey<Trait> trait) {
        for (int i = 0; i < genes.size(); i++) {
            if (genes.get(i).trait().equals(trait)) {
                return i;
            }
        }
        return -1;
    }

    private static Optional<ResourceKey<Trait>> conflict(List<TraitRoll> genes, ResourceKey<Trait> candidate,
                                                         HolderLookup.Provider registries) {
        Optional<Trait> candidateTrait = GeneticsRegistries.trait(registries, candidate);
        for (TraitRoll gene : genes) {
            boolean clash = candidateTrait.map(t -> t.conflictsWith(gene.trait())).orElse(false)
                    || GeneticsRegistries.trait(registries, gene.trait())
                    .map(t -> t.conflictsWith(candidate)).orElse(false);
            if (clash) {
                return Optional.of(gene.trait());
            }
        }
        return Optional.empty();
    }

    /** Genes depois de purgar o ultimo gene imbuido. */
    public static ImbuedGenes purged(ImbuedGenes genes) {
        if (genes.isEmpty()) {
            return genes;
        }
        List<TraitRoll> remaining = new ArrayList<>(genes.genes());
        remaining.removeLast();
        return new ImbuedGenes(remaining, remaining.isEmpty() ? 0 : genes.xp() / 2);
    }

    // ------------------------------------------------------------------ evolucao

    /**
     * Soma evolucao ao item. A cada limiar, um gene ainda nao maximo sobe um nivel,
     * e quem usa o item recebe aviso, som e particulas.
     */
    public static void grantEvolution(ServerLevel level, LivingEntity holder, ItemStack stack, int amount) {
        ImbuedGenes genes = stack.get(ModDataComponents.IMBUED_GENES.get());
        if (genes == null || genes.isEmpty() || amount <= 0) {
            return;
        }
        HolderLookup.Provider registries = level.registryAccess();
        List<TraitRoll> list = new ArrayList<>(genes.genes());
        int xp = genes.xp() + amount;
        RandomSource random = level.random;
        List<TraitRoll> evolved = new ArrayList<>();
        while (true) {
            int cost = evolutionCost(new ImbuedGenes(list, 0));
            List<Integer> open = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).potency() < maxLevel(registries, list.get(i).trait())) {
                    open.add(i);
                }
            }
            if (open.isEmpty()) {
                xp = Math.min(xp, cost);
                break;
            }
            if (xp < cost) {
                break;
            }
            xp -= cost;
            int index = open.get(random.nextInt(open.size()));
            TraitRoll up = new TraitRoll(list.get(index).trait(), list.get(index).potency() + 1);
            list.set(index, up);
            evolved.add(up);
        }
        stack.set(ModDataComponents.IMBUED_GENES.get(), new ImbuedGenes(list, xp));
        if (!evolved.isEmpty()) {
            announceEvolution(level, holder, stack, evolved.getLast(), registries);
        }
    }

    private static void announceEvolution(ServerLevel level, LivingEntity holder, ItemStack stack, TraitRoll gene,
                                          HolderLookup.Provider registries) {
        int color = GeneticsRegistries.trait(registries, gene.trait()).map(Trait::color).orElse(0xD070F0);
        if (holder instanceof ServerPlayer player) {
            MutableComponent message = Component.translatable("message.atrocities.imbue.evolved",
                    stack.getHoverName(),
                    Trait.displayName(gene.trait()).withColor(color),
                    Component.literal(roman(gene.potency())).withColor(color)).withColor(0xF0E0FF);
            player.displayClientMessage(message, true);
        }
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), ModSounds.GENE_EVOLVE.get(),
                SoundSource.PLAYERS, 0.9f, 1.2f);
        GeneticsFx.spiral(level, GeneticsFx.dust(color, 1.1f), holder.position(), 0.7, holder.getBbHeight(), 28);
        GeneticsFx.burst(level, ParticleTypes.TOTEM_OF_UNDYING, holder.position().add(0, holder.getBbHeight() * 0.6, 0),
                18, 0.4, 0.3);
    }

    public static String roman(int value) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return value >= 1 && value <= numerals.length ? numerals[value - 1] : String.valueOf(value);
    }
}
