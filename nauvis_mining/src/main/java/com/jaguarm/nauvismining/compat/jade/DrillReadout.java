package com.jaguarm.nauvismining.compat.jade;

import com.jaguarm.nauvismining.NauvisMining;
import com.jaguarm.nauvismining.machine.miner.MinerBlockEntity;
import com.jaguarm.nauvismining.machine.miner.MinerScreen;
import com.jaguarm.nauvismining.machine.miner.MinerStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** What a drill says when looked at: what it is mining, or why it has stopped. */
public class DrillReadout implements IServerDataProvider<BlockAccessor> {

    public static final DrillReadout INSTANCE = new DrillReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisMining.MODID, "drill");

    static final String STATUS = "Status";
    static final String MINING = "Mining";

    private DrillReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MinerBlockEntity drill)) {
            return;
        }
        data.putInt(STATUS, drill.status().ordinal());
        if (drill.mining() != null) {
            data.putString(MINING, BuiltInRegistries.BLOCK.getKey(drill.mining()).toString());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: one line, in the screen's words. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(STATUS)) {
                return;
            }
            MinerStatus status = MinerStatus.byOrdinal(data.getIntOr(STATUS, 0));
            Block mining = data.getString(MINING)
                    .map(Identifier::tryParse)
                    .map(BuiltInRegistries.BLOCK::getValue)
                    .orElse(null);
            tooltip.add(MinerScreen.statusText(status, mining));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
