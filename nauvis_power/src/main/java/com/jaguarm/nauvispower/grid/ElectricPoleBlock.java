package com.jaguarm.nauvispower.grid;

import com.jaguarm.nauvispower.multiblock.MachineShape;
import com.jaguarm.nauvispower.multiblock.Multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An electric pole, and the reason the rest of this package exists.
 *
 * <p><b>It has no ticker and no {@code tick} override</b>, and that is the point rather than an
 * omission. A pole does nothing on its own: it joins a {@link PowerNetwork} when it loads and
 * leaves when it goes, and the network is what ticks. Compare the boiler and the steam engine,
 * which schedule their own ticks because each can answer "have I got work?" by looking at itself.
 * A pole cannot - it is a node in a graph, and the graph is the thing with work to do.
 *
 * <h2>A tier is a shape and a reach</h2>
 *
 * <p>Three of them: one tile four blocks tall, one tile five blocks tall, and two tiles by two
 * six blocks tall. Both numbers are constants on a subclass rather than constructor arguments,
 * because {@code createBlockStateDefinition} runs inside {@link Block}'s constructor and a field
 * of a subclass does not exist yet when it does - see PITFALLS.md. An overridden method returning
 * a static is safe there, and is what {@link #shape()} and {@link #wireReach()} are.
 *
 * <p>The three share a block entity type, the way the two electric inserters do: what differs is
 * a number the block already knows, and nothing that is saved changes.
 *
 * <h2>The multi-block is not this class's</h2>
 *
 * <p>Placement, teardown, which cell holds the block entity and which one you clicked are all
 * {@link Multiblock}'s - the same mechanism the boiler and the steam engine use. It did not start
 * that way: the pole had four rules of its own over a {@code PolePart} enum, and {@code Multiblock}
 * was generalised out of them. Keeping both was tolerable while a pole was one tile and always
 * four blocks tall; the tiers ended it, because a five-block pole needs a five-value enum and a
 * two-by-two one needs two more axes, and {@link MachineShape} has had both all along.
 *
 * <p>What is still the pole's own is the part {@code Multiblock} has no opinion about: it is
 * climbable, so you go up it like a ladder, which is why the post collides even though the
 * crossarm does not. That is Immersive Engineering's behaviour too, and it is the difference
 * between a pole being scenery and being somewhere to stand while you wire the next one.
 *
 * <p>Right-click any part to see what it is connected to, which is the only way to see a network
 * from inside the game and is how the wire reach and the supply area were checked by hand.
 */
public abstract class ElectricPoleBlock extends BaseEntityBlock implements Multiblock.MachineBlock {

    protected ElectricPoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(shape().part(), shape().anchor()));
    }

    /**
     * How far this pole can throw a wire to another, in blocks.
     *
     * <p>Factorio's numbers: 7.5, 9 and 30. Behaviour rather than identity, so it is tunable - but
     * the <em>gaps</em> between the tiers are the whole reason the upper two exist, so moving one
     * without the others makes an item pointless.
     */
    public abstract double wireReach();

    /**
     * How far past its own footprint this pole supplies machines, in blocks.
     *
     * <p>Factorio states supply as a square: 5x5 for the one-tile poles, 4x4 for the big one,
     * 18x18 for the substation. Those are the footprint plus this on every side, which is why it
     * is a margin rather than a radius - a two-tile pole has no middle tile to measure from.
     */
    public int supplyReach() {
        return PowerNetworkManager.SUPPLY_RADIUS;
    }

    /** How tall this tier stands, in blocks. Read off the shape, so it cannot disagree with it. */
    public int height() {
        return shape().height();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(shape().part());
    }

    /**
     * Only the foot has one. The other cells are structure, and a block entity each would be
     * twenty-four times the memory for a base of big poles to hold nothing.
     */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Multiblock.isAnchor(this, state) ? new ElectricPoleBlockEntity(pos, state) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return shape().cell(Multiblock.part(this, state)).shape(facing(state));
    }

    /**
     * The post, never the crossarm.
     *
     * <p>An arm that reaches most of the way across its block would catch you as you walked past
     * the top of a pole - from a shape several blocks over your head that you were not looking at.
     * The outline still traces the whole pole; only what you bump into is trimmed. See
     * {@link PoleBoxes}.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return shape().cell(Multiblock.part(this, state)).collisionShape(facing(state));
    }

    /** Null - and so no placement at all - unless the whole pole fits. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return Multiblock.getStateForPlacement(this, defaultBlockState(), context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by,
            ItemStack stack) {
        Multiblock.setPlacedBy(this, level, pos, state);
    }

    /** The whole teardown, in one rule. See {@link Multiblock#updateShape}. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState,
            RandomSource random) {
        BlockState result = Multiblock.updateShape(
                this, state, level, pos, direction, neighbourPos, neighbourState);
        return result.isAir()
                ? result
                : super.updateShape(state, level, ticks, pos, direction, neighbourPos,
                        neighbourState, random);
    }

    /** Creative would otherwise hand back a free pole. See {@link Multiblock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Multiblock.preventDropFromAnchor(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        // Whichever cell was clicked, the network hangs off the foot.
        BlockPos foot = Multiblock.anchorPos(this, state, pos);
        PowerNetworkManager manager = PowerNetworkManager.of(serverLevel);
        PowerNetwork network = manager.networkAt(foot);
        if (network == null) {
            player.sendOverlayMessage(Component.translatable("nauvis_power.electric_pole.detached"));
            return InteractionResult.SUCCESS;
        }

        player.sendOverlayMessage(Component.translatable(
                manager.isActive(network)
                        ? "nauvis_power.electric_pole.status"
                        : "nauvis_power.electric_pole.status_idle",
                network.poleCount(),
                network.endpointCount()));
        return InteractionResult.SUCCESS;
    }
}
