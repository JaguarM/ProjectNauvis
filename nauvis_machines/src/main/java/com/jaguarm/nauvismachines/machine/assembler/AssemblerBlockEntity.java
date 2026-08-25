package com.jaguarm.nauvismachines.machine.assembler;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.jaguarm.facrafting.recipe.CraftPlanner;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.facrafting.registry.ModRecipes;
import com.jaguarm.nauvismachines.machine.MachineAccess;
import com.jaguarm.nauvismachines.registry.ModBlockEntities;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.transfer.ResourceHandler;
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
 * (ingredients arriving, a result being taken away), a recipe being chosen, or a neighbour
 * changing. A Factorio base is thousands of machines and most of them are idle at any moment.
 */
public class AssemblerBlockEntity extends BlockEntity {

    /** Ingredient slots. Six is the largest ingredient count in Factorio's recipe set. */
    public static final int INPUT_SLOTS = 6;

    /** Results land here. One slot: every Factorio recipe has exactly one product. */
    public static final int OUTPUT_SLOT = INPUT_SLOTS;

    public static final int SLOT_COUNT = INPUT_SLOTS + 1;

    private final AssemblerInventory inventory = new AssemblerInventory(SLOT_COUNT, this::onInventoryChanged);

    /** What hoppers, inserters and pipes see. Never the raw inventory - see {@link MachineAccess}. */
    private final ResourceHandler<ItemResource> automationView = new MachineAccess(inventory, INPUT_SLOTS);

    /**
     * The chosen recipe, by key rather than by value: recipes are reloadable data, so a recipe
     * object held across a {@code /reload} would be a stale one. Null means idle.
     */
    private @Nullable ResourceKey<Recipe<?>> recipeKey;

    /** Ticks spent on the current craft. Reaching the recipe's craft time means finished. */
    private int progress;

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLER.get(), pos, state);
    }

    public AssemblerInventory inventory() {
        return inventory;
    }

    public ResourceHandler<ItemResource> automationView() {
        return automationView;
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
        recipeKey = key;
        progress = 0;
        setChanged();
        wake();
    }

    /** Called by {@link AssemblerBlock}, and only ever on a tick this machine asked for. */
    public void serverTick(ServerLevel level) {
        FacraftRecipe recipe = recipe(level);
        if (recipe == null) {
            // No recipe, or one that no longer exists. Nothing to schedule for; choosing a
            // recipe wakes it again.
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return;
        }

        int craftTicks = recipe.craftTicks();

        // The ingredients are checked once, as a craft starts. Counting down is the cheap part;
        // simulating a whole craft every tick for every machine in a base is not.
        if (progress == 0 && !craft(recipe, false)) {
            return;
        }

        if (progress < craftTicks) {
            progress++;
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

            ItemStack result = recipe.result().create();
            int stored = inventory.insert(
                    OUTPUT_SLOT, ItemResource.of(result), result.getCount(), transaction);
            if (stored != result.getCount()) {
                return false;
            }

            if (commit) {
                transaction.commit();
            }
            return true;
        }
    }

    /**
     * Whether this stack is something the machine is currently short of, rather than a pointer
     * at what to make next.
     *
     * <p>This is what lets one click do both jobs: hand an idle machine a gear wheel and it
     * starts making gear wheels, hand a machine that is already making gear wheels an iron
     * plate and it takes the iron. Clearing the recipe first is how you re-target a machine to
     * make one of its own ingredients.
     */
    public boolean wants(ServerLevel level, ItemStack stack) {
        FacraftRecipe recipe = recipe(level);
        if (recipe == null || stack.isEmpty()) {
            return false;
        }
        for (SizedIngredient ingredient : recipe.ingredients()) {
            if (ingredient.ingredient().test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes as much of a held stack as will fit into the input slots.
     *
     * @return how many items were taken, which may be none if the inputs are full.
     */
    public int acceptFromHand(ItemStack stack) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = automationView.insert(ItemResource.of(stack), stack.getCount(), transaction);
            if (taken > 0) {
                transaction.commit();
            }
            return taken;
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
            if (holder.value().result().item().value() == item) {
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
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("Inventory"));
        output.putInt("Progress", progress);
        if (recipeKey != null) {
            output.putString("Recipe", recipeKey.identifier().toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Inventory").ifPresent(inventory::deserialize);
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
