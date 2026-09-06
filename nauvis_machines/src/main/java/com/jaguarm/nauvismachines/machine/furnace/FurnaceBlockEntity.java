package com.jaguarm.nauvismachines.machine.furnace;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.jaguarm.facrafting.queue.CraftListeners;
import com.jaguarm.facrafting.recipe.CraftPlanner;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.facrafting.recipe.RecipeLocks;
import com.jaguarm.facrafting.registry.ModRecipes;
import com.jaguarm.nauvismachines.machine.MachineAccess;
import com.jaguarm.nauvismachines.machine.MachinePower;
import com.jaguarm.nauvismachines.machine.PowerAccess;
import com.jaguarm.nauvismachines.registry.ModBlockEntities;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A furnace: given ore and something to burn, or electricity, it makes plates.
 *
 * <p>The difference from an assembler is that nobody tells it what to make. Factorio's furnace
 * runs the {@code smelting} category - iron, copper, steel and stone brick - and chooses among
 * them by what is put into it, so there is no recipe selector, no panel, and one input slot. The
 * recipes are Facrafting recipes carrying that category, generated from Factorio's own numbers;
 * a furnace is the one machine that runs them and the panel is the one place that never offers
 * them, which is exactly Factorio's arrangement.
 *
 * <h2>Three tiers, one entity</h2>
 *
 * <p>Stone and steel burn fuel; the electric one spends FE. Crafting speed, which kind it is and
 * what a tick costs are read off the block, so a tier is a class and this is the same entity
 * for all three. A burner spends its fuel <em>only while working</em>, which is Factorio's rule
 * and not vanilla's: a furnace with coal lit and nothing to smelt keeps the coal, and - the part
 * that matters here - has nothing to tick for, so it sleeps.
 *
 * <h2>Sleeping</h2>
 *
 * <p>Non-negotiable #5, the assembler's way: no ticker, a block tick scheduled while a smelt is
 * under way and nothing scheduled otherwise. It wakes on its inventory changing (ore, coal or a
 * plate taken away), on a neighbour changing, and on electricity arriving.
 *
 * <h2>What it reports</h2>
 *
 * <p>Every plate it finishes is announced through Facrafting's machine listener, because "craft
 * fifty iron plates" is Factorio's first technology and iron plates are never crafted by hand.
 * And before it runs a recipe it asks Facrafting's lock whether the world has researched it, so
 * a furnace fed steel's five plates before steel processing simply waits.
 */
