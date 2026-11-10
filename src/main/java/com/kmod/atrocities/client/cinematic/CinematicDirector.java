package com.kmod.atrocities.client.cinematic;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.network.CutscenePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Diretor das cutscenes dos chefes (somente cliente).
 * <ul>
 *   <li><b>Camera</b>: percorre um trilho de quadros-chave ao redor do chefe (com colisao contra blocos),
 *   entrando e saindo suavemente da visao do jogador.</li>
 *   <li><b>Tela</b>: faixas de cinema, vinheta, cartao de titulo com letras surgindo uma a uma, faixa
 *   vermelha que se abre, clarao no rugido e a dica da mecanica da fase.</li>
 *   <li><b>Congelamento</b>: o jogador nao anda nem ataca; entidades dentro da arena param no cliente tambem.</li>
 * </ul>
 */
public final class CinematicDirector {

    /** Quadro-chave do trilho: angulo em volta do chefe, distancia e altura (em "tamanhos" do chefe). */
    private record Key(float time, float yaw, float distance, float height, float look) {
    }

    private static final Key[] INTRO = {
            new Key(0.00f, 160f, 3.4f, 1.6f, 0.10f),
            new Key(0.30f, 100f, 2.6f, 0.5f, 0.30f),
            new Key(0.52f, 35f, 1.7f, 0.15f, 0.60f),
            new Key(0.70f, 10f, 1.35f, 0.35f, 0.80f),
            new Key(1.00f, -15f, 1.9f, 0.6f, 0.70f)};

    private static final Key[] PHASE = {
            new Key(0.00f, -35f, 1.25f, 0.55f, 0.75f),
            new Key(0.45f, 20f, 1.6f, 0.35f, 0.70f),
            new Key(0.60f, 40f, 2.3f, 0.9f, 0.55f),
            new Key(1.00f, 95f, 2.6f, 1.1f, 0.50f)};

    /** Ticks antes do fim em que o chefe ruge (entrada) ou explode na transicao (fase). */
    private static final int INTRO_ROAR_BEFORE_END = AtrocityBoss.EMERGE_ROAR_AT;
    private static final int PHASE_CLIMAX_BEFORE_END = AtrocityBoss.TRANSITION_CLIMAX_AT;

    private static final int BLEND_IN = 10;
    private static final int BLEND_OUT = 14;

    private static boolean active;
    private static int bossId;
    private static int kind;
    private static int phase;
    private static int duration;
    private static int elapsed;
    private static float radius;
    private static Vec3 center = Vec3.ZERO;
    private static float frontYaw;
    private static float mirror = 1f;

    /** Pose calculada pela camera do cutscene. */
    public record Pose(Vec3 position, float yaw, float pitch, boolean detached) {
    }

