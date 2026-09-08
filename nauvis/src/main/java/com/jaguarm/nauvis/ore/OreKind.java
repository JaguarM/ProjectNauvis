package com.jaguarm.nauvis.ore;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ores a patch is made of: the three Factorio's starting area always holds a patch of, minus
 * stone, which here is every block of ground.
 */
public enum OreKind {
    IRON("iron", Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
    COPPER("copper", Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
    COAL("coal", Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);

    public final String id;
    private final Block ore;
    private final Block deepslateOre;

    OreKind(String id, Block ore, Block deepslateOre) {
        this.id = id;
        this.ore = ore;
        this.deepslateOre = deepslateOre;
    }

    /** The ore this ground becomes, or null where there is no ground: a cave, water, another ore. */
    public @Nullable BlockState oreFor(BlockState ground) {
        if (ground.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
            return deepslateOre.defaultBlockState();
        }
        if (ground.is(BlockTags.STONE_ORE_REPLACEABLES)) {
            return ore.defaultBlockState();
        }
        return null;
    }

    public boolean is(BlockState state) {
        return state.is(ore) || state.is(deepslateOre);
    }
}
