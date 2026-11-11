package com.kmod.atrocities.mutation;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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
                                                        BoolArgumentType.getBool(context, "volatile"))))))));
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
