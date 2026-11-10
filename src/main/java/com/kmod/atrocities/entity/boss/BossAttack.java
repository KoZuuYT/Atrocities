package com.kmod.atrocities.entity.boss;

/**
 * Golpe do repertorio de um chefe.
 * <p>
 * O nome {@code id} tambem e o nome da animacao disparada no controlador "action" do GeckoLib, entao
 * {@code duration} deve bater com o comprimento da animacao (em ticks).
 *
 * @param id        nome do golpe e da animacao
 * @param windup    ticks de preparacao (o "telegrafo") antes do impacto
 * @param duration  duracao total do golpe, em ticks
 * @param cooldown  ticks ate o golpe poder ser usado de novo
 * @param minRange  distancia minima ao alvo
 * @param maxRange  distancia maxima ao alvo
 * @param weight    peso no sorteio entre os golpes disponiveis
 * @param minPhase  fase minima do chefe (1 ou 2)
 */
public record BossAttack(String id, int windup, int duration, int cooldown, float minRange, float maxRange,
                         int weight, int minPhase) {

    public boolean inRange(double distance) {
        return distance >= minRange && distance <= maxRange;
    }
}
