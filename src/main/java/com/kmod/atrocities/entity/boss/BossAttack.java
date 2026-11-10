package com.kmod.atrocities.entity.boss;

/**
 * Golpe do repertorio de um chefe.
 * <p>
 * {@code animation} e o nome da animacao disparada no controlador "action" do GeckoLib (por padrao igual ao
 * {@code id}); golpes novos podem reaproveitar a animacao de outro golpe.
 *
 * @param id        nome do golpe
 * @param windup    ticks de preparacao (o "telegrafo") antes do impacto
 * @param duration  duracao total do golpe, em ticks
 * @param cooldown  ticks ate o golpe poder ser usado de novo
 * @param minRange  distancia minima ao alvo
 * @param maxRange  distancia maxima ao alvo
 * @param weight    peso no sorteio entre os golpes disponiveis
 * @param minPhase  fase minima do chefe (1 a 3)
 * @param animation animacao tocada pelo golpe
 */
public record BossAttack(String id, int windup, int duration, int cooldown, float minRange, float maxRange,
                         int weight, int minPhase, String animation) {

    public BossAttack(String id, int windup, int duration, int cooldown, float minRange, float maxRange,
                      int weight, int minPhase) {
        this(id, windup, duration, cooldown, minRange, maxRange, weight, minPhase, id);
    }

    public boolean inRange(double distance) {
        return distance >= minRange && distance <= maxRange;
    }
}
