package com.kmod.atrocities.registry;

import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.item.BileLauncherItem;
import com.kmod.atrocities.item.BioThrowableItem;
import com.kmod.atrocities.item.CarapaceMaulItem;
import com.kmod.atrocities.item.CodexItem;
import com.kmod.atrocities.item.ExtractionSyringeItem;
import com.kmod.atrocities.item.SanguineCleaverItem;
import com.kmod.atrocities.item.MaturedEmbryoItem;
import com.kmod.atrocities.item.StasisCapsuleItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
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

    /** Embriao Maduro: saido da Incubadora; usado no chao, desperta o chefe do seu genoma. */
    public static final DeferredItem<MaturedEmbryoItem> MATURED_EMBRYO =
            ModRegistries.ITEMS.registerItem("matured_embryo", MaturedEmbryoItem::new,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    /** Espinho Quitinoso: projetil do Colosso Quitinoso; tambem pode ser arremessado. */
    public static final DeferredItem<BioThrowableItem> CHITIN_SPINE =
            ModRegistries.ITEMS.registerItem("chitin_spine",
                    props -> new BioThrowableItem(props, BioProjectile.Kind.SPINE, 12f, 2.2f, 12),
                    new Item.Properties().stacksTo(16));

    /** Bolota de Bile: projetil da Legiao Coalescida; arremessada, vira nuvem toxica. Municao do Lanca-Bile. */
    public static final DeferredItem<BioThrowableItem> BILE_GLOB =
            ModRegistries.ITEMS.registerItem("bile_glob",
                    props -> new BioThrowableItem(props, BioProjectile.Kind.BILE, 6f, 1.3f, 20),
                    new Item.Properties().stacksTo(16));

    // ------------------------------------------------------------------ loot dos chefes (Fase 5)

    /** Coracao Aberrante: cai de todo chefe (mais quanto maior o tier). Comida poderosa e base das armas. */
    public static final DeferredItem<Item> ABERRANT_HEART =
            ModRegistries.ITEMS.registerSimpleItem("aberrant_heart", new Item.Properties()
                    .rarity(Rarity.RARE).stacksTo(16)
                    .food(new FoodProperties.Builder().nutrition(6).saturationModifier(1.2f).alwaysEdible()
                            .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3), 1f)
                            .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 200, 1), 1f)
                            .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 0), 1f)
                            .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 160, 0), 1f)
                            .build()));

    /** Placa de Carapaca: trofeu do Colosso Quitinoso. */
    public static final DeferredItem<Item> COLOSSUS_CARAPACE =
            ModRegistries.ITEMS.registerSimpleItem("colossus_carapace",
                    new Item.Properties().rarity(Rarity.RARE).fireResistant());

    /** Presa da Matriarca: trofeu da Matriarca Hemofaga. */
    public static final DeferredItem<Item> MATRIARCH_FANG =
            ModRegistries.ITEMS.registerSimpleItem("matriarch_fang",
                    new Item.Properties().rarity(Rarity.RARE).fireResistant());

    /** Nucleo da Legiao: trofeu da Legiao Coalescida. */
    public static final DeferredItem<Item> LEGION_CORE =
            ModRegistries.ITEMS.registerSimpleItem("legion_core",
                    new Item.Properties().rarity(Rarity.RARE).fireResistant());

    /** Marreta de Carapaca: arma pesada com Impacto Sismico. */
    public static final DeferredItem<CarapaceMaulItem> CARAPACE_MAUL =
            ModRegistries.ITEMS.registerItem("carapace_maul", CarapaceMaulItem::new,
                    new Item.Properties().rarity(Rarity.EPIC).fireResistant());

    /** Cutelo Sanguineo: arma rapida com roubo de vida. */
    public static final DeferredItem<SanguineCleaverItem> SANGUINE_CLEAVER =
            ModRegistries.ITEMS.registerItem("sanguine_cleaver", SanguineCleaverItem::new,
                    new Item.Properties().rarity(Rarity.EPIC).fireResistant());

    /** Lanca-Bile: dispara Bolotas de Bile. */
    public static final DeferredItem<BileLauncherItem> BILE_LAUNCHER =
            ModRegistries.ITEMS.registerItem("bile_launcher", BileLauncherItem::new,
                    new Item.Properties().rarity(Rarity.EPIC).fireResistant().durability(640));

    // ------------------------------------------------------------------ Codex (Fase 8)

    /** Codex Atrocitas: livro-guia ilustrado; as paginas dos chefes se revelam ao enfrenta-los. */
    public static final DeferredItem<CodexItem> CODEX =
            ModRegistries.ITEMS.registerItem("codex_atrocitas", CodexItem::new,
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

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
