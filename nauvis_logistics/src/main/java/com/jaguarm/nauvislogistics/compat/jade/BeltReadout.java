package com.jaguarm.nauvislogistics.compat.jade;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.belt.BeltLines;
import com.jaguarm.nauvislogistics.belt.BeltRun;
import com.jaguarm.nauvislogistics.belt.Belts;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a belt says about the line it belongs to.
 *
 * <p>The two questions a belt raises and neither of which the block answers: how long is this
 * line, and how full is it. A belt block holds nothing - the run holds everything - so a readout
 * of the block alone would always say nothing at all, exactly as the pipe's would.
 *
 * <p>How full is quoted per lane, because a Factorio player thinks in lanes: a belt that is
 * {@code 20/20 left, 3/20 right} is a belt that needs a second source, and one number averaging
 * the two would hide the only thing worth knowing.
 */
public class BeltReadout implements IServerDataProvider<BlockAccessor> {

    public static final BeltReadout INSTANCE = new BeltReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "belt");

    static final String BELTS = "Belts";
    static final String LEFT = "Left";
    static final String RIGHT = "Right";
    static final String CAPACITY = "LaneCapacity";
    static final String MOVING = "Moving";

    private BeltReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BeltLines lines = BeltLines.of(level);
        BeltRun run = lines.runAt(accessor.getPosition());
        if (run == null) {
            return;
        }

        data.putInt(BELTS, run.blocks().size());
        data.putInt(LEFT, run.lane(Belts.LEFT).size());
        data.putInt(RIGHT, run.lane(Belts.RIGHT).size());
        data.putInt(CAPACITY, run.length() / Belts.SPACING);
        data.putBoolean(MOVING, lines.isActive(run));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: how long the line is, and how much is on each lane of it. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(BELTS)) {
                return;
            }

            tooltip.add(Component.translatable("jade.nauvis_logistics.belt.length",
                    data.getIntOr(BELTS, 0)));
            tooltip.add(Component.translatable("jade.nauvis_logistics.belt.lanes",
                    data.getIntOr(LEFT, 0), data.getIntOr(RIGHT, 0), data.getIntOr(CAPACITY, 0)));

            if (data.getBooleanOr(MOVING, false)) {
                tooltip.add(Component.translatable("jade.nauvis_logistics.belt.carrying"));
            } else {
                tooltip.add(Component.translatable("jade.nauvis_logistics.belt.empty"));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
