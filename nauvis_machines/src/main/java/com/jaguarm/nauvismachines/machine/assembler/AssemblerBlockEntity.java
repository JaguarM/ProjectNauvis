package com.jaguarm.nauvismachines.machine.assembler;

import com.jaguarm.nauvislib.module.ModuleEffect;
import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.module.Productivity;
import com.jaguarm.nauvislib.transfer.FluidOutputAccess;
import com.jaguarm.nauvislib.transfer.PortTank;
import com.jaguarm.nauvislib.transfer.PowerAccess;
import com.jaguarm.nauvislib.transfer.MachinePower;
import com.jaguarm.nauvislib.transfer.MachineAccess;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.jaguarm.facrafting.queue.CraftListeners;
import com.jaguarm.facrafting.recipe.CraftPlanner;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.facrafting.registry.ModRecipes;
import com.jaguarm.nauvismachines.registry.ModBlockEntities;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * An assembling machine: told what to make, it makes it, over and over, from whatever is put
 * into it.
 *
 * <p>This is the shortcut PLAN.md asks for and no more - a recipe selector over one flat input
 * inventory, running Facrafting's timed recipes. The later version has a fixed recipe per
 * machine, a buffer per ingredient, module slots and three tiers. What must not change is the
 * recipe itself: three electronic circuits, five iron gear wheels, nine iron plates and half a
 * second are Factorio's numbers, and they are generated rather than typed.
 *
 * <h2>Sleeping</h2>
 *
 * <p>Non-negotiable #5: a machine with nothing to do costs nothing. There is deliberately no
 * {@code BlockEntityTicker} here, because a ticker runs whether or not there is work. The
 * machine schedules a block tick for itself while a craft is under way and simply stops
 * scheduling when there is none, which is free - an unscheduled position is never visited -
 * and scheduled ticks are saved with the chunk, so a craft survives a reload.
 *
 * <p>It wakes on anything that could give it something to do: a change to its inventory
 * (ingredients arriving, a result being taken away), a recipe being chosen, a neighbour changing,
 * or electricity arriving. A Factorio base is thousands of machines and most of them are idle at
 * any moment.
 *
 * <h2>Power</h2>
 *
 * <p>It runs on FE and stops when the buffer is empty, which is the whole of it for now -
 * PLAN.md's brownout, where a machine that cannot refill runs slower instead of stopping, is a
 * later refinement. What it publishes is an insert-only buffer under NeoForge's energy
 * capability, so a pole from {@code nauvis_power} fills it without either mod knowing what the
 * other is, and so would a cable from anywhere else.
 *
 * <p>The last item in the wake list is the one that is easy to miss. A machine that ran dry has
 * stopped scheduling ticks, so the grid coming back has to reach it from outside - see
 * {@code MachinePower}, and {@code assembler_wakes_when_power_arrives}, which is the test that
 * fails if it does not.
 */
public class AssemblerBlockEntity extends BlockEntity implements MenuProvider {

    /** Ingredient slots. Six is the largest ingredient count in Factorio's recipe set. */
    public static final int INPUT_SLOTS = 6;

    /** Results land here. One slot: every Factorio recipe has exactly one product. */
    public static final int OUTPUT_SLOT = INPUT_SLOTS;

    public static final int SLOT_COUNT = INPUT_SLOTS + 1;

    /**
     * FE burnt per tick of a craft by the first machine.
     *
     * <p>Factorio's assembling machine 1 draws 75 kW where a steam engine makes 900, so one
     * engine runs twelve of them and one boiler runs twenty-four. Those ratios are the number
     * worth keeping; the FE it is expressed in is not, and neither figure is identity, so both
     * are tunable. The engine's ENERGY_PER_TICK is the other half of the pair. The tiers each say
     * their own - see {@link AssemblerBlock#energyPerTick()} - and this is the first tier's, kept
     * as the number every other machine's cost is quoted against.
     */
    public static final int ENERGY_PER_TICK = AssemblingMachine1Block.ENERGY_PER_TICK;

    /**
     * Five seconds of work. Enough to carry on through a gap in supply, small enough that an
     * assembler is not somewhere the grid can park a surplus.
     */
    public static final int ENERGY_CAPACITY = ENERGY_PER_TICK * 100;

    /** How many ticks of a buffer every tier carries: five seconds of its own draw. */
    private static final int BUFFER_TICKS = 100;

