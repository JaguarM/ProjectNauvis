package com.jaguarm.nauvisfluids.tank;

import com.jaguarm.nauvisfluids.registry.ModBlockEntities;
import com.jaguarm.nauvislib.transfer.FluidBuffer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/** A storage tank: twenty-five thousand of one fluid, and nothing else. */
public class StorageTankBlockEntity extends BlockEntity {

    /** Factorio's storage tank holds twenty-five thousand. */
    public static final int CAPACITY = 25000;

    private final Tank tank = new Tank();

    /** One slot, any fluid while empty, that fluid and no other once it holds one. */
    private class Tank extends FluidStacksResourceHandler implements FluidBuffer {
        Tank() {
            super(1, CAPACITY);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    }

    public StorageTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_TANK.get(), pos, state);
    }

    /** The tank, both ways round. Registered as {@code Capabilities.Fluid.BLOCK} at all four ports. */
    public ResourceHandler<FluidResource> tank() {
        return tank;
    }

    public int stored() {
        return tank.getAmountAsInt(0);
    }

    public Fluid fluid() {
        return tank.getResource(0).getFluid();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("Tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Tank").ifPresent(tank::deserialize);
    }
}
