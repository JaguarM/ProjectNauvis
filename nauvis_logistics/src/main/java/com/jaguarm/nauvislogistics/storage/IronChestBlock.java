package com.jaguarm.nauvislogistics.storage;

import com.jaguarm.nauvislogistics.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.ChestBlock;

/**
 * Thirty-six slots against Factorio's thirty-two. Slot count is behaviour, not identity - the id,
 * the eight iron plates and the half-second are the parts non-negotiable #1 governs - and 36 is
 * 9x4, which is a screen vanilla already has. Thirty-two would have cost a custom GUI to be four
 * slots worse.
 */
public class IronChestBlock extends MetalChestBlock {

    public static final MapCodec<IronChestBlock> CODEC = simpleCodec(IronChestBlock::new);

    public static final int ROWS = 4;

    public IronChestBlock(Properties properties) {
        super(ModBlockEntities.IRON_CHEST::get, properties);
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
    }

    @Override
    public int rows() {
        return ROWS;
    }
}