public class FurnaceBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final int SLOT_COUNT = 3;

    /** The recipe category a furnace runs. Factorio's name for it, written by the generator. */
    public static final String SMELTING = "smelting";

    /** How many ticks of a buffer an electric furnace carries: five seconds of its own draw. */
    private static final int BUFFER_TICKS = 100;

    /** Why the furnace is doing what it is doing. Read by the screen and by the hover readout. */
    public enum Status {
        /** Nothing in the input slot. */
        IDLE,
        /** A smelt is under way. */
        SMELTING,
        /** Something is in the input slot and no recipe here can take it - or none is researched yet. */
        CANNOT_SMELT,
        /** The recipe wants more than is in the slot: steel is five plates at once. */
        WAITING,
        /** The output slot has no room for the result. */
        OUTPUT_FULL,
        /** A burner with nothing to burn. */
        NO_FUEL,
        /** An electric furnace with an empty buffer. */
        NO_POWER;

        public static Status of(int ordinal) {
            Status[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
        }
    }

    private final float craftingSpeed;
    private final boolean burner;
    private final int energyPerTick;

    private final FurnaceInventory inventory;

    /** What hoppers and inserters see: ore and fuel in, plates out. Never the raw inventory. */
    private final ResourceHandler<ItemResource> automationView;

    /** An electric furnace's buffer, unrestricted because the machine spends from it. Null for a burner. */
    private final @Nullable MachinePower energy;

    /** Insert only, for the grid. Null for a burner, which no pole should think it supplies. */
    private final @Nullable EnergyHandler gridView;

    /**
     * What is being smelted, by key. Chosen from the input rather than by anyone, and kept so
     * the screen can name it and so a smelt in progress survives a reload.
     */
    private @Nullable ResourceKey<Recipe<?>> recipeKey;

    private int progress;
    private int craftTicks;

    /** Ticks of fuel left in a burner, and what the last item was worth, for the flame. */
    private int burnTime;
    private int burnTimeTotal;

    private Status status = Status.IDLE;

    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case FurnaceMenu.DATA_PROGRESS -> progress;
                case FurnaceMenu.DATA_CRAFT_TICKS -> craftTicks;
                case FurnaceMenu.DATA_BURN_TIME -> burnTime;
                case FurnaceMenu.DATA_BURN_TIME_TOTAL -> burnTimeTotal;
                case FurnaceMenu.DATA_ENERGY -> energyStored();
                case FurnaceMenu.DATA_ENERGY_PER_TICK -> energyPerTick;
                case FurnaceMenu.DATA_ENERGY_CAPACITY -> energyCapacity();
                case FurnaceMenu.DATA_STATUS -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            // Server-authoritative: the client is told, never asked.
        }

        @Override
        public int getCount() {
            return FurnaceMenu.DATA_COUNT;
        }
    };

    public FurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FURNACE.get(), pos, state);
        // The block is the tier. A block entity built for a state that is not one of ours gets
        // the stone furnace's numbers rather than a crash.
        FurnaceBlock tier = state.getBlock() instanceof FurnaceBlock block ? block : null;
        craftingSpeed = tier == null ? StoneFurnaceBlock.CRAFTING_SPEED : tier.craftingSpeed();
        burner = tier == null || tier.isBurner();
        energyPerTick = tier == null ? 0 : tier.energyPerTick();

        inventory = new FurnaceInventory(this::onInventoryChanged, this::smeltable,
                burner ? this::burnable : resource -> false);
        automationView = new MachineAccess(inventory, OUTPUT_SLOT);

        if (burner) {
            energy = null;
            gridView = null;
        } else {
            energy = new MachinePower(energyPerTick * BUFFER_TICKS, this::onPowerChanged);
            gridView = new PowerAccess(energy);
        }
    }

    public float craftingSpeed() {
        return craftingSpeed;
    }

    public boolean isBurner() {
        return burner;
    }

    public int energyPerTick() {
        return energyPerTick;
    }

    public int energyCapacity() {
        return energyPerTick * BUFFER_TICKS;
    }

    public int energyStored() {
        return energy == null ? 0 : energy.getAmountAsInt();
    }

    public FurnaceInventory inventory() {
        return inventory;
    }

    public ResourceHandler<ItemResource> automationView() {
        return automationView;
    }

    /** What a power pole fills, or null for a burner. Registered as {@code Capabilities.Energy.BLOCK}. */
    public @Nullable EnergyHandler gridView() {
        return gridView;
    }

    public @Nullable ResourceKey<Recipe<?>> recipeKey() {
        return recipeKey;
    }

    public int progress() {
        return progress;
    }

    public int burnTime() {
        return burnTime;
    }

    public Status status() {
        return status;
    }

    /** Factorio's rule, the assembler's arithmetic: the recipe's time over the machine's speed. */
    public static int craftTicksFor(FacraftRecipe recipe, float craftingSpeed) {
        return Math.max(1, Math.round(recipe.craftTicks() / craftingSpeed));
    }

    /**
     * The smelting recipe that takes this item, if the pack has one and the world may run it.
     *
     * <p>A walk over the timed recipes rather than a lookup, because Facrafting keys recipes by
     * result and a furnace is asked by ingredient. Two hundred string compares, once per craft
     * start and once per insertion attempt; a base of a thousand furnaces spends less on this in
     * a tick than on one block update.
     *
     * <p>The lock is part of the answer. Steel's recipe exists from the first tick and is
     * researched later, and a furnace that smelted it regardless would make steel processing a
     * technology that unlocks nothing.
     */
    public static @Nullable RecipeHolder<FacraftRecipe> smeltingRecipeFor(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (RecipeHolder<FacraftRecipe> holder
                : level.getServer().getRecipeManager().recipeMap().byType(ModRecipes.FACRAFT_TYPE.get())) {
            FacraftRecipe recipe = holder.value();
            if (!SMELTING.equals(recipe.category()) || recipe.ingredients().size() != 1) {
                continue;
            }
            if (!recipe.ingredients().get(0).ingredient().test(stack)) {
                continue;
            }
            if (!RecipeLocks.isUnlocked(level, holder.id())) {
                continue;
            }
            return holder;
        }
        return null;
    }

    /**
     * Whether the input slot may take this. Permissive with no level to ask - a block entity
     * deserialises before it has one, and a furnace that threw away its own ore on load would be
     * a memorable bug.
     */
    private boolean smeltable(ItemResource resource) {
        return !(level instanceof ServerLevel server) || smeltingRecipeFor(server, resource.toStack(1)) != null;
    }

    private boolean burnable(ItemResource resource) {
        return level == null || resource.toStack(1).getBurnTime(null, level.fuelValues()) > 0;
    }

    /** Called by {@link FurnaceBlock}, and only ever on a tick this furnace asked for. */
    public void serverTick(ServerLevel level) {
        ItemStack input = inventory.getResource(INPUT_SLOT).toStack(inventory.getAmountAsInt(INPUT_SLOT));
        RecipeHolder<FacraftRecipe> holder = currentRecipe(level, input);
        if (holder == null) {
            // Nothing to make, or nothing here can make it. Either way there is nothing to
            // schedule for: the input slot changing is what wakes it.
            progress = 0;
            setRecipe(null);
            settle(level, input.isEmpty() ? Status.IDLE : Status.CANNOT_SMELT);
            return;
        }

        FacraftRecipe recipe = holder.value();
        if (!holder.id().equals(recipeKey)) {
            progress = 0;
            setRecipe(holder.id());
        }
        craftTicks = craftTicksFor(recipe, craftingSpeed);

        // The ingredients are checked once, as a smelt starts - counting down is the cheap part.
        if (progress == 0) {
            Status blocked = smelt(recipe, false);
            if (blocked != null) {
                settle(level, blocked);
                return;
            }
        }

        if (!spendable(level)) {
            // Out of fuel or out of charge, holding the smelt where it stands. Coal arriving in
            // the slot or electricity arriving in the buffer is what wakes it.
            settle(level, burner ? Status.NO_FUEL : Status.NO_POWER);
            return;
        }

        progress++;
        spend();

        if (progress >= craftTicks) {
            Status blocked = smelt(recipe, true);
            if (blocked != null) {
                // Finished but unpaid: the output filled up, or the ore was taken back out.
                settle(level, blocked);
                return;
            }
            progress = 0;
            CraftListeners.fireMachine(level, recipe.result().create());
        }

        status = Status.SMELTING;
        setLit(level, true);
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * The recipe for what is in the input slot: the one already chosen if it still fits, else
     * a fresh look. Re-resolving by key is one map lookup; the walk only happens when the input
     * changes to something else, which a single slot can only do by emptying first.
     */
    private @Nullable RecipeHolder<FacraftRecipe> currentRecipe(ServerLevel level, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        if (recipeKey != null) {
            RecipeHolder<?> held = level.getServer().getRecipeManager().byKey(recipeKey).orElse(null);
            if (held != null && held.value() instanceof FacraftRecipe recipe
                    && SMELTING.equals(recipe.category())
                    && recipe.ingredients().size() == 1
                    && recipe.ingredients().get(0).ingredient().test(input)
                    && RecipeLocks.isUnlocked(level, held.id())) {
                @SuppressWarnings("unchecked")
                RecipeHolder<FacraftRecipe> same = (RecipeHolder<FacraftRecipe>) held;
                return same;
            }
        }
        return smeltingRecipeFor(level, input);
    }

    /**
     * Pays for one smelt out of the input slot and banks the result, all or nothing.
     *
     * @param commit false to ask whether it is possible without doing it.
     * @return null when the smelt was, or would have been, paid for in full; otherwise why not.
     */
    private @Nullable Status smelt(FacraftRecipe recipe, boolean commit) {
        // The input slot alone: a furnace must not pay for a smelt out of its own output, and a
        // burner must not smelt its coal.
        List<ItemStack> inputs = inventory.copyToList().subList(INPUT_SLOT, INPUT_SLOT + 1);
        Int2IntMap plan = CraftPlanner.plan(inputs, recipe);
        if (plan == null) {
            return Status.WAITING;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            for (Int2IntMap.Entry entry : plan.int2IntEntrySet()) {
                int slot = INPUT_SLOT + entry.getIntKey();
                int amount = entry.getIntValue();
                if (inventory.extract(slot, inventory.getResource(slot), amount, transaction) != amount) {
                    return Status.WAITING;
                }
            }

            ItemStack result = recipe.result().create();
            int stored = inventory.insert(OUTPUT_SLOT, ItemResource.of(result), result.getCount(), transaction);
            if (stored != result.getCount()) {
                return Status.OUTPUT_FULL;
            }

            if (commit) {
                transaction.commit();
            }
            return null;
        }
    }

    /** Whether there is something to pay this tick with: burning fuel, or charge. */
    private boolean spendable(ServerLevel level) {
        if (burner) {
            return burnTime > 0 || refuel(level);
        }
        return energy != null && energy.getAmountAsInt() >= energyPerTick;
    }

    private void spend() {
        if (burner) {
            burnTime--;
        } else if (energy != null) {
            energy.set(energy.getAmountAsInt() - energyPerTick);
        }
    }

    /** Burns one item of fuel. @return whether there is now fuel to spend. */
    private boolean refuel(ServerLevel level) {
        ItemResource candidate = inventory.getResource(FUEL_SLOT);
        if (candidate.isEmpty()) {
            return false;
        }
        int worth = candidate.toStack(1).getBurnTime(null, level.fuelValues());
        if (worth <= 0) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (inventory.extract(FUEL_SLOT, candidate, 1, transaction) != 1) {
                return false;
            }
            transaction.commit();
        }
        burnTime = worth;
        burnTimeTotal = worth;
        setChanged();
        return true;
    }

    /** Stops, for the given reason, keeping whatever progress was made. Nothing is scheduled. */
    private void settle(ServerLevel level, Status why) {
        status = why;
        setLit(level, false);
        setChanged();
    }

    /**
     * Turns the fire on or off across the whole machine.
     *
     * <p>Every cell carries {@code lit}, so every cell is set - five or ten block updates, on a
     * transition rather than a tick. Clients only and the shape left alone: nothing structural has
     * changed, so the neighbours need no shape update and no inserter needs waking for it.
     */
    private void setLit(ServerLevel level, boolean lit) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FurnaceBlock block) || state.getValue(FurnaceBlock.LIT) == lit) {
            return;
        }
        for (BlockPos pos : block.shape().positions(worldPosition, block.facing(state))) {
            BlockState cell = level.getBlockState(pos);
            if (cell.getBlock() == block && cell.getValue(FurnaceBlock.LIT) != lit) {
                level.setBlock(pos, cell.setValue(FurnaceBlock.LIT, lit),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    /** Records what is being smelted and tells the clients watching, who name it on the screen. */
    private void setRecipe(@Nullable ResourceKey<Recipe<?>> key) {
        if (key == null ? recipeKey == null : key.equals(recipeKey)) {
            return;
        }
        recipeKey = key;
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
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
        wake();
    }

    /** Electricity arrived, or was spent. The wake is the half that matters; see the assembler. */
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
        return new FurnaceMenu(containerId, playerInventory, inventory, menuData, worldPosition, burner);
    }

    /** Spilled when the machine is broken. See the assembler: this is the hook, and not the block's. */
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
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("Inventory"));
        if (energy != null) {
            energy.serialize(output.child("Energy"));
        }
        output.putInt("Progress", progress);
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnTimeTotal", burnTimeTotal);
        if (recipeKey != null) {
            output.putString("Recipe", recipeKey.identifier().toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Inventory").ifPresent(inventory::deserialize);
        if (energy != null) {
            input.child("Energy").ifPresent(energy::deserialize);
        }
        progress = input.getIntOr("Progress", 0);
        burnTime = input.getIntOr("BurnTime", 0);
        burnTimeTotal = input.getIntOr("BurnTimeTotal", 0);
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
