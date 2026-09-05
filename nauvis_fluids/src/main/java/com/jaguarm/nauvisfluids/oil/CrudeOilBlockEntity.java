package com.jaguarm.nauvisfluids.oil;

import com.jaguarm.nauvisfluids.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * How much is left in one oil well.
 *
 * <p>Factorio's {@code crude-oil} resource, number for number. Its prototype says
 * {@code infinite = true, normal = 300000, minimum = 60000, infinite_depletion_amount = 10}, and
 * that is the whole model:
 *
 * <ul>
 *   <li><b>yield</b> is {@code amount / normal} - an amount of 300000 is 100%, and the tooltip's
 *       percentage is that and nothing more;
 *   <li>every pumpjack cycle lowers the amount by ten;
 *   <li>and it never falls below a floor: 60000, or a fifth of what the well started with,
 *       whichever is more. A 100% well bottoms out at 20% and pumps for ever at that; a 400% well
 *       bottoms out at 80%.
 * </ul>
 *
 * <p>So an oil well is not finite. It is <em>infinite with a decay to a floor</em>, which is the
 * thing this pack was first asked to build as "finite" and deliberately is not: a player who runs
 * their only oil field dry has no way to plastic, and Factorio never lets that happen.
 *
 * <h2>The starting amount is derived, not stored by worldgen</h2>
 *
 * <p>Nothing is written into this block entity when the world generates. The first time anything
 * asks, the amount is worked out from the world seed and the well's position by
 * {@link CrudeOilField#initialAmount}, and saved from then on. That keeps the worldgen feature to
 * placing blocks - a block entity in a proto-chunk is a thing to avoid having opinions about - and
 * it means a well placed by hand in creative is as rich as a well generated there would have been.
 *
 * <p>No ticker. A well does nothing on its own; the pumpjack on top of it does the work.
 */
public class CrudeOilBlockEntity extends BlockEntity {

    /** Factorio's {@code normal}: the amount that reads as 100% yield. */
    public static final long NORMAL = 300_000;

    /** Factorio's {@code minimum}: the amount no well is pumped below. 20% of normal. */
    public static final long MINIMUM = 60_000;

    /** Factorio's {@code infinite_depletion_amount}: what one pumpjack cycle takes off the amount. */
    public static final int DEPLETION = 10;

    /** Unset until first asked for, or until {@link #reset} is called. */
    private static final long UNSET = -1;

    private long amount = UNSET;
    private long initial = UNSET;

    public CrudeOilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUDE_OIL.get(), pos, state);
    }

    /** What is left, in Factorio's resource units. */
    public long amount() {
        settle();
        return amount;
    }

    /** What the well began with. The floor is a fifth of this or {@link #MINIMUM}, whichever is more. */
    public long initial() {
        settle();
        return initial;
    }

    /** Factorio's tooltip percentage, rounded the way its tooltip rounds. */
    public int yieldPercent() {
        return (int) Math.round(amount() * 100.0 / NORMAL);
    }

    /** The amount this well is never pumped below. */
    public long floor() {
        return Math.max(MINIMUM, initial() / 5);
    }

    /** Whether this well has reached its floor and will now pump at that rate for ever. */
    public boolean isAtFloor() {
        return amount() <= floor();
    }

    /**
     * Starts this well over at {@code amount}, which becomes its initial amount too.
     *
     * <p>For worldgen that wants a say, for the map-maker's creative placement, and for tests.
     */
    public void reset(long amount) {
        this.amount = Math.max(0, amount);
        this.initial = this.amount;
        setChanged();
    }

    /**
     * One pumpjack cycle's worth of depletion: ten off the amount, stopping at the floor.
     *
     * <p>Factorio's rule in words is "while cycles left is greater than the floor, each cycle
     * reduces it by one", and this is that with the floor honoured exactly rather than overshot.
     */
    public void deplete() {
        long before = amount();
        long after = Math.max(floor(), before - DEPLETION);
        if (after != before) {
            amount = after;
            setChanged();
        }
    }

    /** Works out the starting amount the first time it is needed. Server only; a client has no seed. */
    private void settle() {
        if (amount != UNSET) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            reset(CrudeOilField.initialAmount(serverLevel.getSeed(), worldPosition));
        } else {
            // A client asking is drawing something, and a well it cannot value is a 100% well.
            amount = NORMAL;
            initial = NORMAL;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (amount != UNSET) {
            output.putLong("Amount", amount);
            output.putLong("Initial", initial);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        amount = input.getLongOr("Amount", UNSET);
        initial = input.getLongOr("Initial", amount);
    }
}
