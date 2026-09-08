package com.jaguarm.nauvisresearch.lab;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvislib.module.ModuleEffect;
import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.module.Productivity;
import com.jaguarm.nauvislib.transfer.PowerAccess;
import com.jaguarm.nauvislib.transfer.MachinePower;
import com.jaguarm.nauvislib.transfer.MachineAccess;
import java.util.List;

import com.jaguarm.nauvisresearch.registry.ModBlockEntities;
import com.jaguarm.nauvisresearch.research.Research;
import com.jaguarm.nauvisresearch.research.Technology;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

/** A lab: turns science packs and electricity into research. */
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
     * What the progress bar is drawn against when nothing is being researched.
     *
     * <p>Only a scale. The real length of a cycle is {@link Technology#ticksPerUnit()} and comes
     * from whatever is being researched; this is what {@code LabMenu} divides by when there is no
     * technology to ask, so that an idle lab draws an empty bar rather than dividing by zero. Ten
     * seconds is the commonest unit time in Factorio's early tree.
     */
    public static final int IDLE_TICKS_PER_CYCLE = 200;

    /**
     * How often a lab with packs and power but no research looks again.
     *
     * <p>One second. See the class javadoc: choosing a technology happens on a screen and reaches
     * no block, so this is the only way a fed lab hears about it. Everything else that gives a lab
     * work arrives as a callback and costs nothing.
     */
    public static final int IDLE_RECHECK_TICKS = 20;

    private final LabInventory packs = new LabInventory(SLOT_COUNT, this::onPacksChanged);

    /** Factorio's lab takes two modules. */
    public static final int MODULE_SLOTS = 2;

    /**
     * The module slots and the free unit they work towards. Read once a unit, as it starts: the
     * speed shortens the unit, the energy scales the draw, and the productivity banks a unit of
     * research the packs never paid for - Factorio 2.0's research productivity.
     */
    private final ModuleSlots modules = new ModuleSlots(MODULE_SLOTS, this::onPacksChanged);
    private final Productivity productivity = new Productivity();

    /** The draw under the modules read at the start of the unit in progress. */
    private int draw = ENERGY_PER_TICK;

    /** Packs in, never out. A hopper under a lab must not drain what it was fed. */
    private final ResourceHandler<ItemResource> automationView = new MachineAccess(packs, SLOT_COUNT);

    private final MachinePower energy = new MachinePower(ENERGY_CAPACITY, this::wake);
    private final EnergyHandler gridView = new PowerAccess(energy);

    private int progress;
    private int cycles;

    /**
     * The length of the cycle in progress, so the screen's bar is drawn against the right total.
     *
     * <p>Saved, and read from the technology when a cycle starts rather than every tick. A
     * research swapped mid-cycle would otherwise redraw the bar against a different total while
     * the progress underneath it had not moved.
     */
    private int cycleTicks = IDLE_TICKS_PER_CYCLE;

    /** What the screen reads. Ints only, which is all a lab has to say. */
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case LabMenu.DATA_PROGRESS -> progress;
                case LabMenu.DATA_ENERGY -> energy.getAmountAsInt();
                case LabMenu.DATA_CYCLES -> cycles;
                case LabMenu.DATA_CYCLE_TICKS -> cycleTicks();
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            switch (id) {
                case LabMenu.DATA_PROGRESS -> progress = value;
                case LabMenu.DATA_CYCLES -> cycles = value;
                case LabMenu.DATA_CYCLE_TICKS -> cycleTicks = value;
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

    public ModuleSlots modules() {
        return modules;
    }

    public Productivity productivity() {
        return productivity;
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

    /** The length of the cycle in progress; a scale for the screen's bar, never a divisor of zero. */
    public int cycleTicks() {
        return cycleTicks > 0 ? cycleTicks : IDLE_TICKS_PER_CYCLE;
    }

    /**
     * Factorio's modifier for how much faster every lab works: {@code research-speed-1} and
     * {@code -2} grant a fifth and then three tenths more, and the tree stops there.
     */
    public static final String LABORATORY_SPEED = "laboratory-speed";

    /**
     * How long a unit of this technology takes in this world: the technology's own time, divided
     * by one plus every laboratory-speed bonus researched.
     *
     * <p>Factorio's rule exactly - a research speed bonus is a multiplier on the lab's speed, so
     * plus fifty per cent makes a thirty-second unit a twenty-second one. Rounded to a whole tick
     * and never below one.
     */
    public static int cycleTicksFor(Technology technology, ServerLevel level) {
        return cycleTicksFor(technology, level, ModuleEffect.NONE);
    }

    /** The same, with the lab's modules on top of the world's research speed. */
    public static int cycleTicksFor(Technology technology, ServerLevel level, ModuleEffect effect) {
        double speed = (1 + Research.bonus(level.getServer(), LABORATORY_SPEED)) * effect.speedFactor();
        return Math.max(1, (int) Math.round(technology.ticksPerUnit() / speed));
    }

    /** True while the lab has everything it needs to be working. */
    public boolean isResearching(ServerLevel level) {
        return energy.getAmountAsInt() >= ENERGY_PER_TICK && wanted(level) != null;
    }

    /**
     * Called by {@link LabBlock}, and only on a tick the lab asked for.
     *
     * <p>Three things stop it, and they are told apart because they want different things done
     * about them: nothing is being researched or the packs for it are missing, there is no power,
     * or it has just finished a unit and has nothing left to spend. The screen says which.
     */
    public void serverTick(ServerLevel level) {
        List<Item> wanted = wanted(level);
        if (wanted == null) {
            // A pack arriving wakes it through onPacksChanged; a technology being chosen happens
            // on a screen and reaches no block at all, which is what the idle recheck is for.
            progress = 0;
            setChanged();
            if (hasAnyPack() && energy.getAmountAsInt() >= ENERGY_PER_TICK) {
                level.scheduleTick(worldPosition, getBlockState().getBlock(), IDLE_RECHECK_TICKS);
            }
            return;
        }

        // The modules are read as a unit starts and hold for the unit, which is Factorio's rule.
        Holder.Reference<Technology> technology = Research.current(level.getServer());
        if (progress == 0) {
            ModuleEffect effect = modules.effect();
            cycleTicks = technology == null
                    ? IDLE_TICKS_PER_CYCLE
                    : cycleTicksFor(technology.value(), level, effect);
            draw = effect.scaleEnergy(ENERGY_PER_TICK);
        }

        if (energy.getAmountAsInt() < draw) {
            // Out of power, holding the cycle where it stands rather than losing it. Nothing here
            // can restart it - see MachinePower, which is what hears the grid come back.
            return;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            energy.extract(draw, transaction);
            transaction.commit();
        }

        progress++;
        if (progress >= cycleTicks) {
            if (!consumeOneOfEach(wanted)) {
                // Something took the packs between the last tick and this one. Keep the progress
                // and wait; the cycle is not lost, it is only paused.
                progress = cycleTicks;
                setChanged();
                return;
            }
            progress = 0;
            cycles++;
            // The unit belongs to the world, not to this machine. Completing the technology is
            // Research's business, including telling everybody about it.
            Research.addUnit(level);

            // Research productivity: every unit earns a fraction of a free one, and a whole one
            // is reported to the world without any packs being spent on it.
            productivity.earn(modules.effect().productivityBonus());
            if (productivity.owed()) {
                productivity.pay();
                cycles++;
                Research.addUnit(level);
            }
        }

        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * The packs one unit of the current research would consume, or null when there is no unit to
     * do: nothing is being researched, or the lab is short of one of the packs it asks for.
     *
     * <p>Short of <em>one</em> is short of all of them. Factorio's lab consumes one of each pack a
     * technology names and will not run on a subset, which is the rule that makes a science farm a
     * balancing problem rather than a pile.
     */
    private @Nullable List<Item> wanted(ServerLevel level) {
        Holder.Reference<Technology> technology = Research.current(level.getServer());
        if (technology == null) {
            return null;
        }
        List<Item> items = technology.value().packItems();
        if (items == null || items.isEmpty()) {
            return null;
        }
        for (Item item : items) {
            if (countOf(item) <= 0) {
                return null;
            }
        }
        return items;
    }

    /** Whether the lab holds anything at all, which is what decides if an idle recheck is worth it. */
    private boolean hasAnyPack() {
        for (int slot = 0; slot < packs.size(); slot++) {
            if (packs.getAmountAsInt(slot) > 0) {
                return true;
            }
        }
        return false;
    }

    private int countOf(Item item) {
        int total = 0;
        for (int slot = 0; slot < packs.size(); slot++) {
            if (packs.getAmountAsInt(slot) > 0 && packs.getResource(slot).is(item)) {
                total += packs.getAmountAsInt(slot);
            }
        }
        return total;
    }

    /**
     * Takes one of each of these packs, or nothing at all.
     *
     * <p>One transaction for the lot, so a lab holding the first two packs of a three-pack
     * technology cannot spend them and stall. That is the pack's standing rule - "can I?" and "do
     * it" are the same call - and it is why a technology needing three packs needed no new code
     * here.
     */
    private boolean consumeOneOfEach(List<Item> wanted) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (Item item : wanted) {
                if (!takeOne(item, transaction)) {
                    return false;
                }
            }
            transaction.commit();
            return true;
        }
    }

    private boolean takeOne(Item item, Transaction transaction) {
        for (int slot = 0; slot < packs.size(); slot++) {
            if (packs.getAmountAsInt(slot) <= 0) {
                continue;
            }
            ItemResource pack = packs.getResource(slot);
            if (pack.is(item) && packs.extract(slot, pack, 1, transaction) == 1) {
                return true;
            }
        }
        return false;
    }

    private void onPacksChanged() {
        setChanged();
        Multiblock.announce(level, worldPosition, getBlockState());
        wake();
    }

    /**
     * Schedules a tick if one is not already coming.
     *
     * <p>Called from three places, and the one that matters is {@link MachinePower}: a lab that
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
        modules.serialize(output.child("Modules"));
        productivity.save(output);
        output.putInt("Draw", draw);
        energy.serialize(output.child("Energy"));
        output.putInt("Progress", progress);
        output.putInt("Cycles", cycles);
        output.putInt("CycleTicks", cycleTicks);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Packs").ifPresent(packs::deserialize);
        input.child("Modules").ifPresent(modules::deserialize);
        productivity.load(input);
        draw = input.getIntOr("Draw", ENERGY_PER_TICK);
        input.child("Energy").ifPresent(energy::deserialize);
        progress = input.getIntOr("Progress", 0);
        cycles = input.getIntOr("Cycles", 0);
        cycleTicks = input.getIntOr("CycleTicks", IDLE_TICKS_PER_CYCLE);
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
        return new LabMenu(containerId, playerInventory, packs, modules, menuData,
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
        for (int slot = 0; slot < modules.size(); slot++) {
            int amount = modules.getAmountAsInt(slot);
            if (amount <= 0) {
                continue;
            }
            ItemResource module = modules.getResource(slot);
            modules.set(slot, ItemResource.EMPTY, 0);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), module.toStack(amount));
        }
    }
}
