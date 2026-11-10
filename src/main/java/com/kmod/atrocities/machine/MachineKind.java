package com.kmod.atrocities.machine;

import com.kmod.atrocities.registry.ModDataComponents;
import com.kmod.atrocities.registry.ModDataMaps;
import com.kmod.atrocities.registry.ModItems;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Layout e regras de cada maquina: slots (indice, posicao na tela e papel), area da barra de
 * progresso e o que cada slot aceita. Compartilhado entre servidor (block entity) e cliente (menu e tela),
 * para que shift-click e previsoes funcionem igual dos dois lados.
 */
public enum MachineKind {

    BIOMASS_EXTRACTOR("biomass_extractor", false, 1, new Rect(80, 39, 28, 8),
            slot(56, 35, Role.INPUT), slot(116, 35, Role.OUTPUT)),

    GENETIC_CENTRIFUGE("genetic_centrifuge", false, 0, new Rect(68, 39, 40, 8),
            slot(44, 24, Role.INPUT), slot(44, 46, Role.INPUT), slot(8, 53, Role.FUEL),
            slot(116, 24, Role.OUTPUT), slot(116, 46, Role.OUTPUT)),

    GENE_SEQUENCER("gene_sequencer", false, 0, new Rect(80, 32, 28, 8),
            slot(38, 19, Role.INPUT), slot(56, 19, Role.INPUT), slot(38, 37, Role.INPUT), slot(56, 37, Role.INPUT),
            slot(8, 53, Role.FUEL), slot(116, 28, Role.OUTPUT)),

    BIOLOGICAL_INCUBATOR("biological_incubator", true, 2, new Rect(80, 34, 28, 8),
            slot(56, 30, Role.INPUT), slot(8, 53, Role.FUEL), slot(116, 30, Role.OUTPUT));

    public enum Role { INPUT, FUEL, OUTPUT }

    public record SlotSpec(int x, int y, Role role) {
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    /** Barra de nutrientes (maquinas com slot de combustivel), logo acima do slot de biomassa. */
    public static final Rect NUTRIENT_BAR = new Rect(9, 17, 14, 33);
    /** Barra do acumulador de biomassa do Extrator. */
    public static final Rect BIOMASS_BAR = new Rect(147, 18, 10, 50);

    private final String id;
    private final boolean syncsContents;
    private final int extraData;
    private final Rect progressBar;
    private final List<SlotSpec> slots;

    MachineKind(String id, boolean syncsContents, int extraData, Rect progressBar, SlotSpec... slots) {
        this.id = id;
        this.syncsContents = syncsContents;
        this.extraData = extraData;
        this.progressBar = progressBar;
        this.slots = List.of(slots);
    }

    private static SlotSpec slot(int x, int y, Role role) {
        return new SlotSpec(x, y, role);
    }

    public String id() {
        return id;
    }

    /** Se o conteudo do inventario e enviado ao cliente (a Incubadora mostra o embriao dentro do tanque). */
    public boolean syncsContents() {
        return syncsContents;
    }

    public List<SlotSpec> slots() {
        return slots;
    }

    public int slotCount() {
        return slots.size();
    }

    public Role role(int slot) {
        return slots.get(slot).role();
    }

    public Rect progressBar() {
        return progressBar;
    }

    public boolean usesNutrients() {
        return slots.stream().anyMatch(spec -> spec.role() == Role.FUEL);
    }

    /** Quantidade de valores sincronizados pelo menu: progresso, duracao, nutrientes e os extras da maquina. */
    public int dataCount() {
        return MachineBlockEntity.DATA_EXTRA + extraData;
    }

    /** O que cada slot aceita (jogador e automacao). Slots de saida nunca aceitam itens. */
    public boolean accepts(int slot, ItemStack stack) {
        return switch (role(slot)) {
            case OUTPUT -> false;
            case FUEL -> stack.is(ModItems.BIOMASS.get());
            case INPUT -> switch (this) {
                case BIOMASS_EXTRACTOR -> biomassYield(stack) > 0;
                case GENETIC_CENTRIFUGE, GENE_SEQUENCER -> stack.has(ModDataComponents.GENE_SAMPLE.get());
                case BIOLOGICAL_INCUBATOR -> stack.is(ModItems.UNSTABLE_EMBRYO.get())
                        && stack.has(ModDataComponents.EMBRYO_GENOME.get());
            };
        };
    }

    /** Pontos de biomassa que o Extrator tira de um item (data map {@code atrocities:biomass_yield}). */
    public static int biomassYield(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        BiomassYield yield = stack.getItemHolder().getData(ModDataMaps.BIOMASS_YIELD);
        return yield == null ? 0 : yield.amount();
    }
}
