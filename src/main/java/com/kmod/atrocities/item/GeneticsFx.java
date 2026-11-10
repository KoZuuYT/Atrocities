package com.kmod.atrocities.item;

import com.kmod.atrocities.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/** Efeitos de feedback (som, particulas, mensagens) das ferramentas geneticas. Somente servidor. */
public final class GeneticsFx {

    private GeneticsFx() {
    }

    /** Recusa a acao: mensagem vermelha na action bar, som seco e um pequeno cooldown contra spam. */
    public static void reject(Player player, Item item, MutableComponent message) {
        player.displayClientMessage(message.withStyle(ChatFormatting.RED), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.GENETICS_REJECT.get(), SoundSource.PLAYERS, 0.8f, 1f);
        player.getCooldowns().addCooldown(item, 10);
    }

    public static void notify(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(Vec3.fromRGB24(rgb).toVector3f(), scale);
    }

    public static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** Filete de particulas de {@code from} ate {@code to}: o fluido "viajando" para o recipiente. */
    public static void stream(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, int steps) {
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Espiral ascendente ao redor de um ponto (efeito de contencao da capsula). */
    public static void spiral(ServerLevel level, ParticleOptions particle, Vec3 base, double radius, double height, int points) {
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double angle = t * Mth.TWO_PI * 3;
            double r = radius * (1 - t * 0.6);
            level.sendParticles(particle, base.x + Math.cos(angle) * r, base.y + t * height,
                    base.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
        }
    }
}
