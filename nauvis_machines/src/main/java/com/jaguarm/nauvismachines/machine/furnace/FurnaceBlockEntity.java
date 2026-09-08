package com.jaguarm.nauvismachines.machine.furnace;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvislib.module.ModuleEffect;
import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.module.Productivity;
import com.jaguarm.nauvislib.transfer.PowerAccess;
import com.jaguarm.nauvislib.transfer.MachinePower;
import com.jaguarm.nauvislib.transfer.MachineAccess;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.jaguarm.facrafting.queue.CraftListeners;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.facrafting.recipe.RecipeLocks;
import com.jaguarm.facrafting.registry.ModRecipes;
import com.jaguarm.nauvismachines.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** A furnace: given ore and something to burn, or electricity, it makes plates. */
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

    /**
     * One thing this furnace could do with what is in it, whichever kind of recipe it came from:
     * what it takes and how many, what it makes, and how long the recipe says, before the
     * machine's own speed is applied.
     */
    public record Smelt(ResourceKey<Recipe<?>> key, Ingredient input, int count, ItemStack result, int ticks) {}

    private final float craftingSpeed;
    private final double pollutionPerMinute;
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
     * What is being smelted, by key. Chosen from the input rather than by anyone, and kept so a
     * smelt in progress survives a reload and is not re-resolved every tick.
     */
    private @Nullable ResourceKey<Recipe<?>> recipeKey;

    /**
     * What the current recipe makes, for the screen. Synced on its own because a client cannot
     * resolve a vanilla recipe key into an item and is not sent every recipe in the game.
     */
    private @Nullable Item making;

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
                case FurnaceMenu.DATA_ENERGY_PER_TICK -> currentEnergyPerTick();
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
        pollutionPerMinute = tier == null ? StoneFurnaceBlock.POLLUTION_PER_MINUTE : tier.pollutionPerMinute();
        burner = tier == null || tier.isBurner();
        energyPerTick = tier == null ? 0 : tier.energyPerTick();

        inventory = new FurnaceInventory(this::onInventoryChanged, this::smeltable,
                burner ? this::burnable : resource -> false);
        automationView = new MachineAccess(inventory, OUTPUT_SLOT);
        // Every smelting recipe makes an intermediate product, so every module is welcome here.
        modules = new ModuleSlots(tier == null ? 0 : tier.moduleSlots(), this::onInventoryChanged);
        draw = energyPerTick;

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

    /**
     * The tier's module slots - none on the burner furnaces, two on the electric one - and the
     * free smelt they work towards. Read once a smelt, as it starts, like the assembler's.
     */
    private final ModuleSlots modules;
    private final Productivity productivity = new Productivity();

    /** The draw under the modules read at the start of the smelt in progress. */
    private int draw;

    public ModuleSlots modules() {
        return modules;
    }

    public Productivity productivity() {
        return productivity;
    }

    /** The tier's draw under the modules in it right now, for the screen. */
    public int currentEnergyPerTick() {
        return modules.effect().scaleEnergy(energyPerTick);
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

    /** What the current recipe makes, or null when there is none. Synced, so the screen can name it. */
    public @Nullable Item making() {
        return making;
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
    public static int craftTicksFor(Smelt smelt, float craftingSpeed) {
        return Math.max(1, Math.round(smelt.ticks() / craftingSpeed));
    }

    /**
     * What a furnace here would do with this item, if anything: Factorio's smelting recipe for
     * it, or vanilla's furnace recipe where Factorio has none.
     */
    public static @Nullable Smelt smeltingRecipeFor(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (RecipeHolder<FacraftRecipe> holder
                : level.getServer().getRecipeManager().recipeMap().byType(ModRecipes.FACRAFT_TYPE.get())) {
            Smelt smelt = smeltOf(holder, stack, level);
            if (smelt != null) {
                return smelt;
            }
        }
        return level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)
                .map(holder -> smeltOf(holder, stack, level))
                .orElse(null);
    }

    /** This recipe as a smelt of {@code input}, or null when it is not one or does not take it. */
    private static @Nullable Smelt smeltOf(RecipeHolder<?> holder, ItemStack input, ServerLevel level) {
        Recipe<?> recipe = holder.value();
        if (recipe instanceof FacraftRecipe facraft) {
            if (!SMELTING.equals(facraft.category()) || facraft.ingredients().size() != 1) {
                return null;
            }
            SizedIngredient wanted = facraft.ingredients().get(0);
            if (!wanted.ingredient().test(input) || !RecipeLocks.isUnlocked(level, holder.id())) {
                return null;
            }
            return new Smelt(holder.id(), wanted.ingredient(), wanted.count(), facraft.resultStack(),
                    facraft.craftTicks());
        }
        if (recipe instanceof SmeltingRecipe smelting) {
            // Only the furnace's own kind: a blast furnace's or a smoker's recipe is a different
            // machine's, however alike the input.
            if (!smelting.input().test(input) || !RecipeLocks.isUnlocked(level, holder.id())) {
                return null;
            }
            // assemble rather than result(): vanilla keeps a cooking recipe's result protected.
            return new Smelt(holder.id(), smelting.input(), 1, smelting.assemble(new SingleRecipeInput(input)),
                    smelting.cookingTime());
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
        Smelt smelt = currentSmelt(level, input);
        if (smelt == null) {
            // Nothing to make, or nothing here can make it. Either way there is nothing to
            // schedule for: the input slot changing is what wakes it.
            progress = 0;
            setSmelt(null);
            settle(level, input.isEmpty() ? Status.IDLE : Status.CANNOT_SMELT);
            return;
        }

        if (!smelt.key().equals(recipeKey)) {
            progress = 0;
        }
        setSmelt(smelt);

        // The modules are read as the smelt starts and hold for the smelt, which is Factorio's rule.
        if (progress == 0) {
            ModuleEffect effect = modules.effect();
            craftTicks = craftTicksFor(smelt, (float) (craftingSpeed * effect.speedFactor()));
            draw = effect.scaleEnergy(energyPerTick);
        }

        // The ingredients are checked once, as a smelt starts - counting down is the cheap part.
        if (progress == 0) {
            Status blocked = smelt(smelt, false);
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
        Pollution.emitTick(level, worldPosition, pollutionPerMinute, modules.effect().energyFactor());

        if (progress >= craftTicks) {
            Status blocked = smelt(smelt, true);
            if (blocked != null) {
                // Finished but unpaid: the output filled up, or the ore was taken back out.
                settle(level, blocked);
                return;
            }
            progress = 0;
            CraftListeners.fireMachine(level, smelt.result().copy());

            // Productivity: every smelt earns a fraction of a free one; a whole one is banked
            // unpaid when it is owed and there is room, and stays owed until there is.
            productivity.earn(modules.effect().productivityBonus());
            if (productivity.owed() && bankFree(smelt)) {
                productivity.pay();
                CraftListeners.fireMachine(level, smelt.result().copy());
            }
        }

        status = Status.SMELTING;
        setLit(level, true);
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * The smelt for what is in the input slot: the one already chosen if it still fits, else a
     * fresh look. Re-resolving by key is one map lookup; the walk only happens when the input
     * changes to something else, which a single slot can only do by emptying first.
     */
    private @Nullable Smelt currentSmelt(ServerLevel level, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        if (recipeKey != null) {
            RecipeHolder<?> held = level.getServer().getRecipeManager().byKey(recipeKey).orElse(null);
            Smelt same = held == null ? null : smeltOf(held, input, level);
            if (same != null) {
                return same;
            }
        }
        return smeltingRecipeFor(level, input);
    }

    /**
     * Pays for one smelt out of the input slot and banks the result, all or nothing.
     *
     * <p>The input slot alone: a furnace must not pay for a smelt out of its own output, and a
     * burner must not smelt its coal.
     *
     * @param commit false to ask whether it is possible without doing it.
     * @return null when the smelt was, or would have been, paid for in full; otherwise why not.
     */
    private @Nullable Status smelt(Smelt smelt, boolean commit) {
        ItemResource resource = inventory.getResource(INPUT_SLOT);
        if (resource.isEmpty() || !smelt.input().test(resource.toStack(1))
                || inventory.getAmountAsInt(INPUT_SLOT) < smelt.count()) {
            return Status.WAITING;
        }

        try (Transaction transaction = Transaction.openRoot()) {
            if (inventory.extract(INPUT_SLOT, resource, smelt.count(), transaction) != smelt.count()) {
                return Status.WAITING;
            }

            ItemStack result = smelt.result();
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

    /** Banks one more result without paying for it: the productivity bonus. Nothing if it will not fit. */
    private boolean bankFree(Smelt smelt) {
        ItemStack result = smelt.result();
        try (Transaction transaction = Transaction.openRoot()) {
            if (inventory.insert(OUTPUT_SLOT, ItemResource.of(result), result.getCount(), transaction)
                    != result.getCount()) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    /** Whether there is something to pay this tick with: burning fuel, or charge. */
    private boolean spendable(ServerLevel level) {
        if (burner) {
            return burnTime > 0 || refuel(level);
        }
        return energy != null && energy.getAmountAsInt() >= draw;
    }

    private void spend() {
        if (burner) {
            burnTime--;
        } else if (energy != null) {
            energy.set(energy.getAmountAsInt() - draw);
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
    private void setSmelt(@Nullable Smelt smelt) {
        ResourceKey<Recipe<?>> key = smelt == null ? null : smelt.key();
        Item item = smelt == null ? null : smelt.result().getItem();
        if (Objects.equals(key, recipeKey) && making == item) {
            return;
        }
        recipeKey = key;
        making = item;
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
        Multiblock.announce(level, worldPosition, getBlockState());
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
        return new FurnaceMenu(containerId, playerInventory, inventory, modules, menuData, worldPosition, burner);
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
        output.putInt("Draw", draw);
        if (energy != null) {
            energy.serialize(output.child("Energy"));
        }
        output.putInt("Progress", progress);
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnTimeTotal", burnTimeTotal);
        if (recipeKey != null) {
            output.putString("Recipe", recipeKey.identifier().toString());
        }
        if (making != null) {
            output.putString("Making", BuiltInRegistries.ITEM.getKey(making).toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Inventory").ifPresent(inventory::deserialize);
        input.child("Modules").ifPresent(modules::deserialize);
        productivity.load(input);
        draw = input.getIntOr("Draw", energyPerTick);
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
        making = input.getString("Making")
                .map(Identifier::tryParse)
                .map(BuiltInRegistries.ITEM::getValue)
                .filter(item -> item != Items.AIR)
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
