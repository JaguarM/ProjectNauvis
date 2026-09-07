package com.jaguarm.nauvismilitary.pollution;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /pollution}: how much is over the chunk you stand in, and how much the world has made.
 * {@code /pollution set <amount>} puts a number over it, for watching what a number does.
 */
@EventBusSubscriber(modid = NauvisMilitary.MODID)
public final class PollutionCommand {

    private PollutionCommand() {}

    private static final String AMOUNT = "amount";

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("pollution")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> here(context.getSource()))
                .then(Commands.literal("set")
                        .then(Commands.argument(AMOUNT, DoubleArgumentType.doubleArg(0))
                                .executes(context -> set(context.getSource(),
                                        DoubleArgumentType.getDouble(context, AMOUNT))))));
    }

    private static ChunkPos chunkOf(CommandSourceStack source) {
        return ChunkPos.containing(BlockPos.containing(source.getPosition()));
    }

    private static int here(CommandSourceStack source) {
        PollutionState state = PollutionState.get(source.getLevel());
        double here = state.at(chunkOf(source));
        source.sendSuccess(() -> Component.translatable("commands.nauvis_military.pollution.here",
                String.format("%.1f", here), String.format("%.0f", state.total())), false);
        BlockPos polluter = state.source(chunkOf(source));
        if (polluter != null) {
            source.sendSuccess(() -> Component.translatable("commands.nauvis_military.pollution.source",
                    polluter.getX(), polluter.getY(), polluter.getZ()), false);
        }
        return (int) here;
    }

    private static int set(CommandSourceStack source, double amount) {
        PollutionState state = PollutionState.get(source.getLevel());
        state.set(chunkOf(source), amount);
        source.sendSuccess(() -> Component.translatable("commands.nauvis_military.pollution.set",
                String.format("%.1f", amount)), true);
        return (int) amount;
    }
}
