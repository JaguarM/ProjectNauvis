package com.jaguarm.nauvisfluids.compat.jade;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpBlockEntity;
import com.jaguarm.nauvisfluids.offshorepump.OffshorePumpStatus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What an offshore pump says: what is in its tank, and whether it is pumping - and if not, why
 * not. The why is the line that earns its place: a pump set beside a poured puddle says so.
 *
 * <p>Two classes, a data half and a {@code Client} half, because Jade throws at registration if
 * one object is both.
 */
public class OffshorePumpReadout implements IServerDataProvider<BlockAccessor> {

    public static final OffshorePumpReadout INSTANCE = new OffshorePumpReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "offshore_pump");

    static final String STORED = "Stored";
    static final String STATUS = "Status";

    private OffshorePumpReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof OffshorePumpBlockEntity pump)) {
            return;
        }
        data.putInt(STORED, pump.stored());
        data.putInt(STATUS, pump.status().ordinal());
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: two lines. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(STATUS)) {
                return;
            }

            tooltip.add(Component.translatable("jade.nauvis_fluids.offshore_pump.stored",
                    data.getIntOr(STORED, 0), OffshorePumpBlockEntity.TANK_CAPACITY));

            OffshorePumpStatus status = OffshorePumpStatus.byOrdinal(data.getIntOr(STATUS, 0));
            tooltip.add(Component.translatable(switch (status) {
                case PUMPING -> "jade.nauvis_fluids.offshore_pump.pumping";
                case OUTPUT_FULL -> "jade.nauvis_fluids.offshore_pump.full";
                case NO_WATER -> "jade.nauvis_fluids.offshore_pump.no_water";
                case WRONG_WATER -> "jade.nauvis_fluids.offshore_pump.wrong_water";
            }));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
