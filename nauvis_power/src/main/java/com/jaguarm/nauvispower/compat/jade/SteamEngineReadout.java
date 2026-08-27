package com.jaguarm.nauvispower.compat.jade;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.SteamEngineBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a steam engine says: whether it is running, and what it is making while it does.
 *
 * <p>Jade's universal energy provider already draws the charge, because the engine publishes
 * {@code Capabilities.Energy.BLOCK} like anything else would. What it cannot know is that a full
 * engine has deliberately stopped rather than broken, or that the thing limiting it is the steam
 * behind it rather than the wire in front.
 *
 * <p>Split into a server half and a {@link Client} half - see {@link BoilerReadout} for why.
 */
public class SteamEngineReadout implements IServerDataProvider<BlockAccessor> {

    public static final SteamEngineReadout INSTANCE = new SteamEngineReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisPower.MODID, "steam_engine");

    static final String STEAM = "Steam";
    static final String CHARGE = "Charge";

    private SteamEngineReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SteamEngineBlockEntity engine) {
            data.putInt(STEAM, engine.steam());
            data.putInt(CHARGE, engine.energyStored());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: which of the three things the engine is doing. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(CHARGE)) {
                return;
            }

            if (data.getIntOr(CHARGE, 0) >= SteamEngineBlockEntity.ENERGY_CAPACITY) {
                // Full, which means nothing is drawing. The engine has stopped on purpose and the
                // boiler behind it has stopped too; that is the design, not a fault.
                tooltip.add(Component.translatable("jade.nauvis_power.engine.full"));
            } else if (data.getIntOr(STEAM, 0) >= SteamEngineBlockEntity.STEAM_PER_TICK) {
                tooltip.add(Component.translatable("jade.nauvis_power.engine.running",
                        SteamEngineBlockEntity.ENERGY_PER_TICK));
            } else {
                tooltip.add(Component.translatable("jade.nauvis_power.engine.no_steam"));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
