package com.jaguarm.nauvislogistics.belt;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A belt block: which way it carries, and how fast. */
public abstract class BeltBlock extends BaseEntityBlock {

    /**
     * Which way items travel. A belt faces the way the player is looking when it is placed, like
     * an inserter and unlike a furnace: a belt is something you point, not something you look at.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Whether this block is drawn straight or as a bend, and which way it bends.
     *
     * <p>Worked out from the neighbours and kept in the block state rather than asked of the run,
     * because a client has to draw a belt in a chunk whose run it may not have built yet - and
     * because a block state is what a model is chosen by. See {@link #withShape}.
     */
    public static final EnumProperty<BeltShape> SHAPE = EnumProperty.create("shape", BeltShape.class);

    /** Half a block, and the same box for collision and outline. */
    private static final VoxelShape BOX = Block.box(0, 0, 0, 16, Belts.HEIGHT * 16, 16);

    /** How many steps a ramp is approximated in. */
    private static final int RAMP_STEPS = 16;

    /** The stair under a ramp, by the horizontal direction the ramp climbs towards. */
    private static final Map<Direction, VoxelShape> RAMPS = Direction.Plane.HORIZONTAL.stream()
            .collect(Collectors.toUnmodifiableMap(high -> high, BeltBlock::ramp));

    /**
     * The stepped stair standing in for a ramp that climbs towards {@code high}.
     *
     * <p>Each step's top is the height of the drawn surface at the step's <em>near</em> edge, which
     * puts the first step flush with the flat belt it is joined to and every later one a little
     * under the ramp rather than poking through it. The top step reaches 20 pixels - past the block
     * it belongs to, the way a fence post does - so the last stride onto the belt at the top of the
     * climb is another quarter block rather than a lurch.
     */
    private static VoxelShape ramp(Direction high) {
        VoxelShape shape = Shapes.empty();
        for (int step = 0; step < RAMP_STEPS; step++) {
            // Measured from the low edge, so `near` is where this step starts climbing.
            double near = 16.0 * step / RAMP_STEPS;
            double far = 16.0 * (step + 1) / RAMP_STEPS;
            double top = Belts.HEIGHT * 16 + near;
            shape = Shapes.or(shape, slice(high, near, far, top));
        }
        return shape;
    }

    /** One step of a ramp: a box from {@code near} to {@code far} along the climb, {@code top} tall. */
    private static VoxelShape slice(Direction high, double near, double far, double top) {
        return switch (high) {
            // `near` is measured from the low edge, which is the face opposite `high`.
            case NORTH -> Block.box(0, 0, 16 - far, 16, top, 16 - near);
            case SOUTH -> Block.box(0, 0, near, 16, top, far);
            case WEST -> Block.box(16 - far, 0, 0, 16 - near, top, 16);
            case EAST -> Block.box(near, 0, 0, far, top, 16);
            default -> throw new IllegalArgumentException("a belt climbs towards a horizontal side, not " + high);
        };
    }

    /** A ramp's stair reaches above its own block, and this is what makes the game notice. */
    @Override
    public boolean collisionExtendsVertically(BlockState state, BlockGetter level, BlockPos pos,
            Entity collidingEntity) {
        return state.getValue(SHAPE).isSlope();
    }

    /** Which way a belt in this state climbs, or null if it is flat. */
    public static @Nullable Direction climbsTowards(BlockState state) {
        return switch (state.getValue(SHAPE)) {
            case UP -> state.getValue(FACING);
            case DOWN -> state.getValue(FACING).getOpposite();
            default -> null;
        };
    }

