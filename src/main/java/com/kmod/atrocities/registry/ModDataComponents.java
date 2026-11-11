package com.kmod.atrocities.registry;

import com.kmod.atrocities.genetics.CapturedSpecimen;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.genetics.GeneSample;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;

/** Data Components do Atrocities (persistidos no item e sincronizados com o cliente). */
public final class ModDataComponents {

    private ModDataComponents() {
    }

    public static final Supplier<DataComponentType<GeneSample>> GENE_SAMPLE =
            ModRegistries.DATA_COMPONENTS.registerComponentType("gene_sample", builder -> builder
                    .persistent(GeneSample.CODEC)
                    .networkSynchronized(GeneSample.STREAM_CODEC)
                    .cacheEncoding());

    public static final Supplier<DataComponentType<EmbryoGenome>> EMBRYO_GENOME =
            ModRegistries.DATA_COMPONENTS.registerComponentType("embryo_genome", builder -> builder
                    .persistent(EmbryoGenome.CODEC)
                    .networkSynchronized(EmbryoGenome.STREAM_CODEC)
                    .cacheEncoding());

    public static final Supplier<DataComponentType<CapturedSpecimen>> CAPTURED_SPECIMEN =
            ModRegistries.DATA_COMPONENTS.registerComponentType("captured_specimen", builder -> builder
                    .persistent(CapturedSpecimen.CODEC)
                    .networkSynchronized(CapturedSpecimen.STREAM_CODEC));

    public static final Supplier<DataComponentType<com.kmod.atrocities.imbue.ImbuedGenes>> IMBUED_GENES =
            ModRegistries.DATA_COMPONENTS.registerComponentType("imbued_genes", builder -> builder
                    .persistent(com.kmod.atrocities.imbue.ImbuedGenes.CODEC)
                    .networkSynchronized(com.kmod.atrocities.imbue.ImbuedGenes.STREAM_CODEC)
                    .cacheEncoding());

    static void bootstrap() {
        // Forca o carregamento da classe para que os componentes sejam registrados.
    }
}
