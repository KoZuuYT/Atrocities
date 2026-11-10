package com.kmod.atrocities.registry;

import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.item.ExtractionSyringeItem;
import com.kmod.atrocities.item.StasisCapsuleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

/** Itens do Atrocities. */
public final class ModItems {

    private ModItems() {
    }

    /**
     * Frasco de Amostra: recipiente de vidro reforcado. Componente base de toda a cadeia genetica
     * (seringas, capsulas de estase e amostras isoladas sao feitas a partir dele).
     */
    public static final DeferredItem<Item> SAMPLE_VIAL =
            ModRegistries.ITEMS.registerSimpleItem("sample_vial", new Item.Properties().stacksTo(16));

    /** Seringa de Extracao: tira amostras geneticas de especimes enfraquecidos. */
    public static final DeferredItem<ExtractionSyringeItem> EXTRACTION_SYRINGE =
            ModRegistries.ITEMS.registerItem("extraction_syringe", ExtractionSyringeItem::new,
                    new Item.Properties().durability(64));

    /** Capsula de Estase: captura um especime vivo inteiro. */
    public static final DeferredItem<StasisCapsuleItem> STASIS_CAPSULE =
            ModRegistries.ITEMS.registerItem("stasis_capsule", StasisCapsuleItem::new,
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Amostra Genetica: frasco cheio, carrega um {@link GeneSample}. */
    public static final DeferredItem<Item> GENE_SAMPLE =
            ModRegistries.ITEMS.registerSimpleItem("gene_sample",
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Embriao Instavel: genoma sequenciado ({@link EmbryoGenome}), pronto para a incubacao. */
    public static final DeferredItem<Item> UNSTABLE_EMBRYO =
            ModRegistries.ITEMS.registerSimpleItem("unstable_embryo",
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    /** Biomassa: polpa organica do Extrator; combustivel (nutrientes) das maquinas biologicas. */
    public static final DeferredItem<Item> BIOMASS =
            ModRegistries.ITEMS.registerSimpleItem("biomass", new Item.Properties());

    /** Embriao Maduro: saido da Incubadora, dormente, carrega o genoma final do chefe. */
    public static final DeferredItem<Item> MATURED_EMBRYO =
            ModRegistries.ITEMS.registerSimpleItem("matured_embryo",
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    public static ItemStack geneSample(GeneSample sample) {
        ItemStack stack = new ItemStack(GENE_SAMPLE.get());
        stack.set(ModDataComponents.GENE_SAMPLE.get(), sample);
        return stack;
    }

    public static ItemStack embryo(EmbryoGenome genome) {
        ItemStack stack = new ItemStack(UNSTABLE_EMBRYO.get());
        stack.set(ModDataComponents.EMBRYO_GENOME.get(), genome);
        return stack;
    }

    public static ItemStack maturedEmbryo(EmbryoGenome genome) {
        ItemStack stack = new ItemStack(MATURED_EMBRYO.get());
        stack.set(ModDataComponents.EMBRYO_GENOME.get(), genome);
        return stack;
    }

    static void bootstrap() {
        // Forca o carregamento da classe para que os campos acima sejam registrados.
    }
}
