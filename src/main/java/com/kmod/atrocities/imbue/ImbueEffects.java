package com.kmod.atrocities.imbue;

import com.kmod.atrocities.Atrocities;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.entity.boss.BossTraits;
import com.kmod.atrocities.genetics.Trait;
import com.kmod.atrocities.genetics.TraitRoll;
import com.kmod.atrocities.item.GeneticsFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Efeitos dos genes imbuidos. Cada traco tem um efeito na arma (quem golpeia ou atira com ela na mao)
 * e outro na armadura (somado entre as pecas vestidas, ate {@value #ARMOR_CAP} niveis).
 * Tracos de datapack sem efeito proprio ficam so como evolucao e enfeite.
 */
public final class ImbueEffects {

    private ImbueEffects() {
    }

    private static final int ARMOR_CAP = 10;
    private static final String ADRENAL_COOLDOWN = "atrocities.imbue.adrenal_cd";
    private static final String VOID_COOLDOWN = "atrocities.imbue.void_cd";
    private static final String SWARM_TAG = "atrocities.imbue_swarm";
    private static final int SWARM_LIFETIME = 400;

    private static final ResourceKey<Trait> HYPERTROPHY = key("hypertrophy");
    private static final ResourceKey<Trait> CHITIN_PLATING = key("chitin_plating");

    /** Evita que efeitos disparem a si mesmos (explosao que mata, que explode...). */
    private static boolean applying;
    private static final List<Swarm> SWARMS = new ArrayList<>();

    private record Swarm(Bee bee, long expires) {
    }

    private static ResourceKey<Trait> key(String path) {
        return ResourceKey.create(com.kmod.atrocities.genetics.GeneticsRegistries.TRAIT,
                ResourceLocation.fromNamespaceAndPath(Atrocities.MODID, path));
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ImbueEffects::onAttributes);
        NeoForge.EVENT_BUS.addListener(ImbueEffects::onIncoming);
        NeoForge.EVENT_BUS.addListener(ImbueEffects::onDamaged);
        NeoForge.EVENT_BUS.addListener(ImbueEffects::onDeath);
        NeoForge.EVENT_BUS.addListener(ImbueEffects::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ImbueEffects::onServerTick);
    }

    // ------------------------------------------------------------------ leitura dos genes

    /** Genes da arma na mao principal, ou mapa vazio. */
    private static Map<ResourceKey<Trait>, Integer> weaponGenes(LivingEntity holder) {
        ItemStack stack = holder.getMainHandItem();
        if (Imbuing.kindOf(stack) != Imbuing.Kind.WEAPON) {
            return Map.of();
        }
        Map<ResourceKey<Trait>, Integer> map = new HashMap<>();
        for (TraitRoll gene : Imbuing.genes(stack).genes()) {
            map.put(gene.trait(), gene.potency());
        }
        return map;
    }

    /** Niveis somados dos genes nas pecas de armadura vestidas. */
    private static Map<ResourceKey<Trait>, Integer> armorGenes(LivingEntity wearer) {
        Map<ResourceKey<Trait>, Integer> map = new HashMap<>();
        for (ItemStack stack : wearer.getArmorSlots()) {
            if (Imbuing.kindOf(stack) == Imbuing.Kind.ARMOR) {
                for (TraitRoll gene : Imbuing.genes(stack).genes()) {
                    map.merge(gene.trait(), gene.potency(), (a, b) -> Math.min(ARMOR_CAP, a + b));
                }
            }
        }
        return map;
    }

    private static int lvl(Map<ResourceKey<Trait>, Integer> genes, ResourceKey<Trait> trait) {
        return genes.getOrDefault(trait, 0);
    }

    // ------------------------------------------------------------------ atributos da armadura

    private static void onAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (Imbuing.kindOf(stack) != Imbuing.Kind.ARMOR) {
            return;
        }
        ImbuedGenes genes = stack.get(com.kmod.atrocities.registry.ModDataComponents.IMBUED_GENES.get());
        if (genes == null || genes.isEmpty()) {
            return;
        }
        EquipmentSlot slot = stack.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot()
                : stack.getEquipmentSlot();
        if (slot == null) {
            return;
        }
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
        int plating = genes.level(CHITIN_PLATING);
        if (plating > 0) {
            add(event, Attributes.ARMOR, "chitin_plating", slot, plating, AttributeModifier.Operation.ADD_VALUE, group);
            add(event, Attributes.ARMOR_TOUGHNESS, "chitin_plating_toughness", slot, plating * 0.5,
                    AttributeModifier.Operation.ADD_VALUE, group);
        }
        int hypertrophy = genes.level(HYPERTROPHY);
        if (hypertrophy > 0) {
            add(event, Attributes.MAX_HEALTH, "hypertrophy", slot, hypertrophy * 2.0,
                    AttributeModifier.Operation.ADD_VALUE, group);
        }
        int adrenal = genes.level(BossTraits.ADRENAL_SURGE);
        if (adrenal > 0) {
            add(event, Attributes.MOVEMENT_SPEED, "adrenal_surge", slot, adrenal * 0.03,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, group);
        }
    }

    private static void add(ItemAttributeModifierEvent event, Holder<Attribute> attribute, String name,
                            EquipmentSlot slot, double amount, AttributeModifier.Operation operation,
                            EquipmentSlotGroup group) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Atrocities.MODID,
                "imbue." + name + "." + slot.getName());
        event.addModifier(attribute, new AttributeModifier(id, amount, operation), group);
    }

    // ------------------------------------------------------------------ multiplicadores de dano

    private static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || applying) {
            return;
        }
        DamageSource source = event.getSource();
        float amount = event.getAmount();

        if (source.getEntity() instanceof LivingEntity attacker && attacker != victim
                && !source.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)) {
            Map<ResourceKey<Trait>, Integer> weapon = weaponGenes(attacker);
            if (!weapon.isEmpty()) {
                amount *= 1f + 0.06f * lvl(weapon, HYPERTROPHY);
                int nocturnal = lvl(weapon, BossTraits.NOCTURNAL_MEMBRANES);
                if (nocturnal > 0 && attacker.level().isNight()) {
                    amount *= 1f + 0.08f * nocturnal;
                }
                int spines = lvl(weapon, BossTraits.CHITIN_SPINES);
                if (spines > 0 && attacker.getRandom().nextFloat() < 0.08f * spines) {
                    amount *= 1.25f;
                    if (victim.level() instanceof ServerLevel level) {
                        GeneticsFx.burst(level, ParticleTypes.CRIT, victim.position().add(0, victim.getBbHeight() * 0.6, 0),
                                14, 0.3, 0.3);
                        level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                                SoundSource.PLAYERS, 0.8f, 1.3f);
                    }
                }
            }
        }

        int thermal = lvl(armorGenes(victim), BossTraits.THERMAL_ORGANS);
        if (thermal > 0 && source.is(DamageTypeTags.IS_FIRE)) {
            amount *= Math.max(0.1f, 1f - 0.15f * thermal);
        }
        event.setAmount(amount);
    }

    // ------------------------------------------------------------------ efeitos ao golpear e ao ser ferido

    private static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || applying || event.getNewDamage() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        applying = true;
        try {
            Map<ResourceKey<Trait>, Integer> weapon = weaponGenes(attacker);
            if (!weapon.isEmpty() && victim.isAlive()) {
                weaponHit(level, attacker, victim, weapon, event.getNewDamage());
            }
            Map<ResourceKey<Trait>, Integer> armor = armorGenes(victim);
            if (!armor.isEmpty()) {
                boolean melee = source.getDirectEntity() == attacker;
                armorHit(level, victim, attacker, armor, melee, event.getNewDamage());
                if (victim instanceof Player) {
                    int xp = attacker instanceof AtrocityBoss ? 3 : 1;
                    for (ItemStack stack : victim.getArmorSlots()) {
                        if (Imbuing.kindOf(stack) == Imbuing.Kind.ARMOR) {
                            Imbuing.grantEvolution(level, victim, stack, xp);
                        }
                    }
                }
            }
        } finally {
            applying = false;
        }
    }

    private static void weaponHit(ServerLevel level, LivingEntity attacker, LivingEntity victim,
                                  Map<ResourceKey<Trait>, Integer> genes, float damage) {
        var random = attacker.getRandom();
        int l;
        if ((l = lvl(genes, BossTraits.HEMOTOXIN)) > 0) {
            effect(victim, MobEffects.POISON, 60 + 20 * l, (l - 1) / 2, attacker);
        }
        if ((l = lvl(genes, BossTraits.ANTICOAGULANT)) > 0) {
            effect(victim, MobEffects.WITHER, 40 + 20 * l, (l - 1) / 2, attacker);
        }
        if ((l = lvl(genes, BossTraits.TOXIC_BILE)) > 0) {
            effect(victim, MobEffects.CONFUSION, 100, 0, attacker);
            effect(victim, MobEffects.HUNGER, 100, l - 1, attacker);
        }
        if ((l = lvl(genes, BossTraits.THERMAL_ORGANS)) > 0) {
            victim.igniteForSeconds(2f * l);
        }
        if ((l = lvl(genes, BossTraits.NOCTURNAL_MEMBRANES)) > 0 && random.nextFloat() < 0.15f * l) {
            effect(victim, MobEffects.BLINDNESS, 20 + 20 * l, 0, attacker);
        }
        if ((l = lvl(genes, CHITIN_PLATING)) > 0) {
            effect(victim, MobEffects.MOVEMENT_SLOWDOWN, 40 + 10 * l, (l - 1) / 2, attacker);
        }
        if ((l = lvl(genes, BossTraits.REGENERATIVE_TISSUE)) > 0 && attacker.getHealth() < attacker.getMaxHealth()) {
            attacker.heal(Math.max(0.5f, damage * 0.04f * l));
            GeneticsFx.burst(level, ParticleTypes.HEART, attacker.position().add(0, attacker.getBbHeight() + 0.3, 0),
                    1, 0.2, 0);
        }
        if ((l = lvl(genes, BossTraits.VOID_STRANDS)) > 0 && !(victim instanceof AtrocityBoss)
                && random.nextFloat() < 0.05f * l) {
            randomTeleport(level, victim);
        }
        if ((l = lvl(genes, BossTraits.HIVE_MIND)) > 0 && random.nextFloat() < 0.05f * l) {
            swarm(level, victim.position().add(0, victim.getBbHeight() + 0.5, 0), victim);
        }
    }

    private static void armorHit(ServerLevel level, LivingEntity wearer, LivingEntity attacker,
                                 Map<ResourceKey<Trait>, Integer> genes, boolean melee, float damage) {
        var random = wearer.getRandom();
        int l;
        if (melee && attacker.isAlive()) {
            if ((l = lvl(genes, BossTraits.HEMOTOXIN)) > 0 && random.nextFloat() < 0.1f * l) {
                effect(attacker, MobEffects.POISON, 60, 0, wearer);
            }
            if ((l = lvl(genes, BossTraits.ANTICOAGULANT)) > 0 && random.nextFloat() < 0.08f * l) {
                effect(attacker, MobEffects.WITHER, 60, 0, wearer);
            }
            if ((l = lvl(genes, BossTraits.TOXIC_BILE)) > 0 && random.nextFloat() < 0.1f * l) {
                effect(attacker, MobEffects.CONFUSION, 100, 0, wearer);
            }
            if ((l = lvl(genes, BossTraits.THERMAL_ORGANS)) > 0 && random.nextFloat() < 0.1f * l) {
                attacker.igniteForSeconds(3f);
            }
            if ((l = lvl(genes, BossTraits.CHITIN_SPINES)) > 0) {
                attacker.hurt(level.damageSources().thorns(wearer), Math.max(0.5f, damage * 0.06f * l));
            }
            if ((l = lvl(genes, BossTraits.VOLATILE_GLANDS)) > 0 && random.nextFloat() < 0.08f * l) {
                Vec3 push = attacker.position().subtract(wearer.position());
                attacker.knockback(1.2, -push.x, -push.z);
                attacker.hurt(level.damageSources().explosion(wearer, wearer), 2f * l);
                GeneticsFx.burst(level, ParticleTypes.EXPLOSION, attacker.position().add(0, 1, 0), 1, 0, 0);
                level.playSound(null, attacker.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.PLAYERS, 0.5f, 1.5f);
            }
        }
        if ((l = lvl(genes, BossTraits.HIVE_MIND)) > 0 && attacker.isAlive() && random.nextFloat() < 0.06f * l) {
            swarm(level, wearer.position().add(0, wearer.getBbHeight() + 0.5, 0), attacker);
        }
        if ((l = lvl(genes, BossTraits.SPORE_SACS)) > 0 && random.nextFloat() < 0.08f * l) {
            sporeBurst(level, wearer.position(), 3 + l * 0.3, l / 3, wearer);
        }
        long now = level.getGameTime();
        if ((l = lvl(genes, BossTraits.VOID_STRANDS)) > 0 && random.nextFloat() < 0.04f * l
                && wearer.getPersistentData().getLong(VOID_COOLDOWN) <= now) {
            wearer.getPersistentData().putLong(VOID_COOLDOWN, now + 100);
            randomTeleport(level, wearer);
        }
        if ((l = lvl(genes, BossTraits.ADRENAL_SURGE)) > 0 && wearer.isAlive()
                && wearer.getHealth() < wearer.getMaxHealth() * 0.3f
                && wearer.getPersistentData().getLong(ADRENAL_COOLDOWN) <= now) {
            wearer.getPersistentData().putLong(ADRENAL_COOLDOWN, now + 1200);
            wearer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 + 20 * l, 1));
            wearer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60 + 20 * l, 0));
            level.playSound(null, wearer.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1f, 1.4f);
            GeneticsFx.burst(level, GeneticsFx.dust(0xE0A030, 1.2f), wearer.position().add(0, 1, 0), 20, 0.5, 0);
        }
    }

    // ------------------------------------------------------------------ abates

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || applying) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity killer) || killer == victim) {
            return;
        }
        ItemStack weapon = killer.getMainHandItem();
        if (Imbuing.kindOf(weapon) != Imbuing.Kind.WEAPON || Imbuing.genes(weapon).isEmpty()) {
            return;
        }
        Map<ResourceKey<Trait>, Integer> genes = weaponGenes(killer);
        applying = true;
        try {
            int l;
            if ((l = lvl(genes, BossTraits.ADRENAL_SURGE)) > 0) {
                killer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 + 20 * l, (l - 1) / 2));
                killer.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 60 + 20 * l, (l - 1) / 2));
            }
            if ((l = lvl(genes, BossTraits.SPORE_SACS)) > 0) {
                sporeBurst(level, victim.position(), 2 + l, l / 2, killer);
            }
            if ((l = lvl(genes, BossTraits.VOLATILE_GLANDS)) > 0) {
                blast(level, victim.position().add(0, victim.getBbHeight() * 0.5, 0), 3f, 4f + 2f * l, killer);
            }
            if (killer instanceof Player) {
                int xp = victim instanceof AtrocityBoss ? 100
                        : Mth.clamp(1 + Mth.ceil(victim.getMaxHealth() / 8f), 1, 30);
                Imbuing.grantEvolution(level, killer, weapon, xp);
            }
        } finally {
            applying = false;
        }
    }

    // ------------------------------------------------------------------ armadura viva

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 20 != 0 || !player.isAlive()) {
            return;
        }
        Map<ResourceKey<Trait>, Integer> genes = armorGenes(player);
        if (genes.isEmpty()) {
            return;
        }
        int regen = lvl(genes, BossTraits.REGENERATIVE_TISSUE);
        if (regen > 0 && (player.tickCount / 20) % Math.max(2, 10 - 2 * regen) == 0
                && player.getHealth() < player.getMaxHealth()) {
            player.heal(1f);
        }
        if (lvl(genes, BossTraits.NOCTURNAL_MEMBRANES) > 0) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() < 220) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, true));
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (SWARMS.isEmpty()) {
            return;
        }
        Iterator<Swarm> it = SWARMS.iterator();
        while (it.hasNext()) {
            Swarm swarm = it.next();
            Bee bee = swarm.bee();
            if (bee.isRemoved()) {
                it.remove();
            } else if (bee.level().getGameTime() >= swarm.expires() || bee.hasStung()) {
                if (bee.level() instanceof ServerLevel level) {
                    GeneticsFx.burst(level, ParticleTypes.POOF, bee.position(), 6, 0.2, 0.02);
                }
                bee.discard();
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ utilitarios

    private static void effect(LivingEntity target, Holder<MobEffect> effect, int ticks, int amplifier,
                               LivingEntity source) {
        target.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, amplifier)), source);
    }

    private static void randomTeleport(ServerLevel level, LivingEntity entity) {
        Vec3 from = entity.position();
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 16;
            double y = Mth.clamp(entity.getY() + entity.getRandom().nextInt(16) - 8,
                    level.getMinBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight() - 1);
            double z = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 16;
            if (entity.isPassenger()) {
                entity.stopRiding();
            }
            if (entity.randomTeleport(x, y, z, true)) {
                level.playSound(null, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT,
                        SoundSource.PLAYERS, 1f, 1f);
                GeneticsFx.burst(level, ParticleTypes.PORTAL, from.add(0, 1, 0), 30, 0.4, 0.2);
                GeneticsFx.burst(level, GeneticsFx.dust(0x6A1B9A, 1.2f), entity.position().add(0, 1, 0), 16, 0.4, 0);
                return;
            }
        }
    }

    private static void swarm(ServerLevel level, Vec3 at, LivingEntity target) {
        Bee bee = EntityType.BEE.create(level);
        if (bee == null) {
            return;
        }
        bee.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
        bee.addTag(SWARM_TAG);
        bee.setPersistentAngerTarget(target.getUUID());
        bee.setRemainingPersistentAngerTime(SWARM_LIFETIME);
        bee.setTarget(target);
        level.addFreshEntity(bee);
        SWARMS.add(new Swarm(bee, level.getGameTime() + SWARM_LIFETIME));
        level.playSound(null, bee.blockPosition(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, 0.8f, 1.2f);
        GeneticsFx.burst(level, GeneticsFx.dust(0xF2C230, 1f), at, 10, 0.3, 0);
    }

    private static void sporeBurst(ServerLevel level, Vec3 at, double radius, int amplifier, LivingEntity owner) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                owner.getBoundingBox().move(at.subtract(owner.position())).inflate(radius))) {
            if (target == owner || !target.isAlive() || isAlly(target, owner)) {
                continue;
            }
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, amplifier), owner);
        }
        GeneticsFx.burst(level, GeneticsFx.dust(0x7C9A3B, 1.6f), at.add(0, 0.8, 0), 40, radius * 0.5, 0.02);
        GeneticsFx.burst(level, ParticleTypes.SPORE_BLOSSOM_AIR, at.add(0, 1, 0), 30, radius * 0.4, 0.01);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.PLAYERS, 1f, 0.8f);
    }

    private static void blast(ServerLevel level, Vec3 at, float radius, float damage, LivingEntity owner) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(at, at).inflate(radius))) {
            if (target == owner || !target.isAlive() || isAlly(target, owner)) {
                continue;
            }
            target.hurt(level.damageSources().explosion(owner, owner), damage);
            Vec3 push = target.position().subtract(at);
            target.knockback(0.6, -push.x, -push.z);
        }
        GeneticsFx.burst(level, ParticleTypes.EXPLOSION, at, 3, radius * 0.3, 0);
        GeneticsFx.burst(level, GeneticsFx.dust(0x9ACD32, 1.5f), at, 30, radius * 0.4, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
    }

    private static boolean isAlly(LivingEntity target, LivingEntity owner) {
        return target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID())
                || target.isAlliedTo(owner);
    }
}
