package com.jaguarm.nauvismachines.compat.jade;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.radar.RadarBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a radar says when looked at: whether it is keeping its area loaded, and if not, why not.
 * A radar has no screen, so this is the only place it says anything.
 */
public class RadarReadout implements IServerDataProvider<BlockAccessor> {

    public static final RadarReadout INSTANCE = new RadarReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisMachines.MODID, "radar");

    static final String CHARTING = "Charting";

    private RadarReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof RadarBlockEntity radar) {
            data.putBoolean(CHARTING, radar.isCharting());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: one line. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(CHARTING)) {
                return;
            }
            int across = RadarBlockEntity.RANGE_CHUNKS * 2 + 1;
            tooltip.add(data.getBooleanOr(CHARTING, false)
                    ? Component.translatable("jade.nauvis_machines.radar.charting", across, across)
                    : Component.translatable("jade.nauvis_machines.radar.no_power"));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
