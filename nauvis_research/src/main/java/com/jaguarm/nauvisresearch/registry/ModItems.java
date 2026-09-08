package com.jaguarm.nauvisresearch.registry;

import java.util.List;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.jaguarm.nauvislib.item.Stacks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NauvisResearch.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisResearch.MODID);

    public static final DeferredItem<BlockItem> LAB = ITEMS.registerSimpleBlockItem(ModBlocks.LAB, () -> Stacks.of(10));

    /**
     * Red science.
     *
     * <p>The id is the dump's, not the modern name. Factorio renamed {@code science-pack-1} to
     * {@code automation-science-pack} after 1.0 and {@code reference/factorio/recipes.json} is
     * from before that, so the whole mapping speaks the old names - see {@code docs/MAPPING.md}.
     * An id is permanent; the display name is not, and says "Automation science pack".
     */
    public static final DeferredItem<Item> AUTOMATION_SCIENCE_PACK = ITEMS.registerSimpleItem("automation_science_pack", () -> Stacks.of(200));

    /**
     * Green science, and the gate in front of the whole of milestone 3 - every technology that
     * unlocks a steel furnace, a solar panel, a medium pole or the second assembler is paid for
     * partly in this.
     */
    public static final DeferredItem<Item> LOGISTIC_SCIENCE_PACK = ITEMS.registerSimpleItem("logistic_science_pack", () -> Stacks.of(200));

    /**
     * Blue science, and the gate in front of everything the oil chain was for.
     *
     * <p>The dump's recipe - an advanced circuit, an electric mining drill and an engine unit,
     * twelve seconds - is the pre-0.17 one, and so is the id; the display name says "Chemical
     * science pack" like the other two. It is the first pack whose ingredients cannot be made
     * by hand: the advanced circuit is plastic, and plastic is a chemical plant.
     */
    public static final DeferredItem<Item> CHEMICAL_SCIENCE_PACK = ITEMS.registerSimpleItem("chemical_science_pack", () -> Stacks.of(200));

    /**
     * Military science: a piercing rounds magazine, a grenade and a gun turret make two, in ten
     * seconds - the dump's recipe. Every ingredient is nauvis_military's, so the recipe carries
     * that condition and the pack is uncraftable without it; the item exists regardless, because
     * a technology's cost has to name something.
     */
    public static final DeferredItem<Item> MILITARY_SCIENCE_PACK = ITEMS.registerSimpleItem("military_science_pack", () -> Stacks.of(200));

    /**
     * Production science: an electric engine unit and an electric furnace make two, in fourteen
     * seconds - the dump's recipe, from before the pack wanted rails. The first of the two packs
     * the rocket silo's thousand units are paid for in beyond blue.
     */
    public static final DeferredItem<Item> PRODUCTION_SCIENCE_PACK = ITEMS.registerSimpleItem("production_science_pack", () -> Stacks.of(200));

    /**
     * High tech science, which Factorio has since renamed utility science: a battery, thirty
     * copper cable, three processing units and a speed module make two, in fourteen seconds.
     * The dump's id, like the rest; the display name is the dump's too, because "utility" would
     * be a name for a different recipe.
     */
    public static final DeferredItem<Item> UTILITY_SCIENCE_PACK = ITEMS.registerSimpleItem("utility_science_pack", () -> Stacks.of(200));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "nauvis_research",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_research"))
                    .icon(() -> new ItemStack(LAB.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(LAB.get());
                        output.accept(AUTOMATION_SCIENCE_PACK.get());
                        output.accept(LOGISTIC_SCIENCE_PACK.get());
                        output.accept(CHEMICAL_SCIENCE_PACK.get());
                        output.accept(MILITARY_SCIENCE_PACK.get());
                        output.accept(PRODUCTION_SCIENCE_PACK.get());
                        output.accept(UTILITY_SCIENCE_PACK.get());
                    })
                    .build());

    /** Every block this mod registers, for the loot table provider to walk. */
    public static List<Block> blocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    private ModItems() {}
}
