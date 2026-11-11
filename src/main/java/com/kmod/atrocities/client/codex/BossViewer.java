package com.kmod.atrocities.client.codex;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.registry.ModSounds;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Vitrine 3D de um chefe dentro do Códex: o modelo GeckoLib real, animado, que gira com o mouse
 * e encena os golpes. Na primeira leitura da ficha toca a cena de revelação (o chefe rompe o chão,
 * ruge e o nome surge letra por letra). Fichas trancadas mostram só a silhueta.
 */
public final class BossViewer {

    /** Lido pelo {@code BossRenderer}: desenha o chefe como silhueta negra (ficha ainda secreta). */
    public static boolean silhouette;

    private static final Map<EntityType<?>, AtrocityBoss> DUMMIES = new HashMap<>();
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlayAndHold("emerge");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation STUNNED = RawAnimation.begin().thenLoop("stunned");

    public static final int REVEAL_TICKS = 130;
    private static final int REVEAL_EMERGE_AT = 12;
    private static final int REVEAL_ROAR_AT = 72;
    private static final int IDLE_DEMO_DELAY = 160;
    private static final int DEMO_INTERVAL = 90;

    private final CodexData.Boss data;
    private final Component name;
    private final Component epithet;
    private final boolean locked;
    @Nullable
    private final AtrocityBoss boss;

    private float yaw = 210f;
    private float pitch = -8f;
    private float zoom = 1f;
    private int clock;
    private int idleTicks;
    private int demoIndex;
    private int revealTick = -1;
    private int roarFlash;
    @Nullable
    private Component caption;
    private int captionTick;
    @Nullable
    private String pose;
    private long lastTickMillis = net.minecraft.Util.getMillis();

    public BossViewer(CodexData.Boss data, boolean locked) {
        this.data = data;
        this.locked = locked;
        this.name = data.entity().getDescription();
        this.epithet = Component.translatable(data.epithet());
        this.boss = dummy(data.entity());
        if (boss != null) {
            setPose(null, null);
        }
    }

    // ------------------------------------------------------------------ instâncias de vitrine

    @Nullable
    private static AtrocityBoss dummy(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        AtrocityBoss cached = DUMMIES.get(type);
        if (cached != null && cached.level() == mc.level) {
            return cached;
        }
        Entity created = type.create(mc.level);
        if (!(created instanceof AtrocityBoss boss)) {
            return null;
        }
        boss.codexDummy = true;
        boss.setNoAi(true);
        boss.setSilent(true);
        DUMMIES.put(type, boss);
        return boss;
    }

    public static void clear() {
        DUMMIES.clear();
    }

    // ------------------------------------------------------------------ controle

    public void tick() {
        lastTickMillis = net.minecraft.Util.getMillis();
        clock++;
        if (roarFlash > 0) {
            roarFlash--;
        }
        if (boss == null) {
            return;
        }
        boss.tickCount++;
        if (!dragging) {
            yaw += revealTick >= 0 ? 0.15f : 0.45f;
        }
        if (revealTick >= 0) {
            tickReveal();
            return;
        }
        if (locked) {
            return;
        }
        if (++idleTicks > IDLE_DEMO_DELAY && (idleTicks - IDLE_DEMO_DELAY) % DEMO_INTERVAL == 0) {
            List<CodexData.Attack> attacks = data.attacks();
            if (!attacks.isEmpty()) {
                playAttack(attacks.get(demoIndex++ % attacks.size()), false);
            }
        }
    }

