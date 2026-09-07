package com.jaguarm.nauvisrocket.registry;

import com.jaguarm.nauvisrocket.NauvisRocket;
import com.jaguarm.nauvisrocket.silo.RocketSiloBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NauvisRocket.MODID);

    /**
     * The rocket silo: nine tiles by nine, the size Factorio made it. A thousand concrete, a
     * thousand steel, two hundred processing units, two hundred electric engine units and a
     * hundred pipes, behind {@code rocket-silo} and every science pack the pack makes.
     *
     * <p>No facing. A Factorio silo has one way round and it is not the player's to choose.
     */
    public static final DeferredBlock<RocketSiloBlock> ROCKET_SILO = BLOCKS.registerBlock(
            "rocket_silo",
            RocketSiloBlock::new,
            properties -> properties
                    .mapColor(MapColor.STONE)
                    .strength(4.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}
}
