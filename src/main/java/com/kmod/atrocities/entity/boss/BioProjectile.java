package com.kmod.atrocities.entity.boss;

import com.kmod.atrocities.item.GeneticsFx;
import com.kmod.atrocities.registry.ModEntities;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Projeteis organicos dos chefes.
 * <ul>
 *   <li>{@link Kind#SPINE}: espinho quitinoso, voo quase reto, perfura.</li>
 *   <li>{@link Kind#BILE}: bolota de bile em arco; estoura numa nuvem toxica.</li>
 *   <li>{@link Kind#WEB}: bolota de teia da Aranha Tecela; prende quem acerta.</li>
 *   <li>{@link Kind#BLAST}: carga volatil do Creeper Detonador; explode ao tocar em algo.</li>
 *   <li>{@link Kind#ORB}: orbe eterea da Arraia-Fantasma; persegue o alvo devagar.</li>
 *   <li>{@link Kind#SERUM} / {@link Kind#SERUM_VOLATILE}: Soro Mutagenico arremessado; injeta no mob atingido.</li>
 * </ul>
 */
public class BioProjectile extends ThrowableItemProjectile {

    public enum Kind {
        SPINE, BILE, WEB, BLAST, ORB, SERUM, SERUM_VOLATILE;

        public boolean isSerum() {
            return this == SERUM || this == SERUM_VOLATILE;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_KIND =
            SynchedEntityData.defineId(BioProjectile.class, EntityDataSerializers.INT);

    private float damage = 10f;
    @Nullable
    private java.util.UUID homing;

    public BioProjectile(EntityType<? extends BioProjectile> type, Level level) {
        super(type, level);
    }

    /** Projetil disparado por um jogador (itens arremessaveis e o Lanca-Bile). */
    public BioProjectile(Level level, LivingEntity owner, Kind kind, float damage, ItemStack display) {
        super(ModEntities.BIO_PROJECTILE.get(), owner, level);
        this.damage = damage;
        entityData.set(DATA_KIND, kind.ordinal());
        setItem(display.copyWithCount(1));
    }

    public BioProjectile(Level level, AtrocityBoss owner, Kind kind, float damage) {
        super(ModEntities.BIO_PROJECTILE.get(), owner, level);
        this.damage = damage;
        entityData.set(DATA_KIND, kind.ordinal());
        setItem(new net.minecraft.world.item.ItemStack(itemFor(kind)));
    }

    private static Item itemFor(Kind kind) {
        return switch (kind) {
            case SPINE -> ModItems.CHITIN_SPINE.get();
            case BILE -> ModItems.BILE_GLOB.get();
            case WEB -> ModItems.WEB_GLOB.get();
            case BLAST -> ModItems.VOLATILE_CHARGE.get();
            case ORB -> ModItems.ETHER_ORB.get();
            case SERUM -> ModItems.MUTAGEN_SERUM.get();
            case SERUM_VOLATILE -> ModItems.VOLATILE_SERUM.get();
        };
    }

    /** Orbe que persegue o alvo. */
    public BioProjectile homingOn(LivingEntity target) {
        this.homing = target.getUUID();
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KIND, Kind.SPINE.ordinal());
    }

    public Kind kind() {
        int index = entityData.get(DATA_KIND);
        return Kind.values()[Math.floorMod(index, Kind.values().length)];
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CHITIN_SPINE.get();
    }

    @Override
    protected double getDefaultGravity() {
        return switch (kind()) {
            case SPINE -> 0.008;
            case ORB -> 0.0;
            case WEB -> 0.02;
            default -> 0.05;
        };
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        if (kind().isSerum() && !(target instanceof LivingEntity)) {
            return false;
        }
        // Projeteis de chefe nao acertam o proprio chefe nem as crias; os dos jogadores acertam tudo.
        return !(getOwner() instanceof AtrocityBoss)
                || (!(target instanceof AtrocityBoss) && !target.getTags().contains(AtrocityBoss.MINION_TAG));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 motion = getDeltaMovement();
            switch (kind()) {
                case BILE -> {
                    level().addParticle(GeneticsFx.dust(0x7FA02A, 1.2f), getX(), getY(), getZ(), 0, 0, 0);
                    if (random.nextInt(3) == 0) {
                        level().addParticle(ParticleTypes.FALLING_SPORE_BLOSSOM, getX(), getY(), getZ(), 0, 0, 0);
                    }
                }
                case WEB -> level().addParticle(GeneticsFx.dust(0xF2EEFF, 1.0f), getX(), getY(), getZ(), 0, 0, 0);
                case BLAST -> {
                    level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0.02, 0);
                    level().addParticle(GeneticsFx.dust(0x6BFF3A, 1.3f), getX(), getY(), getZ(), 0, 0, 0);
                }
                case ORB -> {
                    level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), -motion.x * 0.1, -motion.y * 0.1,
                            -motion.z * 0.1);
                    level().addParticle(GeneticsFx.dust(0x5AF0FF, 1.6f), getX(), getY(), getZ(), 0, 0, 0);
                }
                case SERUM -> level().addParticle(GeneticsFx.dust(0x8CFF4F, 0.9f), getX(), getY(), getZ(), 0, 0, 0);
                case SERUM_VOLATILE -> level().addParticle(GeneticsFx.dust(0xFF7A2E, 0.9f), getX(), getY(), getZ(), 0, 0, 0);
                default -> level().addParticle(ParticleTypes.CRIT, getX() - motion.x * 0.5, getY() - motion.y * 0.5,
                        getZ() - motion.z * 0.5, 0, 0, 0);
            }
        } else {
            if (kind() == Kind.ORB && homing != null && level() instanceof ServerLevel server
                    && server.getEntity(homing) instanceof LivingEntity target && target.isAlive()) {
                // Curva suave em direcao ao alvo, sem acelerar.
                Vec3 motion = getDeltaMovement();
                double speed = Math.max(0.6, motion.length());
                Vec3 want = target.getEyePosition().subtract(position()).normalize().scale(speed);
                setDeltaMovement(motion.scale(0.9).add(want.scale(0.1)).normalize().scale(speed));
            }
            if (tickCount > (kind() == Kind.ORB ? 120 : 200)) {
                discard();
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(result.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Entity owner = getOwner();
        if (kind().isSerum()) {
            if (level() instanceof ServerLevel server && target instanceof net.minecraft.world.entity.Mob mob) {
                com.kmod.atrocities.mutation.Mutation.inject(server, mob,
                        owner instanceof net.minecraft.world.entity.player.Player player ? player : null,
                        kind() == Kind.SERUM_VOLATILE);
            }
            return;
        }
        if (kind() == Kind.BLAST) {
            return;
        }
        if (kind() == Kind.WEB) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 5), owner);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), owner);
            target.setDeltaMovement(target.getDeltaMovement().multiply(0.1, 0.5, 0.1));
            target.hurtMarked = true;
        }
        boolean hurt = target.hurt(owner instanceof AtrocityBoss boss
                ? damageSources().mobProjectile(this, boss) : damageSources().thrown(this, owner), damage);
        if (hurt && owner instanceof AtrocityBoss boss) {
            boss.applyHitEffects(target);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = result.getLocation();
        if (kind().isSerum()) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.1);
            GeneticsFx.burst(level, GeneticsFx.dust(kind() == Kind.SERUM ? 0x8CFF4F : 0xFF7A2E, 1.4f), at, 16, 0.3, 0.05);
            level.playSound(null, at.x, at.y, at.z, ModSounds.MUTAGEN_SHATTER.get(), getSoundSource(), 1f, 1f);
        } else if (kind() == Kind.BLAST) {
            if (getOwner() instanceof com.kmod.atrocities.entity.mutant.MutantEntity mutant) {
                mutant.chargeBlast(level, at);
            } else {
                GeneticsFx.burst(level, ParticleTypes.EXPLOSION, at, 2, 0.4, 0);
            }
        } else if (kind() == Kind.WEB) {
            GeneticsFx.burst(level, GeneticsFx.dust(0xF2EEFF, 1.6f), at, 24, 0.6, 0.05);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, at.x, at.y, at.z, ModSounds.WEAVER_WEB.get(), getSoundSource(), 1f, 1.2f);
        } else if (kind() == Kind.ORB) {
            GeneticsFx.burst(level, ParticleTypes.END_ROD, at, 16, 0.2, 0.15);
            GeneticsFx.burst(level, GeneticsFx.dust(0x5AF0FF, 2f), at, 20, 0.4, 0.05);
            level.playSound(null, at.x, at.y, at.z, ModSounds.BOSS_SPINE_IMPACT.get(), getSoundSource(), 1f, 1.6f);
        } else if (kind() == Kind.BILE) {
            GeneticsFx.burst(level, GeneticsFx.dust(0x7FA02A, 2f), at, 30, 0.6, 0.05);
            GeneticsFx.burst(level, ParticleTypes.ITEM_SLIME, at, 16, 0.4, 0.1);
            if (getOwner() instanceof AtrocityBoss boss) {
                boss.sporeCloud(level, at, 2.5f, 80);
            } else {
                toxicCloud(level, at, getOwner() instanceof LivingEntity living ? living : null);
            }
            level.playSound(null, at.x, at.y, at.z, ModSounds.BOSS_BILE_SPLASH.get(), getSoundSource(), 1.4f,
                    0.8f + random.nextFloat() * 0.3f);
        } else {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.1);
            level.playSound(null, at.x, at.y, at.z, ModSounds.BOSS_SPINE_IMPACT.get(), getSoundSource(), 1f,
                    0.9f + random.nextFloat() * 0.3f);
        }
        discard();
    }

    /** Nuvem de bile dos jogadores: mais fraca e curta que a dos chefes. */
    public static void toxicCloud(ServerLevel level, Vec3 at, @javax.annotation.Nullable LivingEntity owner) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setOwner(owner);
        cloud.setRadius(2.2f);
        cloud.setRadiusOnUse(-0.3f);
        cloud.setWaitTime(5);
        cloud.setDuration(60);
        cloud.setRadiusPerTick(-2.2f / 90f);
        cloud.setParticle(GeneticsFx.dust(0x7FA02A, 1.2f));
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        level.addFreshEntity(cloud);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putInt("kind", kind().ordinal());
        if (homing != null) {
            tag.putUUID("homing", homing);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        entityData.set(DATA_KIND, tag.getInt("kind"));
        homing = tag.hasUUID("homing") ? tag.getUUID("homing") : null;
    }
}
