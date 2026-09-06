package com.jaguarm.nauvisfluids.command;

import java.util.List;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.oil.CrudeOilFieldFeature;
import com.jaguarm.nauvisfluids.registry.ModBlocks;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /oil}: puts oil in the ground where there is none.
 *
 * <p>Factorio's map editor can place a crude oil resource anywhere, and this is that. Two things
 * need it. A <b>superflat world runs no features</b> - the default "Classic Flat" preset has
 * {@code features: false}, which is why it has no trees, no ores and no oil - so a test world of
 * the kind everyone builds a factory in has no oil at all unless somebody puts it there. And a
 * playtest that wants to try a pumpjack should not have to walk three hundred blocks first.
 *
 * <p>{@code /oil field} puts a whole field down around where you stand, exactly as worldgen would
 * - the same placement, the same spacing, the same levelling - and says how many wells it managed.
 * {@code /oil well} puts one well under your feet. Gamemaster only, like {@code /research}: this is
 * the editor's tool, not the player's.
 */
@EventBusSubscriber(modid = NauvisFluids.MODID)
public final class OilCommand {

    private OilCommand() {}

    private static final String POSITION = "position";

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("oil")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("field")
                        .executes(context -> field(context.getSource(), feet(context.getSource())))
                        .then(Commands.argument(POSITION, BlockPosArgument.blockPos())
                                .executes(context -> field(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, POSITION)))))
                .then(Commands.literal("well")
                        .executes(context -> well(context.getSource(), feet(context.getSource())))
                        .then(Commands.argument(POSITION, BlockPosArgument.blockPos())
                                .executes(context -> well(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, POSITION))))));
    }

    /** Where the source stands. A command block or the console has a position too. */
    private static BlockPos feet(CommandSourceStack source) {
        return BlockPos.containing(source.getPosition());
    }

    /**
     * A field around {@code origin}, placed by the same code worldgen uses.
     *
     * <p>The origin is the surface column at the position, which is what a feature is handed, so
     * a player standing on a hill or in a valley gets what generation would have put there.
     */
    private static int field(CommandSourceStack source, BlockPos origin) {
        ServerLevel level = source.getLevel();
        List<BlockPos> wells = CrudeOilFieldFeature.placeField(level, level.getRandom(), origin);
        if (wells.isEmpty()) {
            source.sendFailure(Component.translatable(key("field.none")));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(key("field"), wells.size(),
                origin.getX(), origin.getZ()), true);
        return wells.size();
    }

    /**
     * One well, in the ground under {@code at}: the highest solid block of that column, with the
     * three by three around it levelled for a pumpjack, exactly as a generated well is.
     */
    private static int well(CommandSourceStack source, BlockPos at) {
        ServerLevel level = source.getLevel();
        BlockPos ground = CrudeOilFieldFeature.ground(level, at.getX(), at.getZ());
        if (ground == null) {
            source.sendFailure(Component.translatable(key("well.none")));
            return 0;
        }
        CrudeOilFieldFeature.levelAround(level, ground);
        level.setBlock(ground, ModBlocks.CRUDE_OIL.get().defaultBlockState(), Block.UPDATE_ALL);
        int yield = level.getBlockEntity(ground) instanceof CrudeOilBlockEntity well ? well.yieldPercent() : 0;
        source.sendSuccess(() -> Component.translatable(key("well"),
                ground.getX(), ground.getY(), ground.getZ(), yield), true);
        return 1;
    }

    private static String key(String what) {
        return "commands.nauvis_fluids.oil." + what;
    }
}
