package com.jaguarm.nauvismachines.machine.radar;

import com.jaguarm.nauvislib.transfer.MachinePower;
import com.jaguarm.nauvislib.transfer.PowerAccess;
import com.jaguarm.nauvismachines.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/** A radar: on the grid, it keeps the chunks around it loaded and ticking. */
public class RadarBlockEntity extends BlockEntity {

    /** 300 kW at the pack's ratio of 120 FE/t to a 900 kW engine: a radar is a third of an engine. */
    public static final int ENERGY_PER_TICK = 40;

    /** Five seconds of running, like every other machine's buffer. */
    public static final int ENERGY_CAPACITY = ENERGY_PER_TICK * 100;

    /** How much of its draw a radar takes at once: a second's. */
    public static final int CHART_TICKS = 20;

    /** Chunks each way from the radar's own: Factorio's seven by seven. */
    public static final int RANGE_CHUNKS = 3;

    private final MachinePower energy = new MachinePower(ENERGY_CAPACITY, this::wake);
    private final EnergyHandler gridView = new PowerAccess(energy);

    /** Whether this radar holds tickets right now. Saved, so a reload knows what it has. */
    private boolean charting;

    public RadarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADAR.get(), pos, state);
    }

    /** What a power pole fills. Registered as {@code Capabilities.Energy.BLOCK}. */
    public EnergyHandler gridView() {
        return gridView;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    /** Whether the area around this radar is being kept loaded. */
    public boolean isCharting() {
        return charting;
    }

    /** Called by {@link RadarBlock}, and only ever on a tick this radar asked for. */
    public void serverTick(ServerLevel level) {
        int draw = ENERGY_PER_TICK * CHART_TICKS;
        if (energy.getAmountAsInt() < draw) {
            // Out of power. Nothing here can wake it - the grid can, through MachinePower.
            release(level);
            setChanged();
            return;
        }
        energy.set(energy.getAmountAsInt() - draw);
        hold(level);
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), CHART_TICKS);
    }

    /** Every chunk this radar charts, its own included. */
    public static Iterable<ChunkPos> area(BlockPos radar) {
        ChunkPos middle = ChunkPos.containing(radar);
        java.util.List<ChunkPos> chunks = new java.util.ArrayList<>();
        for (int x = -RANGE_CHUNKS; x <= RANGE_CHUNKS; x++) {
            for (int z = -RANGE_CHUNKS; z <= RANGE_CHUNKS; z++) {
                chunks.add(new ChunkPos(middle.x() + x, middle.z() + z));
            }
        }
        return chunks;
    }

    private void hold(ServerLevel level) {
        if (charting) {
            return;
        }
        for (ChunkPos chunk : area(worldPosition)) {
            RadarChunks.CONTROLLER.forceChunk(level, worldPosition, chunk.x(), chunk.z(), true, false);
        }
        charting = true;
    }

    private void release(ServerLevel level) {
        if (!charting) {
            return;
        }
        for (ChunkPos chunk : area(worldPosition)) {
            RadarChunks.CONTROLLER.forceChunk(level, worldPosition, chunk.x(), chunk.z(), false, false);
        }
        charting = false;
    }

    private void wake() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!serverLevel.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            serverLevel.scheduleTick(worldPosition, block, 1);
        }
    }

    /** A chunk that has just loaded - by this radar's own ticket, as often as not - has a radar that has never been woken. */
    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    /**
     * The tickets go with the radar. Here and not in {@code setRemoved}, which a chunk unloading
     * calls too: a radar that let its tickets go every time the server stopped would never be
     * there to take them back.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            release(serverLevel);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("Energy"));
        output.putBoolean("Charting", charting);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Energy").ifPresent(energy::deserialize);
        charting = input.getBooleanOr("Charting", false);
    }
}