    private CinematicDirector() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, event -> {
            if (active) {
                Input input = event.getInput();
                input.forwardImpulse = 0;
                input.leftImpulse = 0;
                input.up = input.down = input.left = input.right = false;
                input.jumping = false;
                input.shiftKeyDown = false;
            }
        });
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, event -> {
            if (active) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            if (active) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(RenderHandEvent.class, event -> {
            if (active) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, CinematicDirector::renderOverlay);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFov.class, event -> {
            if (active) {
                float weight = weight((float) event.getPartialTick());
                event.setFOV(Mth.lerp(weight, event.getFOV(), Math.min(event.getFOV(), 55.0)));
            }
        });
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Pre.class, CinematicDirector::onEntityTick);
    }

    /** Chamado pelo payload do servidor. */
    public static void play(CutscenePayload payload) {
        Minecraft.getInstance().execute(() -> {
            if (payload.duration() <= 0) {
                if (active && payload.bossId() == bossId) {
                    active = false;
                }
                return;
            }
            active = true;
            bossId = payload.bossId();
            kind = payload.kind();
            phase = payload.phase();
            duration = payload.duration();
            radius = payload.radius();
            center = payload.center();
            elapsed = 0;
            mirror = phase == 3 ? -1f : 1f;
            Entity boss = boss();
            frontYaw = boss != null ? ((AtrocityBoss) boss).yBodyRot : 0f;
            if (boss == null && Minecraft.getInstance().player != null) {
                frontYaw = Minecraft.getInstance().player.getYRot() + 180f;
            }
        });
    }

    public static boolean isActive() {
        return active;
    }

    private static void tick() {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            active = false;
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        if (++elapsed >= duration) {
            active = false;
        }
    }

    @Nullable
    private static AtrocityBoss boss() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.getEntity(bossId) instanceof AtrocityBoss boss ? boss : null;
    }

    private static float time(float partialTick) {
        return Math.min(duration, elapsed + partialTick);
    }

    /** Peso da camera cinematografica (0 = visao do jogador, 1 = trilho). */
    private static float weight(float partialTick) {
        float t = time(partialTick);
        float in = Mth.clamp(t / BLEND_IN, 0f, 1f);
        float out = Mth.clamp((duration - t) / BLEND_OUT, 0f, 1f);
        return smooth(Math.min(in, out));
    }

    private static float smooth(float x) {
        return x * x * (3f - 2f * x);
    }

    /**
     * Pose da camera neste quadro, misturada com a camera normal do jogador nas pontas.
     *
     * @return {@code null} quando nao ha cutscene ou o chefe nao esta carregado
     */
    @Nullable
    public static Pose pose(Vec3 playerPosition, float playerYaw, float playerPitch, float partialTick) {
        if (!active) {
            return null;
        }
        AtrocityBoss boss = boss();
        ClientLevel level = Minecraft.getInstance().level;
        if (boss == null || level == null) {
            return null;
        }
        float t = time(partialTick);
        float progress = t / Math.max(1, duration);
        Key[] track = kind == CutscenePayload.INTRO ? INTRO : PHASE;
        int index = 0;
        while (index < track.length - 2 && progress > track[index + 1].time()) {
            index++;
        }
        Key a = track[index];
        Key b = track[index + 1];
        float local = smooth(Mth.clamp((progress - a.time()) / Math.max(1.0E-4f, b.time() - a.time()), 0f, 1f));
        float yawOffset = Mth.lerp(local, a.yaw(), b.yaw()) * mirror;
        float distance = Mth.lerp(local, a.distance(), b.distance());
        float height = Mth.lerp(local, a.height(), b.height());
        float look = Mth.lerp(local, a.look(), b.look());

        double size = Math.max(boss.getBbWidth(), boss.getBbHeight()) + 1.5;
        Vec3 base = new Vec3(Mth.lerp(partialTick, boss.xo, boss.getX()), Mth.lerp(partialTick, boss.yo, boss.getY()),
                Mth.lerp(partialTick, boss.zo, boss.getZ()));
        float yaw = (frontYaw + yawOffset) * Mth.DEG_TO_RAD;
        Vec3 direction = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        // Leve balanco de "camera na mao".
        Vec3 sway = new Vec3(Mth.sin(t * 0.07f) * 0.12, Mth.sin(t * 0.05f + 1f) * 0.08, Mth.cos(t * 0.06f) * 0.12);
        Vec3 target = base.add(0, boss.getBbHeight() * look, 0);
        Vec3 desired = base.add(direction.scale(distance * size)).add(0, height * size, 0).add(sway);

        BlockHitResult hit = level.clip(new ClipContext(target, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 back = target.subtract(hit.getLocation());
            desired = hit.getLocation().add(back.lengthSqr() > 1.0E-4 ? back.normalize().scale(0.4) : Vec3.ZERO);
        }
        Vec3 delta = target.subtract(desired);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float cineYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90f;
        float cinePitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);

        float w = weight(partialTick);
        Vec3 position = playerPosition.lerp(desired, w);
        return new Pose(position, Mth.rotLerp(w, playerYaw, cineYaw), Mth.lerp(w, playerPitch, cinePitch), w > 0.25f);
    }

    // ------------------------------------------------------------------ congelamento no cliente

    private static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!active || !entity.level().isClientSide || entity instanceof Player || entity.getId() == bossId) {
            return;
        }
        if (entity.position().distanceToSqr(center) <= radius * radius) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ sobreposicao (faixas e titulos)

    private static void renderOverlay(RenderGuiEvent.Post event) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float t = time(partial);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        AtrocityBoss boss = boss();

        // Faixas de cinema entram e saem deslizando.
        float bars = smooth(Mth.clamp(Math.min(t / 12f, (duration - t) / 12f), 0f, 1f));
        int barHeight = Math.round(height * 0.12f * bars);
        graphics.fillGradient(0, barHeight, width, barHeight + 50, alpha(0.55f * bars) << 24, 0);
        graphics.fillGradient(0, height - barHeight - 50, width, height - barHeight, 0, alpha(0.55f * bars) << 24);
        graphics.fill(0, 0, width, barHeight, 0xFF000000);
        graphics.fill(0, height - barHeight, width, height, 0xFF000000);

        int accent = boss != null ? boss.archetype().color() : 0xB01020;
        String path = boss != null ? BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).getPath() : "boss";

        // Clarao no rugido (entrada) ou na ruptura (troca de fase).
        int flashAt = duration - (kind == CutscenePayload.INTRO ? INTRO_ROAR_BEFORE_END : PHASE_CLIMAX_BEFORE_END);
        float sinceFlash = t - flashAt;
        if (sinceFlash >= 0 && sinceFlash < 10) {
            float strength = 1f - sinceFlash / 10f;
            graphics.fill(0, 0, width, height, (alpha(0.45f * strength) << 24) | (accent & 0xFFFFFF));
        }

        float fadeOut = Mth.clamp((duration - t) / 10f, 0f, 1f);
        int cx = width / 2;
        if (kind == CutscenePayload.INTRO) {
            float start = duration * 0.42f;
            if (t >= start) {
                float local = t - start;
                Component name = boss != null ? boss.getDisplayName().copy() : Component.literal("???");
                String tier = boss != null ? roman(boss.tier()) : "I";
                float labelAlpha = Mth.clamp(local / 8f, 0f, 1f) * fadeOut;
                drawTracked(graphics, font, Component.translatable("cutscene.atrocities.tier", tier).getString()
                                .toUpperCase(), cx, (int) (height * 0.56f), 1.0f, 3, 0xF2A51A, labelAlpha,
                        Integer.MAX_VALUE);
                int letters = (int) (local / 1.1f);
                drawTracked(graphics, font, name.getString().toUpperCase(), cx, (int) (height * 0.60f), 3.0f, 2,
                        accent, fadeOut, letters);
                drawLine(graphics, cx, (int) (height * 0.60f) + 30, Mth.clamp((local - 6) / 14f, 0f, 1f),
                        Math.min(width - 40, 260), accent, fadeOut);
                float epithetAlpha = Mth.clamp((local - 14) / 10f, 0f, 1f) * fadeOut;
                drawCentered(graphics, font, Component.translatable("cutscene.atrocities." + path + ".epithet")
                        .withStyle(ChatFormatting.ITALIC), cx, (int) (height * 0.60f) + 36, 1.3f, 0xD8D0D0, epithetAlpha);
                float hintAlpha = Mth.clamp((local - 26) / 10f, 0f, 1f) * fadeOut;
                drawHint(graphics, font, Component.translatable("cutscene.atrocities." + path + ".intro.hint"),
                        width, height, barHeight, hintAlpha);
            }
        } else {
            float local = t - 4;
            if (local > 0) {
                float labelAlpha = Mth.clamp(local / 6f, 0f, 1f) * fadeOut;
                drawTracked(graphics, font, Component.translatable("cutscene.atrocities.phase", roman(phase))
                                .getString().toUpperCase(), cx, (int) (height * 0.30f), 1.4f, 4, 0xE3262F, labelAlpha,
                        Integer.MAX_VALUE);
                int letters = (int) Math.max(0, (local - 6) / 1.0f);
                drawTracked(graphics, font, Component.translatable("cutscene.atrocities." + path + ".phase" + phase)
                        .getString().toUpperCase(), cx, (int) (height * 0.30f) + 16, 2.4f, 2, accent, fadeOut, letters);
                drawLine(graphics, cx, (int) (height * 0.30f) + 42, Mth.clamp((local - 10) / 12f, 0f, 1f),
                        Math.min(width - 40, 220), accent, fadeOut);
                float hintAlpha = Mth.clamp((local - 18) / 10f, 0f, 1f) * fadeOut;
                drawHint(graphics, font, Component.translatable("cutscene.atrocities." + path + ".phase" + phase + ".hint"),
                        width, height, barHeight, hintAlpha);
            }
        }
    }

    private static int alpha(float value) {
        return Mth.clamp(Math.round(value * 255f), 0, 255);
    }

    /** Cor com alfa minimo (o Font trata alfa muito baixo como opaco). */
    private static int argb(int rgb, float alpha) {
        return Math.max(5, alpha(alpha)) << 24 | (rgb & 0xFFFFFF);
    }

    /** Texto com espacamento largo entre letras, centralizado, mostrando so as {@code letters} primeiras. */
    private static void drawTracked(GuiGraphics graphics, Font font, String text, int cx, int y, float scale, int spacing,
                                    int color, float alpha, int letters) {
        if (alpha <= 0.02f || letters <= 0) {
            return;
        }
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += font.width(String.valueOf(text.charAt(i))) + spacing;
        }
        total -= spacing;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        int x = -total / 2;
        for (int i = 0; i < text.length() && i < letters; i++) {
            String ch = String.valueOf(text.charAt(i));
            // A letra mais recente surge mais clara e "cai" no lugar.
            boolean fresh = letters != Integer.MAX_VALUE && i == letters - 1;
            int c = fresh ? argb(0xFFFFFF, alpha) : argb(color, alpha);
            graphics.drawString(font, ch, x, fresh ? -1 : 0, c, true);
            x += font.width(ch) + spacing;
        }
        graphics.pose().popPose();
    }

    private static void drawCentered(GuiGraphics graphics, Font font, Component text, int cx, int y, float scale,
                                     int color, float alpha) {
        if (alpha <= 0.02f) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(cx, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, -font.width(text) / 2, 0, argb(color, alpha), true);
        graphics.pose().popPose();
    }

    /** Faixa que se abre do centro para as bordas, com um nucleo claro. */
    private static void drawLine(GuiGraphics graphics, int cx, int y, float progress, int maxWidth, int color, float alpha) {
        if (progress <= 0 || alpha <= 0.02f) {
            return;
        }
        int half = Math.round(maxWidth * 0.5f * smooth(progress));
        // Segmentos que esmaecem do centro para as pontas.
        for (int x = 0; x < half; x += 2) {
            float fade = alpha * (1f - x / (float) Math.max(1, half));
            int c = argb(color, fade);
            graphics.fill(cx + x, y, cx + x + 2, y + 2, c);
            graphics.fill(cx - x - 2, y, cx - x, y + 2, c);
        }
        graphics.fill(cx - 3, y - 1, cx + 3, y + 3, argb(0xFFFFFF, alpha));
    }

    /** Dica da mecanica, dentro da faixa preta de baixo. */
    private static void drawHint(GuiGraphics graphics, Font font, Component text, int width, int height, int barHeight,
                                 float alpha) {
        if (alpha <= 0.02f) {
            return;
        }
        int y = height - Math.max(barHeight, 24) + 6;
        for (FormattedCharSequence line : font.split(text, Math.min(width - 40, 360))) {
            graphics.drawString(font, line, width / 2 - font.width(line) / 2, y, argb(0xC8C0B8, alpha), false);
            y += 10;
        }
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }
}
