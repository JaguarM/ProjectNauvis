package com.jaguarm.nauvislogistics.belt;

import java.util.List;

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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
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
     * Which way this belt bends, given what is around it.
     *
     * <p>One feeder, arriving from a side, is a corner. None, one from directly behind, or more
     * than one, is a straight - two feeders being a side-load, which Factorio draws as a straight
     * belt something joins rather than as a bend.
     *
     * <p>The test for a feeder is the same one {@link BeltLines} builds runs with, and it has to
     * stay that way or a belt will be drawn bending in a direction nothing travels. It is written
     * out twice rather than shared because the two ask different things: this one asks a
     * {@code LevelReader} mid-update, and that one asks the set of belts it already knows about.
     */
    public static BlockState withShape(BlockState state, LevelReader level, BlockPos pos) {
        Direction travel = state.getValue(FACING);
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
     * Re-reads the bend on this belt and on every belt beside it.
     *
     * <p>Called when a belt joins the graph, which covers the two ways a belt can arrive already
     * pointing at something without {@code getStateForPlacement} ever having run: put there by a
     * command, a structure or another mod, and loaded from disk with its neighbour in a chunk that
     * had not arrived yet. Both leave a corner drawn as a straight belt, which works perfectly and
     * looks like a mistake.
     *
     * <p>Its neighbours as well as itself, because the two halves of a corner learn about each
     * other at different moments and only one of them gets a notification.
     */
    public static void refreshShapes(ServerLevel level, BlockPos pos) {
        refresh(level, pos);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            refresh(level, pos.relative(side));
        }
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
     * A belt in hand turns the belt you click on, a quarter at a time.
     *
     * <p>Laying a line means getting a lot of belts pointing the right way, and the alternative is
     * breaking one and putting it back - which drops what was on it and, halfway along a line,
     * cuts the line in two to do it. Turning is what Factorio gives you and it is what the belt in
     * your hand is already for.
     *
     * <p><b>Crouch to place instead.</b> That needs no code: vanilla skips a block's own use when
     * the player is crouching with something in hand, and falls through to putting the block down
     * - see {@code ServerPlayerGameMode.useItemOn}. So the two things you want to do with a belt in
     * your hand are the two things the same button already does.
     *
     * <p><b>It points the way a belt you placed there would have pointed</b> - the way you are
     * facing - rather than turning by a quarter each click. So laying a line and fixing a line are
     * the same action with the same result, and running a belt in hand along a row you have already
     * built rewrites the lot to face the way you are walking, which is how a belt gets laid.
     *
     * <p><b>A faster belt replaces instead of turning</b> - see {@link #upgrade}. That is the other
     * half of the same gesture: running a red belt along a yellow line upgrades the lot, exactly as
     * running a yellow one along it re-points the lot.
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

        if (held.speed() > speed()) {
            return upgrade(server, state, pos, player, stack, held);
        }

        Direction placed = player.getDirection();
        if (placed == state.getValue(FACING)) {
            // Already pointing that way. Saying so costs a graph rebuild and a sound.
            return InteractionResult.SUCCESS;
        }
        server.setBlock(pos, withShape(state.setValue(FACING, placed), server, pos), Block.UPDATE_ALL);

        // The lines through it are different lines now, and nothing else will say so: the block
        // entity was never removed, so neither of the hooks that maintain the graph has fired.
        BeltLines.of(server).beltTurned(pos);
        // The belts either side may have stopped being corners, or started. setBlock tells them
        // through updateShape, but only about the block that changed - so say it plainly.
        refreshShapes(server, pos);

        SoundType sound = state.getSoundType(server, pos, player);
        server.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                sound.getVolume() * 0.6F, sound.getPitch() * 1.2F);
        return InteractionResult.SUCCESS;
    }

    /**
     * Replaces this belt with a faster one, keeping the line and everything standing on it.
     *
     * <p>Factorio's fast-replace, and the reason a bus is ever upgraded rather than rebuilt: you
     * run a red belt along a yellow line and the line becomes red under you, still pointing the way
     * it pointed, still carrying what it was carrying. Doing it by breaking and re-placing costs a
     * dropped item for every belt and cuts the line in two while you do it.
     *
     * <h2>Five things it has to get right</h2>
     *
     * <p><b>The block is swapped, not a property set.</b> Speed is a fact about the block - see the
     * class note - so an upgrade is a different block, which destroys the block entity and builds
     * another. That is what {@link BeltLines#beltRemoved} and {@link BeltLines#beltPlaced} are for
     * and they fire on their own, so {@code beltTurned} is wrong on this path: it is for the case
     * where the block entity <em>survives</em> and nothing else would notice.
     *
     * <p><b>The load is carried across by hand.</b> Nothing does it for free, and the default is
     * worse than nothing: replacing the block runs {@code preRemoveSideEffects}, which spills what
     * is standing here onto the floor. So it comes off the old run before the swap and goes onto
     * the new one after - see {@link BeltBlockEntity#takeCargo}. The rest of the line is untouched,
     * because every item on it is pinned to the block it is standing on.
     *
     * <p><b>The facing is kept, and this is the one place the belt-in-hand rule does not apply.</b>
     * Turning takes the way the player is looking, because pointing a belt is what that gesture is
     * for. Upgrading must not: a player walking a red belt along a line is saying <em>faster</em>,
     * not <em>this way</em>, and a line with a corner in it would be silently straightened - which
     * breaks it where it is least visible. The bend is re-read rather than copied, because the belt
     * behind this one is a different block now and so is no longer a feeder.
     *
     * <p><b>It is paid for.</b> One belt off the stack, the old one back in the player's hands,
     * unless they are in creative - the same trade breaking and re-placing would have made.
     *
     * <p><b>And it never downgrades.</b> A slower belt in hand falls through to turning, which
     * leaves the line's speed alone. Factorio allows the downgrade; here a bus is a thing a player
     * walks along with a belt in hand, and one stray click that quietly halves a main line is worth
     * more than the convenience.
     */
    private InteractionResult upgrade(ServerLevel level, BlockState state, BlockPos pos, Player player,
            ItemStack stack, BeltBlock faster) {
        List<BeltBlockEntity.Cargo> carried = level.getBlockEntity(pos) instanceof BeltBlockEntity belt
                ? belt.takeCargo()
                : List.of();

        BlockState upgraded = faster.defaultBlockState().setValue(FACING, state.getValue(FACING));
        level.setBlock(pos, withShape(upgraded, level, pos), Block.UPDATE_ALL);

        if (level.getBlockEntity(pos) instanceof BeltBlockEntity fresh) {
            fresh.giveCargo(carried);
        }

        // As after a turn: the belts either side may have stopped being corners, or started, and
        // a tier boundary is never a corner - the two belts are different blocks, so neither
        // feeds the other in the sense a bend is drawn from.
        refreshShapes(level, pos);

        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
            player.getInventory().placeItemBackInInventory(new ItemStack(state.getBlock()));
        }

        SoundType sound = upgraded.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                sound.getVolume() * 0.6F, sound.getPitch());
        return InteractionResult.SUCCESS;
    }

    // No getTicker override, deliberately. The run ticks; see BeltRun.

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BOX;
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
        if (entity.isPassenger() || entity.isShiftKeyDown()) {
            return;
        }
        Direction travel = state.getValue(FACING);
        double step = speed() / (double) Belts.UNITS_PER_BLOCK;

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
        // damage and step sounds are both worked out from this.
        boolean standing = entity.onGround();
        entity.move(MoverType.SELF, new Vec3(travel.getStepX() * step, 0.0, travel.getStepZ() * step));
        entity.setOnGround(standing);
    }

    // Nothing wakes a belt from a neighbour, and nothing needs to. A run is awake exactly while it
    // has something on it: an inserter putting an item on wakes it through the capability, and a
    // run that empties drops out of the active set by itself. A jammed run stays awake and costs
    // almost nothing, because BeltLane makes a jam free rather than expensive.
    //
    // Nor is there a hook for a neighbouring belt appearing: that belt's own block entity joins
    // the graph when it loads, and joining rebuilds the runs around it. See BeltLines.
}
