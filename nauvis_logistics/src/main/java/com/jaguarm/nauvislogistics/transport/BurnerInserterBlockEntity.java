package com.jaguarm.nauvislogistics.transport;

import com.jaguarm.nauvislogistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The inserter you can build before there is a grid: it burns coal.
 *
 * <p>Being visibly slow is the point of the burner tier - it is the one you are supposed to want
 * to replace, and {@link ElectricInserterBlockEntity} is what you replace it with.
 */
public class BurnerInserterBlockEntity extends InserterBlockEntity {

    /** The only slot: what it burns. Fuel goes in, nothing comes out. */
    public static final int FUEL_SLOT = 0;
    public static final int SLOT_COUNT = 1;

    /**
     * Ticks per item moved.
     *
     * <p>The one number in this mod that is not from Factorio's dump, because the dump is
     * recipes and this is behaviour. Factorio's burner inserter manages roughly 0.6 items a
     * second, which is 33 ticks here; 30 is that rounded to something a person can count.
     */
    public static final int SWING_TICKS = 30;

    private final InserterFuel fuel = new InserterFuel(SLOT_COUNT, this::onSupplyChanged);

    /** What a player or another inserter can put fuel into. Insert-only: see {@link FuelAccess}. */
    private final ResourceHandler<ItemResource> fuelAccess = new FuelAccess(fuel);

    /** Ticks of fuel left. Burns only while actually swinging, so an idle inserter wastes none. */
    private int burnTime;

    /** What the last item of fuel was worth, so a progress display can show a fraction. */
    private int burnTimeTotal;

    public BurnerInserterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BURNER_INSERTER.get(), pos, state);
    }

    public ResourceHandler<ItemResource> fuelAccess() {
        return fuelAccess;
    }

    public InserterFuel fuel() {
        return fuel;
    }

    public int burnTime() {
        return burnTime;
    }

    @Override
    public int swingTicks() {
        return SWING_TICKS;
    }

    @Override
    public boolean running() {
        return burnTime > 0;
    }

    @Override
    protected boolean readyToSwing(ServerLevel level) {
        return burnTime > 0 || refuel(level);
    }

    @Override
    protected void spendOneTick() {
        burnTime--;
    }

    /** Burns one item of fuel. @return whether there is now fuel to spend. */
    private boolean refuel(ServerLevel level) {
        ItemResource candidate = fuel.getResource(FUEL_SLOT);
        if (candidate.isEmpty()) {
            return false;
        }

        // getBurnTime rather than FuelValues.burnDuration, which is deprecated in favour of it -
        // the null recipe type asks for the plain furnace-fuel value.
        int worth = candidate.toStack(1).getBurnTime(null, level.fuelValues());
        if (worth <= 0) {
            return false;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            if (fuel.extract(FUEL_SLOT, candidate, 1, transaction) != 1) {
                return false;
            }
            transaction.commit();
        }

        burnTime = worth;
        burnTimeTotal = worth;
        setChanged();
        return true;
    }

    /**
     * Spilled when the inserter is broken. See the note in the assembler: this is the hook, not
     * {@code Block#affectNeighborsAfterRemoval}, and getting it wrong silently eats the coal.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        int amount = fuel.getAmountAsInt(FUEL_SLOT);
        if (amount > 0) {
            ItemStack stack = fuel.getResource(FUEL_SLOT).toStack(amount);
            fuel.set(FUEL_SLOT, ItemResource.EMPTY, 0);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fuel.serialize(output.child("Fuel"));
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnTimeTotal", burnTimeTotal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Fuel").ifPresent(fuel::deserialize);
        burnTime = input.getIntOr("BurnTime", 0);
        burnTimeTotal = input.getIntOr("BurnTimeTotal", 0);
    }
}
