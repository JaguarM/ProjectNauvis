package com.jaguarm.nauvisresearch.lab;

import com.jaguarm.nauvisresearch.registry.ModBlockEntities;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A lab: turns science packs and electricity into research.
 *
 * <h2>What it is researching, and why that is not here</h2>
 *
 * <p>There is no technology tree yet, and the lab does not need one to be built. Factorio's lab
 * does not know what it is working on either: it is told which packs a technology wants, consumes
 * one of each, and reports a cycle finished. The tree, the unlocks and the choosing all live
 * elsewhere.
 *
 * <p>So this counts cycles. {@link #cycles()} is how much research this lab has done, it is saved
 * with the block, and when there is a tree to hand it to, the handing over is the only part that
 * changes. What a cycle consumes is <b>one of every kind of science pack the lab is holding</b> -
 * which today, with one pack in the game, is one red science pack, and which is exactly the rule a
 * technology imposes once technologies exist.
 *
 * <h2>The numbers</h2>
 *
 * <p>Factorio's lab draws 60 kW against an assembling machine 1's 75, and the pack keeps the
 * ratio rather than the units - so eight FE a tick against the assembler's ten. That much is
 * identity by proportion. {@link #TICKS_PER_CYCLE} is not: in Factorio the time comes from the
 * technology being researched, not from the lab, so until there are technologies it is a
 * stand-in and it is free to change.
 *
 * <h2>Sleeping</h2>
 *
 * <p>No ticker. A lab with no packs, or no power, schedules nothing and costs nothing - see
 * non-negotiable #5. Three things can give it work again, and it needs all three: a pack arriving
 * in its slots, energy arriving in its buffer (which is what {@link LabPower}'s callback is for,
 * because a lab that ran dry has stopped scheduling and cannot notice anything itself), and a
 * neighbour changing, which covers a pole being connected.
 */
public class LabBlockEntity extends BlockEntity implements MenuProvider {

    /** One slot per kind of science pack, which is Factorio's arrangement. See {@link LabInventory}. */
    public static final int SLOT_COUNT = 6;

    /**
     * FE a tick while researching.
     *
     * <p>Factorio's lab is 60 kW and its assembling machine 1 is 75, so a lab is eight tenths of
     * an assembler's ten. There is no honest conversion between kilowatts and FE; what is kept is
     * the ratio, so the two numbers move together or not at all.
     */
    public static final int ENERGY_PER_TICK = 8;

    /** Ten seconds of running. Enough to ride out a gap in the supply without banking a lot. */
    public static final int ENERGY_CAPACITY = ENERGY_PER_TICK * 200;

    /**
     * How long one cycle of research takes.
     *
     * <p><b>Not identity.</b> In Factorio this comes from the technology - most early ones are ten
     * or fifteen seconds at a lab speed of one - so there is nothing here to be faithful to until
     * technologies exist. Two hundred ticks is ten seconds, which is the commonest of those.
     */
    public static final int TICKS_PER_CYCLE = 200;

    private final LabInventory packs = new LabInventory(SLOT_COUNT, this::onPacksChanged);

    /** Packs in, never out. A hopper under a lab must not drain what it was fed. */
    private final ResourceHandler<ItemResource> automationView = new LabAccess(packs);

    private final LabPower energy = new LabPower(ENERGY_CAPACITY, this::wake);
    private final EnergyHandler gridView = new LabPowerAccess(energy);

    private int progress;
    private int cycles;

    /** What the screen reads. Ints only, which is all a lab has to say. */
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case LabMenu.DATA_PROGRESS -> progress;
                case LabMenu.DATA_ENERGY -> energy.getAmountAsInt();
                case LabMenu.DATA_CYCLES -> cycles;
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            switch (id) {
                case LabMenu.DATA_PROGRESS -> progress = value;
                case LabMenu.DATA_CYCLES -> cycles = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return LabMenu.DATA_COUNT;
        }
    };

    public LabBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAB.get(), pos, state);
    }

    public LabInventory inventory() {
        return packs;
    }

    public ResourceHandler<ItemResource> automationView() {
        return automationView;
    }

    public EnergyHandler gridView() {
        return gridView;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    /** Ticks into the cycle in progress. */
    public int progress() {
        return progress;
    }

    /** Cycles of research finished. What a technology tree will one day be told about. */
    public int cycles() {
        return cycles;
    }

    /** True while the lab has everything it needs to be working. */
    public boolean isResearching(ServerLevel level) {
        return hasPacks() && energy.getAmountAsInt() >= ENERGY_PER_TICK;
    }

    /** Called by {@link LabBlock}, and only on a tick the lab asked for. */
    public void serverTick(ServerLevel level) {
        if (!hasPacks()) {
            // Nothing to do. A pack arriving wakes it through onPacksChanged.
            progress = 0;
            setChanged();
            return;
        }

        if (energy.getAmountAsInt() < ENERGY_PER_TICK) {
            // Out of power, holding the cycle where it stands rather than losing it. Nothing here
            // can restart it - see LabPower, which is what hears the grid come back.
            return;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            energy.extract(ENERGY_PER_TICK, transaction);
            transaction.commit();
        }

        progress++;
        if (progress >= TICKS_PER_CYCLE) {
            if (!consumeOneOfEach()) {
                // Something took the packs between the last tick and this one. Keep the progress
                // and wait; the cycle is not lost, it is only paused.
                progress = TICKS_PER_CYCLE;
                setChanged();
                return;
            }
            progress = 0;
            cycles++;
        }

        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** Whether there is at least one kind of pack to work on. */
    private boolean hasPacks() {
        for (int slot = 0; slot < packs.size(); slot++) {
            if (packs.getAmountAsInt(slot) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes one of every kind of pack the lab holds, or nothing at all.
     *
     * <p>One transaction for the lot, so a lab holding two kinds and one of the second cannot
     * spend the first and stall. That is the pack's standing rule - "can I?" and "do it" are the
     * same call - and it is what makes the tech tree's arrival cheap: this is already the
     * behaviour a technology needing three packs will want.
     */
    private boolean consumeOneOfEach() {
        try (Transaction transaction = Transaction.openRoot()) {
            for (int slot = 0; slot < packs.size(); slot++) {
                if (packs.getAmountAsInt(slot) <= 0) {
                    continue;
                }
                ItemResource pack = packs.getResource(slot);
                if (packs.extract(slot, pack, 1, transaction) != 1) {
                    return false;
                }
            }
            transaction.commit();
            return true;
        }
    }

    private void onPacksChanged() {
        setChanged();
        wake();
    }

    /**
     * Schedules a tick if one is not already coming.
     *
     * <p>Called from three places, and the one that matters is {@link LabPower}: a lab that
     * stopped for want of power is not ticking, so nothing it does can restart it. The wake has to
     * come from whatever filled the buffer.
     */
    private void wake() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        if (!serverLevel.getBlockTicks().hasScheduledTick(worldPosition, getBlockState().getBlock())) {
            serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    /** A neighbour changed - most usefully, a pole that can now reach this lab. */
    public void wakeFromNeighbour() {
        wake();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        packs.serialize(output.child("Packs"));
        energy.serialize(output.child("Energy"));
        output.putInt("Progress", progress);
        output.putInt("Cycles", cycles);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Packs").ifPresent(packs::deserialize);
        input.child("Energy").ifPresent(energy::deserialize);
        progress = input.getIntOr("Progress", 0);
        cycles = input.getIntOr("Cycles", 0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nauvis_research.lab");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LabMenu(containerId, playerInventory, packs, menuData,
                ContainerLevelAccess.create(level, worldPosition));
    }

    /**
     * Spilled when the lab is broken, so a stack of science is not a way to lose an afternoon.
     *
     * <p><b>This is the hook, not {@code Block.affectNeighborsAfterRemoval}.</b> In 26.2 the base
     * implementation here is what drops a machine's contents, and it only does so for a
     * {@link net.minecraft.world.Container} - which a {@code ResourceHandler} is not. A
     * capability-based inventory that does not override this silently eats everything in it, and
     * nothing in the removal path complains.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < packs.size(); slot++) {
            int amount = packs.getAmountAsInt(slot);
            if (amount <= 0) {
                continue;
            }
            ItemResource pack = packs.getResource(slot);
            packs.set(slot, ItemResource.EMPTY, 0);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pack.toStack(amount));
        }
    }
}
