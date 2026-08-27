package com.jaguarm.nauvislogistics.transport;

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
     * Ticks per item moved.
     *
     * <p>Factorio's inserter manages about 0.83 items a second against a burner inserter's 0.6,
     * so this is 24 ticks where the burner is 30. Behaviour rather than identity, like the
     * burner's number, and derived from the same place.
     */
    public static final int SWING_TICKS = 24;

    /**
     * FE per tick of a swing.
     *
     * <p>Factorio's inserter draws 13 kW where a steam engine makes 900. At this pack's scale -
     * an engine is 120 FE a tick - that is 1.7, and two is the nearest whole number. An inserter
     * is cheap to run on purpose: a base has thousands of them and a handful of assemblers.
     */
    public static final int ENERGY_PER_TICK = 2;

    /** Ten seconds of swinging. Small: an inserter is not somewhere to park a surplus. */
    public static final int ENERGY_CAPACITY = ENERGY_PER_TICK * 200;

    /** Unrestricted, because the inserter spends from it. What the grid sees is {@link #gridView}. */
    private final InserterPower energy = new InserterPower(ENERGY_CAPACITY, this::onSupplyChanged);

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

    @Override
    public int swingTicks() {
        return SWING_TICKS;
    }

    @Override
    public boolean running() {
        return energy.getAmountAsInt() >= ENERGY_PER_TICK;
    }

    @Override
    protected boolean readyToSwing(ServerLevel level) {
        return running();
    }

    @Override
    protected void spendOneTick() {
        energy.set(energy.getAmountAsInt() - ENERGY_PER_TICK);
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