    private void tickReveal() {
        int t = revealTick++;
        Minecraft mc = Minecraft.getInstance();
        if (t == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CUTSCENE_STING.get(), 1f, 0.5f));
        }
        if (t == REVEAL_EMERGE_AT) {
            setPose(EMERGE, "emerge");
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ORGAN_SPAWN.get(), 0.9f, 0.6f));
        }
        if (t == REVEAL_ROAR_AT) {
            boss.triggerAnim("action", "roar");
            roarFlash = 10;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.PHASE_SHIFT.get(), 0.8f, 0.7f));
        }
        if (t >= REVEAL_TICKS) {
            revealTick = -1;
            setPose(null, null);
        }
    }

    /** Toca a cena de revelação (primeira leitura ou botão "Reproduzir cena"). */
    public void startReveal() {
        if (boss == null || locked) {
            return;
        }
        revealTick = 0;
        yaw = 200f;
        zoom = 1f;
        caption = null;
        setPose(null, null);
    }

    public boolean revealing() {
        return revealTick >= 0;
    }

    /** Encena um golpe da lista (clique na aba Golpes, ou modo demonstração). */
    public void playAttack(CodexData.Attack attack, boolean byPlayer) {
        if (boss == null || locked || revealing()) {
            return;
        }
        if (byPlayer) {
            idleTicks = 0;
        }
        setPose(null, null);
        if (!attack.animation().isEmpty()) {
            boss.triggerAnim("action", attack.animation());
        }
        caption = Component.translatable(attack.name());
        captionTick = clock;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.5f, 0.8f));
    }

    /** Pose contínua de demonstração: "walk", "stunned" ou null (parado). */
    public void togglePose(String which) {
        if (boss == null || locked || revealing()) {
            return;
        }
        idleTicks = 0;
        if (which.equals(pose)) {
            setPose(null, null);
        } else {
            setPose(which.equals("walk") ? WALK : STUNNED, which);
        }
    }

    @Nullable
    public String pose() {
        return pose;
    }

    public void roar() {
        if (boss == null || locked || revealing()) {
            return;
        }
        idleTicks = 0;
        boss.triggerAnim("action", "roar");
        roarFlash = 8;
        caption = Component.translatable("codex.atrocities.viewer.roar");
        captionTick = clock;
    }

    private void setPose(@Nullable RawAnimation animation, @Nullable String id) {
        if (boss == null) {
            return;
        }
        boss.codexPose = animation;
        pose = id;
        AnimationController<?> main = boss.getAnimatableInstanceCache().getManagerForId(boss.getId())
                .getAnimationControllers().get("main");
        if (main != null) {
            main.forceAnimationReset();
        }
    }

    private boolean dragging;

    public void drag(double dx, double dy) {
        dragging = true;
        idleTicks = 0;
        yaw -= (float) dx * 1.6f;
        pitch = Mth.clamp(pitch + (float) dy * 0.8f, -35f, 25f);
    }

    public void release() {
        dragging = false;
    }

    public void zoom(double amount) {
        idleTicks = 0;
        zoom = Mth.clamp(zoom + (float) amount * 0.1f, 0.6f, 1.9f);
    }

    // ------------------------------------------------------------------ desenho

    public void render(GuiGraphics g, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float partial = Mth.clamp((net.minecraft.Util.getMillis() - lastTickMillis) / 50f, 0f, 1f);
        float time = clock + partial;
        int color = data.color();

        // Fundo: tanque de incubação escuro, tingido pela linhagem, com bolhas subindo.
        g.fillGradient(x, y, x + w, y + h, 0xFF07090A, CodexStyle.withAlpha(CodexStyle.darken(color, 0.35f), 255));
        RandomSource bubbles = RandomSource.create(data.entity().hashCode());
        for (int i = 0; i < 22; i++) {
            float speed = 0.25f + bubbles.nextFloat() * 0.5f;
            int bx = x + 3 + bubbles.nextInt(Math.max(1, w - 6));
            float travel = (time * speed + bubbles.nextInt(400)) % (h + 10);
            int by = y + h - (int) travel;
            if (by > y && by < y + h - 2) {
                g.fill(bx, by, bx + 1 + (i % 3 == 0 ? 1 : 0), by + 1 + (i % 3 == 0 ? 1 : 0),
                        CodexStyle.withAlpha(CodexStyle.lighten(color, 0.4f), 70 + (i % 4) * 25));
            }
        }
        // Grade de medição do laboratório.
        for (int gx = x + 8; gx < x + w; gx += 16) {
            g.fill(gx, y, gx + 1, y + h, 0x0FFFFFFF);
        }
        for (int gy = y + h - 8; gy > y; gy -= 16) {
            g.fill(x, gy, x + w, gy + 1, 0x0FFFFFFF);
        }

        if (!locked) {
            // Contraluz do tanque: brilho em camadas atras do especime, pulsando como um coracao.
            float beat = (float) Math.pow(Math.max(0f, Mth.sin(time * 0.14f)), 10);
            int cx = x + w / 2;
            int cy = y + h / 2 + 6;
            for (int layer = 0; layer < 7; layer++) {
                int rx = (int) (w * (0.48f - layer * 0.06f));
                int ry = (int) (h * (0.42f - layer * 0.05f));
                int alpha = (int) ((10 + layer * 4) * (0.75f + 0.5f * beat));
                g.fill(cx - rx, cy - ry, cx + rx, cy + ry, CodexStyle.withAlpha(color, alpha));
            }
            // Pedestal: aneis elipticos de luz sob o modelo, girando.
            int baseY = y + h - 14;
            for (int ring = 0; ring < 3; ring++) {
                float radius = w * (0.18f + ring * 0.09f);
                int segments = 40;
                for (int i = 0; i < segments; i++) {
                    float angle = i / (float) segments * Mth.TWO_PI + time * 0.03f * (ring % 2 == 0 ? 1 : -1);
                    if ((i + ring) % 4 == 0) {
                        continue;
                    }
                    int px = cx + (int) (Mth.cos(angle) * radius);
                    int py = baseY + (int) (Mth.sin(angle) * radius * 0.22f);
                    int alpha = (int) ((Mth.sin(angle) * 0.5f + 0.5f) * (150 - ring * 40) + 30);
                    g.fill(px, py, px + 2, py + 1, CodexStyle.withAlpha(CodexStyle.lighten(color, 0.3f), alpha));
                }
            }
            // Linha de varredura subindo pelo tanque.
            int scan = y + h - (int) ((time * 1.4f) % (h + 30));
            if (scan > y && scan < y + h) {
                g.fillGradient(x, scan - 10, x + w, scan, 0, CodexStyle.withAlpha(CodexStyle.lighten(color, 0.5f), 38));
                g.fill(x, scan, x + w, scan + 1, CodexStyle.withAlpha(CodexStyle.lighten(color, 0.6f), 90));
            }
        }

        float rise = 0f;
        if (revealTick >= 0 && revealTick < REVEAL_EMERGE_AT) {
            rise = 1f;
        }
        if (boss != null) {
            renderModel(g, x, y, w, h, partial, rise);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        if (locked) {
            renderLockedOverlay(g, font, x, y, w, h, time);
        } else {
            renderCaption(g, font, x, y, w, h, time);
        }
        if (revealTick >= 0) {
            renderRevealOverlay(g, font, x, y, w, h, revealTick + partial);
        }
        if (roarFlash > 0) {
            g.fill(x, y, x + w, y + h, CodexStyle.withAlpha(0xFFFFFF, (int) (roarFlash / 10f * 110)));
        }
        // Vinheta.
        for (int i = 0; i < 6; i++) {
            int a = 60 - i * 10;
            g.renderOutline(x + i, y + i, w - i * 2, h - i * 2, CodexStyle.withAlpha(0x000000, a));
        }
        g.pose().popPose();
    }

    private void renderModel(GuiGraphics g, int x, int y, int w, int h, float partial, float rise) {
        AtrocityBoss model = boss;
        float size = Math.max(model.getBbHeight(), model.getBbWidth() * 1.15f);
        float scale = h * 0.6f / size * zoom * data.viewScale();
        float bodyYaw = yaw + (dragging ? 0 : (revealTick >= 0 ? 0.15f : 0.45f) * partial);
        model.yBodyRot = model.yBodyRotO = bodyYaw;
        model.yHeadRot = model.yHeadRotO = bodyYaw;
        model.setYRot(bodyYaw);
        model.yRotO = bodyYaw;
        model.setXRot(0);
        model.xRotO = 0;

        g.enableScissor(x, y, x + w, y + h);
        PoseStack pose = g.pose();
        pose.pushPose();
        float shakeX = roarFlash > 0 ? (float) Math.sin((clock + partial) * 3.1f) * roarFlash * 0.25f : 0f;
        pose.translate(x + w / 2f + shakeX, y + h * 0.84f + rise * h, 150);
        pose.scale(scale, scale, -scale);
        pose.mulPose(new Quaternionf().rotateZ(Mth.PI).rotateX(pitch * Mth.DEG_TO_RAD));
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        silhouette = locked;
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(model, 0, 0, 0, 0f, partial, pose, g.bufferSource(),
                    LightTexture.FULL_BRIGHT));
            g.flush();
        } finally {
            silhouette = false;
            dispatcher.setRenderShadow(true);
            pose.popPose();
            Lighting.setupFor3DItems();
            g.disableScissor();
        }
    }

    private void renderCaption(GuiGraphics g, Font font, int x, int y, int w, int h, float time) {
        if (caption == null || revealTick >= 0) {
            return;
        }
        float age = time - captionTick;
        if (age > 70) {
            caption = null;
            return;
        }
        float alpha = Mth.clamp(Math.min(age / 6f, (70 - age) / 10f), 0f, 1f);
        int a = (int) (alpha * 255);
        if (a < 8) {
            return;
        }
        int tw = font.width(caption);
        int cy = y + h - 16;
        g.fill(x + w / 2 - tw / 2 - 6, cy - 3, x + w / 2 + tw / 2 + 6, cy + 11, CodexStyle.withAlpha(0x000000, a * 2 / 3));
        g.fill(x + w / 2 - tw / 2 - 6, cy - 3, x + w / 2 - tw / 2 - 5, cy + 11, CodexStyle.withAlpha(data.color(), a));
        g.drawCenteredString(font, caption, x + w / 2, cy, CodexStyle.withAlpha(0xF2E6D8, a));
    }

    private void renderLockedOverlay(GuiGraphics g, Font font, int x, int y, int w, int h, float time) {
        // Estática: o espécime ainda não foi catalogado.
        RandomSource noise = RandomSource.create((long) (time * 0.5f));
        for (int i = 0; i < 70; i++) {
            int nx = x + noise.nextInt(w);
            int ny = y + noise.nextInt(h);
            g.fill(nx, ny, nx + 1 + noise.nextInt(4), ny + 1, CodexStyle.withAlpha(0xB0141F, 40 + noise.nextInt(90)));
        }
        int band = (int) ((time * 1.7f) % (h + 20)) - 10;
        g.fill(x, y + band, x + w, y + band + 3, 0x22FF3030);
        Component secret = Component.translatable("codex.atrocities.viewer.unknown");
        int pulse = (int) (150 + 100 * Mth.sin(time * 0.2f));
        g.drawCenteredString(font, secret, x + w / 2, y + 8, CodexStyle.withAlpha(0xE3262F, pulse));
    }

    private void renderRevealOverlay(GuiGraphics g, Font font, int x, int y, int w, int h, float t) {
        // Preto total no começo, abrindo enquanto o chefe rompe o chão.
        float black = t < REVEAL_EMERGE_AT ? 1f : Mth.clamp(1f - (t - REVEAL_EMERGE_AT) / 18f, 0f, 1f);
        if (black > 0f) {
            g.fill(x, y, x + w, y + h, CodexStyle.withAlpha(0x000000, (int) (black * 255)));
        }
        // Faixas de cinema.
        float bars = Mth.clamp(Math.min(t / 10f, (REVEAL_TICKS - t) / 12f), 0f, 1f);
        int bar = (int) (h * 0.13f * bars);
        g.fill(x, y, x + w, y + bar, 0xFF000000);
        g.fill(x, y + h - bar, x + w, y + h, 0xFF000000);
        if (t < REVEAL_EMERGE_AT + 6) {
            Component stamp = Component.translatable("codex.atrocities.viewer.cataloguing");
            int a = (int) (Mth.clamp(t / 5f, 0f, 1f) * (180 + 75 * Mth.sin(t * 0.9f)));
            g.drawCenteredString(font, stamp, x + w / 2, y + h / 2 - 4, CodexStyle.withAlpha(0xE3262F, Math.max(8, a)));
        }
        if (t >= REVEAL_ROAR_AT - 4) {
            String full = name.getString();
            int letters = Mth.clamp((int) ((t - REVEAL_ROAR_AT + 4) / 1.6f), 0, full.length());
            String shown = full.substring(0, letters);
            float fade = Mth.clamp((REVEAL_TICKS - t) / 14f, 0f, 1f);
            int a = (int) (255 * fade);
            if (a > 8) {
                float scale = Math.min(1.5f, (w - 10) / (float) Math.max(1, font.width(full)));
                g.pose().pushPose();
                g.pose().translate(x + w / 2f, y + bar + 4, 0);
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, Component.literal(shown).withStyle(name.getStyle()), -font.width(full) / 2, 0,
                        CodexStyle.withAlpha(0xF2E6D8, a));
                g.pose().popPose();
                float ep = Mth.clamp((t - REVEAL_ROAR_AT - 14) / 10f, 0f, 1f) * fade;
                if (ep > 0.03f) {
                    g.drawCenteredString(font, epithet, x + w / 2, y + h - bar - 12,
                            CodexStyle.withAlpha(data.color() == 0 ? 0xE3262F : CodexStyle.lighten(data.color(), 0.35f),
                                    (int) (ep * 255)));
                }
            }
        }
    }
}
