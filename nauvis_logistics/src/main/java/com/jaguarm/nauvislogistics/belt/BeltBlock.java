package com.jaguarm.nauvislogistics.belt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * {@code docs/NEXT.md}.
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

    private static final VoxelShape SHAPE =
            Block.box(0, 0, 0, 16, Belts.HEIGHT * 16, 16);

    protected BeltBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    /** How far an item on this belt moves in one tick, in {@link Belts#UNITS_PER_BLOCK}ths. */
    public abstract int speed();

    /** The Factorio entity this is, so {@code check_models.py} can hold the speed to the wiki. */
    public abstract String factorioId();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeltBlockEntity(pos, state);
    }

    // No getTicker override, deliberately. The run ticks; see BeltRun.

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
