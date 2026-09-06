package com.jaguarm.nauvismachines.module;

import java.util.function.Consumer;

import com.jaguarm.nauvislib.module.Module;
import com.jaguarm.nauvislib.module.ModuleEffect;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A module: an item that changes the machine it is put in, for as long as it is in there.
 *
 * <p>Factorio's first tier, at Factorio 2.0's numbers. A <b>speed module</b> is a fifth faster for
 * half again the power; an <b>efficiency module</b> is three tenths cheaper to run and nothing
 * else; a <b>productivity module</b> banks a free craft every twenty-five, and pays for it with
 * a twentieth of the speed and two fifths more power. The effects add across a machine's slots,
 * and {@code nauvis_lib}'s {@link ModuleEffect} holds the floors.
 *
 * <p>The item is this mod's because {@code PLAN.md} gives the modules to the machines mod; the
 * {@link Module} interface it implements is the library's, so every other machine mod's slots
 * take it without naming this one. The tooltip says what it does in the same words the machine
 * screens use, one line per effect that is not zero.
 */
public class ModuleItem extends Item implements Module {

    /** Speed module 1: +20% speed, +50% energy. */
    public static final ModuleEffect SPEED = new ModuleEffect(0.2, 0.5, 0);

    /** Efficiency module 1: -30% energy. */
    public static final ModuleEffect EFFICIENCY = new ModuleEffect(0, -0.3, 0);

    /** Productivity module 1: +4% productivity, -5% speed, +40% energy. */
    public static final ModuleEffect PRODUCTIVITY = new ModuleEffect(-0.05, 0.4, 0.04);

    private final ModuleEffect effect;

    public ModuleItem(Properties properties, ModuleEffect effect) {
        super(properties);
        this.effect = effect;
    }

    @Override
    public ModuleEffect effect() {
        return effect;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> adder, TooltipFlag flag) {
        line(adder, "speed", effect.speed());
        line(adder, "energy", effect.energy());
        line(adder, "productivity", effect.productivity());
    }

    private static void line(Consumer<Component> adder, String what, double amount) {
        if (amount == 0) {
            return;
        }
        String percent = (amount > 0 ? "+" : "") + Math.round(amount * 100) + "%";
        adder.accept(Component.translatable("tooltip.nauvis_machines.module." + what, percent)
                .withStyle(amount > 0 == what.equals("energy") ? ChatFormatting.RED : ChatFormatting.GREEN));
    }
}
