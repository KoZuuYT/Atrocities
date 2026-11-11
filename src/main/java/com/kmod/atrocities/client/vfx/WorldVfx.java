package com.kmod.atrocities.client.vfx;

import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.fx.Vfx;
import com.kmod.atrocities.network.VfxPayload;
import com.kmod.atrocities.registry.ModItems;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Efeitos visuais renderizados no mundo (somente cliente). Cada efeito e geometria procedural com brilho aditivo:
 * aneis de choque com parede de energia, esferas de explosao com raios, colunas de luz, telegrafos de area e de
 * linha que se preenchem ate o golpe, fios de energia espiralando nas cargas, aureolas de atordoamento, arcos de
 * corte e rastros de fita dos projeteis. As particulas vanilla do servidor so complementam esta camada.
 */
public final class WorldVfx {

    private static final int MAX_EFFECTS = 256;
    private static final int TRAIL_LENGTH = 12;

    private static final class Effect {
        final Vfx.Kind kind;
        final Vec3 pos;
        final float radius;
        final int color;
        final int duration;
        final int entity;
        final int target;
        final float extra;
        final int seed;
        int age;

        Effect(Vfx.Kind kind, Vec3 pos, float radius, int color, int duration, int entity, int target, float extra) {
            this.kind = kind;
            this.pos = pos;
            this.radius = radius;
            this.color = color;
            this.duration = Math.max(1, duration);
            this.entity = entity;
            this.target = target;
            this.extra = extra;
            this.seed = (int) (Math.random() * 100_000);
        }

        int lifetime() {
            return switch (kind) {
                case TELEGRAPH_AREA, TELEGRAPH_LINE -> duration + 5;
                case STUN -> duration + 6;
                default -> duration;
            };
        }
    }

    private record Trail(ArrayDeque<Vec3> points, int color) {
    }

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static final Map<Integer, Boolean> SWINGING = new HashMap<>();

    private static int ticks;

