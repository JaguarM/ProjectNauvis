package com.jaguarm.nauvisrocket.compat.jade;

import com.jaguarm.nauvisrocket.NauvisRocket;
import com.jaguarm.nauvisrocket.silo.RocketSiloBlockEntity;
import com.jaguarm.nauvisrocket.silo.RocketSiloScreen;
import com.jaguarm.nauvisrocket.silo.RocketSiloStatus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a silo says when looked at: how far the rocket has got, what it is waiting for, and how
 * many it has sent. Jade's own providers draw the slots and the energy bar.
 */
public class RocketSiloReadout implements IServerDataProvider<BlockAccessor> {

    public static final RocketSiloReadout INSTANCE = new RocketSiloReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisRocket.MODID, "rocket_silo");

    static final String STATUS = "Status";
    static final String PARTS = "Parts";
    static final String NEEDED = "Needed";
    static final String OWED = "Owed";
    static final String LAUNCHES = "Launches";
    static final String AUTO = "Auto";

    private RocketSiloReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof RocketSiloBlockEntity silo)) {
            return;
        }
        data.putInt(STATUS, silo.status().ordinal());
        data.putInt(PARTS, silo.parts());
        data.putInt(NEEDED, silo.partsNeeded());
        data.putInt(OWED, silo.owed());
        data.putInt(LAUNCHES, silo.launches());
        data.putBoolean(AUTO, silo.autoLaunch());
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: the status line the screen shows, and the count of launches. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(STATUS)) {
                return;
            }
            tooltip.add(RocketSiloScreen.statusText(RocketSiloStatus.of(data.getIntOr(STATUS, 0)),
                    data.getIntOr(PARTS, 0), data.getIntOr(NEEDED, 0), data.getIntOr(OWED, 0),
                    data.getBooleanOr(AUTO, true)));
            int launches = data.getIntOr(LAUNCHES, 0);
            if (launches > 0) {
                tooltip.add(Component.translatable("jade.nauvis_rocket.silo.launches", launches));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
