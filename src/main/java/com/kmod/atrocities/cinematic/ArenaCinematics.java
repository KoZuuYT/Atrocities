package com.kmod.atrocities.cinematic;

import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.network.CutscenePayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cutscenes de chefe com "tempo congelado" so dentro da arena.
 * <ul>
 *   <li>Toda entidade (menos jogadores e o chefe que dirige a cena) dentro do raio para de ser atualizada:
 *   crias, projeteis e itens ficam suspensos no ar.</li>
 *   <li>Os jogadores dentro do raio no inicio assistem a cena: ficam presos no lugar, invulneraveis e sem
 *   poder atacar ou interagir. A camera e controlada no cliente ({@code CinematicDirector}).</li>
 *   <li>Quem esta fora do raio continua jogando normalmente e ve a borda da bolha de tempo.</li>
 * </ul>
 */
public final class ArenaCinematics {

    /** Raio da arena congelada, em blocos. */
    public static final double RADIUS = 40.0;

    private static final Map<UUID, Scene> ACTIVE = new HashMap<>();

    private ArenaCinematics() {
    }

    private static final class Scene {
        final UUID bossId;
        final ResourceKey<Level> dimension;
        final Vec3 center;
        final long endTick;
        final Map<UUID, Vec3> anchors = new HashMap<>();

        Scene(UUID bossId, ResourceKey<Level> dimension, Vec3 center, long endTick) {
            this.bossId = bossId;
            this.dimension = dimension;
            this.center = center;
            this.endTick = endTick;
        }

        boolean contains(Entity entity) {
            return entity.level().dimension() == dimension && entity.position().distanceToSqr(center) <= RADIUS * RADIUS;
        }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, ArenaCinematics::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ArenaCinematics::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, event -> {
            if (event.getEntity() instanceof Player player && isWatching(player)) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AttackEntityEvent.class, event -> {
            if (isWatching(event.getEntity())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.RightClickBlock.class, event -> {
            if (isWatching(event.getEntity())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.RightClickItem.class, event -> {
            if (isWatching(event.getEntity())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.EntityInteract.class, event -> {
            if (isWatching(event.getEntity())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.LeftClickBlock.class, event -> {
            if (isWatching(event.getEntity())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class,
                event -> ACTIVE.values().forEach(scene -> scene.anchors.remove(event.getEntity().getUUID())));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> ACTIVE.clear());
    }

    /**
     * Inicia uma cutscene dirigida pelo chefe.
     *
     * @param kind     {@link CutscenePayload#INTRO} ou {@link CutscenePayload#PHASE}
     * @param phase    fase em que o chefe entra
     * @param duration duracao em ticks
     */
    public static void start(AtrocityBoss boss, int kind, int phase, int duration) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = boss.position();
        Scene scene = new Scene(boss.getUUID(), level.dimension(), center, level.getGameTime() + duration);
        CutscenePayload payload = new CutscenePayload(boss.getId(), kind, phase, duration, (float) RADIUS, center);
        for (ServerPlayer player : level.players()) {
            if (!scene.contains(player)) {
                continue;
            }
            scene.anchors.put(player.getUUID(), player.position());
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            if (player.connection.hasChannel(CutscenePayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        ACTIVE.put(boss.getUUID(), scene);
    }

    /** Encerra a cutscene do chefe (morte ou remocao no meio da cena). */
    public static void stop(AtrocityBoss boss) {
        Scene scene = ACTIVE.remove(boss.getUUID());
        if (scene == null || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        CutscenePayload abort = new CutscenePayload(boss.getId(), CutscenePayload.INTRO, 1, 0, 0f, scene.center);
        for (UUID id : scene.anchors.keySet()) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer player && player.connection.hasChannel(CutscenePayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, abort);
            }
        }
    }

    /** Fim natural da cena: libera os jogadores (o cliente encerra a camera pelo proprio relogio). */
    public static void end(AtrocityBoss boss) {
        ACTIVE.remove(boss.getUUID());
    }

    public static boolean isActive(AtrocityBoss boss) {
        return ACTIVE.containsKey(boss.getUUID());
    }

    /** O jogador esta assistindo a uma cutscene (preso e invulneravel). */
    public static boolean isWatching(Player player) {
        if (ACTIVE.isEmpty() || player.level().isClientSide) {
            return false;
        }
        for (Scene scene : ACTIVE.values()) {
            if (scene.anchors.containsKey(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || entity instanceof Player || ACTIVE.containsKey(entity.getUUID())) {
            return;
        }
        for (Scene scene : ACTIVE.values()) {
            if (scene.contains(entity)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Scene> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            Scene scene = iterator.next();
            ServerLevel level = server.getLevel(scene.dimension);
            if (level == null || level.getGameTime() >= scene.endTick
                    || !(level.getEntity(scene.bossId) instanceof AtrocityBoss boss) || !boss.isAlive()) {
                iterator.remove();
                continue;
            }
            // Jogadores que assistem ficam presos no ponto em que estavam.
            scene.anchors.forEach((id, anchor) -> {
                if (level.getPlayerByUUID(id) instanceof ServerPlayer player) {
                    player.setDeltaMovement(Vec3.ZERO);
                    player.fallDistance = 0;
                    if (player.position().distanceToSqr(anchor) > 0.04) {
                        player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
                    }
                }
            });
            // Borda da bolha de tempo, visivel para quem esta do lado de fora.
            if (level.getGameTime() % 4 == 0) {
                drawBubble(level, scene);
            }
        }
    }

    private static void drawBubble(ServerLevel level, Scene scene) {
        long time = level.getGameTime();
        int points = 96;
        for (int layer = 0; layer < 3; layer++) {
            double y = scene.center.y + 0.5 + layer * 4.0;
            double ringRadius = Math.sqrt(Math.max(0, RADIUS * RADIUS - (layer * 4.0) * (layer * 4.0)));
            for (int i = 0; i < points; i++) {
                if ((i + time / 4 + layer) % 3 != 0) {
                    continue;
                }
                double angle = i / (double) points * Mth.TWO_PI + layer * 0.2;
                double x = scene.center.x + Math.cos(angle) * ringRadius;
                double z = scene.center.z + Math.sin(angle) * ringRadius;
                level.sendParticles(GeneticsFx.dust(0x7A1020, 2.2f), x, y, z, 1, 0.1, 0.4, 0.1, 0);
                if (layer == 0 && i % 6 == 0) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.5, z, 2, 0.1, 0.6, 0.1, 0.02);
                }
            }
        }
    }
}
