package com.jaguarm.nauvis;

import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What the pack mod puts in the world.
 *
 * <p>Empty, for now, and deliberately so. The assembling machine started here and has moved to
 * {@code nauvis_machines}, because {@code data/mapping.json} names it
 * {@code nauvis_machines:assembling_machine_1} and non-negotiable #1 makes an id permanent from
 * the first commit - a block registered under the wrong namespace is exactly the kind of
 * mistake that survives into world saves.
 *
 * <p>What belongs here is what PLAN.md gives the pack mod and nothing else: the sixteen raw
 * resources the other mods build on, and terrain. Those arrive with milestone 3. The registries
 * stay wired up so that adding one is a single line rather than a round of plumbing.
 *
 * <p>{@code ../nauvis_machines/.../data/} is the worked example of datagen for models, language
 * and loot tables; copy that shape when the first item lands here.
 */
public final class ModContent {

    private ModContent() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Nauvis.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Nauvis.MODID);

    /** Every block this mod registers, for a loot table provider to walk. */
    public static java.util.List<Block> blocks() {
        return BLOCKS.getEntries().stream().map(holder -> (Block) holder.value()).toList();
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
