package com.jaguarm.nauvislib.module;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/** A machine's module slots: so many, one module in each, and the sum of what they do. */
public class ModuleSlots extends ItemStacksResourceHandler {

    private final Runnable onChanged;
    private final Predicate<Module> allowed;

    /** Slots that take any module. */
    public ModuleSlots(int count, Runnable onChanged) {
        this(count, onChanged, module -> true);
    }

    /**
     * @param allowed a further rule on which modules this machine takes, asked of every module
     *                offered. Factorio's productivity restriction is the only one there is.
     */
    public ModuleSlots(int count, Runnable onChanged, Predicate<Module> allowed) {
        super(count);
        this.onChanged = onChanged;
        this.allowed = allowed;
    }

    /** The module a stack is, or null for anything else. */
    public static Module moduleOf(ItemResource resource) {
        return resource.getItem() instanceof Module module ? module : null;
    }

    public static Module moduleOf(ItemStack stack) {
        return stack.getItem() instanceof Module module ? module : null;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        Module module = moduleOf(resource);
        return module != null && allowed.test(module);
    }

    /** One module a slot. */
    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }

    /** Everything in the slots, added up. {@link ModuleEffect#NONE} for an empty machine. */
    public ModuleEffect effect() {
        ModuleEffect total = ModuleEffect.NONE;
        for (int slot = 0; slot < size(); slot++) {
            if (getAmountAsInt(slot) <= 0) {
                continue;
            }
            Module module = moduleOf(getResource(slot));
            if (module != null) {
                total = total.plus(module.effect());
            }
        }
        return total;
    }

    /** Whether any module in here would be refused by a rule, for a machine whose rule has changed. */
    public boolean holdsAnyRefusedBy(Predicate<Module> rule) {
        for (int slot = 0; slot < size(); slot++) {
            if (getAmountAsInt(slot) <= 0) {
                continue;
            }
            Module module = moduleOf(getResource(slot));
            if (module != null && !rule.test(module)) {
                return true;
            }
        }
        return false;
    }
}
