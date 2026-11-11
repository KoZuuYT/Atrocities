package com.kmod.atrocities.client.fx;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Numeros de dano flutuantes sobre os chefes. O cliente compara a vida sincronizada de cada chefe a cada tick:
 * quedas viram numeros brancos (dourados e maiores quando o chefe esta atordoado/vulneravel) e curas viram
 * numeros verdes. Cada numero estoura ao surgir, sobe com leve deriva e some aos poucos.
 */
public final class DamageNumbers {

    private static final int LIFETIME = 32;
    private static final double TRACK_RANGE = 64.0;

    private static final Int2FloatOpenHashMap LAST_HEALTH = new Int2FloatOpenHashMap();
    private static final Int2FloatOpenHashMap PENDING_HEAL = new Int2FloatOpenHashMap();
    private static final List<Popup> POPUPS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private record Kind(int color, float scale, String prefix) {
    }

    private static final Kind NORMAL = new Kind(0xFFFFFF, 1f, "");
    private static final Kind CRITICAL = new Kind(0xFFC940, 1.45f, "");
    private static final Kind HEAVY = new Kind(0xFF9A3C, 1.25f, "");
    private static final Kind HEAL = new Kind(0x6EE06E, 0.9f, "+");

    private DamageNumbers() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, DamageNumbers::render);
    }

    private static final class Popup {
        final Vec3 origin;
        final double driftX;
        final double driftZ;
        final String text;
        final Kind kind;
        int age;

        Popup(Vec3 origin, String text, Kind kind) {
            this.origin = origin;
            this.text = text;
            this.kind = kind;
            this.driftX = (RANDOM.nextDouble() - 0.5) * 0.05;
            this.driftZ = (RANDOM.nextDouble() - 0.5) * 0.05;
        }
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            LAST_HEALTH.clear();
            PENDING_HEAL.clear();
            POPUPS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        POPUPS.removeIf(popup -> ++popup.age >= LIFETIME);

        IntSet seen = new IntOpenHashSet();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof AtrocityBoss boss) || boss.distanceToSqr(minecraft.player) > TRACK_RANGE * TRACK_RANGE) {
                continue;
            }
            int id = boss.getId();
            seen.add(id);
            float health = boss.getHealth();
            if (!LAST_HEALTH.containsKey(id) || boss.isEmerging()) {
                LAST_HEALTH.put(id, health);
                continue;
            }
            float delta = health - LAST_HEALTH.get(id);
            LAST_HEALTH.put(id, health);
            Vec3 at = boss.position().add((RANDOM.nextDouble() - 0.5) * boss.getBbWidth() * 0.8,
                    boss.getBbHeight() * (0.65 + RANDOM.nextDouble() * 0.35),
                    (RANDOM.nextDouble() - 0.5) * boss.getBbWidth() * 0.8);
            if (!com.kmod.atrocities.config.AtrocitiesConfig.DAMAGE_NUMBERS.get()) {
                continue;
            }
            if (delta <= -0.5f) {
                Kind kind = boss.isStunned() ? CRITICAL : -delta >= 30f ? HEAVY : NORMAL;
                String text = format(-delta) + (kind == CRITICAL ? "!" : "");
                POPUPS.add(new Popup(at, text, kind));
                if (kind != NORMAL) {
                    // Estrela de impacto atras do numero nos golpes criticos e pesados.
                    com.kmod.atrocities.client.vfx.WorldVfx.local(com.kmod.atrocities.fx.Vfx.Kind.IMPACT, at,
                            kind == CRITICAL ? 0.9f : 0.6f, kind.color(), 7, 0f);
                }
            } else if (delta > 0) {
                // Curas pequenas (regeneracao) se acumulam ate valer a pena mostrar.
                float heal = PENDING_HEAL.get(id) + delta;
                if (heal >= 3f) {
                    POPUPS.add(new Popup(at, HEAL.prefix() + format(heal), HEAL));
                    heal = 0;
                }
                PENDING_HEAL.put(id, heal);
            }
        }
        LAST_HEALTH.keySet().removeIf(id -> !seen.contains((int) id));
        PENDING_HEAL.keySet().removeIf(id -> !seen.contains((int) id));
        if (POPUPS.size() > 64) {
            POPUPS.subList(0, POPUPS.size() - 64).clear();
        }
    }

    private static final int[][] OUTLINE = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}};

    private static String format(float value) {
        return value >= 10f ? String.valueOf(Math.round(value)) : String.format("%.1f", value);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || POPUPS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partial = minecraft.isPaused() ? 0f : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Font font = minecraft.font;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();

        for (Popup popup : POPUPS) {
            float age = popup.age + partial;
            float life = age / LIFETIME;
            // Sobe rapido no inicio e desacelera (ease-out).
            double rise = 1.1 * (1 - Math.pow(1 - Math.min(1, life * 1.4), 3));
            Vec3 at = popup.origin.add(popup.driftX * age, rise, popup.driftZ * age);
            // Estouro: comeca 80% maior e assenta no tamanho normal em ~4 ticks.
            float pop = 1f + 0.8f * Math.max(0f, 1f - age / 4f);
            float distanceScale = (float) Math.max(1.0, cameraPos.distanceTo(at) / 8.0);
            float scale = 0.028f * popup.kind.scale() * pop * distanceScale;
            int alpha = (int) (255 * Mth.clamp((LIFETIME - age) / 10f, 0.05f, 1f));
            int color = (alpha << 24) | popup.kind.color();
            int shadow = (alpha << 24) | 0x1A0505;

            pose.pushPose();
            pose.translate(at.x - cameraPos.x, at.y - cameraPos.y, at.z - cameraPos.z);
            pose.mulPose(camera.rotation());
            // Estouro com leve inclinacao, como um carimbo.
            float tilt = (float) Math.signum(popup.driftX) * 0.18f * Math.max(0f, 1f - age / 6f);
            pose.mulPose(com.mojang.math.Axis.ZP.rotation(tilt));
            pose.scale(scale, -scale, scale);
            float x = -font.width(popup.text) / 2f;
            // Contorno escuro nos quatro lados para ler em qualquer fundo.
            for (int[] offset : OUTLINE) {
                font.drawInBatch(popup.text, x + offset[0], offset[1], shadow, false, pose.last().pose(), buffers,
                        Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            }
            pose.translate(0, 0, -0.01f);
            font.drawInBatch(popup.text, x, 0, color, false, pose.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
