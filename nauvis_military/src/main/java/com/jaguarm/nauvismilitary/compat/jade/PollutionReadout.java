package com.jaguarm.nauvismilitary.compat.jade;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.jaguarm.nauvismilitary.pollution.PollutionState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The cloud over whatever is looked at: one line, on any block, whenever there is any.
 *
 * <p>Factorio shows pollution as a map overlay. There is no map here, so the hover readout says
 * what is over the chunk of the block under the cursor - which, walking through a base, is the
 * same information read one chunk at a time. Nothing at all where the air is clean, so a base
 * that has not started polluting is not told about pollution.
 */
public class PollutionReadout implements IServerDataProvider<BlockAccessor> {

    public static final PollutionReadout INSTANCE = new PollutionReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisMilitary.MODID, "pollution");

    static final String POLLUTION = "Pollution";

    private PollutionReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }
        double amount = PollutionState.get(level).at(ChunkPos.containing(accessor.getPosition()));
        if (amount > 0) {
            data.putDouble(POLLUTION, amount);
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(POLLUTION)) {
                return;
            }
            tooltip.add(Component.translatable("readout.nauvis_military.pollution",
                    String.format("%.1f", data.getDoubleOr(POLLUTION, 0))));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
