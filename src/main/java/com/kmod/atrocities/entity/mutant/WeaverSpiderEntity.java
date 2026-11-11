package com.kmod.atrocities.entity.mutant;

import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.entity.boss.BossAttack;
import com.kmod.atrocities.fx.BossFx;
import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Aranha Tecela: aranha gigante de abdomen roxo brilhante. Escala paredes.
 * <ul>
 *   <li>Mordida: veneno forte.</li>
 *   <li>Disparo de Teia: bolota que prende o alvo (3 em leque na Furia).</li>
 *   <li>Bote: salta sobre o alvo.</li>
 *   <li>Ninhada: aranhas-das-cavernas saem do abdomen.</li>
 *   <li>Spray de Veneno (Furia): cone de veneno que deixa uma nuvem toxica.</li>
 * </ul>
 */
public class WeaverSpiderEntity extends MutantEntity {

    private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
            SynchedEntityData.defineId(WeaverSpiderEntity.class, EntityDataSerializers.BOOLEAN);

    private static final BossAttack BITE = new BossAttack("bite", 8, 20, 30, 0f, 3.0f, 5, 1);
    private static final BossAttack WEB_SHOT = new BossAttack("web_shot", 12, 24, 80, 4f, 24f, 4, 1);
    private static final BossAttack POUNCE = new BossAttack("pounce", 10, 40, 100, 5f, 16f, 4, 1);
    private static final BossAttack BROOD = new BossAttack("brood", 18, 36, 320, 0f, 24f, 2, 1);
    private static final BossAttack VENOM_SPRAY = new BossAttack("venom_spray", 14, 40, 160, 0f, 8f, 3, 2);
    private static final List<BossAttack> ATTACKS = List.of(BITE, WEB_SHOT, POUNCE, BROOD, VENOM_SPRAY);

    private static final int COLOR = 0xB04BFF;
    private boolean airborne;

    public WeaverSpiderEntity(EntityType<? extends WeaverSpiderEntity> type, Level level) {
        super(type, level, BossEvent.BossBarColor.PURPLE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return mutantAttributes(0.32D, 4.0D, 0.7D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CLIMBING, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    public boolean onClimbable() {
        return entityData.get(DATA_CLIMBING);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            entityData.set(DATA_CLIMBING, horizontalCollision && canAct() && !isAttacking());
        }
    }

    @Override
    public EntityType<?> origin() {
        return EntityType.SPIDER;
    }

    @Override
    protected EntityType<?> fallbackMinion() {
        return EntityType.CAVE_SPIDER;
    }

    @Override
    protected double mutantHealth() {
        return 600;
    }

    @Override
    protected double mutantDamage() {
        return 12;
    }

    @Override
    protected int mutantColor() {
        return COLOR;
    }

    @Override
    protected MutantSounds sounds() {
        return ModSounds.WEAVER_SPIDER;
    }

    @Override
    protected List<BossAttack> attacks() {
        return ATTACKS;
    }

    @Override
    protected double preferredRange() {
        return 2.2;
    }

    @Override
    protected Telegraph telegraph(BossAttack attack) {
        return switch (attack.id()) {
            case "web_shot", "pounce" -> Telegraph.LINE;
            case "brood" -> Telegraph.NONE;
            default -> Telegraph.AREA;
        };
    }

    @Override
    protected double telegraphRadius(BossAttack attack) {
        return attack == VENOM_SPRAY ? 7.0 : 3.0;
    }

    @Override
    protected void tickAttack(ServerLevel level, BossAttack attack, int tick, @Nullable LivingEntity target) {
        switch (attack.id()) {
            case "bite" -> bite(level, tick);
            case "web_shot" -> webShot(level, tick, target);
            case "pounce" -> pounce(level, tick, target);
            case "brood" -> brood(level, tick);
            case "venom_spray" -> venomSpray(level, tick);
            default -> {
            }
        }
    }

    private void poison(LivingEntity victim) {
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
    }

    private void bite(ServerLevel level, int tick) {
        if (tick == BITE.windup()) {
            Vec3 at = front(0.8, 0.3);
            strike(at, 2.2, 1.0f, 0.5, 0.2, null).forEach(this::poison);
            GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 1.4f), at, 14, 0.4, 0.08);
            playSound(ModSounds.BOSS_SWING.get(), 1.6f, 1.4f);
        }
    }

