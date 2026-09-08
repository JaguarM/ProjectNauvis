package com.jaguarm.nauvisrocket.silo;

import java.util.List;

import com.jaguarm.facrafting.queue.CraftListeners;
import com.jaguarm.facrafting.recipe.CraftPlanner;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.facrafting.registry.ModRecipes;
import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvislib.module.ModuleEffect;
import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.module.Productivity;
import com.jaguarm.nauvislib.multiblock.MachineCell;
import com.jaguarm.nauvislib.transfer.MachineAccess;
import com.jaguarm.nauvislib.transfer.MachinePower;
import com.jaguarm.nauvislib.transfer.PowerAccess;
import com.jaguarm.nauvisrocket.registry.ModBlockEntities;
import com.jaguarm.nauvisrocket.registry.ModItems;
import com.mojang.logging.LogUtils;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A rocket silo: builds rocket parts out of what it is given, a hundred of them make a rocket,
 * and a satellite in its slot sends the rocket up.
 *
 * <h2>Two recipes, neither chosen</h2>
 *
 * <p>Factorio's silo runs one recipe and nobody picks it: {@code rocket-part}, in the
 * {@code rocket-building} category only the silo has. It is found here by what it makes, the way
 * a furnace finds smelting recipes, and run the assembler's way - a craft's ingredients checked as
 * it starts, paid for and banked in one transaction as it ends - except that what it makes goes
 * into the rocket rather than into a slot: {@link #parts} climbs by one.
 *
 * <p>The launch is the dump's other recipe, {@code space-science-pack}: a hundred rocket parts
 * and a satellite make a thousand packs. The silo does not run it as a timed craft - its three
 * hundred seconds are the hundred parts' three seconds each, already spent - but it reads its
 * numbers off it, so how many parts a rocket is and what comes back are the generated data's and
 * not this class's. When the parts are there and the slot holds the satellite, the countdown
 * starts on its own, as Factorio 2.0's does - or, with automatic launch switched off on the
 * screen, waits for the Launch button, which sends the rocket up with whatever it holds, cargo or
 * none, as Factorio 1.1's did. The science is owed to the output slot and paid into it as fast as
 * it is taken away, since a thousand of anything is sixteen stacks.
 *
 * <h2>Sleeping</h2>
 *
 * <p>No ticker. A silo ticks while it is building or counting down, and stops for want of
 * ingredients, of a satellite, or of power; it wakes on its inventory changing, on a neighbour
 * changing, and on electricity arriving through {@link MachinePower}. The one thing it never
 * does is poll.
 */
public class RocketSiloBlockEntity extends BlockEntity implements MenuProvider {

    /** Three ingredient slots: a rocket part has three ingredients. */
    public static final int INPUT_SLOTS = 3;
    public static final int SATELLITE_SLOT = INPUT_SLOTS;
    public static final int OUTPUT_SLOT = SATELLITE_SLOT + 1;
    public static final int SLOT_COUNT = OUTPUT_SLOT + 1;

    /** Factorio's silo takes four modules. */
    public static final int MODULE_SLOTS = 4;

    /** Factorio's category for what only a silo can make. */
    public static final String ROCKET_BUILDING = "rocket-building";

    /**
     * 4 MW at the pack's ratio of 120 FE/t to a 900 kW engine: a silo is four and a half steam
     * engines, which is Factorio's number and the reason a rocket is a whole power station's work.
     */
    public static final int ENERGY_PER_TICK = 533;

    /** Five seconds of building, like every other machine's buffer. */
    public static final int ENERGY_CAPACITY = ENERGY_PER_TICK * 100;

    /** Factorio's {@code crafting_speed = 1}: a rocket part takes exactly its three seconds. */
    public static final float CRAFTING_SPEED = 1.0F;

    /** How long the countdown runs: five seconds of fire under the rocket, then it goes. */
    public static final int LAUNCH_TICKS = 100;

    /** How often a silo with no recipe looks again: ten seconds. */
    public static final int NO_RECIPE_RECHECK_TICKS = 200;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Whether the missing recipe has been said in the log, so it is said once per silo. */
    private boolean noRecipeReported;

    private final RocketSiloInventory inventory = new RocketSiloInventory(this::onInventoryChanged,
            this::wanted, resource -> resource.is(ModItems.SATELLITE.get()));

    /** What inserters see: ingredients and a satellite in, science out. Never the raw slots. */
    private final ResourceHandler<ItemResource> automationView = new MachineAccess(inventory, OUTPUT_SLOT);

    /** Every rocket part is an intermediate product, so every module is welcome here. */
    private final ModuleSlots modules = new ModuleSlots(MODULE_SLOTS, this::onInventoryChanged);
    private final Productivity productivity = new Productivity();

    private final MachinePower energy = new MachinePower(ENERGY_CAPACITY, this::onPowerChanged);
    private final EnergyHandler gridView = new PowerAccess(energy);

    /** The rocket part recipe and the launch recipe, by key once found; null until looked for. */
    private @Nullable ResourceKey<Recipe<?>> partRecipeKey;
    private @Nullable ResourceKey<Recipe<?>> launchRecipeKey;

    /** Rocket parts built into the rocket standing on the pad. */
    private int parts;

    /** Ticks into the current part, and how many it takes at the modules read as it started. */
    private int progress;
    private int craftTicks;

    /** Ticks left of the countdown; zero when no rocket is leaving. */
    private int launchTicks;

    /** Space science the last launch sent back and the output slot has not had room for yet. */
    private int owed;

    /** Rockets this silo has launched, for the readout and for the record. */
    private int launches;

    /** Whether a complete rocket with a satellite goes on its own. Off, it waits for the button. */
    private boolean autoLaunch = true;

    /** The button was pressed: launch as soon as the rocket is complete, cargo or none. */
    private boolean launchRequested;

    private RocketSiloStatus status = RocketSiloStatus.NO_INGREDIENTS;

    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case RocketSiloMenu.DATA_PROGRESS -> progress;
                case RocketSiloMenu.DATA_CRAFT_TICKS -> craftTicks;
                case RocketSiloMenu.DATA_ENERGY -> energy.getAmountAsInt();
                case RocketSiloMenu.DATA_ENERGY_PER_TICK -> modules.effect().scaleEnergy(ENERGY_PER_TICK);
                case RocketSiloMenu.DATA_PARTS -> parts;
                case RocketSiloMenu.DATA_PARTS_NEEDED -> partsNeeded;
                case RocketSiloMenu.DATA_LAUNCH_TICKS -> launchTicks;
                case RocketSiloMenu.DATA_OWED -> owed;
                case RocketSiloMenu.DATA_STATUS -> status.ordinal();
                case RocketSiloMenu.DATA_LAUNCHES -> launches;
                case RocketSiloMenu.DATA_AUTO_LAUNCH -> autoLaunch ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            // Server-authoritative: the client is told, never asked.
        }

        @Override
        public int getCount() {
            return RocketSiloMenu.DATA_COUNT;
        }
    };

    /** How many parts the launch recipe wants, cached for the screen; a hundred until it is read. */
    private int partsNeeded = 100;

    public RocketSiloBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROCKET_SILO.get(), pos, state);
    }

    public RocketSiloInventory inventory() {
        return inventory;
    }

    public ModuleSlots modules() {
        return modules;
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

    public int parts() {
        return parts;
    }

    public int partsNeeded() {
        return partsNeeded;
    }

    public int owed() {
        return owed;
    }

    public int launches() {
        return launches;
    }

    public int launchTicks() {
        return launchTicks;
    }

    public RocketSiloStatus status() {
        return status;
    }

    public boolean autoLaunch() {
        return autoLaunch;
    }

    /** Switches automatic launch on or off: the screen's toggle. */
    public void setAutoLaunch(boolean automatic) {
        if (autoLaunch != automatic) {
            autoLaunch = automatic;
            setChanged();
            wake();
        }
    }

    /**
     * The Launch button: sends the rocket up the moment it is complete, with the satellite if
     * there is one and with nothing if not. Nothing happens to a rocket still being built - the
     * request is kept until it is - or to one already leaving.
     */
    public void requestLaunch() {
        launchRequested = true;
        setChanged();
        wake();
    }

    /**
     * Puts parts into the rocket without building them. For a gametest, and for a gamemaster:
     * a hundred parts is a hundred and fifty minutes of one silo, which no test waits for.
     */
    public void loadRocket(int count) {
        parts = Math.max(0, parts + count);
        setChanged();
        wake();
    }

    /** Called by {@link RocketSiloBlock}, and only ever on a tick this silo asked for. */
    public void serverTick(ServerLevel level) {
        payOut();

        if (launchTicks > 0) {
            countDown(level);
            return;
        }

        FacraftRecipe part = partRecipe(level);
        FacraftRecipe launch = launchRecipe(level);
        if (part == null || launch == null) {
            // A pack without the recipes - or a recipe that arrives later than this tick did.
            // Said once in the log, with what was found, and looked at again every ten seconds:
            // one machine in a state the shipped pack never reaches costs nothing to re-check.
            if (!noRecipeReported) {
                noRecipeReported = true;
                LOGGER.warn("Rocket silo at {} has no recipe to run: rocket part {}, launch {}, {} rocket-building "
                        + "recipes among {} timed recipes", worldPosition, part == null ? "missing" : "found",
                        launch == null ? "missing" : "found", countRocketBuilding(level),
                        level.getServer().getRecipeManager().recipeMap().byType(ModRecipes.FACRAFT_TYPE.get()).size());
            }
            settle(RocketSiloStatus.NO_RECIPE);
            level.scheduleTick(worldPosition, getBlockState().getBlock(), NO_RECIPE_RECHECK_TICKS);
            return;
        }
        noRecipeReported = false;
        partsNeeded = partsPerRocket(launch);

        if (parts >= partsNeeded) {
            boolean cargo = holdsCargo(launch);
            if (launchRequested || (autoLaunch && cargo)) {
                launchRequested = false;
                beginLaunch(level, launch, cargo);
                level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
                return;
            }
            // The rocket is complete and waits: for its satellite, or for the button. Either wakes it.
            settle(RocketSiloStatus.READY);
            return;
        }

        // The modules are read as a part starts and hold for the part, which is Factorio's rule.
        ModuleEffect effect = modules.effect();
        craftTicks = Math.max(1, Math.round(part.craftTicks() / (CRAFTING_SPEED * (float) effect.speedFactor())));
        int draw = effect.scaleEnergy(ENERGY_PER_TICK);

        if (progress == 0 && !build(part, false)) {
            settle(RocketSiloStatus.NO_INGREDIENTS);
            return;
        }
        if (energy.getAmountAsInt() < draw) {
            // Out of power, holding the part where it stands. The grid wakes it, through MachinePower.
            settle(RocketSiloStatus.NO_POWER);
            return;
        }

        if (progress < craftTicks) {
            progress++;
            energy.set(energy.getAmountAsInt() - draw);
        }
        if (progress >= craftTicks) {
            if (!build(part, true)) {
                // The ingredients were taken back out mid-part. Hold it and sleep; anything
                // that changes the inventory wakes it.
                settle(RocketSiloStatus.NO_INGREDIENTS);
                return;
            }
            progress = 0;
            parts++;
            CraftListeners.fireMachine(level, part.resultStack());
            productivity.earn(effect.productivityBonus());
            if (productivity.owed()) {
                productivity.pay();
                parts++;
                CraftListeners.fireMachine(level, part.resultStack());
            }
        }

        status = RocketSiloStatus.BUILDING;
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** The countdown: fire under the rocket every few ticks, then the rocket goes and the science comes back. */
    private void countDown(ServerLevel level) {
        launchTicks--;
        if (launchTicks % 4 == 0) {
            Launch.exhaust(level, base());
        }
        if (launchTicks <= 0) {
            Launch.liftOff(level, nose());
            FacraftRecipe launch = launchRecipe(level);
            // What comes back is the cargo's doing: a rocket sent up empty sends nothing back.
            ItemStack science = launch == null || !carryingCargo ? ItemStack.EMPTY : launch.resultStack();
            owed += science.getCount();
            launches++;
            carryingCargo = false;
            if (!science.isEmpty()) {
                CraftListeners.fireMachine(level, science);
            }
            Launch.celebrate(level.getServer(), worldPosition);
            payOut();
        }
        status = RocketSiloStatus.LAUNCHING;
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** Whether the rocket on its way up has a satellite in it, for what comes back. */
    private boolean carryingCargo;

    /** Takes the satellite if the rocket is to carry one, empties the rocket, lights the engines. */
    private void beginLaunch(ServerLevel level, FacraftRecipe launch, boolean withCargo) {
        SizedIngredient cargo = cargoOf(launch);
        carryingCargo = false;
        if (withCargo && cargo != null) {
            try (Transaction transaction = Transaction.openRoot()) {
                ItemResource held = inventory.getResource(SATELLITE_SLOT);
                if (inventory.extract(SATELLITE_SLOT, held, cargo.count(), transaction) != cargo.count()) {
                    return;
                }
                transaction.commit();
            }
            carryingCargo = true;
        }
        parts -= partsNeeded;
        launchTicks = LAUNCH_TICKS;
        status = RocketSiloStatus.LAUNCHING;
        Launch.ignite(level, base());
        setChanged();
    }

    /** What the last launch sent back, into the output slot as far as it has room. */
    private void payOut() {
        if (owed <= 0) {
            return;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int paid = inventory.insert(OUTPUT_SLOT, ItemResource.of(ModItems.SPACE_SCIENCE_PACK.get()), owed, transaction);
            if (paid > 0) {
                transaction.commit();
                owed -= paid;
                setChanged();
            }
        }
    }

    /**
     * Pays for one rocket part, all or nothing. {@link CraftPlanner} works out which slots pay and
     * the transaction makes the exchange atomic; with {@code commit} false it is the question
     * rather than the deed.
     */
    private boolean build(FacraftRecipe part, boolean commit) {
        List<ItemStack> inputs = inventory.copyToList().subList(0, INPUT_SLOTS);
        Int2IntMap plan = CraftPlanner.plan(inputs, part);
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
            if (commit) {
                transaction.commit();
            }
            return true;
        }
    }

    /** Whether the satellite slot holds what the launch recipe wants besides rocket parts. */
    private boolean holdsCargo(FacraftRecipe launch) {
        SizedIngredient cargo = cargoOf(launch);
        if (cargo == null) {
            return true;
        }
        int held = inventory.getAmountAsInt(SATELLITE_SLOT);
        return held >= cargo.count() && cargo.ingredient().test(inventory.getResource(SATELLITE_SLOT).toStack(1));
    }

    /**
     * How many of this a rocket part takes in this slot, by the recipe as this server has it: slot
     * one is ingredient one, and zero for anything else. This block entity's copy on a client has
     * no recipe manager and is asked nothing; the client's menu works the same rule out of its own
     * copy of the recipe, in {@code ClientSiloRules}.
     */
    private int wanted(int slot, ItemResource resource) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        FacraftRecipe part = partRecipe(serverLevel);
        return part == null ? 0 : wanted(part, slot, resource);
    }

    /** How many of this ingredient {@code slot} of the recipe takes, or zero if this is not it. */
    public static int wanted(FacraftRecipe part, int slot, ItemResource resource) {
        List<SizedIngredient> ingredients = part.ingredients();
        if (slot < 0 || slot >= ingredients.size()) {
            return 0;
        }
        SizedIngredient ingredient = ingredients.get(slot);
        return ingredient.ingredient().test(resource.toStack(1)) ? ingredient.count() : 0;
    }

    /** The launch recipe's ingredient that is not the rocket part: the satellite, in the dump. */
    private static @Nullable SizedIngredient cargoOf(FacraftRecipe launch) {
        ItemStack part = new ItemStack(ModItems.ROCKET_PART.get());
        for (SizedIngredient ingredient : launch.ingredients()) {
            if (!ingredient.ingredient().test(part)) {
                return ingredient;
            }
        }
        return null;
    }

    /** How many rocket parts the launch recipe asks for: a hundred, in the dump. */
    private static int partsPerRocket(FacraftRecipe launch) {
        ItemStack part = new ItemStack(ModItems.ROCKET_PART.get());
        for (SizedIngredient ingredient : launch.ingredients()) {
            if (ingredient.ingredient().test(part)) {
                return ingredient.count();
            }
        }
        return 100;
    }

    private @Nullable FacraftRecipe partRecipe(ServerLevel level) {
        if (partRecipeKey == null) {
            partRecipeKey = recipeProducing(level, ModItems.ROCKET_PART.get());
        }
        return resolve(level, partRecipeKey);
    }

    private @Nullable FacraftRecipe launchRecipe(ServerLevel level) {
        if (launchRecipeKey == null) {
            launchRecipeKey = recipeProducing(level, ModItems.SPACE_SCIENCE_PACK.get());
        }
        return resolve(level, launchRecipeKey);
    }

    private static @Nullable FacraftRecipe resolve(ServerLevel level, @Nullable ResourceKey<Recipe<?>> key) {
        if (key == null) {
            return null;
        }
        RecipeHolder<?> holder = level.getServer().getRecipeManager().byKey(key).orElse(null);
        return holder != null && holder.value() instanceof FacraftRecipe facraft ? facraft : null;
    }

    /** How many timed recipes this server has in the silo's category, for the log line. */
    private static int countRocketBuilding(ServerLevel level) {
        int count = 0;
        for (RecipeHolder<FacraftRecipe> holder
                : level.getServer().getRecipeManager().recipeMap().byType(ModRecipes.FACRAFT_TYPE.get())) {
            if (ROCKET_BUILDING.equals(holder.value().category())) {
                count++;
            }
        }
        return count;
    }

    /** The timed recipe in the silo's category that makes this item, or null. */
    public static @Nullable ResourceKey<Recipe<?>> recipeProducing(ServerLevel level, Item item) {
        for (RecipeHolder<FacraftRecipe> holder
                : level.getServer().getRecipeManager().recipeMap().byType(ModRecipes.FACRAFT_TYPE.get())) {
            FacraftRecipe recipe = holder.value();
            if (ROCKET_BUILDING.equals(recipe.category()) && recipe.hasItemResult()
                    && recipe.resultStack().getItem() == item) {
                return holder.id();
            }
        }
        return null;
    }

    /** The middle of the deck under the rocket, where the fire is. */
    private Vec3 base() {
        return Vec3.atBottomCenterOf(worldPosition.above());
    }

    /** The top of the nose, where the rocket leaves from. */
    private Vec3 nose() {
        int height = 0;
        for (MachineCell cell : RocketSiloShape.SHAPE.cells()) {
            height = Math.max(height, cell.y());
        }
        return Vec3.atBottomCenterOf(worldPosition.above(height + 1));
    }

    private void settle(RocketSiloStatus why) {
        status = why;
        setChanged();
    }

    private void wake() {
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
        Multiblock.announce(level, worldPosition, getBlockState());
        wake();
    }

    private void onPowerChanged() {
        setChanged();
        wake();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RocketSiloMenu(containerId, playerInventory, inventory, modules, menuData,
                ContainerLevelAccess.create(level, worldPosition), this);
    }

    /**
     * Spilled when the silo is broken - the slots, the modules, and the science still owed. The
     * parts built into the rocket are not: a rocket is not a thing that comes apart. This is the
     * hook, not {@code Block#affectNeighborsAfterRemoval}; see {@code PITFALLS.md}.
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
        if (owed > 0) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                    new ItemStack(ModItems.SPACE_SCIENCE_PACK.get(), owed));
            owed = 0;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("Inventory"));
        modules.serialize(output.child("Modules"));
        productivity.save(output);
        energy.serialize(output.child("Energy"));
        output.putInt("Parts", parts);
        output.putInt("Progress", progress);
        output.putInt("CraftTicks", craftTicks);
        output.putInt("LaunchTicks", launchTicks);
        output.putInt("Owed", owed);
        output.putInt("Launches", launches);
        output.putBoolean("AutoLaunch", autoLaunch);
        output.putBoolean("LaunchRequested", launchRequested);
        output.putBoolean("CarryingCargo", carryingCargo);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Inventory").ifPresent(inventory::deserialize);
        input.child("Modules").ifPresent(modules::deserialize);
        productivity.load(input);
        input.child("Energy").ifPresent(energy::deserialize);
        parts = input.getIntOr("Parts", 0);
        progress = input.getIntOr("Progress", 0);
        craftTicks = input.getIntOr("CraftTicks", 0);
        launchTicks = input.getIntOr("LaunchTicks", 0);
        owed = input.getIntOr("Owed", 0);
        launches = input.getIntOr("Launches", 0);
        autoLaunch = input.getBooleanOr("AutoLaunch", true);
        launchRequested = input.getBooleanOr("LaunchRequested", false);
        carryingCargo = input.getBooleanOr("CarryingCargo", false);
    }
}
