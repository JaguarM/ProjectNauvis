package com.jaguarm.nauvismining.machine;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/** A machine module. See {@link ModuleType} for what each one trades away. */
public class ModuleItem extends Item {

    private final ModuleType type;

    public ModuleItem(Properties properties, ModuleType type) {
        super(properties);
        this.type = type;
    }

    public ModuleType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("tooltip.nauvis_mining.module." + type.id())
                .withStyle(ChatFormatting.GRAY));
        adder.accept(Component.translatable("tooltip.nauvis_mining.module.slots")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The module type of a stack, or null if it is not a module. */
    public static @Nullable ModuleType typeOf(ItemStack stack) {
        return stack.getItem() instanceof ModuleItem module ? module.type() : null;
    }
}
