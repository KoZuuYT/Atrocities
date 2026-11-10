package com.kmod.atrocities.item;

import com.kmod.atrocities.config.AtrocitiesConfig;
import com.kmod.atrocities.entity.boss.AtrocityBoss;
import com.kmod.atrocities.genetics.EmbryoGenome;
import com.kmod.atrocities.registry.ModDataComponents;
import com.kmod.atrocities.registry.ModEntities;
import com.kmod.atrocities.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * Embriao Maduro: usado sobre o chao, desperta o chefe do seu genoma.
 * Exige dificuldade diferente de Pacifico e espaco livre para o tamanho do chefe.
 */
public class MaturedEmbryoItem extends Item {

    public MaturedEmbryoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        EmbryoGenome genome = stack.get(ModDataComponents.EMBRYO_GENOME.get());
        Player player = context.getPlayer();
        if (genome == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            if (player != null) {
                GeneticsFx.reject(player, this, Component.translatable("message.atrocities.embryo.peaceful"));
            }
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        EntityType<? extends AtrocityBoss> type = ModEntities.bossFor(genome.archetype());
        AtrocityBoss boss = type.create(level);
        if (boss == null) {
            return InteractionResult.FAIL;
        }
        float yaw = player != null ? player.getYRot() + 180f : 0f;
        boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
        boss.initFromGenome(genome);
        if (!level.noCollision(boss, boss.getBoundingBox())) {
            if (player != null) {
                GeneticsFx.reject(player, this, Component.translatable("message.atrocities.embryo.no_space"));
            }
            return InteractionResult.FAIL;
        }
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            boss.setCustomName(stack.getHoverName());
        }
        boss.startEmerging();
        level.addFreshEntity(boss);

        Vec3 at = boss.position();
        GeneticsFx.sound(level, at, ModSounds.EMBRYO_AWAKEN.get(), 3f, 0.7f);
        GeneticsFx.burst(level, GeneticsFx.dust(genome.archetype().color(), 2.5f), at.add(0, 0.4, 0), 80,
                boss.getBbWidth() * 0.6, 0.05);
        GeneticsFx.burst(level, ParticleTypes.SCULK_SOUL, at.add(0, 0.5, 0), 20, boss.getBbWidth() * 0.4, 0.05);
        GeneticsFx.spiral(level, ParticleTypes.WITCH, at, boss.getBbWidth() * 0.7, boss.getBbHeight(), 50);

        Component announce = Component.translatable("message.atrocities.boss.awakened", boss.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        if (AtrocitiesConfig.BOSS_BROADCAST.get()) {
            level.getServer().getPlayerList().broadcastSystemMessage(announce, false);
        } else if (player != null) {
            player.displayClientMessage(announce, false);
        }
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