    private WorldVfx() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, WorldVfx::onRender);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> clear());
    }

    private static boolean enabled() {
        return AtrocitiesConfig.RENDERED_VFX.get();
    }

    public static void clear() {
        EFFECTS.clear();
        TRAILS.clear();
        SWINGING.clear();
    }

    /** Efeito recebido do servidor. */
    public static void play(VfxPayload payload) {
        Minecraft.getInstance().execute(() -> {
            Vfx.Kind[] kinds = Vfx.Kind.values();
            if (payload.kind() < 0 || payload.kind() >= kinds.length) {
                return;
            }
            Vfx.Kind kind = kinds[payload.kind()];
            Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
            switch (kind) {
                case FLASH -> PostFx.flash(at, payload.radius(), payload.color(), payload.extra());
                case ABERRATION -> PostFx.aberrate(at, payload.radius(), payload.extra());
                default -> add(new Effect(kind, at, payload.radius(), payload.color(), payload.duration(),
                        payload.entity(), payload.target(), payload.extra()));
            }
        });
    }

    /** Efeito criado no proprio cliente (cortes das armas). */
    public static void local(Vfx.Kind kind, Vec3 at, float radius, int color, int duration, float extra) {
        add(new Effect(kind, at, radius, color, duration, -1, -1, extra));
    }

    private static void add(Effect effect) {
        if (!enabled()) {
            return;
        }
        if (EFFECTS.size() >= MAX_EFFECTS) {
            EFFECTS.remove(0);
        }
        EFFECTS.add(effect);
    }

    // ------------------------------------------------------------------ tick

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        ticks++;
        Iterator<Effect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            Effect effect = it.next();
            effect.age++;
            if (effect.age >= effect.lifetime() || endedEarly(mc, effect)) {
                it.remove();
            }
        }
        if (!enabled()) {
            TRAILS.clear();
            return;
        }
        tickTrails(mc);
        tickSwings(mc);
    }

    /** Cargas e atordoamentos somem junto com o estado real do chefe (golpe cancelado, atordoamento curto). */
    private static boolean endedEarly(Minecraft mc, Effect effect) {
        if (effect.entity < 0 || effect.age < 3) {
            return false;
        }
        Entity entity = mc.level.getEntity(effect.entity);
        if (entity == null || !entity.isAlive()) {
            return effect.kind != Vfx.Kind.SHOCKWAVE;
        }
        if (entity instanceof AtrocityBoss boss) {
            return switch (effect.kind) {
                case CHARGE -> boss.charge() <= 0f;
                case STUN -> !boss.isStunned() && effect.age < effect.duration;
                default -> false;
            };
        }
        return false;
    }

    private static void tickTrails(Minecraft mc) {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof BioProjectile projectile && entity.distanceToSqr(eye) < 96 * 96) {
                Trail trail = TRAILS.computeIfAbsent(entity.getId(),
                        id -> new Trail(new ArrayDeque<>(), projectileColor(projectile.kind())));
                trail.points.addFirst(entity.position().add(0, entity.getBbHeight() * 0.5, 0));
                while (trail.points.size() > TRAIL_LENGTH) {
                    trail.points.removeLast();
                }
            }
        }
        // Projeteis que sumiram: o rastro encolhe ate acabar.
        TRAILS.entrySet().removeIf(entry -> {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive()) {
                ArrayDeque<Vec3> points = entry.getValue().points;
                points.pollLast();
                points.pollLast();
                return points.size() < 2;
            }
            return false;
        });
    }

    private static int projectileColor(BioProjectile.Kind kind) {
        return switch (kind) {
            case SPINE -> 0xF4E7B0;
            case BILE -> 0x9BE03A;
            case WEB -> 0xBFC8D8;
            case BLAST -> 0xFF7A2A;
            case ORB -> 0xB06CFF;
            case SERUM -> 0x4AD8FF;
            case SERUM_VOLATILE -> 0xFF5A3A;
            case SHARD -> 0xFF4FD8;
            case GOO -> 0x2EE6A8;
        };
    }

    /** Cortes de energia das armas bioforjadas (todo jogador por perto). */
    private static void tickSwings(Minecraft mc) {
        for (Player player : mc.level.players()) {
            boolean swinging = player.swinging && player.swingTime <= 1;
            boolean was = SWINGING.getOrDefault(player.getId(), false);
            SWINGING.put(player.getId(), swinging);
            if (!swinging || was || player.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 48 * 48) {
                continue;
            }
            Item item = player.getMainHandItem().getItem();
            int color;
            float size;
            if (item == ModItems.SANGUINE_CLEAVER.get()) {
                color = 0xFF2A4A;
                size = 2.0f;
            } else if (item == ModItems.CARAPACE_MAUL.get()) {
                color = 0x8C7BFF;
                size = 2.3f;
            } else if (item == ModItems.TITAN_FIST.get()) {
                color = 0x7CFF6A;
                size = 1.6f;
            } else {
                continue;
            }
            Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
            Vec3 at = player.position().add(0, player.getBbHeight() * 0.62, 0).add(look.scale(0.6));
            local(Vfx.Kind.SLASH, at, size, color, 6, player.getYRot() + (player.getRandom().nextBoolean() ? 0 : 180));
        }
    }

    // ------------------------------------------------------------------ render

    private static Camera camera;
    private static Vec3 cam = Vec3.ZERO;
    private static Matrix4f matrix;
    private static VertexConsumer out;
    private static Vector3f camLeft;
    private static Vector3f camUp;

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (EFFECTS.isEmpty() && TRAILS.isEmpty())) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        camera = event.getCamera();
        cam = camera.getPosition();
        camLeft = camera.getLeftVector();
        camUp = camera.getUpVector();
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        out = buffers.getBuffer(VfxRenderTypes.GLOW);
        float time = ticks + partialTick;
        PostFx.Shock strongest = null;
        for (Effect effect : EFFECTS) {
            float age = Math.min(effect.age + partialTick, effect.lifetime());
            float t = Mth.clamp(age / effect.duration, 0f, 1f);
            switch (effect.kind) {
                case SHOCKWAVE -> {
                    Vec3 center = effect.pos;
                    float radius = shockwave(effect, center, t);
                    float strength = (1f - t) * Math.min(1f, effect.radius / 8f);
                    if (strongest == null || strength > strongest.strength()) {
                        strongest = project(event, center, radius, strength);
                    }
                }
                case BURST -> burst(effect.pos, effect.radius, effect.color, t, effect.seed, time);
                case PILLAR -> pillar(effect, t, age, time);
                case TELEGRAPH_AREA -> telegraphArea(mc, effect, age, partialTick, time);
                case TELEGRAPH_LINE -> telegraphLine(mc, effect, age, partialTick, time);
                case CHARGE -> charge(mc, effect, t, partialTick, time);
                case STUN -> stun(mc, effect, age, partialTick, time);
                case SLASH -> slash(effect, t);
                case IMPACT -> impact(effect.pos, effect.radius, effect.color, t, effect.seed);
                case RIFT -> rift(effect, t, time);
                default -> {
                }
            }
        }
        for (Trail trail : TRAILS.values()) {
            ribbon(trail, partialTick);
        }
        poseStack.popPose();
        buffers.endBatch(VfxRenderTypes.GLOW);
        PostFx.setShock(strongest);
    }

    /** Ponto do mundo -> coordenadas de tela (0..1) para a distorcao da onda. */
    private static PostFx.Shock project(RenderLevelStageEvent event, Vec3 center, float radius, float strength) {
        Vec3 rel = center.subtract(cam);
        double distance = rel.length();
        if (distance > 64) {
            return null;
        }
        Matrix4f viewProj = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Vector4f clip = viewProj.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f));
        float falloff = (float) Mth.clamp(1 - distance / 64, 0, 1);
        if (clip.w <= 0.05f) {
            // Atras da camera: distorce a partir do centro, mais fraco.
            return new PostFx.Shock(0.5f, 0.5f, 0.6f, strength * falloff * 0.4f);
        }
        float u = clip.x / clip.w * 0.5f + 0.5f;
        float v = clip.y / clip.w * 0.5f + 0.5f;
        float screenRadius = (float) Mth.clamp(radius / Math.max(1.0, distance) * 0.9, 0.05, 1.4);
        return new PostFx.Shock(u, v, screenRadius, strength * falloff);
    }

    // ------------------------------------------------------------------ primitivas

    private static void vertex(double x, double y, double z, int rgb, float alpha) {
        out.addVertex(matrix, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Mth.clamp(alpha, 0f, 1f));
    }

    private static void vertex(Vec3 p, int rgb, float alpha) {
        vertex(p.x, p.y, p.z, rgb, alpha);
    }

    /**
     * Faixa de anel no plano (u, v) em volta de {@code center}: do raio {@code r0} (alpha {@code a0}) ate
     * {@code r1} (alpha {@code a1}), entre os angulos dados.
     */
    private static void band(Vec3 center, Vec3 u, Vec3 v, double r0, double r1, int rgb0, float a0, int rgb1, float a1,
                             int segments, double from, double to) {
        if (r1 <= 0 || (a0 <= 0.003f && a1 <= 0.003f)) {
            return;
        }
        double step = (to - from) / segments;
        for (int i = 0; i < segments; i++) {
            double c0 = Math.cos(from + step * i);
            double s0 = Math.sin(from + step * i);
            double c1 = Math.cos(from + step * (i + 1));
            double s1 = Math.sin(from + step * (i + 1));
            vertex(center.add(u.scale(c0 * r0)).add(v.scale(s0 * r0)), rgb0, a0);
            vertex(center.add(u.scale(c0 * r1)).add(v.scale(s0 * r1)), rgb1, a1);
            vertex(center.add(u.scale(c1 * r1)).add(v.scale(s1 * r1)), rgb1, a1);
            vertex(center.add(u.scale(c1 * r0)).add(v.scale(s1 * r0)), rgb0, a0);
        }
    }

    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private static void ground(Vec3 center, double r0, double r1, int rgb, float a0, float a1, int segments) {
        band(center, X, Z, Math.max(0, r0), r1, rgb, a0, rgb, a1, segments, 0, Mth.TWO_PI);
    }

    private static Vec3 left() {
        return new Vec3(camLeft.x(), camLeft.y(), camLeft.z());
    }

    private static Vec3 up() {
        return new Vec3(camUp.x(), camUp.y(), camUp.z());
    }

    /** Disco de brilho virado para a camera (centro forte, borda transparente). */
    private static void glow(Vec3 center, double radius, int rgb, float alpha) {
        band(center, left(), up(), 0, radius, rgb, alpha, rgb, 0f, 16, 0, Mth.TWO_PI);
    }

    /** Raio de luz virado para a camera, afinando ate a ponta. */
    private static void ray(Vec3 center, double angle, double length, double width, int rgb, float alpha) {
        Vec3 l = left();
        Vec3 u = up();
        Vec3 dir = l.scale(Math.cos(angle)).add(u.scale(Math.sin(angle)));
        Vec3 side = l.scale(-Math.sin(angle)).add(u.scale(Math.cos(angle))).scale(width);
        Vec3 tip = center.add(dir.scale(length));
        vertex(center.add(side), rgb, alpha);
        vertex(tip, rgb, 0f);
        vertex(tip, rgb, 0f);
        vertex(center.subtract(side), rgb, alpha);
    }

    /** Parede cilindrica vertical (alpha some para cima). */
    private static void wall(Vec3 base, double radius, double height, int rgb, float alpha, int segments) {
        if (alpha <= 0.003f || radius <= 0) {
            return;
        }
        double step = Mth.TWO_PI / segments;
        for (int i = 0; i < segments; i++) {
            double c0 = Math.cos(step * i) * radius;
            double s0 = Math.sin(step * i) * radius;
            double c1 = Math.cos(step * (i + 1)) * radius;
            double s1 = Math.sin(step * (i + 1)) * radius;
            vertex(base.x + c0, base.y, base.z + s0, rgb, alpha);
            vertex(base.x + c0, base.y + height, base.z + s0, rgb, 0f);
            vertex(base.x + c1, base.y + height, base.z + s1, rgb, 0f);
            vertex(base.x + c1, base.y, base.z + s1, rgb, alpha);
        }
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static int segmentsFor(double radius) {
        return Mth.clamp((int) (radius * 7), 20, 120);
    }

    private static float easeOut(float t) {
        float inv = 1 - t;
        return 1 - inv * inv * inv;
    }

    private static Vec3 follow(Minecraft mc, int id, Vec3 fallback, float partialTick) {
        if (id < 0) {
            return fallback;
        }
        Entity entity = mc.level.getEntity(id);
        return entity == null ? fallback : entity.getPosition(partialTick);
    }

    // ------------------------------------------------------------------ efeitos

    private static float shockwave(Effect effect, Vec3 center, float t) {
        float e = easeOut(t);
        double radius = effect.radius * e;
        float fade = (float) Math.pow(1 - t, 1.4) * effect.extra * (effect.radius > 12 ? 0.65f : 1f);
        double width = 0.35 + effect.radius * 0.09;
        int segments = segmentsFor(effect.radius);
        Vec3 base = center.add(0, 0.06, 0);
        int hot = mix(effect.color, 0xFFFFFF, 0.55f);
        ground(base, radius - width, radius - width * 0.3, effect.color, 0f, 0.85f * fade, segments);
        band(base, X, Z, radius - width * 0.3, radius, hot, 0.85f * fade, effect.color, 0f, segments, 0, Mth.TWO_PI);
        ground(base, radius * 0.55 - width * 0.5, radius * 0.55, effect.color, 0f, 0.3f * fade, segments);
        double height = Math.min(3.2, 0.6 + effect.radius * 0.2) * (1 - t * 0.7);
        wall(base, radius - width * 0.15, height, effect.color, 0.3f * fade, segments);
        // Clarão no centro nos primeiros instantes.
        if (t < 0.35f) {
            float flash = 1 - t / 0.35f;
            ground(base, 0, Math.min(effect.radius * 0.5, 4), hot, 0.35f * flash, 0f, 24);
        }
        return (float) radius;
    }

    private static void burst(Vec3 center, float size, int color, float t, int seed, float time) {
        float e = easeOut(t);
        double radius = size * (0.3 + 0.7 * e);
        float fade = (1 - t) * (1 - t);
        int hot = mix(color, 0xFFFFFF, 0.7f);
        glow(center, radius * 1.4, color, 0.75f * fade);
        glow(center, radius * 0.55 * (1 - t), hot, fade);
        band(center, left(), up(), radius * 0.78, radius * 0.92, color, 0f, hot, 0.7f * fade, 32, 0, Mth.TWO_PI);
        band(center, left(), up(), radius * 0.92, radius, hot, 0.7f * fade, color, 0f, 32, 0, Mth.TWO_PI);
        int rays = 7;
        double spin = seed + time * 0.02;
        for (int i = 0; i < rays; i++) {
            double angle = spin + i * Mth.TWO_PI / rays + Math.sin(seed * 13.1 + i) * 0.3;
            double length = radius * (1.6 + 0.8 * Math.abs(Math.sin(seed * 7.7 + i * 2.3))) * (1 - t * 0.4);
            ray(center, angle, length, radius * 0.07, hot, 0.8f * fade);
        }
    }

    private static void pillar(Effect effect, float t, float age, float time) {
        float fadeIn = Math.min(1f, age / 3f);
        float fade = (float) Math.pow(1 - t, 1.2) * fadeIn;
        double radius = effect.radius * (1 - 0.55 * t);
        double height = effect.extra > 0 ? effect.extra : 24;
        int hot = mix(effect.color, 0xFFFFFF, 0.65f);
        Vec3 base = effect.pos;
        wall(base, radius, height, effect.color, 0.65f * fade, 24);
        wall(base, radius * 0.45, height * 1.1, hot, 0.9f * fade, 16);
        ground(base.add(0, 0.05, 0), 0, radius * 2.6, effect.color, 0.55f * fade, 0f, 32);
        for (int k = 0; k < 3; k++) {
            double y = ((time * 0.05 + k / 3.0) % 1.0) * height;
            float a = (float) (0.4 * fade * (1 - y / height));
            ground(base.add(0, y, 0), radius * 1.05, radius * 1.45, effect.color, a, 0f, 24);
        }
    }

    private static void telegraphArea(Minecraft mc, Effect effect, float age, float partialTick, float time) {
        Vec3 center = follow(mc, effect.entity, effect.pos, partialTick).add(0, 0.05, 0);
        double radius = effect.radius;
        float progress = Mth.clamp(age / effect.duration, 0f, 1f);
        float out = age > effect.duration ? 1 - (age - effect.duration) / 5f : 1f;
        float pulse = 0.75f + 0.25f * Mth.sin(time * 0.6f);
        int danger = effect.color;
        int hot = mix(danger, 0xFFE070, progress);
        int segments = segmentsFor(radius);
        ground(center, 0, radius, danger, 0.05f * out, 0.16f * pulse * out, segments);
        band(center, X, Z, radius - 0.22, radius, danger, 0f, hot, 0.95f * out, segments, 0, Mth.TWO_PI);
        band(center, X, Z, radius, radius + 0.12, hot, 0.95f * out, danger, 0f, segments, 0, Mth.TWO_PI);
        double fill = radius * progress;
        ground(center, fill - 0.6, fill, hot, 0f, 0.75f * out, segments);
        // Marcas radiais girando na borda.
        int marks = 12;
        double spin = time * 0.03;
        for (int i = 0; i < marks; i++) {
            double from = spin + i * Mth.TWO_PI / marks;
            band(center, X, Z, radius - 0.75, radius - 0.35, hot, 0.6f * out, hot, 0.6f * out, 2, from, from + 0.12);
        }
        if (age > effect.duration) {
            ground(center, 0, radius, mix(danger, 0xFFFFFF, 0.5f), 0.5f * out, 0.2f * out, segments);
        }
    }

    private static void telegraphLine(Minecraft mc, Effect effect, float age, float partialTick, float time) {
        Vec3 from = follow(mc, effect.entity, effect.pos, partialTick);
        Entity target = effect.target >= 0 ? mc.level.getEntity(effect.target) : null;
        if (target == null) {
            return;
        }
        Vec3 to = target.getPosition(partialTick);
        Vec3 flat = new Vec3(to.x - from.x, 0, to.z - from.z);
        double length = flat.length();
        if (length < 0.5) {
            return;
        }
        Vec3 dir = flat.scale(1 / length);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        float progress = Mth.clamp(age / effect.duration, 0f, 1f);
        float out = age > effect.duration ? 1 - (age - effect.duration) / 5f : 1f;
        double width = Math.max(0.6, effect.radius);
        int danger = effect.color;
        int hot = mix(danger, 0xFFE070, progress);
        double y0 = from.y + 0.06;
        double y1 = to.y + 0.06;
        // Faixa: centro fraco, bordas fortes.
        for (int edge = -1; edge <= 1; edge += 2) {
            Vec3 outer = side.scale(edge * width * 0.5);
            Vec3 inner = side.scale(edge * (width * 0.5 - 0.14));
            vertex(from.x + outer.x, y0, from.z + outer.z, hot, 0.9f * out);
            vertex(to.x + outer.x, y1, to.z + outer.z, hot, 0.9f * out);
            vertex(to.x + inner.x, y1, to.z + inner.z, danger, 0.15f * out);
            vertex(from.x + inner.x, y0, from.z + inner.z, danger, 0.15f * out);
        }
        // Setas correndo em direcao ao alvo, ate onde a carga ja chegou.
        double spacing = 1.4;
        double offset = (time * 0.12) % spacing;
        for (double s = offset; s < length; s += spacing) {
            if (s / length > progress + 0.12) {
                break;
            }
            double k = s / length;
            double y = Mth.lerp(k, y0, y1) + 0.01;
            Vec3 tip = new Vec3(from.x + dir.x * s, y, from.z + dir.z * s);
            for (int edge = -1; edge <= 1; edge += 2) {
                Vec3 wing = tip.subtract(dir.scale(0.55)).add(side.scale(edge * width * 0.36));
                Vec3 thick = dir.scale(0.22);
                vertex(wing, hot, 0f);
                vertex(wing.add(thick), hot, 0.85f * out);
                vertex(tip.add(thick), hot, 0.85f * out);
                vertex(tip, hot, 0.2f * out);
            }
        }
        ground(new Vec3(to.x, y1, to.z), 0.9, 1.3, hot, 0f, 0.8f * out, 28);
        ground(new Vec3(to.x, y1, to.z), 0, 1.3 * progress, danger, 0.25f * out, 0.05f * out, 28);
    }

    private static void charge(Minecraft mc, Effect effect, float t, float partialTick, float time) {
        Entity owner = effect.entity >= 0 ? mc.level.getEntity(effect.entity) : null;
        if (owner == null) {
            return;
        }
        Vec3 center = owner.getPosition(partialTick).add(0, owner.getBbHeight() * 0.55, 0);
        double shell = effect.radius;
        int hot = mix(effect.color, 0xFFFFFF, 0.6f);
        int wisps = 8 + Math.round(t * 8);
        for (int i = 0; i < wisps; i++) {
            double a0 = (effect.seed * 0.37 + i * 2.399) % Mth.TWO_PI;
            double speed = 0.035 * (1 + (i % 3) * 0.35) * (1 + t);
            double tilt = Math.sin(a0 * 3.1) * 0.7;
            Vec3 previous = null;
            for (int j = 0; j < 7; j++) {
                double f = ((time - j * 0.9) * speed + i / (double) wisps) % 1.0;
                if (f < 0) {
                    f += 1;
                }
                double r = shell * (1 - f);
                double angle = a0 + f * Mth.TWO_PI * 1.5;
                Vec3 p = center.add(Math.cos(angle) * r, tilt * r * 0.6, Math.sin(angle) * r);
                if (previous != null && previous.distanceToSqr(p) < shell * shell * 0.25) {
                    float fade = (1 - j / 7f) * (float) (0.35 + 0.65 * f) * (0.5f + 0.5f * t);
                    segment(previous, p, 0.05 + 0.06 * t, j == 0 ? hot : effect.color, fade);
                }
                previous = p;
            }
        }
        float pulse = 0.8f + 0.2f * Mth.sin(time * (0.4f + t));
        glow(center, 0.35 + shell * 0.35 * t, effect.color, (0.25f + 0.55f * t) * pulse);
        glow(center, 0.15 + shell * 0.12 * t, hot, 0.6f * t);
    }

    /** Segmento de fita virado para a camera entre dois pontos. */
    private static void segment(Vec3 a, Vec3 b, double width, int rgb, float alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 toCam = cam.subtract(a);
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize().scale(width);
        vertex(a.add(side), rgb, alpha);
        vertex(b.add(side), rgb, alpha);
        vertex(b.subtract(side), rgb, alpha);
        vertex(a.subtract(side), rgb, alpha);
    }

    private static void stun(Minecraft mc, Effect effect, float age, float partialTick, float time) {
        Entity owner = effect.entity >= 0 ? mc.level.getEntity(effect.entity) : null;
        if (owner == null) {
            return;
        }
        float in = Math.min(1f, age / 4f);
        float out = age > effect.duration ? 1 - (age - effect.duration) / 6f : 1f;
        float fade = in * out;
        Vec3 center = owner.getPosition(partialTick).add(0, owner.getBbHeight() + 0.35, 0);
        double radius = effect.radius;
        int hot = mix(effect.color, 0xFFFFFF, 0.5f);
        double spin = time * 0.12;
        band(center, X, Z, radius * 0.82, radius, effect.color, 0f, hot, 0.7f * fade, 36, spin, spin + Mth.TWO_PI);
        band(center, X, Z, radius, radius * 1.12, hot, 0.7f * fade, effect.color, 0f, 36, spin, spin + Mth.TWO_PI);
        for (int i = 0; i < 3; i++) {
            double angle = spin * 1.6 + i * Mth.TWO_PI / 3;
            Vec3 star = center.add(Math.cos(angle) * radius, Math.sin(time * 0.25 + i) * 0.12, Math.sin(angle) * radius);
            glow(star, 0.28, hot, 0.9f * fade);
            for (int r = 0; r < 4; r++) {
                ray(star, r * Mth.HALF_PI + time * 0.1, 0.42, 0.04, 0xFFFFFF, 0.9f * fade);
            }
        }
    }

    private static void slash(Effect effect, float t) {
        double yaw = Math.toRadians(effect.extra);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        double span = Math.toRadians(150);
        float head = Math.min(1f, easeOut(Math.min(1f, t * 1.6f)));
        float tail = Math.max(0f, head - 0.55f - t * 0.3f);
        float fade = 1 - t * t;
        int hot = mix(effect.color, 0xFFFFFF, 0.7f);
        int segments = 22;
        double radius = effect.radius;
        for (int i = 0; i < segments; i++) {
            float u0 = tail + (head - tail) * i / segments;
            float u1 = tail + (head - tail) * (i + 1) / segments;
            float w0 = (u0 - tail) / Math.max(0.001f, head - tail);
            float w1 = (u1 - tail) / Math.max(0.001f, head - tail);
            Vec3 o0 = arc(effect.pos, forward, right, radius, span, u0);
            Vec3 o1 = arc(effect.pos, forward, right, radius, span, u1);
            Vec3 i0 = arc(effect.pos, forward, right, radius * (1 - 0.3 * w0), span, u0);
            Vec3 i1 = arc(effect.pos, forward, right, radius * (1 - 0.3 * w1), span, u1);
            vertex(i0, effect.color, 0f);
            vertex(o0, hot, 0.95f * w0 * fade);
            vertex(o1, hot, 0.95f * w1 * fade);
            vertex(i1, effect.color, 0f);
        }
    }

    /** Ponto no arco de corte: vai da direita para a esquerda e desce na diagonal. */
    private static Vec3 arc(Vec3 center, Vec3 forward, Vec3 right, double radius, double span, float u) {
        double angle = -span / 2 + span * u;
        Vec3 dir = forward.scale(Math.cos(angle)).add(right.scale(-Math.sin(angle)));
        return center.add(dir.scale(radius)).add(0, (0.5 - u) * radius * 0.45, 0);
    }

    private static void impact(Vec3 center, float size, int color, float t, int seed) {
        float e = easeOut(t);
        double radius = size * (0.4 + 1.2 * e);
        float fade = (1 - t) * (1 - t);
        int hot = mix(color, 0xFFFFFF, 0.75f);
        glow(center, radius * 0.7, color, 0.8f * fade);
        for (int i = 0; i < 4; i++) {
            ray(center, seed + i * Mth.HALF_PI, radius * 1.6, radius * 0.08, hot, fade);
        }
    }

    private static void rift(Effect effect, float t, float time) {
        float open = Mth.sin(Mth.PI * t);
        if (open <= 0.01f) {
            return;
        }
        Vec3 center = effect.pos;
        Vec3 horizontal = left().scale(0.45 * open);
        Vec3 vertical = new Vec3(0, 1, 0).scale(open);
        double size = effect.radius;
        int hot = mix(effect.color, 0xFFFFFF, 0.6f);
        band(center, horizontal, vertical, size * 0.75, size, effect.color, 0f, hot, 0.9f, 40, 0, Mth.TWO_PI);
        band(center, horizontal, vertical, size, size * 1.2, hot, 0.9f, effect.color, 0f, 40, 0, Mth.TWO_PI);
        band(center, horizontal, vertical, 0, size * 0.75, effect.color, 0.35f, effect.color, 0.1f, 40, 0, Mth.TWO_PI);
        for (int i = 0; i < 5; i++) {
            double angle = time * 0.08 + i * Mth.TWO_PI / 5;
            ray(center, angle, size * 1.5 * open, size * 0.06, hot, 0.5f * open);
        }
    }

    private static void ribbon(Trail trail, float partialTick) {
        ArrayDeque<Vec3> points = trail.points;
        if (points.size() < 2) {
            return;
        }
        int count = points.size();
        int index = 0;
        Vec3 previous = null;
        int hot = mix(trail.color, 0xFFFFFF, 0.5f);
        for (Vec3 point : points) {
            if (previous != null) {
                float k = index / (float) count;
                segment(previous, point, 0.16 * (1 - k), k < 0.2f ? hot : trail.color, 0.85f * (1 - k));
            }
            previous = point;
            index++;
        }
        glow(points.peekFirst(), 0.35, trail.color, 0.6f);
    }
}
