package com.kmod.atrocities.mutation;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /atrocities mutate <alvos> [volatil]}: aplica uma dose de soro nos mobs (operadores, nivel 2).
 * Util para eventos de servidor e testes.
 */
public final class MutationCommand {

    private MutationCommand() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> event.getDispatcher().register(
                Commands.literal("atrocities").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("mutate")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> mutate(context, false))
                                        .then(Commands.argument("volatile", BoolArgumentType.bool())
                                                .executes(context -> mutate(context,
                                                        BoolArgumentType.getBool(context, "volatile"))))))
                        .then(Commands.literal("outbreak")
                                .then(Commands.literal("start")
                                        .executes(context -> outbreak(context, context.getSource().getPlayerOrException(), null))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> outbreak(context,
                                                        EntityArgument.getPlayer(context, "player"), null))
                                                .then(Commands.argument("type", ResourceLocationArgument.id())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                                                MutationRules.all().stream().map(MutationRules.Rule::id), builder))
                                                        .executes(context -> outbreak(context,
                                                                EntityArgument.getPlayer(context, "player"),
                                                                ResourceLocationArgument.getId(context, "type"))))))
                                .then(Commands.literal("stop").executes(context -> {
                                    int count = Outbreak.stopAll(context.getSource().getLevel());
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "commands.atrocities.outbreak.stopped", count), true);
                                    return count;
                                })))));
    }

    private static int outbreak(CommandContext<CommandSourceStack> context, ServerPlayer player,
                                @org.jetbrains.annotations.Nullable ResourceLocation type) {
        var result = Outbreak.start(player.serverLevel(), player, type);
        if (result.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.atrocities.outbreak.failed"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.atrocities.outbreak.started",
                player.getDisplayName()), true);
        return 1;
    }

    private static int mutate(CommandContext<CommandSourceStack> context, boolean volatileSerum) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int done = 0;
        for (Entity entity : EntityArgument.getEntities(context, "targets")) {
            if (entity instanceof Mob mob && Mutation.isMutable(mob)) {
                Mutation.Result result = Mutation.inject(source.getLevel(), mob, null, volatileSerum);
                if (result.consumed()) {
                    done++;
                }
            }
        }
        int count = done;
        source.sendSuccess(() -> Component.translatable("commands.atrocities.mutate", count), true);
        return count;
    }
}
