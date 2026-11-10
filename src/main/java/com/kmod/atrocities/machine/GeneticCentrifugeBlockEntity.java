package com.kmod.atrocities.machine;

import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.genetics.TraitRoll;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModBlockEntities;
import com.kmod.atrocities.registry.ModDataComponents;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Centrifuga Genetica: purifica amostras.
 * <ul>
 *   <li>Uma amostra: +6 a +12 de pureza, ate o teto de {@value #REFINE_CAP}%.</li>
 *   <li>Duas amostras da mesma especie: fundem-se. Pureza = maior + 1/3 da menor (ate 100%),
 *       tracos unidos pela maior potencia; um frasco vazio sobra.</li>
 * </ul>
 */
public class GeneticCentrifugeBlockEntity extends MachineBlockEntity {

    public static final int DURATION = 200;
    public static final int REFINE_CAP = 90;
    public static final int REFINE_MIN = 6;
    public static final int REFINE_MAX = 12;

    private static final int SAMPLE_A = 0;
    private static final int SAMPLE_B = 1;
    private static final int OUTPUT = 3;
    private static final int BYPRODUCT = 4;

    public GeneticCentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENETIC_CENTRIFUGE.get(), MachineKind.GENETIC_CENTRIFUGE, pos, state);
    }

    @Override
    protected int processDuration(ServerLevel level) {
        GeneSample a = sample(inventory.getStackInSlot(SAMPLE_A));
        GeneSample b = sample(inventory.getStackInSlot(SAMPLE_B));
        if (!inventory.getStackInSlot(OUTPUT).isEmpty()) {
            return 0;
        }
        return canProcess(a, b) && (b == null || canOutput(BYPRODUCT, new ItemStack(ModItems.SAMPLE_VIAL.get())))
                ? DURATION : 0;
    }

    /** Regras de entrada, compartilhadas com a previsao da tela. */
    public static boolean canProcess(@Nullable GeneSample a, @Nullable GeneSample b) {
        if (a == null) {
            return false;
        }
        if (b != null) {
            return a.species().equals(b.species());
        }
        return a.purity() < REFINE_CAP;
    }

    @Override
    protected void workingTick(ServerLevel level) {
        if (progress % 30 == 1) {
            playSound(level, ModSounds.CENTRIFUGE_SPIN.get(), 0.4f, 0.9f + progress / (float) maxProgress * 0.6f);
        }
    }

    @Override
    protected void finish(ServerLevel level) {
        GeneSample a = sample(inventory.getStackInSlot(SAMPLE_A));
        GeneSample b = sample(inventory.getStackInSlot(SAMPLE_B));
        if (a == null) {
            return;
        }
        GeneSample result = b == null ? refine(a, level.random) : merge(a, b);
        inventory.setStackInSlot(SAMPLE_A, ItemStack.EMPTY);
        if (b != null) {
            inventory.setStackInSlot(SAMPLE_B, ItemStack.EMPTY);
            output(BYPRODUCT, new ItemStack(ModItems.SAMPLE_VIAL.get()));
        }
        output(OUTPUT, ModItems.geneSample(result));

        playSound(level, ModSounds.CENTRIFUGE_COMPLETE.get(), 0.8f, 1f + result.purity() / 200f);
        GeneticsFx.burst(level, ParticleTypes.ENCHANTED_HIT, center().add(0, 0.6, 0), 12, 0.3, 0.1);
        if (result.purity() >= 85) {
            GeneticsFx.burst(level, GeneticsFx.dust(0xFFD54F, 1f), center().add(0, 0.7, 0), 16, 0.35, 0);
        }
    }

    public static GeneSample refine(GeneSample sample, RandomSource random) {
        int gain = random.nextIntBetweenInclusive(REFINE_MIN, REFINE_MAX);
        return new GeneSample(sample.species(), Math.min(REFINE_CAP, sample.purity() + gain), sample.traits());
    }

    public static int mergedPurity(GeneSample a, GeneSample b) {
        int high = Math.max(a.purity(), b.purity());
        int low = Math.min(a.purity(), b.purity());
        return Math.min(GeneSample.MAX_PURITY, high + (int) Math.ceil(low / 3.0));
    }

    public static GeneSample merge(GeneSample a, GeneSample b) {
        Map<ResourceKey<com.kmod.atrocities.genetics.Trait>, Integer> best = new LinkedHashMap<>();
        for (TraitRoll roll : a.traits()) {
            best.merge(roll.trait(), roll.potency(), Math::max);
        }
        for (TraitRoll roll : b.traits()) {
            best.merge(roll.trait(), roll.potency(), Math::max);
        }
        List<TraitRoll> traits = new ArrayList<>();
        best.forEach((trait, potency) -> traits.add(new TraitRoll(trait, potency)));
        traits.sort(Comparator.comparing((TraitRoll roll) -> roll.trait().location()));
        return new GeneSample(a.species(), mergedPurity(a, b), traits);
    }

    static @Nullable GeneSample sample(ItemStack stack) {
        return stack.get(ModDataComponents.GENE_SAMPLE.get());
    }
}
