package com.jaguarm.nauvispower.compat.jade;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.storage.AccumulatorBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What an accumulator says: which way its charge is moving, and how fast.
 *
 * <p>Jade's own energy bar draws the charge. What it cannot know is the direction - a battery at
 * half looks the same filling as emptying - and that is the one thing a player wants from a glance
 * at an accumulator field at dusk. Three states, and the idle one says the level in words because
 * a full battery and an empty one are both idle and want opposite things done about them.
 *
 * <p>Two classes, a data half and a {@code Client} half, because Jade throws at registration if
 * one object is both.
 */
public class AccumulatorReadout implements IServerDataProvider<BlockAccessor> {

    public static final AccumulatorReadout INSTANCE = new AccumulatorReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisPower.MODID, "accumulator");

    static final String CHARGE = "Charge";
    static final String FLOW = "Flow";

    private AccumulatorReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof AccumulatorBlockEntity accumulator) {
            data.putInt(CHARGE, accumulator.energyStored());
            data.putInt(FLOW, accumulator.flow());
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
            if (!data.contains(CHARGE)) {
                return;
            }
            int flow = data.getIntOr(FLOW, 0);
            int charge = data.getIntOr(CHARGE, 0);
            if (flow > 0) {
                tooltip.add(Component.translatable("jade.nauvis_power.accumulator.charging", flow));
            } else if (flow < 0) {
                tooltip.add(Component.translatable("jade.nauvis_power.accumulator.discharging", -flow));
            } else if (charge >= AccumulatorBlockEntity.CAPACITY) {
                tooltip.add(Component.translatable("jade.nauvis_power.accumulator.full"));
            } else if (charge <= 0) {
                tooltip.add(Component.translatable("jade.nauvis_power.accumulator.empty"));
            } else {
                tooltip.add(Component.translatable("jade.nauvis_power.accumulator.idle",
                        100 * charge / AccumulatorBlockEntity.CAPACITY));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
