package com.jaguarm.nauvispower.registry;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerBlock;
import com.jaguarm.nauvispower.generator.SolarPanelBlock;
import com.jaguarm.nauvispower.generator.SteamEngineBlock;
import com.jaguarm.nauvispower.grid.BigElectricPoleBlock;
import com.jaguarm.nauvispower.grid.MediumElectricPoleBlock;
import com.jaguarm.nauvispower.grid.SubstationBlock;
import com.jaguarm.nauvispower.grid.SmallElectricPoleBlock;
import com.jaguarm.nauvispower.storage.AccumulatorBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
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
     * Three blocks tall - see {@link ElectricPoleBlock}.
     *
     * <p>Not {@code requiresCorrectToolForDrops}: a pole is two planks and two lengths of wire,
     * and the first grid a player builds should not wait on a pickaxe.
     *
     * <p>Pistons are refused outright. A piston can only ever move part of a pole, and the pole
     * would answer by deleting itself - correct, and a baffling thing to watch happen. Immersive
     * Engineering blocks pushing on its posts for the same reason.
     */
    public static final DeferredBlock<SmallElectricPoleBlock> SMALL_ELECTRIC_POLE = BLOCKS.registerBlock(
            "small_electric_pole",
            SmallElectricPoleBlock::new,
            properties -> properties
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion());

    /**
     * Reaching nine blocks instead of seven and a half, a block taller, and in steel.
     *
     * <p>Behind {@code electric-energy-distribution-1}, which is behind steel and green science,
     * so a player meets it once their first grid has grown past the point where the small pole's
     * spacing is a nuisance. Not {@code requiresCorrectToolForDrops}, for the same reason the
     * small one is not.
     */
    public static final DeferredBlock<MediumElectricPoleBlock> MEDIUM_ELECTRIC_POLE = BLOCKS.registerBlock(
            "medium_electric_pole",
            MediumElectricPoleBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion());

    /** Two tiles by two and six blocks tall, reaching thirty. The bus pole. */
    public static final DeferredBlock<BigElectricPoleBlock> BIG_ELECTRIC_POLE = BLOCKS.registerBlock(
            "big_electric_pole",
            BigElectricPoleBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion());

    /** The same tower one storey shorter, reaching eighteen and covering eighteen by eighteen. */
    public static final DeferredBlock<SubstationBlock> SUBSTATION = BLOCKS.registerBlock(
            "substation",
            SubstationBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .noOcclusion());

    /**
     * Three by three and half a block high, so a field of them is a floor. {@code noOcclusion}
     * because a half block is not a full cube, and a full cube's shadowing would darken the
     * ground beside it.
     */
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.registerBlock(
            "solar_panel",
            SolarPanelBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    /**
     * Two by two and a block high, in copper: a battery for the grid. Behind
     * {@code electric-energy-accumulators}, which is behind the battery, which is oil.
     */
    public static final DeferredBlock<AccumulatorBlock> ACCUMULATOR = BLOCKS.registerBlock(
            "accumulator",
            AccumulatorBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}
}
