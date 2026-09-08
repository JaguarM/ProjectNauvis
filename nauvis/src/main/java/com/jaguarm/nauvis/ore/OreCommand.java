package com.jaguarm.nauvis.ore;

import java.util.List;

import com.jaguarm.nauvis.Nauvis;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /ore patch <iron|copper|coal> [position]} puts a patch under a position, its top four
 * blocks below the feet; {@code /ore starting} puts the world's three starting patches where the
 * seed says. Gamemaster only, like {@code /oil}, for a world whose preset ran no features.
 */
@EventBusSubscriber(modid = Nauvis.MODID)
public final class OreCommand {

    private OreCommand() {}

    private static final String POSITION = "position";
    /** How far under the feet the floor's middle goes: within a pickaxe's reach, out of sight. */
    private static final int UNDER_FEET = 6;

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        var patch = Commands.literal("patch");
        for (OreKind kind : OreKind.values()) {
            patch.then(Commands.literal(kind.id)
                    .executes(context -> patch(context.getSource(), kind, feet(context.getSource())))
                    .then(Commands.argument(POSITION, BlockPosArgument.blockPos())
                            .executes(context -> patch(context.getSource(), kind,
                                    BlockPosArgument.getLoadedBlockPos(context, POSITION)))));
        }
        dispatcher.register(Commands.literal("ore")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(patch)
                .then(Commands.literal("starting").executes(context -> starting(context.getSource()))));
    }

    private static BlockPos feet(CommandSourceStack source) {
        return BlockPos.containing(source.getPosition());
    }

    private static int patch(CommandSourceStack source, OreKind kind, BlockPos at) {
        ServerLevel level = source.getLevel();
        OrePatch patch = OrePatches.patch(level.getRandom(), kind, at.getX(), at.getZ(),
                at.getY() - UNDER_FEET - OrePatches.LAYERS_MAX + 1, OrePatches.LAYERS_MAX);
        int placed = OrePatchFeature.place(level, patch);
        if (placed == 0) {
            source.sendFailure(Component.translatable(key("patch.none")));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(key("patch"), kind.id, placed,
                Math.round(2 * patch.halfWidth()), Math.round(2 * patch.halfDepth()),
                patch.minY(), patch.maxY(), at.getX(), at.getZ()), true);
        return placed;
    }

    private static int starting(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<OrePatch> patches = OrePatches.startingPatches(level.getSeed());
        int placed = 0;
        for (OrePatch patch : patches) {
            placed += OrePatchFeature.place(level, patch);
            source.sendSuccess(() -> Component.translatable(key("starting.patch"), patch.kind().id,
                    patch.centreX(), patch.bottomY(), patch.centreZ()), true);
        }
        if (placed == 0) {
            source.sendFailure(Component.translatable(key("patch.none")));
        }
        return placed;
    }

    private static String key(String what) {
        return "commands.nauvis.ore." + what;
    }
}
