package com.jaguarm.nauvispower.generator;

import com.jaguarm.nauvispower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * A solar panel: makes electricity from daylight, and nothing else.
 *
 * <p>Factorio's panel peaks at 60 kW, which at this pack's ratio - a 900 kW steam engine is
 * 120 FE a tick - is {@value #PEAK} FE a tick. The engine keeps that ratio too, so the arithmetic
 * a player knows holds: it takes a hair over twenty-three panels to match one engine at noon,
 * and a panel makes nothing at night, which is why Factorio's solar power comes with accumulators
 * and this pack's does not yet - see {@code GAPS.md}.
 *
 * <h2>Daylight is the sky's darkening</h2>
 *
 * <p>{@code Level.getSkyDarken()} is the number the game already keeps for how much the sky is
 * dimmed: 0 at noon, 11 at midnight, and part way in rain and at dusk. The output is the peak
 * scaled by how far from 11 it is, so a panel ramps up at dawn and down at dusk the way
 * Factorio's does, and loses a third in rain, which Factorio's does not - a divergence this pack
 * is content with, since weather is a thing Minecraft has and a solar panel that ignored it
 * would read as broken. The fraction is carried between ticks rather than rounded away.
 *
 * <p>And the panel has to see the sky. A panel under a roof makes nothing, which is the one thing
 * Minecraft can say about solar power that Factorio cannot.
 *
 * <h2>Sleeping</h2>
 *
 * <p>No ticker. A panel full of power stops and is woken by whatever draws from it, through
 * {@link GeneratorAccess}, exactly as the steam engine is. A panel in the dark, or under a roof, has
 * no work and no signal that will arrive when the sun does - there is no event for dawn - so it
 * looks up once every {@value #RECHECK_TICKS} ticks, which is the one bounded poll in this mod and
 * is written down in {@code GAPS.md}. Everything else about it is free.
 */
public class SolarPanelBlockEntity extends BlockEntity {

    /** 60 kW at the pack's ratio of 120 FE/t to a 900 kW engine. The ratio is what is kept. */
    public static final int PEAK = 8;

    /** Five seconds of noon. Enough to ride out a cloud, small enough to be worth filling. */
    public static final int ENERGY_CAPACITY = PEAK * 100;

    /** How dark the sky can get: {@code getSkyDarken()} at midnight. */
    public static final int FULL_DARK = 11;

    /** How often a panel with nothing to make looks up again. Ten seconds. */
    public static final int RECHECK_TICKS = 200;

    /** Unrestricted, so the panel can fill it. What the outside world gets is {@link #cableView}. */
    private final SimpleEnergyHandler energy =
            new SimpleEnergyHandler(ENERGY_CAPACITY, ENERGY_CAPACITY, ENERGY_CAPACITY);

    /** What cables and poles see: extraction only, and a wake-up on the way out. */
    private final EnergyHandler cableView = new GeneratorAccess(energy, this::wake);

    /** The fraction of an FE carried between ticks, in elevenths. */
    private int owed;

    /** What the last tick made, for the readout. */
    private int lastOutput;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    public EnergyHandler cableView() {
        return cableView;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    /** FE a tick the panel made on its last tick: the peak in full sun, nothing at night. */
    public int lastOutput() {
        return lastOutput;
    }

    /** Whether the sky is open above the middle of the panel. */
    public boolean seesSky(ServerLevel level) {
        return level.canSeeSky(worldPosition.above());
    }

    /** What the panel makes this tick, in elevenths of the peak. See {@link #elevenths(int, boolean)}. */
    public int elevenths(ServerLevel level) {
        return elevenths(level.getSkyDarken(), seesSky(level));
    }

    /**
     * What a panel makes a tick, in elevenths of the peak: the peak times how far the sky is from
     * full dark. Zero under a roof, at midnight, or both; the peak times eleven at noon.
     *
     * <p>A pure function of the two things a panel reads, so the night can be tested headlessly -
     * the gametest world's sky stands at noon whatever the clock is set to.
     */
    public static int elevenths(int skyDarken, boolean seesSky) {
        if (!seesSky) {
            return 0;
        }
        int light = FULL_DARK - Math.clamp(skyDarken, 0, FULL_DARK);
        return PEAK * light;
    }

    /** Called by {@link SolarPanelBlock}, and only ever on a tick this panel asked for. */
    public void serverTick(ServerLevel level) {
        if (energy.getAmountAsInt() >= ENERGY_CAPACITY) {
            // Full. Nothing wakes it but something drawing energy, which GeneratorAccess does.
            lastOutput = 0;
            return;
        }

        int made = elevenths(level);
        if (made == 0) {
            // Dark, or roofed over. No signal will come when either changes - the sun has no
            // event - so look again in a while.
            lastOutput = 0;
            owed = 0;
            setChanged();
            level.scheduleTick(worldPosition, getBlockState().getBlock(), RECHECK_TICKS);
            return;
        }

        owed += made;
        int whole = owed / FULL_DARK;
        owed %= FULL_DARK;
        lastOutput = whole;
        energy.set(Math.min(ENERGY_CAPACITY, energy.getAmountAsInt() + whole));
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** A chunk that has just loaded has a panel that has never been woken. */
    @Override
    public void onLoad() {
        super.onLoad();
        wake();
    }

    /**
     * Schedules the next tick unless one is already coming.
     *
     * <p>A panel waiting out the night has a tick a long way off; a neighbour change or a draw
     * cannot bring it forward, because the scheduler keeps the first. That is fine for a draw - a
     * panel in the dark has nothing to give - and for a roof coming off, ten seconds late is the
     * price of not polling.
     */
    void wake() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!serverLevel.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            serverLevel.scheduleTick(worldPosition, block, 1);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("Energy"));
        output.putInt("Owed", owed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Energy").ifPresent(energy::deserialize);
        owed = input.getIntOr("Owed", 0);
    }
}