    private void webShot(ServerLevel level, int tick, @Nullable LivingEntity target) {
        if (tick != WEB_SHOT.windup() || target == null) {
            return;
        }
        Vec3 origin = front(0.2, 0.45);
        Vec3 aim = target.getEyePosition().add(0, -0.5, 0).subtract(origin);
        double horizontal = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        double baseYaw = Math.atan2(aim.z, aim.x);
        int count = phase() >= 2 ? 3 : 1;
        for (int i = 0; i < count; i++) {
            double yaw = baseYaw + (i - (count - 1) / 2.0) * 0.18;
            BioProjectile web = new BioProjectile(level, this, BioProjectile.Kind.WEB, attackDamage(0.5f));
            web.setPos(origin.x, origin.y, origin.z);
            web.shoot(Math.cos(yaw) * horizontal, aim.y + horizontal * 0.05, Math.sin(yaw) * horizontal, 1.6f, 1f);
            level.addFreshEntity(web);
        }
        playSound(ModSounds.WEAVER_WEB.get(), 2f, 1f);
    }

    private void pounce(ServerLevel level, int tick, @Nullable LivingEntity target) {
        int start = POUNCE.windup();
        if (tick == start) {
            leapTowards(target != null ? target.position() : position().add(forward().scale(8)), 2.5);
            airborne = true;
            playSound(ModSounds.MUTANT_LEAP.get(), 2f, 1.2f);
        }
        if (airborne && tick > start + 3 && onGround()) {
            airborne = false;
            strike(position(), 2.8, 1.1f, 0.8, 0.4, null).forEach(this::poison);
            groundCrack(level, position(), 1.8, 40);
            playSound(ModSounds.MUTANT_LAND.get(), 2f, 1.1f);
        } else if (airborne && tick == POUNCE.duration() - 1) {
            airborne = false;
        }
    }

    private void brood(ServerLevel level, int tick) {
        if (tick == 2) {
            playSound(roarSound(), 2.5f, 1f);
        }
        if (tick == BROOD.windup()) {
            spawnMinions(level, phase() >= 2 ? 5 : 3, 6);
            Vec3 abdomen = front(-getBbWidth() * 0.9, 0.5);
            GeneticsFx.burst(level, GeneticsFx.dust(COLOR, 2f), abdomen, 40, 0.8, 0.15);
            GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, abdomen, 20, 0.6, 0.2);
            playSound(ModSounds.MUTATION_RUPTURE.get(), 1.6f, 1.4f);
        }
    }

    private void venomSpray(ServerLevel level, int tick) {
        int start = VENOM_SPRAY.windup();
        if (tick >= start && tick < start + 16 && tick % 2 == 0) {
            float yaw = getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
            Vec3 mouth = front(0.2, 0.4);
            for (int i = 0; i < 8; i++) {
                double angle = yaw + (random.nextDouble() - 0.5) * 1.1;
                Vec3 dir = new Vec3(-Math.sin(angle), -0.05, Math.cos(angle)).scale(0.5 + random.nextDouble() * 0.5);
                level.sendParticles(GeneticsFx.dust(COLOR, 1.6f), mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y, dir.z, 1.0);
            }
            strike(position(), 7, 0.25f, 0.2, 0.05, victim -> inFront(victim, 35)).forEach(this::poison);
            if (tick % 6 == 0) {
                playSound(ModSounds.WEAVER_SPRAY.get(), 1.8f, 0.9f);
            }
        }
        if (tick == start + 16) {
            Vec3 at = front(3.5, 0);
            AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
            cloud.setOwner(this);
            cloud.setRadius(3.5f);
            cloud.setDuration(100);
            cloud.setRadiusPerTick(-3.5f / 150f);
            cloud.setWaitTime(0);
            cloud.setParticle(GeneticsFx.dust(COLOR, 1.3f));
            cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
            level.addFreshEntity(cloud);
            BossFx.shake(level, at, 12, 0.3f, 8);
        }
    }

    @Override
    protected void onAttackCancelled(BossAttack attack) {
        airborne = false;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.SPIDER_STEP, 0.8f, 0.6f);
    }
}
