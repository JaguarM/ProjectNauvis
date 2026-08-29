package com.jaguarm.nauvislogistics.storage;

import com.jaguarm.nauvislogistics.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Fifty-four slots against Factorio's forty-eight, for exactly the reason the iron chest has
 * thirty-six against its thirty-two: 54 is 9x6, vanilla's largest chest screen, and stopping at 48
 * would have cost a menu and a screen to end up six slots worse.
 *
 * <p>The gap to the iron chest is what matters and it is kept - Factorio's is a half again, ours
 * is a half again. Eight steel plates, which is eight times five iron plates plus the smelting,
 * so it is a real step rather than a cheaper way to the same box.
 */
public class SteelChestBlock extends MetalChestBlock {

    public static final MapCodec<SteelChestBlock> CODEC = simpleCodec(SteelChestBlock::new);

    public static final int ROWS = 6;

    public SteelChestBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public int rows() {
        return ROWS;
    }

    @Override
    protected BlockEntityType<MetalChestBlockEntity> type() {
        return ModBlockEntities.STEEL_CHEST.get();
    }
}
