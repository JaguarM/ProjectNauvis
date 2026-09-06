package com.jaguarm.nauvispower.compat.jade;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a boiler says: how much steam it is holding, how much water it has to make more, and
 * whether it is burning.
 *
 * <p>All three matter, and only together. A boiler that has stopped because it is full, one that
 * has run dry and one that has run out of coal are the same block from the outside, and the
 * difference is what the player needs to do about it.
 *
 * <p><b>The reading and the drawing are two classes on purpose.</b> Jade has refused to let one
 * object be both a data provider and a component provider since 1.21.6 - it throws at registration
 * - because the data half runs on the server and the drawing half on the client, and one class
 * doing both is one class that can accidentally reach across. {@link Client} is the drawing half,
 * and the pair shares a uid so a player toggling this readout off turns off both.
 */
public class BoilerReadout implements IServerDataProvider<BlockAccessor> {

    public static final BoilerReadout INSTANCE = new BoilerReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisPower.MODID, "boiler");

    static final String STEAM = "Steam";
    static final String WATER = "Water";
    static final String BURNING = "Burning";

    private BoilerReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof BoilerBlockEntity boiler) {
            data.putInt(STEAM, boiler.steam());
            data.putInt(WATER, boiler.water());
            data.putBoolean(BURNING, boiler.burnTime() > 0);
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: turns the numbers above into three lines. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(STEAM)) {
                return;
            }

            int steam = data.getIntOr(STEAM, 0);
            int water = data.getIntOr(WATER, 0);
            tooltip.add(Component.translatable("jade.nauvis_power.water", water,
                    BoilerBlockEntity.WATER_CAPACITY));
            tooltip.add(Component.translatable("jade.nauvis_power.steam", steam,
                    BoilerBlockEntity.STEAM_CAPACITY));

            if (data.getBooleanOr(BURNING, false)) {
                tooltip.add(Component.translatable("jade.nauvis_power.boiler.burning"));
            } else if (steam >= BoilerBlockEntity.STEAM_CAPACITY) {
                // Not a fault. A boiler nobody is drawing from is supposed to stop - see
                // power_chain_sleeps - and saying so stops it reading as one.
                tooltip.add(Component.translatable("jade.nauvis_power.boiler.full"));
            } else if (water < BoilerBlockEntity.WATER_PER_TICK) {
                tooltip.add(Component.translatable("jade.nauvis_power.boiler.no_water"));
            } else {
                tooltip.add(Component.translatable("jade.nauvis_power.boiler.no_fuel"));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
