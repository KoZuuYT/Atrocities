package com.kmod.atrocities.fx;

import com.kmod.atrocities.network.VfxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Efeitos visuais renderizados (somente servidor -> clientes). Cada chamada vira um {@link VfxPayload} para quem
 * esta perto; o cliente desenha geometria com brilho aditivo, distorcao e pos-processamento.
 */
public final class Vfx {

    /** Tipos de efeito. A ordem e o protocolo: so acrescente no fim. */
    public enum Kind {
        /** Anel de choque rente ao chao que cresce ate {@code radius} e distorce a tela de quem esta perto. */
        SHOCKWAVE,
        /** Esfera de energia que explode e some, com estrela de clarão. */
        BURST,
        /** Coluna de luz vertical ({@code extra} = altura). */
        PILLAR,
        /** Area de perigo que se preenche ate o golpe (segue a entidade). */
        TELEGRAPH_AREA,
        /** Faixa de perigo da entidade ate o alvo (segue os dois). */
        TELEGRAPH_LINE,
        /** Aura de carga: fios de energia espiralando para dentro da entidade. */
        CHARGE,
        /** Aureola girando sobre a cabeca de quem esta atordoado. */
        STUN,
        /** Arco de corte ({@code extra} = direcao em graus). */
        SLASH,
        /** Clarao de tela ({@code extra} = intensidade, {@code radius} = alcance). */
        FLASH,
        /** Pulso de aberracao cromatica ({@code extra} = intensidade, {@code radius} = alcance). */
        ABERRATION,
        /** Estrela de impacto pequena (golpe critico, faisca). */
        IMPACT,
        /** Fenda/portal achatado que abre e fecha (teleportes). */
        RIFT
    }

    private static final double RANGE = 96;

    private Vfx() {
    }

    public static void send(ServerLevel level, Kind kind, Vec3 at, float radius, int color, int duration,
                            Entity entity, Entity target, float extra) {
        VfxPayload payload = new VfxPayload(kind.ordinal(), at.x, at.y, at.z, radius, color & 0xFFFFFF, duration,
                entity == null ? -1 : entity.getId(), target == null ? -1 : target.getId(), extra);
        double range = Math.max(RANGE, radius * 3);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) <= range * range
                    && player.connection.hasChannel(VfxPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void shockwave(ServerLevel level, Vec3 at, float radius, int color, int duration) {
        send(level, Kind.SHOCKWAVE, at, radius, color, duration, null, null, 1f);
    }

    public static void burst(ServerLevel level, Vec3 at, float radius, int color, int duration) {
        send(level, Kind.BURST, at, radius, color, duration, null, null, 1f);
    }

    public static void pillar(ServerLevel level, Vec3 at, float radius, float height, int color, int duration) {
        send(level, Kind.PILLAR, at, radius, color, duration, null, null, height);
    }

    public static void telegraphArea(ServerLevel level, Entity owner, float radius, int color, int duration) {
        send(level, Kind.TELEGRAPH_AREA, owner.position(), radius, color, duration, owner, null, 0f);
    }

    public static void telegraphLine(ServerLevel level, Entity owner, Entity target, float width, int color, int duration) {
        send(level, Kind.TELEGRAPH_LINE, owner.position(), width, color, duration, owner, target, 0f);
    }

    public static void charge(ServerLevel level, Entity owner, int color, int duration) {
        send(level, Kind.CHARGE, owner.position(), owner.getBbWidth() * 0.5f + 1.2f, color, duration, owner, null, 0f);
    }

    public static void stun(ServerLevel level, Entity owner, int duration) {
        send(level, Kind.STUN, owner.position(), owner.getBbWidth() * 0.45f + 0.4f, 0xFFD84A, duration, owner, null, 0f);
    }

    public static void slash(ServerLevel level, Vec3 at, float radius, float yaw, int color) {
        send(level, Kind.SLASH, at, radius, color, 7, null, null, yaw);
    }

    public static void flash(ServerLevel level, Vec3 at, float range, int color, float intensity) {
        send(level, Kind.FLASH, at, range, color, 10, null, null, intensity);
    }

    public static void aberration(ServerLevel level, Vec3 at, float range, float intensity) {
        send(level, Kind.ABERRATION, at, range, 0xFFFFFF, 12, null, null, intensity);
    }

    public static void impact(ServerLevel level, Vec3 at, float size, int color) {
        send(level, Kind.IMPACT, at, size, color, 6, null, null, 0f);
    }

    public static void rift(ServerLevel level, Vec3 at, float size, int color, int duration) {
        send(level, Kind.RIFT, at, size, color, duration, null, null, 0f);
    }
}
