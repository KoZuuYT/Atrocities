package com.kmod.atrocities.client.vfx;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.client.cinematic.CinematicDirector;
import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Pos-processamento cinematografico (somente cliente). Um {@code PostChain} proprio
 * ({@code atrocities:shaders/post/cinematic.json}) faz bloom das mascaras emissivas, aberracao cromatica,
 * profundidade de campo nas cutscenes, refracao das ondas de choque, gradacao de cor e clarões.
 * <p>
 * A cadeia so roda quando algo pede (perto de um chefe, numa cutscene, num clarao): fora disso o custo e zero.
 * Tudo se mistura suavemente: os valores alvo mudam por tick e os atuais os seguem.
 */
public final class PostFx {

    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(Atrocities.MODID,
            "shaders/post/cinematic.json");

    /** Distorcao de uma onda de choque na tela (coordenadas 0..1). */
    public record Shock(float u, float v, float radius, float strength) {
    }

    @Nullable
    private static PostChain chain;
    private static boolean failed;
    private static int width;
    private static int height;

    // Estado suavizado.
    private static float combat;
    private static float fury;
    private static float cinematic;
    private static float danger;
    private static float tintR = 1;
    private static float tintG = 1;
    private static float tintB = 1;

    // Pulsos.
    private static float flash;
    private static float flashR = 1;
    private static float flashG = 1;
    private static float flashB = 1;
    private static float aberration;
    @Nullable
    private static Shock shock;
    private static float focus = 10;
    private static int lastHurtTime;
    private static int ticks;

