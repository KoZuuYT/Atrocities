package com.kmod.atrocities.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Tremor de camera dos impactos dos chefes. Varios tremores se somam (com teto) e decaem suavemente;
 * o movimento usa senos de frequencias diferentes para nao parecer um "pulo" repetitivo.
 */
public final class CameraShake {

    private static final float MAX_TRAUMA = 2.5f;

    private static float trauma;
    private static float decay;
    private static int ticks;

    private CameraShake() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, CameraShake::onCamera);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
    }

    /** Adiciona um tremor (chamado pelo payload do servidor). */
    public static void add(float intensity, int duration) {
        Minecraft.getInstance().execute(() -> {
            trauma = Math.min(MAX_TRAUMA, trauma + intensity);
            // O tremor somado termina junto com o mais recente.
            decay = trauma / Math.max(1, duration);
        });
    }

    private static void tick() {
        ticks++;
        if (trauma > 0) {
            trauma = Math.max(0, trauma - Math.max(decay, 0.02f));
            if (trauma == 0) {
                decay = 0;
            }
        }
    }

    private static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0 || Minecraft.getInstance().isPaused()) {
            return;
        }
        float time = ticks + (float) event.getPartialTick();
        // Trauma ao quadrado: tremores pequenos ficam sutis, grandes ficam violentos.
        float power = trauma * trauma * 1.6f;
        event.setYaw(event.getYaw() + power * Mth.sin(time * 2.1f) * 0.9f);
        event.setPitch(event.getPitch() + power * Mth.sin(time * 2.7f + 1.3f) * 0.7f);
        event.setRoll(event.getRoll() + power * Mth.sin(time * 1.7f + 2.6f) * 0.6f);
    }
}
