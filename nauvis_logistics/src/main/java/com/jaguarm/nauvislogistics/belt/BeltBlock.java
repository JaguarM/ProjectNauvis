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

/**
 * A belt block: which way it carries, and how fast.
 *
 * <p>It holds nothing and does nothing. <b>The run holds the items and the run ticks</b> - see
 * {@link BeltRun} - and a belt block is a fact about where the run goes. This is the third time
 * this pack has drawn that line, after {@code PowerNetwork} and {@code FluidNetwork}, and it
 * matters most here: a belt that were a block entity passing items to the next block would cost a
 * tick per block per second and take a tick per block to move anything, which is the difference
 * between a belt and a bucket chain.
 *
 * <h2>Speed is a subclass, not a field</h2>
 *
 * <p>Every tier is its own class with its own constant, rather than one class with a speed field.
 * That is not taste: {@code createBlockStateDefinition} runs inside {@code Block}'s constructor,
 * before any field of a subclass exists, and the drills already shipped one bug from reading a
 * field there. A constant on a subclass exists long before any block does. See
 * {@code docs/PITFALLS.md}.
 *
 * <p>It also settles what happens where two tiers meet: a run only continues through belts of the
 * same block, so a fast belt after a normal one is a second run that the first hands off into,
 * which is what Factorio does with its transport lines.
 *
 * <h2>Half a block high, and you walk over it</h2>
 *
 * <p>{@link Belts#HEIGHT} is 0.5, under vanilla's 0.6 step height, so crossing a belt is walking
 * rather than jumping. Collision and silhouette agree exactly here, which for something meant to
 * be walked across is the whole point.
 *
 * <p>And it carries you. See {@link #stepOn}.
 *
 * <h2>It climbs, the way a rail does</h2>
 *
 * <p>A belt hands to the first belt of its own kind directly ahead of it, one <em>above</em> that,
 * or one <em>below</em> - {@link #successorOf}, which is vanilla's {@code RailState.getRail} probe
 * with the nouns changed. So a line changes level by being built that way, with no item for it and
 * nothing to place but belts.
 *
 * <p>The block that gets the ramp is always the <em>low</em> one, which is also vanilla's rule: a
 * belt whose next belt is one along and one up is drawn climbing, and the belt on top of the step
 * is flat. See {@link BeltShape}.
 *
 * <p><b>The collision is a stair and the model is a ramp</b>, which is the one place in this pack
 * where the two are meant to disagree. Four steps of a quarter block, so every step up is 0.25 and
 * a slope is walked rather than jumped - vanilla's rail slope is a plain 8-pixel box you have to
 * jump, and a belt you cannot walk up would not be a belt. See {@link #RAMP_STEPS}.
 */
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

    /**
     * How many steps a ramp is approximated in.
     *
     * <p>Sixteen, so each is one pixel - and the number is not about how it looks. <b>It is set by
     * the slowest belt.</b> A player walks up anything under vanilla's 0.6 step height, but an item,
     * a minecart and an experience orb have a step height of <em>zero</em>: {@code maxUpStep}
     * returns 0 on {@code Entity} and only {@code LivingEntity} overrides it. Nothing carries those
     * up a slope except {@link #stepOn} lifting them, and a lift can only be as big as the belt's
     * own speed without shoving things along faster than the belt runs. A transport belt moves 1.5
     * pixels a tick, so a riser has to be under that or the slowest belt's cargo stops dead against
     * it - which it did.
     *
     * <p>They sit on the pixel grid, so the shape is cheap despite the count, and they hug the drawn
     * ramp within a pixel, which is closer than the quarter-block steps this started with.
     */
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

    /**
     * A ramp's stair reaches above its own block, and this is what makes the game notice.
     *
     * <p>Without it a belt stops carrying anything over the top quarter of every slope, and nothing
     * about that looks like a bug: {@code stepOn} is only ever called for
     * {@code Entity.getOnPosLegacy}, which is the supporting block <em>or</em> the block a fifth of
     * a block under the entity's feet - and standing on a step that reaches past the block it
     * belongs to, that is the air above the ramp. So the belt asked the air to carry the player and
     * the air declined.
     *
     * <p>This is the switch that makes {@code getOnPosLegacy} keep the supporting block instead, and
     * it is there for fences and walls, which are tall for the same reason.
     */
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

    /**
     * The belt this one hands to, or null if it hands to nothing.
     *
     * <p><b>Three places, in this order: straight ahead, one above that, one below it.</b> That is
     * vanilla's rail probe - {@code RailState.getRail} - and copying it is the point: a Minecraft
     * player already knows that a line of rails climbs by being built one block up, and a belt that
     * behaved differently would be a second thing to learn for no gain.
     *
     * <p>Level wins over diagonal, so a line that could go either way goes straight on; and a belt
     * facing back at this one is never a successor, because two belts cannot each hand to the other.
     *
     * <p>Same block, not just any belt: a run has one speed, so a tier change is a hand-off between
     * two runs rather than a continuation of one. See {@link BeltLines}.
     *
     * <p><b>This is the rule, and {@link BeltLines} asks it a second way.</b> The two are not
     * shared because they have different information - this one reads a {@code LevelReader} in the
     * middle of a block update, that one walks the set of belts it already knows are loaded - but
     * the *positions and their order* are shared, through {@link #successorCandidate}, because
     * getting those out of step is what would draw a belt climbing in a direction nothing travels.
     */
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

    /**
     * Which way this belt bends or climbs, given what is around it.
     *
     * <h2>A slope first, and a bend only if it is not one</h2>
     *
     * <p>Because the drawing has to agree with where items actually go. A belt whose successor is
     * one block up is climbing however many belts are beside it, and drawing it as a corner would
     * put a bend under a line that is going over a step. The bend logic is what is left when the
     * belt is level with both ends.
     *
     * <p>It climbs {@link BeltShape#UP} when it hands to the block one along and one up, and
     * {@link BeltShape#DOWN} when the belt one <em>behind</em> and one up hands to it - a ramp
     * always belonging to the lower of the two blocks, which is vanilla's rule for rails.
     *
     * <h2>Then the bend</h2>
     *
     * <p>One feeder, arriving from a side, is a corner. None, one from directly behind, or more
     * than one, is a straight - two feeders being a side-load, which Factorio draws as a straight
     * belt something joins rather than as a bend.
     *
     * <p>Only feeders level with this belt count towards a bend, because a corner that also
     * changed level is not a shape this block can be in. The run still carries items through it;
     * see {@code docs/GAPS.md}.
     *
     * <p>The test for a feeder is the same one {@link BeltLines} builds runs with, and it has to
     * stay that way or a belt will be drawn bending in a direction nothing travels. It is written
     * out twice rather than shared because the two ask different things: this one asks a
     * {@code LevelReader} mid-update, and that one asks the set of belts it already knows about.
     */
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

    /**
     * Re-reads the bend on this belt and on every belt beside it.
     *
     * <p>Called when a belt joins the graph, which covers the two ways a belt can arrive already
     * pointing at something without {@code getStateForPlacement} ever having run: put there by a
     * command, a structure or another mod, and loaded from disk with its neighbour in a chunk that
     * had not arrived yet. Both leave a corner drawn as a straight belt, which works perfectly and
     * looks like a mistake.
     *
     * <p>Its neighbours as well as itself, because the two halves of a corner learn about each
     * other at different moments and only one of them gets a notification. All twelve of them since
     * a line can climb - a belt one along and one up is as much a neighbour as one beside it, and
     * it is the half of a new slope that gets no notification at all.
     */
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

    /**
     * <b>A belt in hand puts that belt here, pointing the way you are facing.</b>
     *
     * <p>One rule, and it is Factorio's fast-replace. Whatever belt is in your hand becomes the
     * belt under the cursor - a faster one, a slower one, or the same one turned - and it points
     * the way a belt you had placed there would have pointed. So laying a line, fixing a line and
     * upgrading a line are the same gesture, and running a belt along a row you have already built
     * rewrites the lot to face the way you are walking.
     *
     * <p>The alternative for any of those is breaking a belt and putting it back, which drops what
     * was on it and, halfway along a line, cuts the line in two to do it.
     *
     * <p><b>Crouch to place instead.</b> That needs no code: vanilla skips a block's own use when
     * the player is crouching with something in hand, and falls through to putting the block down
     * - see {@code ServerPlayerGameMode.useItemOn}. So the two things you want to do with a belt in
     * your hand are the two things the same button already does.
     *
     * <h2>Two implementations of the one rule</h2>
     *
     * <p><b>The same belt is a state change</b>, which keeps the block entity and so keeps what is
     * standing on it for free. The graph still has to be told, because no block entity was removed
     * and so neither hook that maintains it fired - {@link BeltBlockEntity#setBlockState} is what
     * catches that, on both sides.
     *
     * <p><b>A different belt is a new block</b>, which is a different job - see {@link #replace}.
     */
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

    /**
     * Puts a belt of another tier here, keeping the line and everything standing on it.
     *
     * <p>Factorio's fast-replace, and the reason a bus is ever upgraded rather than rebuilt: you
     * run a red belt along a yellow line and the line becomes red under you, still carrying what it
     * was carrying, pointing where you are walking. Doing the same by breaking and re-placing costs
     * a dropped item for every belt and cuts the line in two while you do it.
     *
     * <p><b>Either way up.</b> A slower belt replaces a faster one exactly as readily, which is
     * Factorio's rule: a belt in hand is a belt you are placing, and refusing half of that would
     * make the gesture something you have to think about.
     *
     * <h2>Three things it has to get right</h2>
     *
     * <p><b>The block is swapped, not a property set.</b> Speed is a fact about the block - see the
     * class note - so another tier is another block, which destroys the block entity and builds a
     * new one. {@link BeltLines#beltRemoved} and {@link BeltLines#beltPlaced} fire on their own for
     * that, so {@code beltTurned} is wrong here: it exists for the case where the block entity
     * <em>survives</em> and nothing else would notice.
     *
     * <p><b>The load is carried across by hand.</b> Nothing does it for free, and the default is
     * worse than nothing: replacing the block runs {@code preRemoveSideEffects}, which spills what
     * is standing here onto the floor. So it comes off the old run before the swap and goes onto
     * the new one after - see {@link BeltBlockEntity#takeCargo}. The rest of the line is untouched,
     * because every item on it is pinned to the block it is standing on.
     *
     * <p><b>It is paid for.</b> One belt off the stack, the old one back in the player's hands,
     * unless they are in creative - the same trade breaking and re-placing would have made.
     *
     * <p>The bend is worked out afresh rather than copied over, because the belts either side of
     * this one may be a different block now and a run only follows one tier: a belt of another tier
     * feeds this one at a seam, and a seam is not drawn as a corner.
     */
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

    /**
     * Carries whatever is standing on it, at the speed it carries everything else.
     *
     * <p>Factorio's belts move the player, and a base is laid out on the assumption that they do -
     * a long bus is something you ride rather than walk beside. You can still walk against a belt,
     * or across one, because this is added to what the entity was already doing rather than
     * replacing it.
     *
     * <p><b>Crouching stops it.</b> That is not Factorio's rule - there, a belt has you whatever
     * you do - but it is Minecraft's, the same reflex that keeps a player on an edge or off a
     * slime block, and without it placing a machine beside a working belt means being carried away
     * mid-click. One line, if it is ever unwanted.
     *
     * <h2>Why this hook</h2>
     *
     * <p>Not {@code entityInside}: a belt is a bottom slab and something standing on top of it is
     * inside the block <em>above</em>, so that never fires. {@code stepOn} is called from
     * {@code Entity.applyEffectsFromBlocks} for whatever the entity is standing on, every tick it
     * is on the ground and whether or not it is moving - which is exactly a conveyor's question.
     *
     * <p>It runs on the server, and on a client for the player it is that client's own - the guard
     * is in {@code LivingEntity.aiStep} - so the two agree and being carried is not laggy.
     */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        carry(pos, state, entity, true);
    }

    /**
     * A slope reaches whatever is touching it, not only what is standing on it.
     *
     * <p><b>This is what gets an item onto a ramp at all</b>, and the reason it is needed is a
     * circle. {@link #stepOn} is only called for the block an entity is <em>supported</em> by, and
     * something arriving off a flat belt is still supported by that flat belt while its nose is
     * against the ramp's first step. So the ramp never gets asked to lift it, and it cannot become
     * the supporting block until it has been lifted. An item measured coming up to a slope stopped
     * dead at the seam and stayed there for as long as anyone watched.
     *
     * <p>Being <em>inside</em> the block has no such condition - the box only has to overlap, which
     * it does the moment the item's nose crosses the line - so the lift can start before the ramp is
     * carrying anything. Create and Immersive Engineering both drive their conveyors from here for
     * the same reason.
     *
     * <p>Only the lift, never the push along the belt: whichever belt is actually underneath is
     * already pushing, and doing it twice at a seam would carry things over it at double speed. And
     * only when {@code stepOn} is not about to do the same job for this same block, which is the one
     * case where the two hooks overlap.
     */
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

    /**
     * How far a slope lifts what is standing on it this tick.
     *
     * <p><b>A push along a slope is not enough on its own.</b> A player or a mob walks up the stair
     * under a ramp because {@code maxUpStep} is 0.6 for them; on {@code Entity} it is <em>zero</em>,
     * so an item, a minecart, a boat or an experience orb is pushed straight into the first riser
     * and stays there. Which is most of what a belt carries.
     *
     * <p>So the belt lifts them itself, and the rule is <b>to the height of the ramp under the
     * leading edge of the thing, one step further on</b>. Three parts, and each is load-bearing:
     *
     * <ul>
     *   <li><b>the leading edge</b>, not the middle, because a box resting on a rising ramp rests on
     *       its front bottom corner, and it is that corner a riser stops. Measuring from the middle
     *       asks for a lift half the box's width too small, which is exactly small enough to leave
     *       an item wedged against the step in front of it - Immersive Engineering's conveyor does
     *       the same and calls it fixing the entity to the highest point under it;</li>
     *   <li><b>one step further on</b>, because a lift only to where the surface already is is no
     *       lift at all - the thing is standing there;</li>
     *   <li><b>and no higher</b>, which is what makes it safe. Something jammed against a wall at
     *       the top of a slope stops rising the moment it reaches the surface, where a fixed nudge
     *       every tick would quietly walk it up into the sky.</li>
     * </ul>
     *
     * <p>Only climbing. Going down needs nothing - there is no riser in the way, and gravity is
     * already pointing where the belt is going.
     */
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

    /**
     * Holds what a belt is carrying down onto it, and forgives it the drop.
     *
     * <p><b>A dropped item bounces.</b> {@code ItemEntity.tick} inverts and halves its downward
     * speed every time it lands, which is the little hop a dropped item does on the floor - and
     * while it is in the air {@code onGround} is false, so {@code applyEffectsFromBlocks} never
     * reaches {@link #stepOn}. On the flat that only costs a stutter. On a slope it costs the ticks
     * that would have lifted it.
     *
     * <p>Only what is not alive. A player or a mob leaving the ground is jumping, and a belt has no
     * business cancelling that; nothing else on a belt has a reason to rise on its own.
     *
     * <p>And the fall distance goes, which both Create and Immersive Engineering do on their belts
     * for the same reason: what a belt sets down, it set down gently, and a long descent should not
     * end in damage at the bottom.
     */
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