    private PostFx() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, PostFx::onRender);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> reset());
    }

    private static void reset() {
        combat = fury = cinematic = danger = flash = aberration = 0;
        shock = null;
    }

    // ------------------------------------------------------------------ pulsos externos

    /** Clarao de tela; enfraquece com a distancia ate {@code range}. */
    public static void flash(Vec3 at, float range, int rgb, float intensity) {
        float strength = intensity * falloff(at, range);
        if (strength <= flash) {
            return;
        }
        flash = Math.min(1f, strength);
        flashR = (rgb >> 16 & 255) / 255f;
        flashG = (rgb >> 8 & 255) / 255f;
        flashB = (rgb & 255) / 255f;
    }

    public static void aberrate(Vec3 at, float range, float intensity) {
        aberration = Math.min(3f, Math.max(aberration, intensity * falloff(at, range)));
    }

    static void setShock(@Nullable Shock value) {
        shock = value;
    }

    private static float falloff(Vec3 at, float range) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return 0f;
        }
        double distance = player.position().distanceTo(at);
        return (float) Mth.clamp(1 - distance / Math.max(1, range), 0, 1);
    }

    // ------------------------------------------------------------------ contexto

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        ticks++;
        Player player = mc.player;
        AtrocityBoss boss = nearestBoss(mc, player);
        float targetCombat = boss != null ? 1f : 0f;
        float targetFury = boss != null && boss.maxPhase() > 1 && boss.phase() >= boss.maxPhase() ? 1f : 0f;
        float targetCinematic = CinematicDirector.isActive() ? 1f : 0f;
        float health = player.getHealth() / Math.max(1f, player.getMaxHealth());
        float targetDanger = player.isAlive() && health < 0.35f ? (0.35f - health) / 0.35f : 0f;

        combat = approach(combat, targetCombat, 0.04f);
        fury = approach(fury, targetFury, 0.03f);
        cinematic = approach(cinematic, targetCinematic, 0.08f);
        danger = approach(danger, targetDanger, 0.05f);

        if (boss != null) {
            int rgb = boss.fluidColor();
            tintR = Mth.lerp(0.05f, tintR, 0.75f + 0.25f * (rgb >> 16 & 255) / 255f);
            tintG = Mth.lerp(0.05f, tintG, 0.75f + 0.25f * (rgb >> 8 & 255) / 255f);
            tintB = Mth.lerp(0.05f, tintB, 0.75f + 0.25f * (rgb & 255) / 255f);
            focus = Mth.lerp(0.2f, focus, (float) Math.max(3, mc.gameRenderer.getMainCamera().getPosition()
                    .distanceTo(boss.position().add(0, boss.getBbHeight() * 0.5, 0))));
        }
        // Tomar dano perto de um chefe sacode as cores.
        if (player.hurtTime > lastHurtTime && combat > 0.2f) {
            aberration = Math.max(aberration, 0.9f);
        }
        lastHurtTime = player.hurtTime;

        flash *= 0.78f;
        if (flash < 0.01f) {
            flash = 0;
        }
        aberration *= 0.86f;
        if (aberration < 0.01f) {
            aberration = 0;
        }
    }

    @Nullable
    private static AtrocityBoss nearestBoss(Minecraft mc, Player player) {
        AtrocityBoss best = null;
        double bestDistance = 48 * 48;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof AtrocityBoss boss && boss.isAlive()) {
                double distance = boss.distanceToSqr(player);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = boss;
                }
            }
        }
        return best;
    }

    private static float approach(float value, float target, float speed) {
        return value < target ? Math.min(target, value + speed) : Math.max(target, value - speed);
    }

    // ------------------------------------------------------------------ render

    private static boolean active() {
        return combat > 0.001f || cinematic > 0.001f || danger > 0.001f || flash > 0.001f || aberration > 0.001f
                || (shock != null && shock.strength() > 0.001f);
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || failed
                || !AtrocitiesConfig.POST_PROCESSING.get() || !active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PostChain post = chain(mc);
        if (post == null) {
            return;
        }
        float time = ticks + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float heartbeat = (float) Math.pow(Math.max(0, Mth.sin(time * 0.35f)), 8);
        float furyPulse = fury * (0.6f + 0.4f * Mth.sin(time * 0.15f));

        post.setUniform("Bloom", 0.55f * combat + 0.45f * cinematic + 0.35f * flash);
        post.setUniform("Aberration", aberration + 0.25f * cinematic + 0.35f * furyPulse + 0.4f * danger * heartbeat);
        post.setUniform("Vignette", 0.25f * combat + 0.45f * cinematic + 0.3f * danger);
        post.setUniform("Saturation", 1f + 0.12f * combat - 0.1f * cinematic - 0.35f * danger);
        post.setUniform("Contrast", 1f + 0.06f * combat + 0.08f * cinematic);
        post.setUniform("TintR", tintR);
        post.setUniform("TintG", tintG);
        post.setUniform("TintB", tintB);
        post.setUniform("TintAmount", 0.12f * combat + 0.18f * cinematic);
        float edge = Math.max(0.35f * furyPulse, danger * (0.25f + 0.35f * heartbeat));
        post.setUniform("EdgeR", 0.75f);
        post.setUniform("EdgeG", 0.04f);
        post.setUniform("EdgeB", 0.08f);
        post.setUniform("EdgeAmount", edge);
        post.setUniform("FlashR", flashR);
        post.setUniform("FlashG", flashG);
        post.setUniform("FlashB", flashB);
        post.setUniform("Flash", flash);
        post.setUniform("Dof", 0.85f * cinematic);
        post.setUniform("FocusDepth", focus);
        post.setUniform("Near", 0.05f);
        post.setUniform("Far", mc.gameRenderer.getDepthFar());
        Shock current = shock;
        post.setUniform("ShockX", current == null ? 0.5f : current.u());
        post.setUniform("ShockY", current == null ? 0.5f : current.v());
        post.setUniform("ShockRadius", current == null ? 0f : current.radius());
        post.setUniform("ShockStrength", current == null ? 0f : current.strength());
        post.setUniform("Grain", 0.6f * cinematic);
        post.setUniform("Threshold", 0.78f - 0.12f * cinematic);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        post.process(event.getPartialTick().getGameTimeDeltaTicks());
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    @Nullable
    private static PostChain chain(Minecraft mc) {
        var target = mc.getMainRenderTarget();
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), target, CHAIN);
                chain.resize(target.width, target.height);
                width = target.width;
                height = target.height;
            } catch (Exception exception) {
                failed = true;
                Atrocities.LOGGER.error("Atrocities: pos-processamento desativado (shader falhou)", exception);
                return null;
            }
        } else if (target.width != width || target.height != height) {
            chain.resize(target.width, target.height);
            width = target.width;
            height = target.height;
        }
        return chain;
    }

    /** Recarregar recursos (F3+T) recria a cadeia com os shaders novos. */
    public static void invalidate() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        failed = false;
    }
}
