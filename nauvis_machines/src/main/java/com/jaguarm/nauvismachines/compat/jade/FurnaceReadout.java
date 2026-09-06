package com.jaguarm.nauvismachines.compat.jade;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlockEntity;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceScreen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a furnace says when looked at: what it is smelting, or why it has stopped.
 *
 * <p>Jade's own providers already show the slots and, for the electric tier, the energy bar. The
 * one thing they cannot know is the difference between a furnace waiting for coal, one waiting
 * for its plates to be taken away, and one holding five iron plates for a recipe nobody has
 * researched - and those want three different things done about them.
 *
 * <p>Two classes, a data half and a {@link Client} half, sharing one uid: Jade refuses one object
 * that is both. See the boiler's readout in {@code nauvis_power} for the pattern.
 */
public class FurnaceReadout implements IServerDataProvider<BlockAccessor> {

    public static final FurnaceReadout INSTANCE = new FurnaceReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisMachines.MODID, "furnace");

    static final String STATUS = "Status";
    static final String MAKING = "Making";

    private FurnaceReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof FurnaceBlockEntity furnace)) {
            return;
        }
        data.putInt(STATUS, furnace.status().ordinal());
        if (furnace.making() != null) {
            data.putString(MAKING, BuiltInRegistries.ITEM.getKey(furnace.making()).toString());
        }
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
            FurnaceBlockEntity.Status status = FurnaceBlockEntity.Status.of(data.getIntOr(STATUS, 0));
            if (status == FurnaceBlockEntity.Status.SMELTING) {
                // Named by what the server's recipe makes, which covers a vanilla recipe the
                // client could never resolve from a key.
                Item made = data.getString(MAKING)
                        .map(Identifier::tryParse)
                        .map(BuiltInRegistries.ITEM::getValue)
                        .orElse(Items.AIR);
                tooltip.add(made == Items.AIR
                        ? FurnaceScreen.statusText(status)
                        : Component.translatable("status.nauvis_machines.furnace.smelting",
                                new ItemStack(made).getHoverName()));
                return;
            }
            tooltip.add(FurnaceScreen.statusText(status));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
