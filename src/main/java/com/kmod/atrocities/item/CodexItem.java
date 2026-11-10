package com.kmod.atrocities.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Códex Atrocitas: abre o livro-guia. Todo o conteúdo vive no cliente
 * ({@code assets/<ns>/codex/}); o progresso de descobertas vem do servidor.
 */
public class CodexItem extends Item {

    public CodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.kmod.atrocities.client.codex.CodexClient.openBook();
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 1f, 0.7f + level.random.nextFloat() * 0.2f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
