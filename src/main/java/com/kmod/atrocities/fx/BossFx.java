package com.kmod.atrocities.fx;

import com.kmod.atrocities.network.ScreenShakePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Efeitos de impacto enviados aos clientes (somente servidor). */
public final class BossFx {

    private BossFx() {
    }

    /**
     * Treme a camera dos jogadores perto de {@code center}. A forca cai linearmente ate zero na borda do raio.
     *
     * @param radius    alcance do tremor, em blocos
     * @param intensity forca no centro
     * @param duration  duracao em ticks
     */
    public static void shake(ServerLevel level, Vec3 center, double radius, float intensity, int duration) {
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            double distanceSqr = player.position().distanceToSqr(center);
            if (distanceSqr > radiusSqr) {
                continue;
            }
            float falloff = 1f - (float) Math.sqrt(distanceSqr) / (float) radius;
            float strength = Mth.clamp(intensity * (0.35f + 0.65f * falloff), 0f, 3f);
            if (strength > 0.03f && player.connection.hasChannel(ScreenShakePayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new ScreenShakePayload(strength, duration));
            }
        }
    }
}
