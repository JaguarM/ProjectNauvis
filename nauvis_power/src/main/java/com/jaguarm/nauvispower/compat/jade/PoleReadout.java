package com.jaguarm.nauvispower.compat.jade;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.grid.PowerNetwork;
import com.jaguarm.nauvispower.grid.PowerNetworkManager;
import com.jaguarm.nauvispower.grid.ElectricPoleBlock;
import com.jaguarm.nauvislib.multiblock.Multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** What a pole says about the network it belongs to. */
public class PoleReadout implements IServerDataProvider<BlockAccessor> {

    public static final PoleReadout INSTANCE = new PoleReadout();

    /** Both tiers, so not the small pole's name. The config key in the lang file has to match. */
    static final Identifier UID =
            Identifier.fromNamespaceAndPath(NauvisPower.MODID, "electric_pole");

    static final String POLES = "Poles";
    static final String MACHINES = "Machines";
    static final String LIVE = "Live";
    static final String ACCUMULATORS = "Accumulators";
    static final String STORED = "Stored";
    static final String STORAGE = "Storage";

    private PoleReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }

        PowerNetworkManager manager = PowerNetworkManager.of(level);
        PowerNetwork network = manager.networkAt(footOf(accessor));
        if (network == null) {
            return;
        }

        data.putInt(POLES, network.poleCount());
        data.putInt(MACHINES, network.endpointCount());
        data.putBoolean(LIVE, manager.isActive(network));
        int batteries = network.bufferCount();
        if (batteries > 0) {
            data.putInt(ACCUMULATORS, batteries);
            data.putLong(STORED, network.storedInBuffers());
            data.putLong(STORAGE, network.bufferCapacity());
        }
    }

    /**
     * The foot of the pole the player is pointing at.
     *
     * <p>Only the foot carries the block entity and the network membership; the other three blocks
     * are structure. Without this, three quarters of every pole would report nothing.
     */
    private static BlockPos footOf(BlockAccessor accessor) {
        BlockState state = accessor.getBlockState();
        BlockPos pos = accessor.getPosition();
        return state.getBlock() instanceof ElectricPoleBlock pole
                ? Multiblock.anchorPos(pole, state, pos)
                : pos;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: how big the network is, and whether it is carrying anything. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(POLES)) {
                // Either the server has not answered yet, or this pole is on no network at all -
                // which is true for exactly one tick after it is placed, and never after that.
                return;
            }

            tooltip.add(Component.translatable("jade.nauvis_power.network",
                    data.getIntOr(POLES, 0), data.getIntOr(MACHINES, 0)));
            tooltip.add(Component.translatable(data.getBooleanOr(LIVE, false)
                    ? "jade.nauvis_power.network.live"
                    : "jade.nauvis_power.network.idle"));
            if (data.contains(ACCUMULATORS)) {
                tooltip.add(Component.translatable("jade.nauvis_power.network.accumulators",
                        data.getIntOr(ACCUMULATORS, 0), data.getLongOr(STORED, 0),
                        data.getLongOr(STORAGE, 0)));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
