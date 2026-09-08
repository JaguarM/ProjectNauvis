package com.jaguarm.nauvisfluids.compat.jade;

import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.pipe.FluidNetwork;
import com.jaguarm.nauvisfluids.pipe.FluidNetworkManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** What a pipe says about the run it belongs to. */
public class PipeReadout implements IServerDataProvider<BlockAccessor> {

    public static final PipeReadout INSTANCE = new PipeReadout();

    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "pipe");

    static final String FLUID = "Fluid";
    static final String AMOUNT = "Amount";
    static final String CAPACITY = "Capacity";
    static final String PIPES = "Pipes";
    static final String FLOWING = "Flowing";

    private PipeReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }

        FluidNetworkManager manager = FluidNetworkManager.of(level);
        FluidNetwork network = manager.networkAt(accessor.getPosition());
        if (network == null) {
            return;
        }

        data.putInt(PIPES, network.pipeCount());
        data.putInt(AMOUNT, network.amount());
        data.putInt(CAPACITY, network.capacity());
        data.putBoolean(FLOWING, manager.isActive(network));

        // The fluid by id, so the client names it from its own registry rather than being sent a
        // string that a resource pack could not translate.
        Fluid fluid = network.fluid().getFluid();
        if (fluid != Fluids.EMPTY) {
            data.putString(FLUID, BuiltInRegistries.FLUID.getKey(fluid).toString());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: what is in the run, and how far it goes. */
    public static class Client implements IBlockComponentProvider {

        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(PIPES)) {
                return;
            }

            int amount = data.getIntOr(AMOUNT, 0);
            int capacity = data.getIntOr(CAPACITY, 0);

            String fluidId = data.getStringOr(FLUID, "");
            if (fluidId.isEmpty() || amount <= 0) {
                tooltip.add(Component.translatable("jade.nauvis_fluids.pipe.empty"));
            } else {
                Fluid fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(fluidId));
                tooltip.add(Component.translatable("jade.nauvis_fluids.pipe.contents",
                        fluid.getFluidType().getDescription(), amount, capacity));
            }

            tooltip.add(Component.translatable("jade.nauvis_fluids.pipe.extent",
                    data.getIntOr(PIPES, 0)));

            if (data.getBooleanOr(FLOWING, false)) {
                tooltip.add(Component.translatable("jade.nauvis_fluids.pipe.flowing"));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
