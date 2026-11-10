package com.kmod.atrocities.item;

import com.kmod.atrocities.entity.boss.BioProjectile;
import com.kmod.atrocities.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Espinho Quitinoso e Bolota de Bile: restos dos chefes que tambem podem ser arremessados. */
public class BioThrowableItem extends Item {

    private final BioProjectile.Kind kind;
    private final float damage;
    private final float velocity;
    private final int cooldown;

    public BioThrowableItem(Properties properties, BioProjectile.Kind kind, float damage, float velocity, int cooldown) {
        super(properties);
        this.kind = kind;
        this.damage = damage;
        this.velocity = velocity;
        this.cooldown = cooldown;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            BioProjectile projectile = new BioProjectile(server, player, kind, damage, stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, velocity, 1f);
            server.addFreshEntity(projectile);
            GeneticsFx.sound(server, player.position(),
                    kind == BioProjectile.Kind.SPINE ? ModSounds.BOSS_SPINE_SHOT.get() : ModSounds.LEGION_SPIT.get(),
                    0.6f, 1.2f + server.random.nextFloat() * 0.2f);
        }
        player.getCooldowns().addCooldown(this, cooldown);
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = kind == BioProjectile.Kind.SPINE ? "tooltip.atrocities.chitin_spine" : "tooltip.atrocities.bile_glob";
        tooltip.add(Component.translatable(key, (int) damage).withStyle(ChatFormatting.GRAY));
    }
}
