package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModSounds;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Orgaos que os chefes criam nas trocas de fase. Cada um e uma mecanica que o jogador precisa resolver:
 * <ul>
 *   <li><b>Pilar de Quitina</b> (Colosso): enquanto houver pilares, o Colosso recebe menos dano. Quebra com
 *   golpes ou quando o proprio Colosso investe contra ele.</li>
 *   <li><b>Casulo de Sangue</b> (Matriarca): pulsa curando a Matriarca e choca crias de tempos em tempos.</li>
 *   <li><b>Nucleo da Legiao</b> (Legiao): rasteja de volta para a massa; se chegar, cura a Legiao.</li>
 * </ul>
 */
public class BossOrganEntity extends PathfinderMob implements GeoEntity {

    public enum Variant {
        PYLON(1.4f, 3.4f, 0xE0B04A),
        COCOON(1.4f, 2.1f, 0xB0141F),
        NUCLEUS(1.0f, 1.0f, 0x8FD03A);

        private final float width;
        private final float height;
        private final int color;

        Variant(float width, float height, int color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        public int color() {
            return color;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    public static final int COCOON_PULSE = 60;
    public static final int COCOON_HATCH = 240;
    public static final float COCOON_HEAL = 0.015f;
    public static final int NUCLEUS_TIMEOUT = 600;
    public static final int NUCLEUS_GRACE = 60;

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(BossOrganEntity.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID owner;
    private int life;

    public BossOrganEntity(EntityType<? extends BossOrganEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.13D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    /** Cria o orgao ja configurado (nao o adiciona ao mundo). */
    public static BossOrganEntity create(EntityType<BossOrganEntity> type, ServerLevel level, AtrocityBoss boss,
                                         Variant variant, Vec3 at, float health) {
        BossOrganEntity organ = new BossOrganEntity(type, level);
        organ.setVariant(variant);
        organ.owner = boss.getUUID();
        organ.addTag(AtrocityBoss.MINION_TAG);
        organ.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
        var maxHealth = organ.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        organ.setHealth(health);
        return organ;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
    }

    public Variant variant() {
        Variant[] values = Variant.values();
        return values[Mth.clamp(entityData.get(DATA_VARIANT), 0, values.length - 1)];
    }

    public void setVariant(Variant variant) {
        entityData.set(DATA_VARIANT, variant.ordinal());
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_VARIANT.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        Variant variant = variant();
        return EntityDimensions.scalable(variant.width, variant.height);
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.atrocities.boss_organ." + variant().key());
    }

    public boolean isOwnedBy(AtrocityBoss boss) {
        return boss.getUUID().equals(owner);
    }

    @Nullable
    public AtrocityBoss owner() {
        if (owner != null && level() instanceof ServerLevel level && level.getEntity(owner) instanceof AtrocityBoss boss
                && boss.isAlive()) {
            return boss;
        }
        return null;
    }

    // ------------------------------------------------------------------ comportamento

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        life++;
        AtrocityBoss boss = owner();
        if (boss == null) {
            if (life > 40) {
                wither(level);
            }
            return;
        }
        Vec3 heart = position().add(0, getBbHeight() * 0.6, 0);
        Vec3 bossHeart = boss.position().add(0, boss.getBbHeight() * 0.55, 0);
        switch (variant()) {
            case PYLON -> {
                // Feixe de ressonancia ate o Colosso: mostra que o pilar o esta protegendo.
                if (life % 6 == 0) {
                    GeneticsFx.stream(level, GeneticsFx.dust(Variant.PYLON.color, 1.0f), heart.add(0, 0.8, 0), bossHeart, 14);
                }
                if (life % 10 == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, heart.x, heart.y + 1.2, heart.z, 4, 0.3, 0.4, 0.3, 0.05);
                }
            }
            case COCOON -> {
                if (life % 10 == 0) {
                    level.sendParticles(GeneticsFx.dust(Variant.COCOON.color, 1.4f), heart.x, heart.y, heart.z, 3,
                            getBbWidth() * 0.4, 0.5, getBbWidth() * 0.4, 0.01);
                }
                if (life % COCOON_PULSE == 0) {
                    playSound(ModSounds.ORGAN_PULSE.get(), 1.6f, 0.8f + random.nextFloat() * 0.2f);
                    if (boss.getHealth() < boss.getMaxHealth()) {
                        boss.heal(boss.getMaxHealth() * COCOON_HEAL);
                        GeneticsFx.stream(level, GeneticsFx.dust(Variant.COCOON.color, 1.8f), heart, bossHeart, 24);
                        level.sendParticles(ParticleTypes.HEART, bossHeart.x, bossHeart.y + 1, bossHeart.z, 2, 0.4, 0.3, 0.4, 0);
                    }
                }
                if (life % COCOON_HATCH == 0) {
                    boss.spawnMinionsAt(level, position(), 1, 8);
                    playSound(ModSounds.ORGAN_SPAWN.get(), 1.6f, 1.0f);
                }
            }
            case NUCLEUS -> {
                if (life % 3 == 0) {
                    level.sendParticles(GeneticsFx.dust(Variant.NUCLEUS.color, 1.2f), getX(), getY() + 0.2, getZ(), 2,
                            0.3, 0.1, 0.3, 0.01);
                }
                // Os primeiros 3 s depois de pousar sao a janela livre para caca-los.
                if (life < NUCLEUS_GRACE) {
                    return;
                }
                if (life % 10 == 0 && onGround()) {
                    getNavigation().moveTo(boss, 1.0D);
                }
                if (life % 16 == 0) {
                    playSound(ModSounds.LEGION_GURGLE.get(), 0.8f, 1.4f + random.nextFloat() * 0.3f);
                }
                double reach = boss.getBbWidth() * 0.5 + 1.2;
                // Se ficar preso no caminho por 25 s, e puxado de volta pela massa mesmo assim.
                if (distanceToSqr(boss) <= reach * reach || boundingBoxNear(boss) || life > NUCLEUS_TIMEOUT) {
                    boss.onOrganArrived(level, this);
                    GeneticsFx.stream(level, GeneticsFx.dust(Variant.NUCLEUS.color, 2f), heart, bossHeart, 16);
                    discard();
                }
            }
        }
    }

    private boolean boundingBoxNear(Entity boss) {
        return getBoundingBox().inflate(0.6).intersects(boss.getBoundingBox());
    }

    /** Murcha (dono morto ou ausente): desaparece em uma nuvem, sem loot. */
    private void wither(ServerLevel level) {
        GeneticsFx.burst(level, GeneticsFx.dust(variant().color, 1.8f), position().add(0, getBbHeight() * 0.5, 0), 30,
                getBbWidth() * 0.5, 0.05);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 10, 0.4, 0.4, 0.4, 0.02);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof AtrocityBoss || attacker instanceof BossOrganEntity
                || (attacker != null && attacker.getTags().contains(AtrocityBoss.MINION_TAG))) {
            return false;
        }
        boolean result = super.hurt(source, amount);
        if (result && level() instanceof ServerLevel level) {
            Vec3 center = position().add(0, getBbHeight() * 0.5, 0);
            level.sendParticles(GeneticsFx.dust(variant().color, 1.5f), center.x, center.y, center.z, 12,
                    getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.05);
            if (variant() == Variant.PYLON) {
                level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 8, 0.4, 0.6, 0.4, 0.3);
            }
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            Vec3 center = position().add(0, getBbHeight() * 0.5, 0);
            GeneticsFx.burst(level, GeneticsFx.dust(variant().color, 2.6f), center, 60, getBbWidth() * 0.6, 0.15);
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            playSound(ModSounds.ORGAN_SHATTER.get(), 2.5f, 0.8f + random.nextFloat() * 0.2f);
            AtrocityBoss boss = owner();
            if (boss != null) {
                boss.onOrganDestroyed(level, this);
            }
        }
    }

    @Override
    protected void tickDeath() {
        // Some na hora; a explosao de particulas ja aconteceu em die().
        if (!level().isClientSide && !isRemoved()) {
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BOSS_HIT_FLESH.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ORGAN_SHATTER.get();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (variant() == Variant.NUCLEUS) {
            super.knockback(strength * 0.5, x, z);
        }
    }

    // ------------------------------------------------------------------ persistencia

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("variant", variant().ordinal());
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.putInt("life", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_VARIANT, tag.getInt("variant"));
        refreshDimensions();
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        life = tag.getInt("life");
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
