package com.jaguarm.nauvispower.grid;

import com.mojang.serialization.MapCodec;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The small electric pole. Two copper cable and two oak planks, four blocks tall, and the reason
 * the rest of this package exists.
 *
 * <p><b>It has no ticker and no {@code tick} override</b>, and that is the point rather than an
 * omission. A pole does nothing on its own: it joins a {@link PowerNetwork} when it loads and
 * leaves when it goes, and the network is what ticks. Compare the boiler and the steam engine,
 * which schedule their own ticks because each can answer "have I got work?" by looking at itself.
 * A pole cannot - it is a node in a graph, and the graph is the thing with work to do.
 *
 * <h2>Four blocks, one thing</h2>
 *
 * <p>A one-block pole reads as a fence post. A real one has to stand well over the machines it
 * feeds, and the honest way to do that in Minecraft is a true multi-block: four real blocks with a
 * {@link PolePart} property, placed together and broken together. The alternative - one block with
 * a {@code VoxelShape} four blocks tall - looks the same until you walk up to it, at which point
 * the renderer culls the whole pole the moment its one real block leaves the screen.
 *
 * <p>The mechanism is vanilla's, from {@code DoorBlock} and {@code DoublePlantBlock}:
 *
 * <ul>
 *   <li>{@link #getStateForPlacement} returns null when there is no headroom, so the pole is never
 *       placed half-built;
 *   <li>{@link #setPlacedBy} puts the other two blocks in;
 *   <li>{@link #updateShape} turns a part to air the moment the part above or below it is not what
 *       it should be. That one rule is the whole teardown, and it covers every way a block can
 *       vanish - broken, exploded, {@code /setblock}, another mod - rather than only the ones
 *       somebody thought to handle.
 * </ul>
 *
 * <p>Only the bottom drops an item, by a loot table condition, and only the bottom carries the
 * block entity. Breaking the middle or the top destroys the bottom through the same rule, and the
 * bottom's own destruction is what hands the player their pole back.
 *
 * <p>Immersive Engineering's {@code wooden_post} is the reference for the shape of this - base
 * block holds the logic, dummies above, break one and the whole thing goes. Read and reimplemented
 * rather than copied; the mechanism below is vanilla's rather than theirs.
 *
 * <p>Right-click any part to see what it is connected to, which is the only way to see a network
 * from inside the game and is how the wire reach and the supply area were checked by hand.
 */
public class SmallElectricPoleBlock extends BaseEntityBlock {

    public static final MapCodec<SmallElectricPoleBlock> CODEC = simpleCodec(SmallElectricPoleBlock::new);

    public static final EnumProperty<PolePart> PART = EnumProperty.create("part", PolePart.class);

    /** How tall a pole is, in blocks. Behaviour, so it is yours to tune - see {@link PolePart}. */
    public static final int HEIGHT = PolePart.values().length;

    public SmallElectricPoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(PART, PolePart.FOOT));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    /**
     * Only the bottom has one. The other two parts are structure, and a block entity each would be
     * three times the memory for a base of ten thousand poles to hold nothing.
     */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == PolePart.FOOT
                ? new SmallElectricPoleBlockEntity(pos, state)
                : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART).shape();
    }

    /**
     * The post, never the crossarm.
     *
     * <p>An arm that reaches most of the way across its block would catch you as you walked past
     * the top of a pole - from a shape three blocks over your head that you were not looking at.
     * The outline still traces the whole pole; only what you bump into is trimmed.
     *
     * <p>The post itself does collide, because a pole is climbable: it is in
     * {@code minecraft:climbable}, so you go up it like a ladder, and something has to be there to
     * climb. That is Immersive Engineering's behaviour too, and it is the difference between a
     * pole being scenery and being somewhere to stand while you wire the next one.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return state.getValue(PART).collisionShape();
    }

    /** Null - and so no placement at all - unless the whole pole fits. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (int above = 1; above < HEIGHT; above++) {
            BlockPos part = pos.above(above);
            if (part.getY() > level.getMaxY() || !level.getBlockState(part).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by,
            ItemStack stack) {
        for (int above = 1; above < HEIGHT; above++) {
            level.setBlockAndUpdate(pos.above(above),
                    state.setValue(PART, PolePart.values()[above]));
        }
    }

    /**
     * The whole teardown, in one rule: a part whose neighbour above or below is not the part it
     * should be stops existing.
     *
     * <p>Turning to air here rather than calling {@code removeBlock} matters. The neighbour-update
     * machinery routes an air result through {@code Block.updateOrDestroy}, which <em>destroys</em>
     * the block with drops enabled - so the bottom's loot table is what gives the player their pole
     * back, whichever of the three parts they actually hit.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState,
            RandomSource random) {
        if (direction.getAxis() == Direction.Axis.Y) {
            PolePart part = state.getValue(PART);
            PolePart expected = direction == Direction.UP ? part.above() : part.below();
            if (expected != null
                    && !(neighbourState.is(this) && neighbourState.getValue(PART) == expected)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    /**
     * In creative, take the bottom out silently first.
     *
     * <p>Without this a creative player breaking the top would get a free pole: the teardown above
     * destroys the bottom with drops enabled, and creative only suppresses the drop from the block
     * the player actually hit. Flag 32 is {@code UPDATE_SUPPRESS_DROPS}. This is
     * {@code DoublePlantBlock#preventDropFromBottomPart}, with a taller pole.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            BlockPos bottom = pos.below(state.getValue(PART).height());
            BlockState bottomState = level.getBlockState(bottom);
            if (bottomState.is(this) && bottomState.getValue(PART) == PolePart.FOOT) {
                level.setBlock(bottom, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, bottom, Block.getId(bottomState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        // Whichever part was clicked, the network hangs off the bottom.
        BlockPos bottom = pos.below(state.getValue(PART).height());
        PowerNetworkManager manager = PowerNetworkManager.of(serverLevel);
        PowerNetwork network = manager.networkAt(bottom);
        if (network == null) {
            player.sendOverlayMessage(Component.translatable("nauvis_power.small_electric_pole.detached"));
            return InteractionResult.SUCCESS;
        }

        player.sendOverlayMessage(Component.translatable(
                manager.isActive(network)
                        ? "nauvis_power.small_electric_pole.status"
                        : "nauvis_power.small_electric_pole.status_idle",
                network.poleCount(),
                network.endpointCount()));
        return InteractionResult.SUCCESS;
    }
}
