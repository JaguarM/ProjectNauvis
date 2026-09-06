package com.jaguarm.nauvisfluids.compat.jade;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.tank.StorageTankBlockEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a storage tank says: what is in it and how much, or that it is empty. Factorio's tank
 * window is exactly that.
 *
 * <p>The fluid travels as its registry id, which the client's registry has in the same order.
 * Two classes, a data half and a {@code Client} half, because Jade throws at registration if one
 * object is both.
 */
public class StorageTankReadout implements IServerDataProvider<BlockAccessor> {
    public static final StorageTankReadout INSTANCE = new StorageTankReadout();
    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "storage_tank");
    static final String STORED = "Stored";
    static final String FLUID = "Fluid";

    private StorageTankReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof StorageTankBlockEntity tank)) {
            return;
        }
        data.putInt(STORED, tank.stored());
        data.putInt(FLUID, BuiltInRegistries.FLUID.getId(tank.fluid()));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: one line. */
    public static class Client implements IBlockComponentProvider {
        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(STORED)) {
                return;
            }
            Fluid fluid = BuiltInRegistries.FLUID.byId(data.getIntOr(FLUID, 0));
            int stored = data.getIntOr(STORED, 0);
            if (fluid == Fluids.EMPTY || stored <= 0) {
                tooltip.add(Component.translatable("jade.nauvis_fluids.storage_tank.empty",
                        StorageTankBlockEntity.CAPACITY));
                return;
            }
            tooltip.add(Component.translatable("jade.nauvis_fluids.storage_tank.contents",
                    fluid.getFluidType().getDescription(), stored, StorageTankBlockEntity.CAPACITY));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
