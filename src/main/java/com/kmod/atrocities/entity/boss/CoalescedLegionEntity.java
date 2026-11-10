package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.genetics.Archetype;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Legiao Coalescida: massa de corpos fundidos que controla o campo com esporos e crias.
 * <p>Golpes:</p>
 * <ul>
 *   <li>Engolfar: bracos fecham sobre quem esta perto, puxando e lentificando.</li>
 *   <li>Explosao de Esporos: nuvem venenosa e repulsao ao redor.</li>
 *   <li>Atracao: tentaculos puxam todos de longe ate a massa.</li>
 *   <li>Brotamento: a massa expele crias e fica exposta (vulneravel) por alguns segundos.</li>
 *   <li>Morteiro de Bile: bolotas em arco que viram nuvens toxicas.</li>
 *   <li>Ruptura (fase 3): geiseres de esporos explodem sob os pes dos jogadores.</li>
 * </ul>
 * <p>Mecanicas:</p>
 * <ul>
 *   <li><b>Fase 1 - Biomassa</b>: cada cria viva por perto da 12% de reducao de dano a Legiao (ate 60%).
 *   Tentaculos ligam a massa as crias.</li>
 *   <li><b>Fase 2 - Fissao</b>: a Legiao se parte em tres Nucleos e fica imune. Os nucleos rastejam de volta;
 *   cada um que chega cura 6%. Quando nao sobra nenhum, ela fica atordoada por mais tempo quanto mais
 *   nucleos tiverem sido destruidos.</li>
 *   <li><b>Fase 3 - Supuracao</b>: a massa se rompe. Sem armadura e recebendo 25% a mais de dano, mas solta
 *   esporos sem parar e ganha a Ruptura.</li>
 * </ul>
 */
public class CoalescedLegionEntity extends AtrocityBoss {

    private static final BossAttack ENGULF = new BossAttack("engulf", 16, 32, 50, 0f, 4.5f, 4, 1);
    private static final BossAttack SPORE_BURST = new BossAttack("spore_burst", 20, 40, 160, 0f, 8f, 3, 1);
    private static final BossAttack PULL = new BossAttack("pull", 18, 40, 180, 6f, 20f, 3, 1);
    private static final BossAttack BUD = new BossAttack("bud", 20, 40, 360, 0f, 40f, 2, 1);
    private static final BossAttack BILE_MORTAR = new BossAttack("bile_mortar", 14, 30, 100, 6f, 30f, 3, 1);
    private static final BossAttack RUPTURE = new BossAttack("rupture", 22, 40, 180, 0f, 26f, 4, 3, "spore_burst");
    private static final List<BossAttack> ATTACKS = List.of(ENGULF, SPORE_BURST, PULL, BUD, BILE_MORTAR, RUPTURE);

