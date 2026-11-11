package com.kmod.atrocities.item;

import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.mutation.Mutation;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Soro Mutagenico (e sua versao Volatil).
 * <ul>
 *   <li>Clique direito num mob mutavel: injeta uma dose (estagio +1; na terceira, metamorfose).</li>
 *   <li>Clique direito no ar: arremessa o frasco, que injeta no mob que acertar (bom contra Phantoms).</li>
 * </ul>
 * O Soro Volatil tem 15% de chance de romper o mob, mas, se for a ultima dose, gera um mutante Alfa.
 */
public class MutagenSerumItem extends Item {

    public static final int INJECT_COOLDOWN = 12;
    public static final int THROW_COOLDOWN = 20;

    private final boolean volatileSerum;

    public MutagenSerumItem(Properties properties, boolean volatileSerum) {
        super(properties);
        this.volatileSerum = volatileSerum;
    }

    public boolean isVolatile() {
        return volatileSerum;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof Mob mob)) {
            return InteractionResult.PASS;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Mutation.Result result = Mutation.inject(level, mob, player, volatileSerum);
        if (!result.consumed()) {
            player.getCooldowns().addCooldown(this, 10);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GENETICS_REJECT.get(),
                    SoundSource.PLAYERS, 0.8f, 1f);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(this, INJECT_COOLDOWN);
        player.swing(hand, true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            BioProjectile vial = new BioProjectile(server, player,
                    volatileSerum ? BioProjectile.Kind.SERUM_VOLATILE : BioProjectile.Kind.SERUM, 0f, stack);
            vial.shootFromRotation(player, player.getXRot(), player.getYRot(), -10f, 1.1f, 0.8f);
            server.addFreshEntity(vial);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.MUTAGEN_THROW.get(),
                    SoundSource.PLAYERS, 0.8f, 0.9f + server.random.nextFloat() * 0.2f);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(this, THROW_COOLDOWN);
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.atrocities.mutagen_serum.use", Mutation.FINAL_STAGE)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.atrocities.mutagen_serum.throw").withStyle(ChatFormatting.DARK_GRAY));
        if (volatileSerum) {
            tooltip.add(Component.translatable("tooltip.atrocities.volatile_serum.alpha").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.atrocities.volatile_serum.risk",
                    Math.round(Mutation.RUPTURE_CHANCE * 100)).withStyle(ChatFormatting.RED));
        }
    }
}