    protected BeltBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, BeltShape.STRAIGHT));
    }

    /** How far an item on this belt moves in one tick, in {@link Belts#UNITS_PER_BLOCK}ths. */
    public abstract int speed();

    /** The Factorio entity this is, so {@code check_models.py} can hold the speed to the wiki. */
    public abstract String factorioId();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withShape(defaultBlockState().setValue(FACING, context.getHorizontalDirection()),
                context.getLevel(), context.getClickedPos());
    }

    /** The belt this one hands to, or null if it hands to nothing. */
    public static @Nullable BlockPos successorOf(LevelReader level, BlockPos pos, BlockState state) {
        Direction travel = state.getValue(FACING);
        for (int step = 0; step < SUCCESSOR_CANDIDATES; step++) {
            BlockPos target = successorCandidate(pos, travel, step);
            BlockState ahead = level.getBlockState(target);
            if (!ahead.is(state.getBlock())) {
                continue;
            }
            return ahead.getValue(FACING) == travel.getOpposite() ? null : target;
        }
        return null;
    }

    /** How many places {@link #successorOf} looks, and so how many {@link BeltLines} must look. */
    public static final int SUCCESSOR_CANDIDATES = 3;

    /**
     * The {@code step}th place a belt at {@code pos} travelling {@code travel} might hand to.
     *
     * <p>Level, then up, then down. The order is the rule and both readers of it walk this method
     * rather than writing the three positions out again.
     */
    public static BlockPos successorCandidate(BlockPos pos, Direction travel, int step) {
        BlockPos ahead = pos.relative(travel);
        return switch (step) {
            case 0 -> ahead;
            case 1 -> ahead.above();
            default -> ahead.below();
        };
    }

    /**
     * Every place a belt could sit and feed the belt at {@code pos}, or be fed by it.
     *
     * <p>Twelve rather than four, now that a line can change level: each of the four sides at this
     * belt's own height, one above and one below. Only ever walked when the graph is rebuilt, which
     * is a placement or a break rather than a tick.
     */
    public static void forEachNeighbour(BlockPos pos, Consumer<BlockPos> visitor) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = pos.relative(side);
            visitor.accept(beside);
            visitor.accept(beside.above());
            visitor.accept(beside.below());
        }
    }

    /**
     * Re-reads the bend when anything beside this belt changes.
     *
     * <p>All four sides every time, not just the one that changed, because a bend is a fact about
     * how many feeders there are rather than about any one of them: a second belt joining turns a
     * corner back into a straight, and it does that by existing rather than by being the neighbour
     * the notification happens to name.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbourState,
            RandomSource random) {
        return withShape(state, level, pos);
    }

    /** Which way this belt bends or climbs, given what is around it. */
    public static BlockState withShape(BlockState state, LevelReader level, BlockPos pos) {
        Direction travel = state.getValue(FACING);

        BlockPos successor = successorOf(level, pos, state);
        if (successor != null && successor.equals(pos.relative(travel).above())) {
            return state.setValue(SHAPE, BeltShape.UP);
        }
        if (descends(level, pos, state)) {
            return state.setValue(SHAPE, BeltShape.DOWN);
        }

        Direction from = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!feeds(level, pos.relative(side), side.getOpposite(), state)) {
                continue;
            }
            if (from != null) {
                // Two of them: a side-load, and a straight belt.
                return state.setValue(SHAPE, BeltShape.STRAIGHT);
            }
            from = side;
        }

        BeltShape shape = BeltShape.STRAIGHT;
        if (from == travel.getCounterClockWise()) {
            shape = BeltShape.FROM_LEFT;
        } else if (from == travel.getClockWise()) {
            shape = BeltShape.FROM_RIGHT;
        }
        return state.setValue(SHAPE, shape);
    }

    /**
     * Whether this belt is the bottom of a step something is coming down.
     *
     * <p>Asked of the belt behind and above rather than answered here, because "does that belt hand
     * to me" is {@link #successorOf}'s question and there must be one answer to it. A belt that
     * merely sits above and behind is not enough: if it can reach something level with itself it
     * will, and then this is an ordinary belt with nothing feeding it.
     */
    private static boolean descends(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos above = pos.relative(state.getValue(FACING).getOpposite()).above();
        BlockState feeder = level.getBlockState(above);
        if (!feeder.is(state.getBlock())) {
            return false;
        }
        return pos.equals(successorOf(level, above, feeder));
    }

    /** Re-reads the bend on this belt and on every belt beside it. */
    public static void refreshShapes(ServerLevel level, BlockPos pos) {
        refresh(level, pos);
        forEachNeighbour(pos, neighbour -> refresh(level, neighbour));
    }

    private static void refresh(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BeltBlock)) {
            return;
        }
        BlockState fixed = withShape(state, level, pos);
        if (fixed != state) {
            level.setBlock(pos, fixed, Block.UPDATE_ALL);
        }
    }

    /** Whether the block at {@code from} is a belt of this kind handing to us, {@code towards}. */
    private static boolean feeds(LevelReader level, BlockPos from, Direction towards, BlockState self) {
        BlockState neighbour = level.getBlockState(from);
        if (!neighbour.is(self.getBlock())) {
            return false;
        }
        return neighbour.getValue(FACING) == towards
                && self.getValue(FACING) != towards.getOpposite();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeltBlockEntity(pos, state);
    }

    /** <b>A belt in hand puts that belt here, pointing the way you are facing.</b> */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof BeltBlock held)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }

        Direction placed = player.getDirection();
        if (held != state.getBlock()) {
            return replace(server, state, pos, player, stack, held, placed);
        }

        if (placed == state.getValue(FACING)) {
            // The same belt, already pointing that way: this click asks for nothing. Saying so
            // costs a graph rebuild and a sound.
            return InteractionResult.SUCCESS;
        }
        server.setBlock(pos, withShape(state.setValue(FACING, placed), server, pos), Block.UPDATE_ALL);

        // Nothing tells the graph by hand any more. The block entity survives a state change and
        // hears about it from `setBlockState`, which fires on both sides - the server saying it
        // here and the client never hearing it at all is what used to leave a turned belt drawn
        // along its old line until the chunk reloaded. See BeltBlockEntity.
        //
        // The belts either side may have stopped being corners, or started, or become the low end
        // of a slope. setBlock tells them through updateShape, but only about the block that
        // changed - so say it plainly.
        refreshShapes(server, pos);

        SoundType sound = state.getSoundType(server, pos, player);
        server.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                sound.getVolume() * 0.6F, sound.getPitch() * 1.2F);
        return InteractionResult.SUCCESS;
    }

    /** Puts a belt of another tier here, keeping the line and everything standing on it. */
    private InteractionResult replace(ServerLevel level, BlockState state, BlockPos pos, Player player,
            ItemStack stack, BeltBlock tier, Direction facing) {
        List<BeltBlockEntity.Cargo> carried = level.getBlockEntity(pos) instanceof BeltBlockEntity belt
                ? belt.takeCargo()
                : List.of();

        BlockState placed = tier.defaultBlockState().setValue(FACING, facing);
        level.setBlock(pos, withShape(placed, level, pos), Block.UPDATE_ALL);

        if (level.getBlockEntity(pos) instanceof BeltBlockEntity fresh) {
            fresh.giveCargo(carried);
        }

        // As after a turn: the belts either side may have stopped being corners, or started.
        refreshShapes(level, pos);

        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
            player.getInventory().placeItemBackInInventory(new ItemStack(state.getBlock()));
        }

        SoundType sound = placed.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                sound.getVolume() * 0.6F, sound.getPitch());
        return InteractionResult.SUCCESS;
    }

    // No getTicker override, deliberately. The run ticks; see BeltRun.

    /**
     * A half slab flat, a four-step stair on a slope.
     *
     * <p>One shape for collision and outline, as before. On a ramp the two genuinely disagree - the
     * model is a smooth 45 degrees and this is four steps under it - and that is the trade for a
     * slope you can walk up: see {@link #RAMP_STEPS}.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction high = climbsTowards(state);
        return high == null ? BOX : RAMPS.get(high);
    }

    /** Carries whatever is standing on it, at the speed it carries everything else. */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        carry(pos, state, entity, true);
    }

    /** A slope reaches whatever is touching it, not only what is standing on it. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effects, boolean precise) {
        super.entityInside(state, level, pos, entity, effects, precise);
        if (!state.getValue(SHAPE).isSlope()) {
            // A flat belt is carried from stepOn, which is the hook that knows what is standing on
            // it rather than what is passing through it.
            return;
        }
        if (entity.onGround() && pos.equals(entity.getOnPosLegacy())) {
            return;
        }
        carry(pos, state, entity, false);
    }

    /**
     * Moves whatever is on this belt: along it, and up it if it slopes.
     *
     * @param push whether to carry it along the belt as well as lift it. See {@link #entityInside}.
     */
    private void carry(BlockPos pos, BlockState state, Entity entity, boolean push) {
        if (entity.isPassenger() || entity.isShiftKeyDown()) {
            return;
        }
        Direction travel = state.getValue(FACING);
        double step = speed() / (double) Belts.UNITS_PER_BLOCK;
        double lift = lift(pos, state, entity, travel, step);
        if (lift == 0.0 && !push) {
            return;
        }

        // move rather than a nudge to the velocity: a velocity decays against friction, so the
        // speed something is actually carried at would be some fraction of the belt's rather than
        // the belt's. This is outside Entity.move - aiStep calls it after travel - so it is not
        // re-entrant, and it collides properly against whatever is in the way.
        //
        // The standing is put back afterwards, and that is not tidiness. A move with no vertical
        // component makes Entity.move decide nothing was landed on and clear onGround, and an
        // entity that is told it is falling while it stands on a belt loses its footing for a
        // tick: the next tick's stepOn does not run, because that hook only fires for something on
        // the ground, so the belt would carry it in stutters. For a player it is worse - fall
        // damage and step sounds are both worked out from this. A lift clears it just the same.
        boolean standing = entity.onGround();
        double along = push ? step : 0.0;
        entity.move(MoverType.SELF,
                new Vec3(travel.getStepX() * along, lift, travel.getStepZ() * along));
        entity.setOnGround(standing);

        hold(entity);
    }

    /** How far a slope lifts what is standing on it this tick. */
    private static double lift(BlockPos pos, BlockState state, Entity entity, Direction travel,
            double step) {
        if (state.getValue(SHAPE) != BeltShape.UP) {
            return 0.0;
        }
        AABB box = entity.getBoundingBox();
        // How far across this block the front of the entity is, from the edge it came in at.
        double leading = switch (travel) {
            case EAST -> box.maxX - pos.getX();
            case WEST -> pos.getX() + 1 - box.minX;
            case SOUTH -> box.maxZ - pos.getZ();
            default -> pos.getZ() + 1 - box.minZ;
        };
        double surface = pos.getY() + Belts.HEIGHT + Math.clamp(leading + step, 0.0, 1.0);
        return Math.clamp(surface - entity.getY(), 0.0, step);
    }

    /** Holds what a belt is carrying down onto it, and forgives it the drop. */
    private static void hold(Entity entity) {
        if (entity instanceof LivingEntity) {
            return;
        }
        entity.resetFallDistance();
        Vec3 speed = entity.getDeltaMovement();
        if (speed.y > 0.0) {
            entity.setDeltaMovement(speed.x, 0.0, speed.z);
        }
    }

    // Nothing wakes a belt from a neighbour, and nothing needs to. A run is awake exactly while it
    // has something on it: an inserter putting an item on wakes it through the capability, and a
    // run that empties drops out of the active set by itself. A jammed run stays awake and costs
    // almost nothing, because BeltLane makes a jam free rather than expensive.
    //
    // Nor is there a hook for a neighbouring belt appearing: that belt's own block entity joins
    // the graph when it loads, and joining rebuilds the runs around it. See BeltLines.
}
