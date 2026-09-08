package com.jaguarm.nauvisfluids.processing;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvislib.pollution.Pollution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

import com.jaguarm.facrafting.queue.CraftListeners;
import com.jaguarm.facrafting.recipe.CraftPlanner;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.nauvislib.module.ModuleEffect;
import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.module.Productivity;
import com.jaguarm.nauvislib.transfer.FluidOutputAccess;
import com.jaguarm.nauvislib.transfer.PortTank;
import com.jaguarm.nauvislib.transfer.MachineAccess;
import com.jaguarm.nauvislib.transfer.MachinePower;
import com.jaguarm.nauvislib.transfer.PowerAccess;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.jspecify.annotations.Nullable;

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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * A machine that turns fluids into fluids, and sometimes items into either: the oil refinery
 * and the chemical plant, which are one machine with two sets of numbers.
 */
public abstract class ProcessingBlockEntity extends BlockEntity implements MenuProvider {

    /** Factorio's fluid box on both machines holds a thousand. */
    public static final int TANK_CAPACITY = 1000;
    /** Both machines craft at speed one: a recipe takes exactly its own time. */
    public static final float CRAFTING_SPEED = 1.0F;
    /** Five seconds of a buffer: enough to ride out a gap, too small to be somewhere the grid parks a surplus. */
    private static final int BUFFER_TICKS = 100;

    protected final ProcessingLayout layout;
    private final List<PortTank> inputs = new ArrayList<>();
    private final List<PortTank> outputs = new ArrayList<>();
    /** What a pipe sees at each output: extraction only, and a wake-up on the way out. */
    private final List<ResourceHandler<FluidResource>> outputViews = new ArrayList<>();
    private final ProcessingInventory items;
    /** What inserters and hoppers see: inputs in, outputs out, never the raw slots. */
    private final ResourceHandler<ItemResource> automationView;
    /** Unrestricted, because the machine spends from it. What the grid sees is {@link #gridView}. */
    private final MachinePower energy;
    private final EnergyHandler gridView;

    /** The chosen recipe, by key: recipes are reloadable data. Null means idle. */
    private @Nullable ResourceKey<Recipe<?>> recipeKey;
    /** The recipe the tanks were last pointed at, so a reload re-points them and a tick does not. */
    private @Nullable FacraftRecipe assignedFor;
    /** For each fluid ingredient and result of that recipe, which port it uses. */
    private int[] inputPorts = new int[0];
    private int[] outputPorts = new int[0];
    private int progress;
    private int craftTicks;
    private ProcessingStatus status = ProcessingStatus.NO_RECIPE;

