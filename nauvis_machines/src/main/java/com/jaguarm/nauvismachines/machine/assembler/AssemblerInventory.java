package com.jaguarm.nauvismachines.machine.assembler;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.StacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The assembler's slots: ingredients in the first {@link AssemblerBlockEntity#INPUT_SLOTS},
 * results after them.
 *
 * <h2>One slot per ingredient</h2>
 *
 * <p>Factorio's assembler has a slot for each ingredient of its recipe and nothing else goes in,
 * which is what keeps an inserter from filling the machine with the first thing it picks up.
 * Slot {@code i} here takes the recipe's ingredient {@code i} and refuses the rest, asked of the
 * block entity through {@code wanted}; a machine with no recipe takes nothing. The first version
 * let anything into any slot, and a belt of plates filled all six and stalled the line - which
 * is exactly the Factorio rule being for something.
 *
 * <p>And a slot holds what its recipe wants: up to twice what a craft needs of that ingredient,
 * however many that is, or the item's stack size if that is more. That is what lets a rocket
 * silo be built at all - it is a thousand concrete and a thousand steel, and a slot that stopped
 * at a stack could never hold a craft's worth of either.
 *
 * <p>A stack of a thousand does not fit vanilla's stack codec, which caps a count at ninety-nine
 * while saving, so these slots are saved as a resource and an amount instead - and read back
 * from either form, so an assembler saved before this rule loads with what it had.
 *
 * <p>Six input slots is not a round number. Six is the most ingredients any of Factorio's 214
 * recipes asks for - the satellite - so six slots can always hold one craft's worth.
 */
public class AssemblerInventory extends StacksResourceHandler<ItemStack, ItemResource> {

    /** A stack as a resource and an amount, with no ceiling on the amount. */
    private static final Codec<ItemStack> BIG_STACK = RecordCodecBuilder.create(i -> i.group(
            ItemResource.CODEC.fieldOf("resource").forGetter(ItemResource::of),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(ItemStack::getCount))
            .apply(i, ItemResource::toStack));

    /**
     * What a slot is saved as, and what it reads: the form above, or vanilla's for an inventory
     * saved before it existed. An empty slot is an empty map either way.
     */
    static final Codec<ItemStack> SLOT_CODEC = ExtraCodecs.optionalEmptyMap(
            Codec.withAlternative(BIG_STACK, ItemStack.CODEC))
            .xmap(stack -> stack.orElse(ItemStack.EMPTY),
                    stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));

    /** How many of a resource one craft takes at this slot: the recipe's ingredient there, or zero. */
    public interface Wants {
        int of(int slot, ItemResource resource);
    }

    private final Runnable onChanged;
    private final Wants wanted;

    /**
     * @param wanted how many of a resource one craft of the chosen recipe takes in this slot, or
     *               zero for a resource that is not the slot's ingredient - or for no recipe at all
     */
    public AssemblerInventory(int size, Runnable onChanged, Wants wanted) {
        super(size, ItemStack.EMPTY, SLOT_CODEC);
        this.onChanged = onChanged;
        this.wanted = wanted;
    }

    /** An input slot takes its recipe's ingredient and nothing else; the output takes what the machine puts there. */
    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (index >= AssemblerBlockEntity.INPUT_SLOTS) {
            return super.isValid(index, resource);
        }
        return !resource.isEmpty() && wanted.of(index, resource) > 0;
    }

    @Override
    public ItemResource getResourceFrom(ItemStack stack) {
        return ItemResource.of(stack);
    }

    @Override
    public int getAmountFrom(ItemStack stack) {
        return stack.getCount();
    }

    @Override
    protected ItemStack getStackFrom(ItemResource resource, int amount) {
        return resource.toStack(amount);
    }

    @Override
    protected ItemStack copyOf(ItemStack stack) {
        return stack.copy();
    }

    @Override
    public boolean matches(ItemStack stack, ItemResource resource) {
        return resource.matches(stack);
    }

    /**
     * Factorio's rule for an input slot; the item's own stack size for the output, which is what
     * the thing taking from it expects.
     */
    @Override
    protected int getCapacity(int index, ItemResource resource) {
        int stackSize = resource.isEmpty() ? Item.ABSOLUTE_MAX_STACK_SIZE : resource.getMaxStackSize();
        if (index >= AssemblerBlockEntity.INPUT_SLOTS || resource.isEmpty()) {
            return stackSize;
        }
        return Math.max(stackSize, 2 * wanted.of(index, resource));
    }

    /**
     * Every change is a reason to wake up: ingredients arriving can start a craft, and a
     * result being taken away can unblock one. See {@link AssemblerBlockEntity} on sleeping.
     */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
