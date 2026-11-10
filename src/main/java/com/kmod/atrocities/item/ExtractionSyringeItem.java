package com.kmod.atrocities.item;

import com.kmod.atrocities.genetics.GeneSample;
import com.kmod.atrocities.genetics.GeneticsRegistries;
import com.kmod.atrocities.genetics.Species;
import com.kmod.atrocities.registry.ModItems;
import com.kmod.atrocities.registry.ModSounds;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Seringa de Extracao: crava a agulha num especime enfraquecido e enche um Frasco de Amostra
 * com material genetico ({@link GeneSample}).
 * <ul>
 *   <li>O alvo precisa estar abaixo da fracao de vida definida pela especie.</li>
 *   <li>Consome um Frasco de Amostra do inventario (mao secundaria primeiro).</li>
 *   <li>Quanto mais ferido o alvo, mais pura a amostra.</li>
 *   <li>A agulha fere o alvo (sem matar), entao ele revida.</li>
 * </ul>
 */
public class ExtractionSyringeItem extends Item {

    public static final int COOLDOWN_TICKS = 30;
    private static final float NEEDLE_DAMAGE = 2f;
    private static final int EXCEPTIONAL_PURITY = 85;

    public ExtractionSyringeItem(Properties properties) {
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

        Optional<Holder.Reference<Species>> found = target instanceof Player
                ? Optional.empty()
                : GeneticsRegistries.speciesFor(level.registryAccess(), target.getType());
        if (found.isEmpty()) {
            GeneticsFx.reject(player, this,
                    Component.translatable("message.atrocities.syringe.incompatible", target.getDisplayName()));
            return InteractionResult.CONSUME;
        }
        Species species = found.get().value();

        float ratio = target.getHealth() / target.getMaxHealth();
        if (ratio > species.extraction().syringeHealthRatio()) {
            GeneticsFx.reject(player, this, Component.translatable("message.atrocities.syringe.too_healthy",
                    target.getDisplayName(), Math.round(species.extraction().syringeHealthRatio() * 100)));
            return InteractionResult.CONSUME;
        }

        boolean creative = player.getAbilities().instabuild;
        ItemStack vial = findVial(player);
        if (vial.isEmpty() && !creative) {
            GeneticsFx.reject(player, this, Component.translatable("message.atrocities.syringe.no_vial"));
            return InteractionResult.CONSUME;
        }

        GeneSample sample = GeneSample.extract(found.get(), ratio, level.random);
        if (!creative) {
            vial.shrink(1);
        }
        ItemStack filled = ModItems.geneSample(sample);
        if (!player.getInventory().add(filled)) {
            player.drop(filled, false);
        }

        float damage = Math.min(NEEDLE_DAMAGE, target.getHealth() - 1f);
        if (damage > 0) {
            target.hurt(level.damageSources().playerAttack(player), damage);
        }

        playExtractionFx(level, player, target, species, sample);

        player.getItemInHand(hand).hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.CONSUME;
    }

    private static void playExtractionFx(ServerLevel level, Player player, LivingEntity target, Species species,
                                         GeneSample sample) {
        Vec3 wound = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6)).subtract(0, 0.35, 0);

        GeneticsFx.sound(level, wound, ModSounds.SYRINGE_PIERCE.get(), 1f, 1f);
        GeneticsFx.sound(level, hand, ModSounds.SYRINGE_DRAW.get(), 0.9f, 0.8f + sample.purity() / 250f);

        GeneticsFx.burst(level, GeneticsFx.dust(species.color(), 1.3f), wound, 24, 0.25, 0.02);
        GeneticsFx.burst(level, ParticleTypes.DAMAGE_INDICATOR, wound, 3, 0.15, 0.1);
        GeneticsFx.stream(level, GeneticsFx.dust(species.color(), 0.8f), wound, hand, 10);

        boolean exceptional = sample.purity() >= EXCEPTIONAL_PURITY;
        if (exceptional) {
            GeneticsFx.burst(level, ParticleTypes.ENCHANTED_HIT, wound, 16, 0.3, 0.3);
        }
        Component message = Component.translatable(
                exceptional ? "message.atrocities.syringe.exceptional" : "message.atrocities.syringe.extracted",
                target.getDisplayName(), sample.purity());
        GeneticsFx.notify(player, message.copy().withStyle(exceptional ? ChatFormatting.GOLD : ChatFormatting.GREEN));
    }

    /** Frasco de Amostra vazio: mao secundaria primeiro, depois o resto do inventario. */
    private static ItemStack findVial(Player player) {
        if (player.getOffhandItem().is(ModItems.SAMPLE_VIAL.get())) {
            return player.getOffhandItem();
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.is(ModItems.SAMPLE_VIAL.get())) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }
}