    private static final int SPORE = 0x5E8C31;
    private static final int FLESH = 0x6B2A2A;
    private static final float BIOMASS_PER_MINION = 0.12f;
    private static final float BIOMASS_MAX = 0.6f;
    private static final double BIOMASS_RADIUS = 16;
    private static final float NUCLEUS_HEAL = 0.06f;
    private static final ResourceLocation RUPTURED =
            ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, "legion_ruptured");

    /** Fissao em andamento: imune e parada ate os nucleos acabarem. */
    private boolean fission;
    private int nucleiDestroyed;
    private int nucleiAbsorbed;
    private final List<Vec3> ruptureSites = new ArrayList<>();

    public CoalescedLegionEntity(EntityType<? extends CoalescedLegionEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(0.22D, 2.0D, 0.0D);
    }

    @Override
    public Archetype archetype() {
        return Archetype.COALESCED;
    }

    @Override
    protected List<BossAttack> attacks() {
        return ATTACKS;
    }

    @Override
    protected double preferredRange() {
        return 2.5;
    }

    @Override
    protected EntityType<?> fallbackMinion() {
        return EntityType.SLIME;
    }

    @Override
    protected boolean canStartAttack(BossAttack attack, LivingEntity target) {
        return attack == BUD || attack == SPORE_BURST || attack == RUPTURE || hasLineOfSight(target);
    }

    @Override
    protected Telegraph telegraph(BossAttack attack) {
        // A Ruptura desenha os proprios avisos sob os jogadores.
        return attack == RUPTURE ? Telegraph.NONE : super.telegraph(attack);
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "engulf" -> engulf(level, tick);
            case "spore_burst" -> sporeBurst(level, tick);
            case "pull" -> pull(level, tick);
            case "bud" -> bud(level, tick);
            case "bile_mortar" -> bileMortar(level, tick, target);
            case "rupture" -> rupture(level, tick, target);
            default -> {
            }
        }
    }

    private void engulf(ServerLevel level, int tick) {
        if (tick == 3) {
            playSound(ModSounds.LEGION_GURGLE.get(), 2f, 0.7f);
        }
        if (tick == ENGULF.windup()) {
            List<LivingEntity> hit = strike(position(), getBbWidth() * 0.5 + 2.5, 0.9f, -0.6, 0.1, null);
            hit.forEach(victim -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this));
            GeneticsFx.burst(level, GeneticsFx.dust(FLESH, 2f), position().add(0, 1, 0), 40, getBbWidth() * 0.6, 0.1);
            playSound(ModSounds.LEGION_ENGULF.get(), 2.5f, 0.8f);
        }
    }

    private void sporeBurst(ServerLevel level, int tick) {
        if (tick < SPORE_BURST.windup() && tick % 4 == 0) {
            GeneticsFx.burst(level, GeneticsFx.dust(SPORE, 1.5f), position().add(0, getBbHeight() * 0.7, 0), 10,
                    getBbWidth() * 0.4, 0.02);
            playSound(ModSounds.LEGION_GURGLE.get(), 1.2f, 1.2f);
        }
        if (tick == SPORE_BURST.windup()) {
            sporeCloud(level, position(), 5f, 120);
            strike(position(), 6, 0.4f, 1.5, 0.4, null);
            ring(level, GeneticsFx.dust(SPORE, 2.5f), position().add(0, 0.5, 0), 4, 40);
            GeneticsFx.burst(level, ParticleTypes.SPORE_BLOSSOM_AIR, position().add(0, getBbHeight(), 0), 60, 3, 0.1);
            playSound(ModSounds.BOSS_SPORES.get(), 3f, 0.6f);
        }
    }

    private void pull(ServerLevel level, int tick) {
        int start = PULL.windup();
        if (tick == 2) {
            playSound(ModSounds.LEGION_GURGLE.get(), 2.5f, 0.5f);
        }
        if (tick < start || tick > start + 14) {
            return;
        }
        Vec3 core = position().add(0, getBbHeight() * 0.5, 0);
        if (tick % 2 == 0) {
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(22),
                    victim -> isValidVictim(victim) && hasLineOfSight(victim))) {
                Vec3 to = core.subtract(victim.position());
                if (to.lengthSqr() > 22 * 22) {
                    continue;
                }
                Vec3 pull = to.normalize().scale(0.35);
                victim.push(pull.x, Math.max(0.05, pull.y * 0.3), pull.z);
                victim.hurtMarked = true;
                GeneticsFx.stream(level, GeneticsFx.dust(FLESH, 1.1f), victim.position().add(0, victim.getBbHeight() * 0.5, 0),
                        core, 12);
            }
        }
        if (tick == start) {
            playSound(ModSounds.LEGION_PULL.get(), 3f, 0.7f);
        }
        if (tick == start + 14) {
            strike(position(), getBbWidth() * 0.5 + 2, 0.5f, 0.8, 0.3, null);
            playSound(ModSounds.LEGION_ENGULF.get(), 2.5f, 0.6f);
        }
    }

    private void bud(ServerLevel level, int tick) {
        if (tick < BUD.windup() && tick % 2 == 0) {
            GeneticsFx.burst(level, GeneticsFx.dust(FLESH, 2f), position().add(0, getBbHeight() * 0.5, 0), 6,
                    getBbWidth() * 0.5, 0.03);
            if (tick % 6 == 0) {
                playSound(ModSounds.LEGION_GURGLE.get(), 1.8f, 0.6f);
            }
        }
        if (tick == BUD.windup()) {
            spawnMinions(level, phase() >= 2 ? 5 : 3, 8);
        }
        if (tick == BUD.duration() - 1) {
            stun(40);
        }
    }

    private void bileMortar(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick == 2) {
            playSound(ModSounds.LEGION_GURGLE.get(), 2f, 1.0f);
        }
        if (target == null || tick != BILE_MORTAR.windup()) {
            return;
        }
        int count = phase() >= 2 ? 5 : 3;
        Vec3 origin = position().add(0, getBbHeight() * 0.95, 0);
        for (int i = 0; i < count; i++) {
            Vec3 landing = target.position().add((random.nextDouble() - 0.5) * 5, 0, (random.nextDouble() - 0.5) * 5);
            Vec3 to = landing.subtract(origin);
            double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
            BioProjectile bile = new BioProjectile(level, this, BioProjectile.Kind.BILE, attackDamage(0.35f));
            bile.setPos(origin.x, origin.y, origin.z);
            float speed = (float) (0.75 + horizontal * 0.028);
            bile.shoot(to.x, to.y + horizontal * 0.55, to.z, speed, 3f);
            level.addFreshEntity(bile);
        }
        GeneticsFx.burst(level, GeneticsFx.dust(0x7FA02A, 2f), origin, 24, 0.6, 0.1);
        playSound(ModSounds.LEGION_SPIT.get(), 2.5f, 0.8f);
    }

    private void rupture(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = RUPTURE.windup();
        if (tick == 0) {
            // Um geiser sob cada jogador (e alguns ao acaso perto do alvo).
            ruptureSites.clear();
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(26),
                    this::isValidVictim)) {
                ruptureSites.add(groundNear(level, player.getX(), player.getZ()));
            }
            Vec3 around = target != null ? target.position() : position();
            while (ruptureSites.size() < 5) {
                ruptureSites.add(groundNear(level, around.x + (random.nextDouble() - 0.5) * 12,
                        around.z + (random.nextDouble() - 0.5) * 12));
            }
            playSound(ModSounds.LEGION_GURGLE.get(), 3f, 0.5f);
        }
        if (tick < start) {
            float progress = (tick + 1) / (float) start;
            if (tick % 2 == 0) {
                for (Vec3 site : ruptureSites) {
                    eruptionWarning(level, site, SPORE, progress);
                }
            }
            return;
        }
        if (tick == start) {
            for (Vec3 site : ruptureSites) {
                strike(site, 2.2, 0.7f, 0.3, 1.1, null);
                for (int h = 0; h < 6; h++) {
                    level.sendParticles(GeneticsFx.dust(SPORE, 2.4f), site.x, site.y + h * 0.8, site.z, 8, 0.4, 0.3, 0.4, 0.02);
                }
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, site.x, site.y + 3, site.z, 30, 0.8, 1.5, 0.8, 0.05);
                level.sendParticles(ParticleTypes.EXPLOSION, site.x, site.y + 0.5, site.z, 1, 0, 0, 0, 0);
                groundCrack(level, site, 1.2, 30);
                sporeCloud(level, site, 2.2f, 80);
            }
            playSound(ModSounds.BOSS_SPORES.get(), 3.5f, 0.5f);
            ruptureSites.clear();
        }
    }

    // ------------------------------------------------------------------ mecanicas

    @Override
    public boolean isShielded() {
        return fission;
    }

    @Override
    protected boolean isDormant() {
        return fission;
    }

    @Override
    protected void onShieldedHit(ServerLevel level, DamageSource source) {
        deflectFeedback(level, source, 1f);
        hint(level, "legion.fission", ChatFormatting.GREEN);
    }

    @Override
    protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float amount) {
        if (phase() >= MAX_PHASE) {
            return amount * 1.25f;
        }
        if (phase() == 1 && !isStunned()) {
            int minions = minions(BIOMASS_RADIUS).size();
            if (minions > 0) {
                float reduction = Math.min(BIOMASS_MAX, BIOMASS_PER_MINION * minions);
                hint(level, "legion.biomass", ChatFormatting.GREEN);
                if (reduction >= 0.36f) {
                    deflectFeedback(level, source, 0.7f);
                }
                return amount * (1f - reduction);
            }
        }
        return amount;
    }

    @Override
    protected void tickPhaseMechanics(ServerLevel level) {
        Vec3 core = position().add(0, getBbHeight() * 0.5, 0);
        if (phase() == 1 && tickCount % 8 == 0) {
            // Tentaculos de carne ligando a massa as crias que a protegem.
            for (LivingEntity minion : minions(BIOMASS_RADIUS)) {
                GeneticsFx.stream(level, GeneticsFx.dust(FLESH, 0.9f), minion.position().add(0, minion.getBbHeight() * 0.5, 0),
                        core, 10);
            }
        }
        if (fission) {
            if (tickCount % 6 == 0) {
                GeneticsFx.burst(level, GeneticsFx.dust(FLESH, 1.8f), core, 6, getBbWidth() * 0.4, 0.02);
                playSound(ModSounds.LEGION_GURGLE.get(), 1f, 0.5f);
            }
            if (tickCount % 10 == 0 && organs(BossOrganEntity.Variant.NUCLEUS).isEmpty()) {
                endFission(level);
            }
        }
        if (phase() >= MAX_PHASE) {
            if (tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, core.x, core.y + 0.5, core.z, 4,
                        getBbWidth() * 0.4, 0.4, getBbWidth() * 0.4, 0.02);
            }
            if (tickCount % 60 == 0) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                double distance = 3 + random.nextDouble() * 4;
                sporeCloud(level, groundNear(level, getX() + Math.cos(angle) * distance, getZ() + Math.sin(angle) * distance),
                        2.5f, 80);
            }
        }
    }

    private void endFission(ServerLevel level) {
        fission = false;
        // A massa se recompoe; quanto mais nucleos destruidos, mais ela demora para se firmar.
        GeneticsFx.burst(level, GeneticsFx.dust(FLESH, 2.6f), position().add(0, getBbHeight() * 0.5, 0), 80,
                getBbWidth() * 0.6, 0.2);
        playSound(ModSounds.LEGION_ENGULF.get(), 3f, 0.5f);
        if (nucleiDestroyed > 0) {
            stun(40 + 30 * nucleiDestroyed);
            hint(level, "legion.reformed_weak", ChatFormatting.GOLD);
        } else {
            hint(level, "legion.reformed", ChatFormatting.RED);
        }
        nucleiDestroyed = 0;
        nucleiAbsorbed = 0;
    }

    @Override
    public void onOrganDestroyed(ServerLevel level, BossOrganEntity organ) {
        if (organ.variant() == BossOrganEntity.Variant.NUCLEUS) {
            nucleiDestroyed++;
            playSound(ModSounds.LEGION_HURT.get(), 2.5f, 1.3f);
        }
    }

    @Override
    public void onOrganArrived(ServerLevel level, BossOrganEntity organ) {
        if (organ.variant() == BossOrganEntity.Variant.NUCLEUS) {
            nucleiAbsorbed++;
            heal(getMaxHealth() * NUCLEUS_HEAL);
            GeneticsFx.burst(level, ParticleTypes.HEART, position().add(0, getBbHeight(), 0), 4, 0.6, 0);
            playSound(ModSounds.LEGION_ENGULF.get(), 2.5f, 1.1f);
            hint(level, "legion.absorbed", ChatFormatting.RED);
        }
    }

    @Override
    protected void onIntroTick(ServerLevel level, int tick) {
        // Corpos se arrastando de todas as direcoes ate se fundirem na massa.
        if (tick < 70 && tick % 3 == 0) {
            Vec3 core = position().add(0, getBbHeight() * 0.4, 0);
            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                double distance = 6 + random.nextDouble() * 6;
                Vec3 from = groundNear(level, getX() + Math.cos(angle) * distance, getZ() + Math.sin(angle) * distance)
                        .add(0, 0.3, 0);
                GeneticsFx.stream(level, GeneticsFx.dust(i == 0 ? SPORE : FLESH, 1.4f), from, core, 18);
            }
            if (tick % 12 == 0) {
                playSound(ModSounds.LEGION_GURGLE.get(), 2.5f, 0.5f + random.nextFloat() * 0.3f);
            }
        }
    }

    @Override
    protected void onTransitionTick(ServerLevel level, int phase, int tick) {
        int before = TRANSITION_TICKS - TRANSITION_CLIMAX_AT;
        if (tick >= before) {
            return;
        }
        float progress = tick / (float) before;
        Vec3 core = position().add(0, getBbHeight() * 0.5, 0);
        if (phase == 2) {
            // A massa incha e lateja antes de se partir.
            level.sendParticles(GeneticsFx.dust(0x8FD03A, 1.5f + progress), core.x, core.y, core.z,
                    3 + Math.round(progress * 6), getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.02);
            if (tick % 8 == 0) {
                playSound(ModSounds.ORGAN_PULSE.get(), 2.5f, 0.6f + progress * 0.5f);
            }
        } else if (phase == MAX_PHASE) {
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, core.x, core.y + 1, core.z, 6, 1.5, 1, 1.5, 0.05);
            if (tick % 10 == 0) {
                playSound(ModSounds.LEGION_GURGLE.get(), 3f, 0.4f + progress * 0.3f);
            }
        }
    }

    @Override
    protected void onPhaseClimax(ServerLevel level, int phase) {
        Vec3 core = position().add(0, getBbHeight() * 0.5, 0);
        if (phase == 2) {
            // Fissao: tres nucleos sao arremessados para longe.
            fission = true;
            nucleiDestroyed = 0;
            nucleiAbsorbed = 0;
            for (int i = 0; i < 3; i++) {
                double angle = (getYRot() + 90) * Mth.DEG_TO_RAD + i * Mth.TWO_PI / 3 + Mth.PI / 3;
                Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 at = core.add(direction.scale(getBbWidth() * 0.6));
                BossOrganEntity nucleus = spawnOrgan(level, BossOrganEntity.Variant.NUCLEUS, at, 30f, 15f);
                nucleus.setDeltaMovement(direction.x * 1.25, 0.65, direction.z * 1.25);
                GeneticsFx.stream(level, GeneticsFx.dust(0x8FD03A, 1.6f), core, at.add(direction.scale(4)), 12);
            }
            playSound(ModSounds.LEGION_ROAR.get(), 4f, 1.2f);
        } else if (phase == MAX_PHASE) {
            // Supuracao: a massa se rompe e perde a armadura.
            sporeCloud(level, position(), 6f, 140);
            GeneticsFx.burst(level, ParticleTypes.SPORE_BLOSSOM_AIR, core, 120, getBbWidth(), 0.2);
            GeneticsFx.burst(level, GeneticsFx.dust(FLESH, 3f), core, 100, getBbWidth() * 0.7, 0.3);
            rupturedArmor();
        }
    }

    private void rupturedArmor() {
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.addOrReplacePermanentModifier(new AttributeModifier(RUPTURED, -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("fission", fission);
        tag.putInt("nuclei_destroyed", nucleiDestroyed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fission = tag.getBoolean("fission");
        nucleiDestroyed = tag.getInt("nuclei_destroyed");
        if (phase() >= MAX_PHASE) {
            rupturedArmor();
        }
    }

    @Override
    protected SoundEvent roarSound() {
        return ModSounds.LEGION_ROAR.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.LEGION_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.LEGION_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.LEGION_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2f;
    }
}
