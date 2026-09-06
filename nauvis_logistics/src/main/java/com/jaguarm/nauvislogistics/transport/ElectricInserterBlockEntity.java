package com.jaguarm.nauvislogistics.transport;

import com.jaguarm.nauvislib.transfer.PowerAccess;
import com.jaguarm.nauvislib.transfer.MachinePower;
import com.jaguarm.nauvislogistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/**
 * The inserter that runs on the grid: faster than the burner, and useless without a pole.
 *
 * <p>Every electric tier is this class - the basic arm and the long-handed one both - because
 * what differs between them is a swing time, a draw and a reach, and all three are facts about
 * the block. It reads them through {@link #tier()}, which is why there is one block entity type
 * for the two blocks and why a third tier will need none of its own.
 *
 * <p>This is the item that closes milestone 1, and it was deliberately left unregistered until
 * there was a grid to plug it into. An electric inserter that worked without electricity would be
 * strictly better than the burner for nothing, and progression that can be skipped is progression
 * that will be.
 *
 * <p>There is no fuel slot and nothing to spill, which is most of why it is a smaller class than
 * {@link BurnerInserterBlockEntity} rather than a bigger one.
 */
public class ElectricInserterBlockEntity extends InserterBlockEntity {

    /**
     * Ten seconds of the basic arm's swinging, and rather less of a hungrier one's.
     *
     * <p>One number for every electric tier on purpose. A buffer is not a tank - an inserter is
     * not somewhere to park a surplus - so what it is for is riding out the tick or two between a
     * pole's rounds, and that is the same job whatever the arm on top costs to move.
     */
    public static final int ENERGY_CAPACITY = ElectricInserterBlock.ENERGY_PER_TICK * 200;

    /** Unrestricted, because the inserter spends from it. What the grid sees is {@link #gridView}. */
    private final MachinePower energy = new MachinePower(ENERGY_CAPACITY, this::onSupplyChanged);

    private final EnergyHandler gridView = new PowerAccess(energy);

    public ElectricInserterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_INSERTER.get(), pos, state);
    }

    /** What a power pole fills. Registered as {@code Capabilities.Energy.BLOCK}. */
    public EnergyHandler gridView() {
        return gridView;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    /** The tier's own numbers. See {@link ElectricInserterBlock}. */
    private ElectricInserterBlock electricTier() {
        return (ElectricInserterBlock) tier();
    }

    @Override
    public int swingTicks() {
        return electricTier().swingTicks();
    }

    @Override
    public boolean running() {
        return energy.getAmountAsInt() >= electricTier().energyPerTick();
    }

    @Override
    protected boolean readyToSwing(ServerLevel level) {
        return running();
    }

    @Override
    protected void spendOneTick() {
        energy.set(energy.getAmountAsInt() - electricTier().energyPerTick());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("Energy"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Energy").ifPresent(energy::deserialize);
    }
}
