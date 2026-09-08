package com.jaguarm.nauvislogistics.transport;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The electric inserter. Nothing to fuel it with by hand - it wants a pole in range. */
public class ElectricInserterBlock extends InserterBlock {

    public static final MapCodec<ElectricInserterBlock> CODEC = simpleCodec(ElectricInserterBlock::new);

    /**
     * Ticks per item moved.
     *
     * <p>Factorio's inserter manages about 0.83 items a second against a burner inserter's 0.6,
     * so this is 24 ticks where the burner is 30. Behaviour rather than identity, like the
     * burner's number, and derived from the same place.
     */
    public static final int SWING_TICKS = 24;

    /**
     * FE per tick of a swing.
     *
     * <p>Factorio's inserter draws 13 kW where a steam engine makes 900. At this pack's scale -
     * an engine is 120 FE a tick - that is 1.7, and two is the nearest whole number. An inserter
     * is cheap to run on purpose: a base has thousands of them and a handful of assemblers.
     */
    public static final int ENERGY_PER_TICK = 2;

    public ElectricInserterBlock(Properties properties) {
        super(properties);
    }

    /** Ticks per item moved, for the block entity that is doing the swinging. */
    public int swingTicks() {
        return SWING_TICKS;
    }

    /** FE spent per tick of a swing. */
    public int energyPerTick() {
        return ENERGY_PER_TICK;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricInserterBlockEntity(pos, state);
    }

    /**
     * No screen, and so still a line of text.
     *
     * <p>An electric inserter has no slot: a screen for it would be a panel containing one bar.
     * What it wants is the hover display - a Factorio-style readout of whatever you are looking
     * at - and until that exists this line is the only way to tell "no power" from "pointing the
     * wrong way". The pole says the same thing for the same reason.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof InserterBlockEntity inserter)) {
            return InteractionResult.PASS;
        }

        player.sendOverlayMessage(inserter.running()
                ? Component.translatable("nauvis_logistics.inserter.running",
                        Component.literal(state.getValue(FACING).getName()))
                : Component.translatable("nauvis_logistics.inserter.no_power"));
        return InteractionResult.SUCCESS;
    }
}
