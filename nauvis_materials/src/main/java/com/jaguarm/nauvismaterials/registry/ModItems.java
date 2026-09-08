package com.jaguarm.nauvismaterials.registry;

import com.jaguarm.nauvislib.item.Stacks;
import com.jaguarm.nauvismaterials.NauvisMaterials;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    private ModItems() {}

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(NauvisMaterials.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisMaterials.MODID);

    /** Copper's first step. Two per ingot, because Factorio pays two cable per plate. */
    public static final DeferredItem<Item> COPPER_CABLE = ITEMS.registerSimpleItem("copper_cable", () -> Stacks.of(200));

    /** The mechanical intermediate: two iron, one gear. */
    public static final DeferredItem<Item> IRON_GEAR_WHEEL = ITEMS.registerSimpleItem("iron_gear_wheel", () -> Stacks.of(100));

    /** Factorio's green circuit, and the gate in front of every electric machine. */
    public static final DeferredItem<Item> ELECTRONIC_CIRCUIT = ITEMS.registerSimpleItem("electronic_circuit", () -> Stacks.of(200));

    /**
     * Five iron plates and thirty-five seconds, which is the longest craft in the mod by a factor
     * of ten and is meant to be: steel is what a Factorio base stops hand-crafting and starts
     * smelting in bulk for. An item and nothing more - it is a material, so what it does is be
     * spent by the recipes above it.
     */
    public static final DeferredItem<Item> STEEL_PLATE = ITEMS.registerSimpleItem("steel_plate", () -> Stacks.of(100));

    /** A gear, two pipes and a steel plate: what an engine unit costs, and what a car and a pump are built on. */
    public static final DeferredItem<Item> ENGINE_UNIT = ITEMS.registerSimpleItem("engine_unit", () -> Stacks.of(50));

    /**
     * The first thing oil is for. Coal and petroleum gas in a chemical plant, and the ingredient
     * that turns a green circuit into a red one.
     */
    public static final DeferredItem<Item> PLASTIC_BAR = ITEMS.registerSimpleItem("plastic_bar", () -> Stacks.of(100));

    /** Petroleum gas and water in a chemical plant; the way to sulfuric acid and to explosives. */
    public static final DeferredItem<Item> SULFUR = ITEMS.registerSimpleItem("sulfur", () -> Stacks.of(50));

    /** Sulfuric acid, an iron plate and a copper plate. Five of these are an accumulator. */
    public static final DeferredItem<Item> BATTERY = ITEMS.registerSimpleItem("battery", () -> Stacks.of(200));

    /** Factorio's red circuit: two green ones, four cable and two plastic, six seconds. */
    public static final DeferredItem<Item> ADVANCED_CIRCUIT = ITEMS.registerSimpleItem("advanced_circuit", () -> Stacks.of(200));

    /**
     * An engine unit, two circuits and fifteen lubricant, ten seconds: the first item made from a
     * fluid in an assembler rather than a chemical plant, which is why it waited on one with a
     * fluid box.
     */
    public static final DeferredItem<Item> ELECTRIC_ENGINE_UNIT = ITEMS.registerSimpleItem("electric_engine_unit", () -> Stacks.of(50));

    /**
     * Twenty circuits, two advanced circuits and five sulfuric acid, ten seconds: the other item
     * made from a fluid in an assembler, and the top of the circuit ladder the pack reaches.
     */
    public static final DeferredItem<Item> PROCESSING_UNIT = ITEMS.registerSimpleItem("processing_unit", () -> Stacks.of(200));

    /**
     * Five copper plates, five plastic and ten steel, thirty seconds: the rocket's skin, and the
     * dearest thing a rocket part is paid for in.
     */
    public static final DeferredItem<Item> LOW_DENSITY_STRUCTURE = ITEMS.registerSimpleItem("low_density_structure", () -> Stacks.of(10));

    /**
     * Ten solid fuel, thirty seconds - the dump's recipe, from before rocket fuel wanted light
     * oil as well. It burns, too: Factorio's 100 MJ is twenty-five of coal's 4, and the furnace
     * fuel data map says so.
     */
    public static final DeferredItem<Item> ROCKET_FUEL = ITEMS.registerSimpleItem("rocket_fuel", () -> Stacks.of(10));

    /** A processing unit and a speed module, thirty seconds: the third thing a rocket part costs. */
    public static final DeferredItem<Item> ROCKET_CONTROL_UNIT = ITEMS.registerSimpleItem("rocket_control_unit", () -> Stacks.of(10));

    /**
     * One iron plate makes two, in half a second. Its recipe shipped for a while before the item
     * did and failed to load every start; it is here so the recipe the concrete technology
     * unlocks names something. Rails were what it was for, and rails are out.
     */
    public static final DeferredItem<Item> IRON_STICK = ITEMS.registerSimpleItem("iron_stick", () -> Stacks.of(100));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_materials"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ELECTRONIC_CIRCUIT.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(COPPER_CABLE.get());
                        output.accept(IRON_GEAR_WHEEL.get());
                        output.accept(ELECTRONIC_CIRCUIT.get());
                        output.accept(STEEL_PLATE.get());
                        output.accept(ENGINE_UNIT.get());
                        output.accept(PLASTIC_BAR.get());
                        output.accept(SULFUR.get());
                        output.accept(BATTERY.get());
                        output.accept(ADVANCED_CIRCUIT.get());
                        output.accept(ELECTRIC_ENGINE_UNIT.get());
                        output.accept(PROCESSING_UNIT.get());
                        output.accept(LOW_DENSITY_STRUCTURE.get());
                        output.accept(ROCKET_FUEL.get());
                        output.accept(ROCKET_CONTROL_UNIT.get());
                        output.accept(IRON_STICK.get());
                    })
                    .build());
}
