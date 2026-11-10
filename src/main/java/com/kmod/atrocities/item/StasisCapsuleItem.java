package com.kmod.atrocities.item;

import com.kmod.atrocities.genetics.CapturedSpecimen;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Species;
import com.kmod.atrocities.registry.ModDataComponents;
import com.kmod.atrocities.registry.ModSounds;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Capsula de Estase: contem um especime vivo inteiro ({@link CapturedSpecimen}).
 * <ul>
 *   <li>Clique numa criatura enfraquecida para captura-la (limite de vida definido pela especie).</li>
 *   <li>Clique num bloco para libera-la; criaturas liberadas nao somem com a distancia.</li>
 *   <li>Jogadores e chefes nao podem ser capturados.</li>
 * </ul>
 * Especimes inteiros rendem material de pureza maxima no Extrator de Biomassa (Fase 3).
 */
public class StasisCapsuleItem extends Item {

    public static final int COOLDOWN_TICKS = 20;

    public StasisCapsuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        // No criativo o jogo passa uma copia do item; alteramos o item real da mao.
        ItemStack held = player.getItemInHand(hand);
        if (held.has(ModDataComponents.CAPTURED_SPECIMEN.get())) {
            GeneticsFx.reject(player, this, Component.translatable("message.atrocities.capsule.full"));
            return InteractionResult.CONSUME;
        }
        if (target instanceof Player) {
            GeneticsFx.reject(player, this, Component.translatable("message.atrocities.capsule.player"));
            return InteractionResult.CONSUME;
        }
        if (target.getType().is(Tags.EntityTypes.BOSSES)) {
            GeneticsFx.reject(player, this,
                    Component.translatable("message.atrocities.capsule.boss", target.getDisplayName()));
            return InteractionResult.CONSUME;
        }
        Optional<Holder.Reference<Species>> found = GeneticsRegistries.speciesFor(level.registryAccess(), target.getType());
        if (found.isEmpty()) {
            GeneticsFx.reject(player, this,
                    Component.translatable("message.atrocities.capsule.incompatible", target.getDisplayName()));
            return InteractionResult.CONSUME;
        }
        Species species = found.get().value();
        if (target.getHealth() / target.getMaxHealth() > species.extraction().captureHealthRatio()) {
            GeneticsFx.reject(player, this, Component.translatable("message.atrocities.capsule.too_healthy",
                    target.getDisplayName(), Math.round(species.extraction().captureHealthRatio() * 100)));
            return InteractionResult.CONSUME;
        }

        target.stopRiding();
        target.ejectPassengers();
        if (target instanceof Leashable leashable && leashable.isLeashed()) {
            leashable.dropLeash(true, true);
        }
        CompoundTag data = new CompoundTag();
        if (!target.save(data)) {
            GeneticsFx.reject(player, this,
                    Component.translatable("message.atrocities.capsule.incompatible", target.getDisplayName()));
            return InteractionResult.CONSUME;
        }
        held.set(ModDataComponents.CAPTURED_SPECIMEN.get(), new CapturedSpecimen(found.get().key(), target.getType(),
                CustomData.of(data), target.getHealth(), target.getMaxHealth()));

        Vec3 feet = target.position();
        Vec3 center = feet.add(0, target.getBbHeight() / 2, 0);
        double radius = Math.max(0.5, target.getBbWidth());
        GeneticsFx.sound(level, center, ModSounds.CAPSULE_CAPTURE.get(), 1f, 1f);
        GeneticsFx.sound(level, player.position(), ModSounds.CAPSULE_SEAL.get(), 0.8f, 1f);
        GeneticsFx.spiral(level, GeneticsFx.dust(species.color(), 1.4f), feet, radius, target.getBbHeight() + 0.5, 40);
        GeneticsFx.burst(level, ParticleTypes.REVERSE_PORTAL, center, 40, radius * 0.5, 0.15);
        GeneticsFx.burst(level, ParticleTypes.FLASH, center, 1, 0, 0);
        GeneticsFx.stream(level, GeneticsFx.dust(species.color(), 0.9f), center,
                player.getEyePosition().subtract(0, 0.4, 0), 12);
        GeneticsFx.notify(player, Component.translatable("message.atrocities.capsule.captured", target.getDisplayName())
                .withStyle(ChatFormatting.AQUA));

        target.discard();
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        CapturedSpecimen specimen = stack.get(ModDataComponents.CAPTURED_SPECIMEN.get());
        Player player = context.getPlayer();
        if (specimen == null || player == null || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        BlockPos spawn = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked
                : clicked.relative(context.getClickedFace());

        Optional<Entity> created = EntityType.create(specimen.entityData().copyTag(), server);
        stack.remove(ModDataComponents.CAPTURED_SPECIMEN.get());
        if (created.isEmpty()) {
            GeneticsFx.reject(player, this, Component.translatable("message.atrocities.capsule.corrupted"));
            return InteractionResult.CONSUME;
        }

        Entity entity = created.get();
        // UUID novo: evita conflito se a capsula tiver sido duplicada (ex.: modo criativo).
        entity.setUUID(UUID.randomUUID());
        entity.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        server.addFreshEntity(entity);

        int color = GeneticsRegistries.species(server.registryAccess(), specimen.species())
                .map(Species::color).orElse(0xFFFFFF);
        Vec3 center = entity.position().add(0, entity.getBbHeight() / 2, 0);
        GeneticsFx.sound(server, center, ModSounds.CAPSULE_RELEASE.get(), 1f, 1f);
        GeneticsFx.burst(server, ParticleTypes.POOF, center, 20, entity.getBbWidth() * 0.4, 0.05);
        GeneticsFx.burst(server, GeneticsFx.dust(color, 1.4f), center, 30, entity.getBbWidth() * 0.5, 0.05);
        GeneticsFx.burst(server, ParticleTypes.FLASH, center, 1, 0, 0);
        GeneticsFx.notify(player, Component.translatable("message.atrocities.capsule.released", entity.getDisplayName())
                .withStyle(ChatFormatting.AQUA));

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResult.CONSUME;
    }
}