    /** What an open screen reads. Ints only; the recipe is read off this block entity instead. */
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            switch (id) {
                case ProcessingMenu.DATA_PROGRESS: return progress;
                case ProcessingMenu.DATA_CRAFT_TICKS: return craftTicks;
                case ProcessingMenu.DATA_ENERGY: return energy.getAmountAsInt();
                case ProcessingMenu.DATA_ENERGY_CAPACITY: return energyCapacity();
                case ProcessingMenu.DATA_STATUS: return status.ordinal();
                default:
                    int slot = id - ProcessingMenu.DATA_TANKS;
                    PortTank tank = tank(slot / 2);
                    if (tank == null) {
                        return 0;
                    }
                    return slot % 2 == 0
                            ? tank.getAmountAsInt(0)
                            : BuiltInRegistries.FLUID.getId(tank.shownFluid());
            }
        }

        @Override
        public void set(int id, int value) {
            // Server-authoritative: the client is told, never asked.
        }

        @Override
        public int getCount() {
            return ProcessingMenu.dataCount(layout);
        }
    };

    protected ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            ProcessingLayout layout) {
        super(type, pos, state);
        this.layout = layout;
        for (int i = 0; i < layout.fluidInputs(); i++) {
            inputs.add(new PortTank(TANK_CAPACITY, this::onTankChanged));
        }
        for (int i = 0; i < layout.fluidOutputs(); i++) {
            PortTank tank = new PortTank(TANK_CAPACITY, this::onTankChanged);
            outputs.add(tank);
            outputViews.add(new FluidOutputAccess(tank, this::wake));
        }
        items = new ProcessingInventory(layout.itemSlots(), this::onItemsChanged);
        automationView = new MachineAccess(items, layout.itemInputs());
        // Every oil recipe makes an intermediate product, so every module is welcome here.
        modules = new ModuleSlots(layout.moduleSlots(), this::onItemsChanged);
        draw = layout.energyPerTick();
        energy = new MachinePower(layout.energyPerTick() * BUFFER_TICKS, this::onPowerChanged);
        gridView = new PowerAccess(energy);
    }

    /**
     * The layout's module slots and the free craft they work towards. Read once a craft, as it
     * starts, like the assembler's; a module pulled out mid-craft finishes that craft as it began.
     */
    private final ModuleSlots modules;
    private final Productivity productivity = new Productivity();

    /** The draw under the modules read at the start of the craft in progress. */
    private int draw;

    public ModuleSlots modules() {
        return modules;
    }

    public Productivity productivity() {
        return productivity;
    }

    /** The layout's draw under the modules in it right now, for the readout. */
    public int currentEnergyPerTick() {
        return modules.effect().scaleEnergy(layout.energyPerTick());
    }

    // --- what the outside world sees ------------------------------------------------------------

    public ProcessingLayout layout() {
        return layout;
    }

    /** The tank behind input port {@code port}, as a pipe fills it. Registered at that port. */
    public ResourceHandler<FluidResource> inputAccess(int port) {
        return inputs.get(port);
    }

    /** The tank behind output port {@code port}, as a pipe drains it. Registered at that port. */
    public ResourceHandler<FluidResource> outputAccess(int port) {
        return outputViews.get(port);
    }

    /** The names the shape gives the ports: {@code in0}, {@code in1}, {@code out0} and so on. */
    public static String inputPort(int port) {
        return "in" + port;
    }

    public static String outputPort(int port) {
        return "out" + port;
    }

    /** What a pipe sees at the port of that name, or null for a name this machine has no port by. */
    public @Nullable ResourceHandler<FluidResource> portAccess(String name) {
        try {
            if (name.startsWith("in")) {
                int port = Integer.parseInt(name.substring(2));
                return port < inputs.size() ? inputAccess(port) : null;
            }
            if (name.startsWith("out")) {
                int port = Integer.parseInt(name.substring(3));
                return port < outputViews.size() ? outputAccess(port) : null;
            }
        } catch (NumberFormatException ignored) {
            // Not one of ours.
        }
        return null;
    }

    public PortTank inputTank(int port) {
        return inputs.get(port);
    }

    public PortTank outputTank(int port) {
        return outputs.get(port);
    }

    /** Tank {@code index}, inputs first, or null past the end. What the menu and the readout count by. */
    public @Nullable PortTank tank(int index) {
        if (index < 0) {
            return null;
        }
        if (index < inputs.size()) {
            return inputs.get(index);
        }
        index -= inputs.size();
        return index < outputs.size() ? outputs.get(index) : null;
    }

    public ProcessingInventory items() {
        return items;
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

    public int energyCapacity() {
        return layout.energyPerTick() * BUFFER_TICKS;
    }

    public @Nullable ResourceKey<Recipe<?>> recipeKey() {
        return recipeKey;
    }

    public int progress() {
        return progress;
    }

    public ProcessingStatus status() {
        return status;
    }

    // --- which port a fluid uses ----------------------------------------------------------------

    /**
     * The input port this machine keeps for a fluid, or -1 for no preference. A fluid with a
     * preference goes there if the port is free; everything else takes the first free port in
     * recipe order.
     */
    protected int preferredInput(@Nullable Fluid fluid) {
        return -1;
    }

    /** The same for outputs. */
    protected int preferredOutput(@Nullable Fluid fluid) {
        return -1;
    }

    /** A port for each fluid: the preferred one where there is one and it is free, else the next free. */
    private static int[] ports(List<@Nullable Fluid> fluids, int count, ToIntFunction<@Nullable Fluid> preferred) {
        int[] ports = new int[fluids.size()];
        Arrays.fill(ports, -1);
        boolean[] taken = new boolean[count];
        for (int i = 0; i < fluids.size(); i++) {
            int port = preferred.applyAsInt(fluids.get(i));
            if (port >= 0 && port < count && !taken[port]) {
                ports[i] = port;
                taken[port] = true;
            }
        }
        for (int i = 0; i < fluids.size(); i++) {
            if (ports[i] >= 0) {
                continue;
            }
            for (int port = 0; port < count; port++) {
                if (!taken[port]) {
                    ports[i] = port;
                    taken[port] = true;
                    break;
                }
            }
        }
        return ports;
    }

    /** Points every tank at the fluid the recipe puts there, and empties the ones it does not use. */
    private void assign(FacraftRecipe recipe) {
        List<@Nullable Fluid> in = new ArrayList<>();
        for (SizedFluidIngredient ingredient : recipe.fluidIngredients()) {
            List<Holder<Fluid>> fluids = ingredient.ingredient().fluids();
            in.add(fluids.isEmpty() ? null : fluids.get(0).value());
        }
        List<@Nullable Fluid> out = new ArrayList<>();
        for (FluidStackTemplate result : recipe.fluidResults()) {
            out.add(result.fluid().value());
        }
        inputPorts = ports(in, inputs.size(), this::preferredInput);
        outputPorts = ports(out, outputs.size(), this::preferredOutput);
        point(inputs, inputPorts, in);
        point(outputs, outputPorts, out);
        assignedFor = recipe;
        setChanged();
    }

    private static void point(List<PortTank> tanks, int[] ports, List<@Nullable Fluid> fluids) {
        Fluid[] byPort = new Fluid[tanks.size()];
        for (int i = 0; i < ports.length; i++) {
            if (ports[i] >= 0) {
                byPort[ports[i]] = fluids.get(i);
            }
        }
        for (int port = 0; port < tanks.size(); port++) {
            tanks.get(port).assign(byPort[port]);
        }
    }

    private void unassign() {
        inputs.forEach(tank -> tank.assign(null));
        outputs.forEach(tank -> tank.assign(null));
        inputPorts = new int[0];
        outputPorts = new int[0];
        assignedFor = null;
    }

    // --- choosing and running --------------------------------------------------------------------

    /**
     * Chooses what this machine makes, discarding any craft under way and any fluid the new
     * recipe has no port for.
     *
     * <p>A recipe this machine cannot run - the wrong category, or more fluids than it has ports -
     * is refused rather than stored, which is the last line behind the panel's own filter. A key
     * that resolves to nothing is accepted: a datapack can remove a recipe while a machine is set
     * to it, and a machine that quietly forgets is worse than one that waits.
     */
    public void setRecipe(@Nullable ResourceKey<Recipe<?>> key) {
        if (Objects.equals(recipeKey, key)) {
            return;
        }
        FacraftRecipe resolved = null;
        if (key != null && level instanceof ServerLevel server) {
            resolved = resolve(server, key);
            if (resolved != null && !layout.accepts(resolved)) {
                return;
            }
        }
        recipeKey = key;
        progress = 0;
        craftTicks = 0;
        if (resolved != null) {
            assign(resolved);
        } else {
            unassign();
        }
        setChanged();
        // A recipe is a slot rule: an inserter refused a moment ago is asleep against a cell that has to hear this.
        Multiblock.announce(level, worldPosition, getBlockState());
        // The screen reads the chosen recipe off this block entity, so a change has to reach the
        // clients watching it. setChanged alone only marks the chunk for saving.
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        wake();
    }

    /** Factorio's rule: the recipe's time over the machine's crafting speed, never below one tick. */
    public static int craftTicksFor(FacraftRecipe recipe) {
        return craftTicksFor(recipe, ModuleEffect.NONE);
    }

    /** The same, with the modules' speed on top of the machine's. */
    public static int craftTicksFor(FacraftRecipe recipe, ModuleEffect effect) {
        return Math.max(1, (int) Math.round(recipe.craftTicks() / (CRAFTING_SPEED * effect.speedFactor())));
    }

    /** Called by the block, and only ever on a tick this machine asked for. */
    public void serverTick(ServerLevel level) {
        FacraftRecipe recipe = recipe(level);
        if (recipe == null) {
            progress = 0;
            settle(ProcessingStatus.NO_RECIPE);
            return;
        }
        if (recipe != assignedFor) {
            assign(recipe);
        }
        // The modules are read as the craft starts and hold for the craft, which is Factorio's rule.
        if (progress == 0) {
            ModuleEffect effect = modules.effect();
            craftTicks = craftTicksFor(recipe, effect);
            draw = effect.scaleEnergy(layout.energyPerTick());
        }
        // The ingredients and the room for the products are checked once, as a craft starts.
        // Counting down is the cheap part; simulating a whole craft every tick is not.
        if (progress == 0) {
            Problem problem = craft(recipe, false);
            if (problem != Problem.NONE) {
                settle(problem.status);
                return;
            }
        }
        if (progress < craftTicks) {
            if (energy.getAmountAsInt() < draw) {
                // Out of power, holding the craft where it stands. The grid wakes this, through
                // MachinePower.
                settle(ProcessingStatus.NO_POWER);
                return;
            }
            progress++;
            energy.set(energy.getAmountAsInt() - draw);
            Pollution.emitTick(level, worldPosition, layout.pollutionPerMinute(), modules.effect().energyFactor());
        }
        // Banking a finished craft costs nothing, and is not held for want of power: the buffer is
        // exactly one craft deep, so the last tick of every craft empties it, and a machine that
        // needed a tick's worth to hand over what it had already made would stall on every craft.
        if (progress >= craftTicks) {
            Problem problem = craft(recipe, true);
            if (problem != Problem.NONE) {
                // Finished but unpaid: an output filled up, or an input was emptied mid-craft.
                // Hold the craft and sleep; whatever changes a tank or a slot wakes it.
                settle(problem.status);
                return;
            }
            progress = 0;
            if (recipe.hasItemResult()) {
                // Factorio's craft-item trigger counts what a machine makes. Facrafting carries
                // the news to whoever is counting, and this mod never learns who that is.
                CraftListeners.fireMachine(level, recipe.resultStack());
            }
            // Productivity: every craft earns a fraction of a free one, and a whole one - every
            // product of the recipe, fluids included - is banked unpaid when it is owed and fits.
            productivity.earn(modules.effect().productivityBonus());
            if (productivity.owed() && bankFree(recipe)) {
                productivity.pay();
                if (recipe.hasItemResult()) {
                    CraftListeners.fireMachine(level, recipe.resultStack());
                }
            }
        }
        status = ProcessingStatus.WORKING;
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** What stops a craft, and the status each one reads as. */
    private enum Problem {
        NONE(ProcessingStatus.WORKING),
        INGREDIENTS(ProcessingStatus.NO_INGREDIENTS),
        OUTPUT(ProcessingStatus.OUTPUT_FULL);

        final ProcessingStatus status;

        Problem(ProcessingStatus status) {
            this.status = status;
        }
    }

    /**
     * Pays for one craft and banks its products, all or nothing.
     *
     * @param commit false to ask whether the craft is possible without performing it.
     */
    private Problem craft(FacraftRecipe recipe, boolean commit) {
        List<SizedFluidIngredient> fluidsIn = recipe.fluidIngredients();
        List<FluidStackTemplate> fluidsOut = recipe.fluidResults();
        if (inputPorts.length != fluidsIn.size() || outputPorts.length != fluidsOut.size()) {
            return Problem.INGREDIENTS;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0; i < fluidsIn.size(); i++) {
                SizedFluidIngredient ingredient = fluidsIn.get(i);
                if (inputPorts[i] < 0) {
                    return Problem.INGREDIENTS;
                }
                PortTank tank = inputs.get(inputPorts[i]);
                FluidResource held = tank.getResource(0);
                if (held.isEmpty() || !ingredient.test(held.toStack(ingredient.amount()))
                        || tank.extract(0, held, ingredient.amount(), transaction) != ingredient.amount()) {
                    return Problem.INGREDIENTS;
                }
            }
            if (!recipe.ingredients().isEmpty()) {
                // The input slots only, so a machine cannot pay for a craft out of its own output.
                List<ItemStack> stacks = items.copyToList().subList(0, layout.itemInputs());
                Int2IntMap plan = CraftPlanner.plan(stacks, recipe);
                if (plan == null) {
                    return Problem.INGREDIENTS;
                }
                for (Int2IntMap.Entry entry : plan.int2IntEntrySet()) {
                    int slot = entry.getIntKey();
                    int amount = entry.getIntValue();
                    if (items.extract(slot, items.getResource(slot), amount, transaction) != amount) {
                        return Problem.INGREDIENTS;
                    }
                }
            }
            for (int i = 0; i < fluidsOut.size(); i++) {
                FluidStackTemplate result = fluidsOut.get(i);
                if (outputPorts[i] < 0) {
                    return Problem.OUTPUT;
                }
                PortTank tank = outputs.get(outputPorts[i]);
                FluidResource made = FluidResource.of(result.fluid());
                if (tank.insert(0, made, result.amount(), transaction) != result.amount()) {
                    return Problem.OUTPUT;
                }
            }
            if (recipe.hasItemResult()) {
                ItemStack result = recipe.resultStack();
                int slot = layout.itemInputs();
                if (items.insert(slot, ItemResource.of(result), result.getCount(), transaction) != result.getCount()) {
                    return Problem.OUTPUT;
                }
            }
            if (commit) {
                transaction.commit();
            }
            return Problem.NONE;
        }
    }

    /**
     * Banks one more craft's products without paying for them: the productivity bonus. All or
     * nothing, so a refinery owed a free craft with one output tank full banks nothing and stays
     * owed, as Factorio's does.
     */
    private boolean bankFree(FacraftRecipe recipe) {
        List<FluidStackTemplate> fluidsOut = recipe.fluidResults();
        if (outputPorts.length != fluidsOut.size()) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0; i < fluidsOut.size(); i++) {
                FluidStackTemplate result = fluidsOut.get(i);
                if (outputPorts[i] < 0) {
                    return false;
                }
                PortTank tank = outputs.get(outputPorts[i]);
                if (tank.insert(0, FluidResource.of(result.fluid()), result.amount(), transaction) != result.amount()) {
                    return false;
                }
            }
            if (recipe.hasItemResult()) {
                ItemStack result = recipe.resultStack();
                int slot = layout.itemInputs();
                if (items.insert(slot, ItemResource.of(result), result.getCount(), transaction) != result.getCount()) {
                    return false;
                }
            }
            transaction.commit();
            return true;
        }
    }

    /** The selected recipe as it exists right now, or null if there is none or it is gone. */
    private @Nullable FacraftRecipe recipe(ServerLevel level) {
        return recipeKey == null ? null : resolve(level, recipeKey);
    }

    private static @Nullable FacraftRecipe resolve(ServerLevel level, ResourceKey<Recipe<?>> key) {
        RecipeHolder<?> holder = level.getServer().getRecipeManager().byKey(key).orElse(null);
        return holder != null && holder.value() instanceof FacraftRecipe facraft ? facraft : null;
    }

    /** Stops without rescheduling, remembering why for the screen and the readout. */
    private void settle(ProcessingStatus why) {
        status = why;
        setChanged();
    }

    // --- waking ----------------------------------------------------------------------------------

    /** A chunk that has just loaded has a machine that has never been woken. One tick, paid once. */
    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    /** Schedules the next tick unless one is already coming. Safe to call from anywhere. */
    public void wake() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!serverLevel.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            serverLevel.scheduleTick(worldPosition, block, 1);
        }
    }

    private void onTankChanged() {
        setChanged();
        wake();
    }

    private void onItemsChanged() {
        setChanged();
        Multiblock.announce(level, worldPosition, getBlockState());
        wake();
    }

    /** Electricity arrived, or was spent. The wake is the half that matters. */
    private void onPowerChanged() {
        setChanged();
        wake();
    }

    // --- the menu, and what is left when the machine goes ----------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    /** Spilled when the machine is broken. The hook is this one, not the block's; see the assembler. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < items.size(); slot++) {
            int amount = items.getAmountAsInt(slot);
            if (amount <= 0) {
                continue;
            }
            ItemResource resource = items.getResource(slot);
            items.set(slot, ItemResource.EMPTY, 0);
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

    protected ContainerData menuData() {
        return menuData;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).serialize(output.child("Input" + i));
        }
        for (int i = 0; i < outputs.size(); i++) {
            outputs.get(i).serialize(output.child("Output" + i));
        }
        items.serialize(output.child("Items"));
        modules.serialize(output.child("Modules"));
        productivity.save(output);
        output.putInt("Draw", draw);
        energy.serialize(output.child("Energy"));
        output.putInt("Progress", progress);
        output.putInt("Status", status.ordinal());
        if (recipeKey != null) {
            output.putString("Recipe", recipeKey.identifier().toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < inputs.size(); i++) {
            input.child("Input" + i).ifPresent(inputs.get(i)::deserialize);
        }
        for (int i = 0; i < outputs.size(); i++) {
            input.child("Output" + i).ifPresent(outputs.get(i)::deserialize);
        }
        input.child("Items").ifPresent(items::deserialize);
        input.child("Modules").ifPresent(modules::deserialize);
        productivity.load(input);
        draw = input.getIntOr("Draw", layout.energyPerTick());
        input.child("Energy").ifPresent(energy::deserialize);
        progress = input.getIntOr("Progress", 0);
        status = ProcessingStatus.byOrdinal(input.getIntOr("Status", ProcessingStatus.NO_RECIPE.ordinal()));
        recipeKey = input.getString("Recipe")
                .map(Identifier::tryParse)
                .map(id -> ResourceKey.create(Registries.RECIPE, id))
                .orElse(null);
        // The ports are worked out again from the recipe on the first tick; what is saved is
        // the tanks and which fluid each was pointed at, which is enough for a pipe meanwhile.
        assignedFor = null;
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
