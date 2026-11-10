package com.kmod.atrocities.combat;

import com.kmod.atrocities.config.AtrocitiesConfig;

/**
 * Formulas de mitigacao dos chefes, pensadas para o meta de ~50 de dano por hit.
 * <p>
 * Soft cap: {@code dano = limiar + (dano - limiar) * fator} para hits acima do limiar.
 * Com os valores padrao (30 / 0.35), um hit de 50 vira 37 e um de 100 vira 54,5.
 * Janelas de vulnerabilidade (ex.: nucleo exposto) devem chamar {@link #apply(float, boolean)}
 * com {@code bypassSoftCap = true}.
 */
public final class DamageMitigation {

    private DamageMitigation() {
    }

    /** Aplica o soft cap usando os valores atuais da configuracao. */
    public static float apply(float incoming, boolean bypassSoftCap) {
        if (bypassSoftCap) {
            return incoming;
        }
        return softCap(incoming,
                AtrocitiesConfig.BOSS_SOFT_CAP_THRESHOLD.get().floatValue(),
                AtrocitiesConfig.BOSS_SOFT_CAP_FACTOR.get().floatValue());
    }

    /** Soft cap puro, sem depender da configuracao (util para testes e tooltips). */
    public static float softCap(float incoming, float threshold, float factor) {
        if (incoming <= threshold) {
            return incoming;
        }
        return threshold + (incoming - threshold) * factor;
    }
}
