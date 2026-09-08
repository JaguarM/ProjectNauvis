package com.jaguarm.nauvispower.compat.jade;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.SolarPanelBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** What a solar panel says: what it is making, and if nothing, why. */
public class SolarPanelReadout implements IServerDataProvider<BlockAccessor> {

    public static final SolarPanelReadout INSTANCE = new SolarPanelReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisPower.MODID, "solar_panel");

    static final String CHARGE = "Charge";
    static final String OUTPUT = "Output";
    static final String SKY = "Sky";

    private SolarPanelReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SolarPanelBlockEntity panel
                && accessor.getLevel() instanceof ServerLevel level) {
            data.putInt(CHARGE, panel.energyStored());
            data.putInt(OUTPUT, panel.lastOutput());
            data.putBoolean(SKY, panel.seesSky(level));
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
            if (!data.getBooleanOr(SKY, true)) {
                tooltip.add(Component.translatable("jade.nauvis_power.solar.no_sky"));
            } else if (data.getIntOr(CHARGE, 0) >= SolarPanelBlockEntity.ENERGY_CAPACITY) {
                tooltip.add(Component.translatable("jade.nauvis_power.solar.full"));
            } else if (data.getIntOr(OUTPUT, 0) > 0) {
                tooltip.add(Component.translatable("jade.nauvis_power.solar.making", data.getIntOr(OUTPUT, 0)));
            } else {
                tooltip.add(Component.translatable("jade.nauvis_power.solar.night"));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