    /**
     * The tier's numbers, read off the block this entity was made for.
     *
     * <p>Factorio's crafting speed: the first machine works at 0.5, so a half-second recipe takes
     * a second in it; the second at 0.75. The recipe's craft time stays the recipe's - it is what
     * a player crafting by hand pays - and what a machine makes of it is the machine's. Both are
     * identity, and both are on the block rather than here so that a tier is a class.
     */
    private final float craftingSpeed;
    private final int energyPerTick;

    private final AssemblerInventory inventory = new AssemblerInventory(SLOT_COUNT, this::onInventoryChanged);

    /**
     * The tier's module slots - none on the first machine - and the free craft they work towards.
     *
     * <p>Their sum is read once a craft, as it starts: the speed multiplies the crafting speed,
     * the energy multiplies the draw, and the productivity goes into the bank after each craft.
     * A productivity module is refused unless the recipe is an intermediate product, which is
     * Factorio's rule and the one restriction modules have; see {@link #allowsProductivity}.
     */
    private final ModuleSlots modules;
    private final Productivity productivity = new Productivity();

    /**
     * Factorio's fluid boxes, on a tier that has them: one fluid in, one fluid out, a thousand
     * each, pointed at whatever fluid the recipe puts there. Null on the first machine, which has
     * none - and so cannot be pointed at a recipe with a fluid in it. The tank behind a port is the
     * library's {@link PortTank}, the same as the refinery's.
     */
    private final @Nullable PortTank fluidIn;
    private final @Nullable PortTank fluidOut;
    /** What a pipe sees at the output: extraction only, and a wake-up on the way out. */
    private final @Nullable ResourceHandler<FluidResource> fluidOutView;

    /** Factorio's fluid box on an assembler holds a thousand, like the refinery's. */
    public static final int TANK_CAPACITY = 1000;

    /** Unrestricted, because the machine spends from it. What the grid sees is {@link #gridView}. */
    private final MachinePower energy;

    /** Insert only: a machine is not a battery, and a grid must not be able to drain one. */
    private final EnergyHandler gridView;

    /** What hoppers, inserters and pipes see. Never the raw inventory - see {@link MachineAccess}. */
    private final ResourceHandler<ItemResource> automationView = new MachineAccess(inventory, INPUT_SLOTS);

    /**
     * The chosen recipe, by key rather than by value: recipes are reloadable data, so a recipe
     * object held across a {@code /reload} would be a stale one. Null means idle.
     */
    private @Nullable ResourceKey<Recipe<?>> recipeKey;

    /** Ticks spent on the current craft. Reaching the recipe's craft time means finished. */
    private int progress;

    /**
     * The current recipe's craft time, cached for the screen.
     *
     * <p>Kept here rather than looked up when asked, because {@link ContainerData} is polled every
     * tick for every open menu, and resolving a recipe key through the recipe manager is not a
     * thing to do on that schedule.
     */
    private int craftTicks;

