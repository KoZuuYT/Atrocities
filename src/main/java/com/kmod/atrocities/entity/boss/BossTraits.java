package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Trait;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Tracos com comportamento proprio nos chefes. Os bonus numericos (armadura, dano, vida, velocidade)
 * vem do campo {@code boss_attributes} de cada traco no datapack; aqui ficam so os efeitos especiais.
 * Tracos removidos por datapack simplesmente nao aparecem no genoma e nao fazem nada.
 */
public final class BossTraits {

    private BossTraits() {
    }

    /** Golpes envenenam. */
    public static final ResourceKey<Trait> HEMOTOXIN = key("hemotoxin");
    /** Golpes causam sangramento (Wither). */
    public static final ResourceKey<Trait> ANTICOAGULANT = key("anticoagulant");
    /** Golpes causam nausea e fome. */
    public static final ResourceKey<Trait> TOXIC_BILE = key("toxic_bile");
    /** Golpes incendeiam; o chefe ignora fogo e lava. */
    public static final ResourceKey<Trait> THERMAL_ORGANS = key("thermal_organs");
    /** Aura de Escuridao a noite e golpes que cegam. */
    public static final ResourceKey<Trait> NOCTURNAL_MEMBRANES = key("nocturnal_membranes");
    /** Regenera uma fracao da vida maxima por segundo. */
    public static final ResourceKey<Trait> REGENERATIVE_TISSUE = key("regenerative_tissue");
    /** Devolve parte do dano corpo a corpo a quem bate. */
    public static final ResourceKey<Trait> CHITIN_SPINES = key("chitin_spines");
    /** Libera nuvens de esporos ao ser ferido. */
    public static final ResourceKey<Trait> SPORE_SACS = key("spore_sacs");
    /** Chama crias da linhagem periodicamente. */
    public static final ResourceKey<Trait> HIVE_MIND = key("hive_mind");
    /** Teleporta-se ao ser atingido por projeteis ou quando o alvo foge. */
    public static final ResourceKey<Trait> VOID_STRANDS = key("void_strands");
    /** Explode ao morrer. */
    public static final ResourceKey<Trait> VOLATILE_GLANDS = key("volatile_glands");
    /** Fase 2 acelera ainda mais os golpes. */
    public static final ResourceKey<Trait> ADRENAL_SURGE = key("adrenal_surge");

    private static ResourceKey<Trait> key(String path) {
        return ResourceKey.create(GeneticsRegistries.TRAIT, ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, path));
    }
}
