package com.jaguarm.nauvisfluids.compat.jade;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.oil.CrudeOilBlockEntity;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackBlockEntity;
import com.jaguarm.nauvisfluids.pumpjack.PumpjackStatus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a pumpjack says: what is in its tank, the yield of the well under it, and whether it is
 * pumping - and if not, why not.
 */
public class PumpjackReadout implements IServerDataProvider<BlockAccessor> {

    public static final PumpjackReadout INSTANCE = new PumpjackReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "pumpjack");

    static final String STORED = "Stored";
    static final String YIELD = "Yield";
    static final String STATUS = "Status";

    private PumpjackReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof PumpjackBlockEntity pumpjack)) {
            return;
        }
        data.putInt(STORED, pumpjack.stored());
        data.putInt(STATUS, pumpjack.status().ordinal());
        CrudeOilBlockEntity well = pumpjack.well();
        if (well != null) {
            data.putInt(YIELD, well.yieldPercent());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: three lines. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(STATUS)) {
                return;
            }

            tooltip.add(Component.translatable("jade.nauvis_fluids.pumpjack.stored",
                    data.getIntOr(STORED, 0), PumpjackBlockEntity.TANK_CAPACITY));
            if (data.contains(YIELD)) {
                tooltip.add(Component.translatable("jade.nauvis_fluids.crude_oil.yield", data.getIntOr(YIELD, 0)));
            }

            PumpjackStatus status = PumpjackStatus.byOrdinal(data.getIntOr(STATUS, 0));
            tooltip.add(Component.translatable(switch (status) {
                case PUMPING -> "jade.nauvis_fluids.pumpjack.pumping";
                case OUTPUT_FULL -> "jade.nauvis_fluids.pumpjack.full";
                case NO_POWER -> "jade.nauvis_fluids.pumpjack.no_power";
                case NO_WELL -> "jade.nauvis_fluids.pumpjack.no_well";
            }));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
