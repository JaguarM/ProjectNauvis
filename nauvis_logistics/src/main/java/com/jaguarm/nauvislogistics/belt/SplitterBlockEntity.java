package com.jaguarm.nauvislogistics.belt;

import java.util.ArrayList;
import java.util.List;

import com.jaguarm.nauvislogistics.multiblock.Multiblock;
import com.jaguarm.nauvislogistics.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The block entity of a splitter. Sits at the anchor block (left track, cell 0).
 *
 * <p>Holds four internal tracks/lanes (two tracks, left and right, each with two lanes).
 *
 * <h2>It is ticked by {@link BeltLines}, on both sides</h2>
 *
 * <p>Not by a scheduled block tick. A splitter is one block of belt with a rule about which way
 * items leave, so it has to be part of the same simulation the runs either side of it are, and
 * that simulation runs on the client too - which is what lets a belt show moving items without a
 * packet per item per tick. See {@link BeltRun}. A splitter on a scheduled tick is a splitter the
 * client never advances: items go in, nothing comes out, and the belt in front of it stays empty
 * however well the server is routing them.
 *
 * <p>So {@link #wake()} joins {@link BeltLines}' active set rather than the tick queue, and
 * {@link #tick} is called from there. The sleep rule of non-negotiable #5 is unchanged and is the
 * same one a run keeps: a splitter with nothing on it is not in the set and costs nothing.
 *
 * <h2>Balancing</h2>
 * <p>Items entering from either input track are carried across the 1-block deck. When reaching the
 * exit (distance 0), the splitter hands off to the preferred output track (alternating 50/50 per
 * lane). If the preferred output is missing or backed up, it overflows to the open output. If both
 * outputs are blocked, the item waits at the front of the splitter.
 */
public class SplitterBlockEntity extends BlockEntity {

    /**
     * One item on the deck: which track it came in on, which it is leaving by, and where it is.
     *
     * <p>{@code exit} is what makes a splitter look like one. An item is told where it is going
     * the moment it arrives, so the crossing is drawn as the diagonal it is rather than as a
     * block-wide sideways jump at the far edge.
     */
    public record Cargo(int track, int exit, int lane, int offset, ItemResource item) {

        public static final Codec<Cargo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("track").forGetter(Cargo::track),
                Codec.INT.fieldOf("exit").forGetter(Cargo::exit),
                Codec.INT.fieldOf("lane").forGetter(Cargo::lane),
                Codec.INT.fieldOf("at").forGetter(Cargo::offset),
                ItemResource.CODEC.fieldOf("item").forGetter(Cargo::item))
                .apply(instance, Cargo::new));

        public static final Codec<List<Cargo>> LIST = CODEC.listOf();
    }

    private final BeltLane[][] lanes = {
        {new BeltLane(), new BeltLane()},
        {new BeltLane(), new BeltLane()}
    };

    /**
     * The track each item on the matching lane is leaving by, leading item first.
     *
     * <p>Parallel to {@code lanes[track][lane]}, and kept in step by the only two places that put
     * something on a lane or take it off - {@link #insertAt} and {@link #takeLead}. It is a list
     * beside the lane rather than a field on the item because {@link BeltLane} stores gaps rather
     * than items with properties, and that representation is the whole reason a belt is cheap.
     */
    private final IntArrayList[][] exits = {
        {new IntArrayList(), new IntArrayList()},
        {new IntArrayList(), new IntArrayList()}
    };

    /**
     * Which output track the next item to arrive on each lane will be sent to.
     *
     * <p>Alternated as items <em>arrive</em>, not as they leave, so an item knows where it is
     * going for the whole crossing and can be drawn going there. Where it actually ends up can
     * still differ: an item whose track is jammed or missing takes the other one at the exit,
     * which is what makes a splitter a balancer and a merger as well as a splitter.
     */
    private final int[] preferredTrack = {SplitterShape.LEFT_TRACK, SplitterShape.LEFT_TRACK};

    public SplitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPLITTER.get(), pos, state);
    }

    public BeltLane lane(int track, int lane) {
        return lanes[track][lane];
    }

    public boolean hasRoomAt(int track, int lane, int position) {
        return lanes[track][lane].hasRoomAt(position);
    }

    public boolean insertAt(int track, int lane, int position, ItemResource item) {
        return insertAt(track, lane, position, item, preferredTrack[lane]);
    }

    private boolean insertAt(int track, int lane, int position, ItemResource item, int exit) {
        if (!lanes[track][lane].hasRoomAt(position)) {
            return false;
        }
        int index = lanes[track][lane].insertAt(position, item);
        exits[track][lane].add(index, exit);
        preferredTrack[lane] = 1 - exit;
        wake();
        setChanged();
        return true;
    }

    /** Takes the leading item off a lane, and the exit that went with it. */
    private ItemResource takeLead(int track, int lane) {
        exits[track][lane].removeInt(0);
        return lanes[track][lane].removeAt(0);
    }

    /** Which track the leading item of a lane is bound for. */
    private int leadExit(int track, int lane) {
        return exits[track][lane].getInt(0);
    }

    public boolean isEmpty() {
        return lanes[0][0].isEmpty() && lanes[0][1].isEmpty()
                && lanes[1][0].isEmpty() && lanes[1][1].isEmpty();
    }

    public int itemCount() {
        return lanes[0][0].size() + lanes[0][1].size()
                + lanes[1][0].size() + lanes[1][1].size();
    }

    public void wake() {
        if (level != null) {
            BeltLines.of(level).markActive(this);
        }
    }

    public void tick(Level level) {
        BlockState state = getBlockState();
        Direction facing = state.getValue(SplitterBlock.FACING);

        // Asked of the block every tick rather than kept here, because the tier is the block: a
        // fast splitter is a FastSplitterBlock against this same block entity type, and a speed
        // remembered in the block entity - or read through a class name - would be the yellow
        // one's. See SplitterBlock.
        int speed = ((SplitterBlock) state.getBlock()).speed();

        // 1. Hand off items reaching the front (position == 0). Each one goes to the track it was
        //    given on arrival, or to the other one if that is jammed or is not there at all.
        for (int lane = 0; lane < Belts.LANES; lane++) {
            for (int track = 0; track < 2; track++) {
                if (lanes[track][lane].lead() == 0) {
                    int bound = leadExit(track, lane);
                    if (!tryOutput(level, bound, lane, track, facing)) {
                        tryOutput(level, 1 - bound, lane, track, facing);
                    }
                }
            }
        }

        // 2. Advance all lanes
        boolean moved = false;
        for (int track = 0; track < 2; track++) {
            for (int lane = 0; lane < Belts.LANES; lane++) {
                moved |= lanes[track][lane].advance(speed);
            }
        }

        if (moved) {
            setChanged();
        }
    }

    private boolean tryOutput(Level level, int targetTrack, int lane, int sourceTrack, Direction facing) {
        BlockPos cellPos = SplitterShape.SHAPE.cellPos(worldPosition, targetTrack, facing);
        BlockPos outputPos = cellPos.relative(facing);

        // Case A: Next block is a BeltRun
        BeltRun other = BeltLines.of(level).runAt(outputPos);
        if (other != null && !other.isDissolved()) {
            int index = other.indexOf(outputPos);
            if (index >= 0 && other.travelAt(index) == facing) {
                int targetPosition = other.frontEdge(index) + Belts.UNITS_PER_BLOCK;
                if (other.lane(lane).hasRoomAt(targetPosition)) {
                    ItemResource item = takeLead(sourceTrack, lane);
                    other.lane(lane).insertAt(targetPosition, item);
                    BeltLines.of(level).markActive(other);
                    other.markChanged(outputPos);
                    return true;
                }
            }
            return false;
        }

        // Case B: Next block is another Splitter
        BlockState targetState = level.getBlockState(outputPos);
        if (targetState.getBlock() instanceof SplitterBlock otherSplitterBlock) {
            if (otherSplitterBlock.facing(targetState) == facing) {
                BlockPos anchor = Multiblock.anchorPos(otherSplitterBlock, targetState, outputPos);
                if (level.getBlockEntity(anchor) instanceof SplitterBlockEntity otherSplitter) {
                    int otherTrack = Multiblock.part(otherSplitterBlock, targetState);
                    if (otherSplitter.hasRoomAt(otherTrack, lane, Belts.UNITS_PER_BLOCK)) {
                        ItemResource item = takeLead(sourceTrack, lane);
                        otherSplitter.insertAt(otherTrack, lane, Belts.UNITS_PER_BLOCK, item);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /** Which track the item at {@code index} of this lane is leaving by. */
    public int exitOf(int track, int lane, int index) {
        return exits[track][lane].getInt(index);
    }

    /**
     * Where an item on the deck is in the world, in level coordinates.
     *
     * <p>{@link BeltRun#pointAt} for a splitter, and the same idea: an item goes from the edge it
     * came in at to the edge it leaves by, and the drawing falls out of that rather than being a
     * second description of the machine. The difference is that a splitter's two edges can be on
     * different tracks, so the line across is a diagonal - which is what a Factorio splitter looks
     * like, and the reason an item is told its exit on arrival rather than at the far side.
     *
     * <p>Lanes do not cross. An item on the left lane of the left track leaves on the left lane of
     * whichever track it is bound for, a block over.
     */
    public Vec3 pointAt(int track, int exit, int lane, double position, Direction facing) {
        // 0 at the edge it came in over, 1 at the edge it leaves by.
        double progress = Math.clamp(1.0 - position / Belts.UNITS_PER_BLOCK, 0.0, 1.0);

        BlockPos from = SplitterShape.SHAPE.cellPos(worldPosition, track, facing);
        BlockPos to = SplitterShape.SHAPE.cellPos(worldPosition, exit, facing);
        Vec3 in = new Vec3(from.getX() + 0.5, from.getY() + Belts.HEIGHT, from.getZ() + 0.5);
        Vec3 out = new Vec3(to.getX() + 0.5, to.getY() + Belts.HEIGHT, to.getZ() + 0.5);

        Vec3 point = in.lerp(out, progress)
                .add(facing.getStepX() * (progress - 0.5), 0, facing.getStepZ() * (progress - 0.5));
        Direction side = Belts.sideOf(facing, lane);
        return point.add(side.getStepX() * Belts.LANE_OFFSET, 0, side.getStepZ() * Belts.LANE_OFFSET);
    }

    public List<Cargo> cargo() {
        List<Cargo> list = new ArrayList<>();
        for (int track = 0; track < 2; track++) {
            for (int lane = 0; lane < Belts.LANES; lane++) {
                var positions = lanes[track][lane].positions();
                for (int i = 0; i < positions.size(); i++) {
                    list.add(new Cargo(track, exits[track][lane].getInt(i), lane,
                            positions.getInt(i), lanes[track][lane].item(i)));
                }
            }
        }
        return list;
    }

    /**
     * Placement, chunk load and a client update tag all arrive here, on both sides.
     *
     * <p>Deliberately no check for a server level, for the reason in the class note.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    @Override
    public void setRemoved() {
        leave();
        super.setRemoved();
    }

    /** Fires just before the chunk goes. Deregistering twice is harmless; missing it is not. */
    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        leave();
    }

    private void leave() {
        if (level != null) {
            BeltLines.of(level).forgetSplitter(this);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) {
            return;
        }
        for (Cargo item : cargo()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    item.item().toStack(1));
        }
        clearLanes();
    }

    /** Empties every lane and the exits beside them, which must never drift apart. */
    private void clearLanes() {
        for (int track = 0; track < 2; track++) {
            for (int lane = 0; lane < Belts.LANES; lane++) {
                lanes[track][lane].clear();
                exits[track][lane].clear();
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        List<Cargo> items = cargo();
        if (!items.isEmpty()) {
            output.store("Cargo", Cargo.LIST, items);
        }
        output.putInt("PrefLeft", preferredTrack[Belts.LEFT]);
        output.putInt("PrefRight", preferredTrack[Belts.RIGHT]);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        clearLanes();
        List<Cargo> items = input.read("Cargo", Cargo.LIST).orElse(List.of());
        for (Cargo item : items) {
            if (item.track() >= 0 && item.track() < 2 && item.lane() >= 0 && item.lane() < Belts.LANES) {
                int index = lanes[item.track()][item.lane()].insertAt(item.offset(), item.item());
                exits[item.track()][item.lane()].add(index, Math.clamp(item.exit(), 0, 1));
            }
        }
        preferredTrack[Belts.LEFT] = input.getInt("PrefLeft").orElse(SplitterShape.LEFT_TRACK);
        preferredTrack[Belts.RIGHT] = input.getInt("PrefRight").orElse(SplitterShape.LEFT_TRACK);
        // A splitter that is already loaded is being re-read - a client update tag, most likely -
        // and it has just been given items that nothing else will announce. On first load the
        // level is still null here and onLoad does it instead.
        wake();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
