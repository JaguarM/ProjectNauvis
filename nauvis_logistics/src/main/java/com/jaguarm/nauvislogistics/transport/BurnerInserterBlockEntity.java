package com.jaguarm.nauvislogistics.transport;

import com.jaguarm.nauvislib.transfer.MachineAccess;
import com.jaguarm.nauvislogistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
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
public class BurnerInserterBlockEntity extends InserterBlockEntity implements MenuProvider {

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

    private final InserterFuel fuel = new InserterFuel(SLOT_COUNT, this::onSupplyChanged,
            () -> level == null ? null : level.fuelValues());

    /** What an open screen reads. Ints only, which is all an inserter has to say. */
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case BurnerInserterMenu.DATA_BURN_TIME -> burnTime;
                case BurnerInserterMenu.DATA_BURN_TIME_TOTAL -> burnTimeTotal;
                case BurnerInserterMenu.DATA_SWING -> swing();
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            // Server-authoritative: the client is told, never asked.
        }

        @Override
        public int getCount() {
            return BurnerInserterMenu.DATA_COUNT;
        }
    };

    /** What a player or another inserter can put fuel into. Insert-only: see {@link MachineAccess}. */
    private final ResourceHandler<ItemResource> fuelAccess = new MachineAccess(fuel, SLOT_COUNT);

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

    /**
     * Something to burn: what is already lit, then the slot, then what it is picking up.
     *
     * <p>The third clause is the one that makes the burner tier playable. A burner inserter
     * working a coal belt fuels itself out of the coal going past, which is why you can build one
     * before you have a grid and why the first inserter a player places does not quietly die.
     * The wiki calls it leeching and says it is "triggered whenever the internal fuel inventory
     * reaches zero" - so it is the last resort, tried only once the slot is empty as well, and it
     * is one lump rather than a stack. An inserter that filled its slot would be hoarding coal
     * out of a line that is feeding a furnace.
     */
    @Override
    protected boolean readyToSwing(ServerLevel level) {
        if (burnTime > 0) {
            return true;
        }
        return refuel(level) || (leech() && refuel(level));
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
     * Takes one item of fuel out of whatever this inserter is picking up from.
     *
     * <p>Costs nothing and takes no swing. Factorio charges energy for the grab, but it can only
     * do that because a burner inserter there has an energy buffer that is not its fuel slot; here
     * {@code burnTime} <em>is</em> the buffer, and this runs precisely when it is empty, so a
     * price would have to be paid out of an empty purse. Free is also the behaviour a player
     * wants to see: an inserter on a coal belt never visibly stops.
     *
     * <p>"Is this fuel?" is asked as "will my own slot take it?" - {@link InserterFuel#isValid} is
     * already the rule and a second copy of it would be a second thing to keep in step.
     *
     * <p>One transaction spans the extract and the insert, exactly as {@link #move} does, because
     * half of this transfer is an item destroyed. Walking every slot of the source also means both
     * lanes of a belt are searched, which is what Factorio settled on in 0.12.16.
     *
     * <p>It takes from the source even when there is nothing to deliver - Factorio's 0.12.11
     * change, "grabs fuel for itself even if the target doesn't need it" - and it will happily
     * take a lump out of the coal it is feeding a furnace with. That is what the fuel is for.
     *
     * @return whether a lump of fuel is now in the slot.
     */
    private boolean leech() {
        ResourceHandler<ItemResource> from = sourceHandler();
        if (from == null) {
            return false;
        }
        for (int index = 0; index < from.size(); index++) {
            ItemResource candidate = from.getResource(index);
            if (candidate.isEmpty() || !fuel.isValid(FUEL_SLOT, candidate)) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                if (from.extract(index, candidate, 1, transaction) != 1) {
                    continue;
                }
                if (fuel.insert(FUEL_SLOT, candidate, 1, transaction) != 1) {
                    // Rolls back on the way out of the block: the lump is still on the belt.
                    continue;
                }
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nauvis_logistics.burner_inserter");
    }

    /**
     * The fuel slot as the player sees it: the real handler, not the insert-only
     * {@link #fuelAccess()} another inserter gets. Somebody standing in front of it may take their
     * coal back out; a hopper feeding it may not.
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BurnerInserterMenu(containerId, playerInventory, fuel, menuData,
                ContainerLevelAccess.create(level, worldPosition));
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
