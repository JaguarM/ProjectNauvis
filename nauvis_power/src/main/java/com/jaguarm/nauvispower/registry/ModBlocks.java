package com.jaguarm.nauvispower.registry;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerBlock;
import com.jaguarm.nauvispower.generator.SteamEngineBlock;
import com.jaguarm.nauvispower.grid.SmallElectricPoleBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NauvisPower.MODID);

    public static final DeferredBlock<BoilerBlock> BOILER = BLOCKS.registerBlock(
            "boiler",
            BoilerBlock::new,
            properties -> properties
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<SteamEngineBlock> STEAM_ENGINE = BLOCKS.registerBlock(
            "steam_engine",
            SteamEngineBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    /**
     * Not {@code requiresCorrectToolForDrops}: a pole is two planks and two lengths of wire, and
     * the first grid a player builds should not wait on a pickaxe.
     */
    public static final DeferredBlock<SmallElectricPoleBlock> SMALL_ELECTRIC_POLE = BLOCKS.registerBlock(
            "small_electric_pole",
            SmallElectricPoleBlock::new,
            properties -> properties
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    private ModBlocks() {}
}
