package com.jaguarm.nauvismilitary.compat.jade;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.jaguarm.nauvismilitary.turret.GunTurretBlockEntity;
import com.jaguarm.nauvismilitary.turret.GunTurretScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a turret says when looked at: whether it is loaded, and with how much.
 *
 * <p>Two classes, a data half and a {@link Client} half, sharing one uid: Jade refuses one object
 * that is both. See the furnace's readout in {@code nauvis_machines} for the pattern.
 */
public class TurretReadout implements IServerDataProvider<BlockAccessor> {

    public static final TurretReadout INSTANCE = new TurretReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisMilitary.MODID, "turret");

    static final String STATUS = "Status";
    static final String ROUNDS = "Rounds";
    static final String HEALTH = "Health";
    static final String MAX_HEALTH = "MaxHealth";

    private TurretReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof GunTurretBlockEntity turret)) {
            return;
        }
        data.putInt(STATUS, turret.status().ordinal());
        data.putInt(ROUNDS, turret.roundsLeft());
        data.putInt(HEALTH, Math.round(turret.health()));
        data.putInt(MAX_HEALTH, Math.round(turret.maxHealth()));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: one line, in the screen's words. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(STATUS)) {
                return;
            }
            tooltip.add(GunTurretScreen.statusText(
                    GunTurretBlockEntity.Status.of(data.getIntOr(STATUS, 0)), data.getIntOr(ROUNDS, 0)));
            int health = data.getIntOr(HEALTH, 0);
            int max = data.getIntOr(MAX_HEALTH, 0);
            if (max > 0 && health < max) {
                tooltip.add(Component.translatable("jade.nauvis_military.turret.health", health, max));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
