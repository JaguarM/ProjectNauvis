package com.jaguarm.nauvisfluids.compat.jade;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** What an oil well says: its yield, which is the one thing Factorio's tooltip says about one. */
public class CrudeOilReadout implements IServerDataProvider<BlockAccessor> {

    public static final CrudeOilReadout INSTANCE = new CrudeOilReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "crude_oil");

    static final String YIELD = "Yield";
    static final String AT_FLOOR = "AtFloor";

    private CrudeOilReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof CrudeOilBlockEntity well) {
            data.putInt(YIELD, well.yieldPercent());
            data.putBoolean(AT_FLOOR, well.isAtFloor());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(YIELD)) {
                return;
            }
            tooltip.add(Component.translatable("jade.nauvis_fluids.crude_oil.yield", data.getIntOr(YIELD, 0)));
            if (data.getBooleanOr(AT_FLOOR, false)) {
                // Not running out. A well at its floor pumps at that rate for ever, and a player who
                // has watched the number fall for hours deserves to be told it has stopped falling.
                tooltip.add(Component.translatable("jade.nauvis_fluids.crude_oil.floor"));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