    /** What an open screen reads. Ints only, which is why the recipe itself is not in here. */
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case AssemblerMenu.DATA_PROGRESS -> progress;
                case AssemblerMenu.DATA_CRAFT_TICKS -> craftTicks;
                case AssemblerMenu.DATA_ENERGY -> energy.getAmountAsInt();
                case AssemblerMenu.DATA_ENERGY_PER_TICK -> currentEnergyPerTick();
                case AssemblerMenu.DATA_FLUID_IN -> fluidIn == null ? 0 : fluidIn.getAmountAsInt(0);
                case AssemblerMenu.DATA_FLUID_IN_ID -> fluidIn == null ? 0
                        : BuiltInRegistries.FLUID.getId(fluidIn.shownFluid());
                case AssemblerMenu.DATA_FLUID_OUT -> fluidOut == null ? 0 : fluidOut.getAmountAsInt(0);
                case AssemblerMenu.DATA_FLUID_OUT_ID -> fluidOut == null ? 0
                        : BuiltInRegistries.FLUID.getId(fluidOut.shownFluid());
                case AssemblerMenu.DATA_ENERGY_CAPACITY -> energyCapacity();
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            // Server-authoritative: the client is told, never asked.
        }

        @Override
        public int getCount() {
            return AssemblerMenu.DATA_COUNT;
        }
    };

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLER.get(), pos, state);
        // The block is the tier. A block entity built for a state that is not one of ours - a
        // structure with the wrong block, a test's bare setBlock - gets the first machine's numbers
        // rather than a crash.
        AssemblerBlock tier = state.getBlock() instanceof AssemblerBlock block ? block : null;
        craftingSpeed = tier == null ? AssemblingMachine1Block.CRAFTING_SPEED : tier.craftingSpeed();
        energyPerTick = tier == null ? AssemblingMachine1Block.ENERGY_PER_TICK : tier.energyPerTick();
        modules = new ModuleSlots(tier == null ? 0 : tier.moduleSlots(), this::onInventoryChanged,
                module -> module.effect().productivity() <= 0 || allowsProductivity());
        if (tier != null && tier.fluidBoxes()) {
            fluidIn = new PortTank(TANK_CAPACITY, this::onInventoryChanged);
            fluidOut = new PortTank(TANK_CAPACITY, this::onInventoryChanged);
            fluidOutView = new FluidOutputAccess(fluidOut, this::wake);
        } else {
            fluidIn = null;
            fluidOut = null;
            fluidOutView = null;
        }
        energy = new MachinePower(energyPerTick * BUFFER_TICKS, this::onPowerChanged);
        gridView = new PowerAccess(energy);
    }

    /** Factorio's crafting speed for this machine's tier. */
    public float craftingSpeed() {
        return craftingSpeed;
    }

    public ModuleSlots modules() {
        return modules;
    }

    /** Whether this machine has fluid boxes at all: the tier's, read once. */
    public boolean hasFluidBoxes() {
        return fluidIn != null;
    }

    /** The input fluid box, or null on a tier without one. What a pipe fills, at the input port. */
    public @Nullable PortTank fluidIn() {
        return fluidIn;
    }

    /** The output fluid box, or null on a tier without one. */
    public @Nullable PortTank fluidOut() {
        return fluidOut;
    }

    /** What a pipe sees at the output port: extraction only. Null on a tier without a fluid box. */
    public @Nullable ResourceHandler<FluidResource> fluidOutView() {
        return fluidOutView;
    }

    /** Which recipes this tier runs. The menu asks for the panel; {@link #setRecipe} asks for itself. */
    public boolean accepts(FacraftRecipe recipe) {
        return getBlockState().getBlock() instanceof AssemblerBlock tier
                ? tier.accepts(recipe)
                : recipe.isHandcraftable();
    }

    /** How far towards a free craft, for the readout. */
    public Productivity productivity() {
        return productivity;
    }

    /** The tier's draw under the modules in it right now. What the screen's bolt is read against. */
    public int currentEnergyPerTick() {
        return modules.effect().scaleEnergy(energyPerTick);
    }

    /**
     * Whether a productivity module may go in: Factorio allows them only for intermediate
     * products, which is what the recipe's tab says. A machine with no recipe chosen takes one, as
     * Factorio's does; choosing a recipe that may not have them is then refused while they sit
     * there - see {@link #setRecipe}.
     */
    public boolean allowsProductivity() {
        if (recipeKey == null || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        FacraftRecipe recipe = recipe(serverLevel);
        return recipe == null || allowsProductivity(recipe);
    }

    /** Factorio's restriction, read off the recipe's crafting-menu tab. */
    public static boolean allowsProductivity(FacraftRecipe recipe) {
        return "intermediate".equals(recipe.group());
    }

    /** FE this tier spends per tick of a craft. */
    public int energyPerTick() {
        return energyPerTick;
    }

    /** The buffer this tier carries: five seconds of its own draw. */
    public int energyCapacity() {
        return energyPerTick * BUFFER_TICKS;
    }

    /**
     * How many ticks a recipe takes in a machine of this speed.
     *
     * <p>Factorio's rule: the recipe's time over the machine's crafting speed. Ten ticks at 0.5 is
     * twenty; at 0.75 it is thirteen and a third, and a craft cannot take a third of a tick, so it
     * is rounded to the nearest and never below one.
     */
    public static int craftTicksFor(FacraftRecipe recipe, float craftingSpeed) {
        return Math.max(1, Math.round(recipe.craftTicks() / craftingSpeed));
    }

    public AssemblerInventory inventory() {
        return inventory;
    }

    public ResourceHandler<ItemResource> automationView() {
        return automationView;
    }

    /** What a power pole fills. Registered as {@code Capabilities.Energy.BLOCK}. */
    public EnergyHandler gridView() {
        return gridView;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    public @Nullable ResourceKey<Recipe<?>> recipeKey() {
        return recipeKey;
    }

    public int progress() {
        return progress;
    }

    /**
     * Chooses what this machine makes, discarding any craft under way.
     *
     * <p>Accepts a key that resolves to nothing: a datapack can remove a recipe while a machine
     * is set to it, and a machine that quietly forgets what it was making is worse than one
     * that sits idle until the recipe comes back.
     */
    public void setRecipe(@Nullable ResourceKey<Recipe<?>> key) {
        if (Objects.equals(recipeKey, key)) {
            return;
        }
        FacraftRecipe chosen = null;
        if (key != null && level instanceof ServerLevel serverLevel) {
            RecipeHolder<?> holder = serverLevel.getServer().getRecipeManager().byKey(key).orElse(null);
            chosen = holder != null && holder.value() instanceof FacraftRecipe facraft ? facraft : null;
            // A recipe this tier cannot run - a fluid on a machine with no fluid box - is refused
            // rather than stored. The panel already filters on the same rule; this is the last
            // line behind it.
            if (chosen != null && !accepts(chosen)) {
                return;
            }
            // Factorio refuses a recipe that may not have productivity modules while any sit in
            // the machine, rather than throwing the modules out or quietly ignoring them.
            if (chosen != null && !allowsProductivity(chosen)
                    && modules.holdsAnyRefusedBy(module -> module.effect().productivity() <= 0)) {
                return;
            }
        }
        recipeKey = key;
        pointTanks(chosen);
        progress = 0;
        craftTicks = 0;
        setChanged();

        // The screen reads the chosen recipe off this block entity, so a change has to reach the
        // clients watching it. setChanged alone only marks the chunk for saving.
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        wake();
    }

    /**
     * Points the fluid boxes at the recipe's fluids, and empties them of anything else.
     *
     * <p>The input box takes only the recipe's fluid ingredient, so a run of the wrong fluid is
     * refused at the wall, and the output box is pointed at the fluid result so a pipe can draw a
     * fluid the machine has not made yet. A recipe with no fluid leaves both boxes pointed at
     * nothing, and nothing goes in. Changing recipe throws away what the new one has no use for,
     * as Factorio does.
     */
    private void pointTanks(@Nullable FacraftRecipe recipe) {
        if (fluidIn == null || fluidOut == null) {
            return;
        }
        Fluid in = null;
        if (recipe != null && !recipe.fluidIngredients().isEmpty()) {
            List<Holder<Fluid>> fluids = recipe.fluidIngredients().get(0).ingredient().fluids();
            in = fluids.isEmpty() ? null : fluids.get(0).value();
        }
        Fluid out = recipe != null && !recipe.fluidResults().isEmpty()
                ? recipe.fluidResults().get(0).fluid().value()
                : null;
        fluidIn.assign(in);
        fluidOut.assign(out);
    }

    /** Called by {@link AssemblerBlock}, and only ever on a tick this machine asked for. */
    public void serverTick(ServerLevel level) {
        FacraftRecipe recipe = recipe(level);
        if (recipe != null && fluidIn != null && fluidIn.assigned() == null && recipe.hasFluids()) {
            // Loaded from disk with the tanks not yet pointed: the recipe is resolved by key only
            // once a level is here, so the first tick is where the boxes learn their fluids.
            pointTanks(recipe);
        }
        if (recipe == null) {
            // No recipe, or one that no longer exists. Nothing to schedule for; choosing a
            // recipe wakes it again.
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }

        // The modules are read as the craft starts and hold for the craft, which is Factorio's
        // rule: a module pulled out mid-craft finishes that craft at the speed it began at.
        ModuleEffect effect = modules.effect();
        craftTicks = craftTicksFor(recipe, (float) (craftingSpeed * effect.speedFactor()));
        int draw = effect.scaleEnergy(energyPerTick);

        // The ingredients are checked once, as a craft starts. Counting down is the cheap part;
        // simulating a whole craft every tick for every machine in a base is not.
        if (progress == 0 && !craft(recipe, false)) {
            return;
        }

        if (energy.getAmountAsInt() < draw) {
            // Out of power, holding the craft where it stands. Nothing here can wake it - the
            // grid can, and MachinePower is what tells us it has. PLAN.md's brownout, where a
            // machine that cannot refill runs slower rather than stopping, is the later shape.
            setChanged();
            return;
        }

        if (progress < craftTicks) {
            progress++;
            energy.set(energy.getAmountAsInt() - draw);
        }

        if (progress >= craftTicks) {
            if (!craft(recipe, true)) {
                // Finished but unpaid: the output slot filled up, or the ingredients were taken
                // back out mid-craft. Hold the craft and sleep rather than spin - anything that
                // changes the inventory wakes it, which covers both cases.
                setChanged();
                return;
            }
            progress = 0;
            // Factorio's craft-item trigger counts what a machine makes, not only what a hand
            // does: "craft a lab" is finished by an assembler making one. Facrafting carries the
            // news to whoever is counting, and this mod never learns who that is.
            CraftListeners.fireMachine(level, recipe.resultStack());

            // Productivity: every craft earns a fraction of a free one, and when a whole one is
            // owed it is handed over unpaid. One that will not fit stays owed, as Factorio's does.
            if (allowsProductivity(recipe)) {
                productivity.earn(effect.productivityBonus());
            }
            if (productivity.owed() && bankFree(recipe)) {
                productivity.pay();
                CraftListeners.fireMachine(level, recipe.resultStack());
            }
        }

        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * Pays for one craft and banks its result, all or nothing.
     *
     * <p>{@link CraftPlanner} works out which slots pay - one ingredient may have to draw from
     * several stacks, and two ingredients may compete for the same one - and the transaction
     * makes the exchange atomic. A craft whose result will not fit rolls back with the
     * ingredients untouched, which is what lets a full output slot stall the machine rather
     * than void a craft.
     *
     * @param commit false to ask whether the craft is possible without performing it.
     * @return whether the craft was, or would have been, paid for in full.
     */
    private boolean craft(FacraftRecipe recipe, boolean commit) {
        // The input slots only, so a machine cannot pay for a craft out of its own output slot.
        // These are the live stacks and are only read here; the handler replaces stacks on
        // change rather than mutating them, so the snapshot cannot go stale underneath.
        List<ItemStack> inputs = inventory.copyToList().subList(0, INPUT_SLOTS);
        Int2IntMap plan = CraftPlanner.plan(inputs, recipe);
        if (plan == null) {
            return false;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            for (Int2IntMap.Entry entry : plan.int2IntEntrySet()) {
                int slot = entry.getIntKey();
                int amount = entry.getIntValue();
                if (inventory.extract(slot, inventory.getResource(slot), amount, transaction) != amount) {
                    return false;
                }
            }

            // The fluid half, on a tier that has it: one fluid in from the input box, one out to
            // the output box, inside the same transaction as the items, so a full output box
            // stalls the craft with the lubricant still in the tank.
            if (!recipe.fluidIngredients().isEmpty()) {
                if (fluidIn == null) {
                    return false;
                }
                SizedFluidIngredient ingredient = recipe.fluidIngredients().get(0);
                FluidResource held = fluidIn.getResource(0);
                if (held.isEmpty() || !ingredient.test(held.toStack(ingredient.amount()))
                        || fluidIn.extract(0, held, ingredient.amount(), transaction) != ingredient.amount()) {
                    return false;
                }
            }

            if (recipe.hasItemResult()) {
                ItemStack result = recipe.resultStack();
                int stored = inventory.insert(
                        OUTPUT_SLOT, ItemResource.of(result), result.getCount(), transaction);
                if (stored != result.getCount()) {
                    return false;
                }
            }
            if (!bankFluid(recipe, transaction)) {
                return false;
            }

            if (commit) {
                transaction.commit();
            }
            return true;
        }
    }

    /** The fluid result into the output box, if the recipe has one. True when there is room, or none to bank. */
    private boolean bankFluid(FacraftRecipe recipe, Transaction transaction) {
        if (recipe.fluidResults().isEmpty()) {
            return true;
        }
        if (fluidOut == null) {
            return false;
        }
        FluidStackTemplate result = recipe.fluidResults().get(0);
        return fluidOut.insert(0, FluidResource.of(result.fluid()), result.amount(), transaction) == result.amount();
    }

    /** Banks one more craft's products without paying for them: the productivity bonus. Nothing if they will not fit. */
    private boolean bankFree(FacraftRecipe recipe) {
        try (Transaction transaction = Transaction.openRoot()) {
            if (recipe.hasItemResult()) {
                ItemStack result = recipe.resultStack();
                if (inventory.insert(OUTPUT_SLOT, ItemResource.of(result), result.getCount(), transaction)
                        != result.getCount()) {
                    return false;
                }
            }
            if (!bankFluid(recipe, transaction)) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    /** The selected recipe as it exists right now, or null if there is none or it is gone. */
    private @Nullable FacraftRecipe recipe(ServerLevel level) {
        if (recipeKey == null) {
            return null;
        }
        RecipeHolder<?> holder = level.getServer().getRecipeManager().byKey(recipeKey).orElse(null);
        return holder != null && holder.value() instanceof FacraftRecipe facraft ? facraft : null;
    }

    /**
     * The timed recipe that produces {@code item}, if the pack has one.
     *
     * <p>How a machine is told what to make until there is a screen to pick from: point at it
     * holding the thing you want it to make. Factorio's recipe list has one recipe per item
     * almost everywhere, so the first match is the right one.
     */
    public static @Nullable ResourceKey<Recipe<?>> recipeProducing(ServerLevel level, Item item) {
        for (RecipeHolder<FacraftRecipe> holder
                : level.getServer().getRecipeManager().recipeMap().byType(ModRecipes.FACRAFT_TYPE.get())) {
            if (holder.value().hasItemResult() && holder.value().resultStack().getItem() == item) {
                return holder.id();
            }
        }
        return null;
    }

    /**
     * Schedules the next tick unless one is already coming.
     *
     * <p>The guard is what makes this safe to call from anywhere: several items inserted in one
     * transaction each report a change, and without it each would queue its own tick.
     */
    private void wake() {
        // isRemoved() matters when the machine is being broken: emptying its slots reports a
        // change like any other, and a machine that no longer exists must not queue work.
        if (level == null || level.isClientSide() || isRemoved()) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            level.scheduleTick(worldPosition, block, 1);
        }
    }

    private void onInventoryChanged() {
        setChanged();
        wake();
    }

    /**
     * Electricity arrived, or was spent.
     *
     * <p>The wake is the half that matters. A machine that stopped for want of power is not
     * scheduled for anything, so without this the grid coming back would reach a machine that
     * never looks again - and it would sit still beside a full pole, which reads as a broken
     * assembler rather than as a missing wake-up.
     */
    private void onPowerChanged() {
        setChanged();
        wake();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AssemblerMenu(containerId, playerInventory, inventory, modules, menuData, worldPosition);
    }

    /**
     * Spilled when the machine is broken, so a factory is not a way to lose iron.
     *
     * <p><b>This is the hook, not {@code Block.affectNeighborsAfterRemoval}.</b> In 26.2 the
     * base implementation here is what drops a machine's contents, and it only does so for a
     * {@link net.minecraft.world.Container} - which a {@code ResourceHandler} is not. A
     * capability-based inventory that does not override this silently eats everything in it,
     * and nothing in the removal path complains. {@code assembler_spills_when_broken} is the
     * test that caught exactly that.
     *
     * <p>Each slot is emptied before its contents are dropped, rather than handing
     * {@code copyToList()} to {@code Containers.dropContents}. That would work today only
     * because the copied list holds the same {@link ItemStack} objects the handler does, and
     * dropping drains them in place - an aliasing detail the method's name denies. If it ever
     * stopped being true, every broken machine would duplicate its contents silently.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            int amount = inventory.getAmountAsInt(slot);
            if (amount <= 0) {
                continue;
            }
            ItemResource resource = inventory.getResource(slot);
            inventory.set(slot, ItemResource.EMPTY, 0);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), resource.toStack(amount));
        }
        for (int slot = 0; slot < modules.size(); slot++) {
            int amount = modules.getAmountAsInt(slot);
            if (amount <= 0) {
                continue;
            }
            ItemResource resource = modules.getResource(slot);
            modules.set(slot, ItemResource.EMPTY, 0);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), resource.toStack(amount));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("Inventory"));
        modules.serialize(output.child("Modules"));
        productivity.save(output);
        if (fluidIn != null && fluidOut != null) {
            fluidIn.serialize(output.child("FluidIn"));
            fluidOut.serialize(output.child("FluidOut"));
        }
        energy.serialize(output.child("Energy"));
        output.putInt("Progress", progress);
        if (recipeKey != null) {
            output.putString("Recipe", recipeKey.identifier().toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Inventory").ifPresent(inventory::deserialize);
        input.child("Modules").ifPresent(modules::deserialize);
        productivity.load(input);
        if (fluidIn != null && fluidOut != null) {
            input.child("FluidIn").ifPresent(fluidIn::deserialize);
            input.child("FluidOut").ifPresent(fluidOut::deserialize);
        }
        input.child("Energy").ifPresent(energy::deserialize);
        progress = input.getIntOr("Progress", 0);
        recipeKey = input.getString("Recipe")
                .map(Identifier::tryParse)
                .map(id -> ResourceKey.create(Registries.RECIPE, id))
                .orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
