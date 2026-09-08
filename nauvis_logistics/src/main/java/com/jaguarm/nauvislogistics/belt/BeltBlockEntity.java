package com.jaguarm.nauvislogistics.belt;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvislogistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A belt's membership of a run, and the items standing on its own block when nobody is asking
 * the run.
 */
public class BeltBlockEntity extends BlockEntity {

    /** One item on this block: which lane, and how far along, measured the way the run measures. */
    public record Cargo(int lane, int offset, ItemResource item) {

        public static final Codec<Cargo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("lane").forGetter(Cargo::lane),
                Codec.INT.fieldOf("at").forGetter(Cargo::offset),
                ItemResource.CODEC.fieldOf("item").forGetter(Cargo::item))
                .apply(instance, Cargo::new));

        public static final Codec<List<Cargo>> LIST = CODEC.listOf();
    }

    /**
     * What was on this block when it was last written, and what will be put back on the run when
     * it next joins one. Empty while the run has it, which is nearly always.
     */
    private List<Cargo> stored = List.of();

    public BeltBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSPORT_BELT.get(), pos, state);
    }

    /** What is standing on this block right now, from the run if there is one. */
    public List<Cargo> cargo() {
        BeltRun run = run();
        if (run == null) {
            return stored;
        }
        List<BeltRun.Parked> parked = run.itemsOn(worldPosition);
        List<Cargo> out = new ArrayList<>(parked.size());
        for (BeltRun.Parked item : parked) {
            out.add(new Cargo(item.lane(), item.offset(), item.item()));
        }
        return out;
    }

    public @Nullable BeltRun run() {
        return level == null ? null : BeltLines.of(level).runAt(worldPosition);
    }

    /**
     * What an inserter, a hopper or another mod's machine sees. Both sides of the belt answer, and
     * which side asked decides which lane it meets - see {@link BeltAccess}.
     */
    public BeltAccess access(@Nullable Direction side) {
        return new BeltAccess(this, side);
    }

    // --- joining and leaving ---------------------------------------------------------------------

    /**
     * Placement and chunk load arrive here the same way, on both sides.
     *
     * <p>The client builds runs too - that is what lets it draw moving items without being told
     * where each one is - so this deliberately does not check for a server level.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) {
            return;
        }
        BeltLines.of(level).beltPlaced(worldPosition);
        handOverStored();
        if (level instanceof ServerLevel server) {
            // Whether this is drawn as a bend is worked out from its neighbours, and there are two
            // ways to arrive without anyone having asked: put here by a command or a structure, or
            // loaded from disk beside a chunk that had not arrived yet. See BeltBlock.refreshShapes.
            BeltBlock.refreshShapes(server, worldPosition);
        }
    }

    /** A belt's own state changed under it - it was turned, or it started or stopped climbing. */
    @Override
    public void setBlockState(BlockState state) {
        BlockState old = getBlockState();
        super.setBlockState(state);
        if (level == null || old == state || !old.is(state.getBlock())) {
            return;
        }
        if (old.getValue(BeltBlock.FACING) != state.getValue(BeltBlock.FACING)
                || old.getValue(BeltBlock.SHAPE) != state.getValue(BeltBlock.SHAPE)) {
            BeltLines.of(level).beltTurned(worldPosition);
        }
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
            BeltLines.of(level).beltRemoved(worldPosition);
        }
    }

    /** Spills what is standing on this block when it is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        BeltRun run = run();
        List<Cargo> spilled = run == null ? stored : takeFrom(run);
        stored = List.of();
        if (level.isClientSide()) {
            return;
        }
        for (Cargo item : spilled) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    item.item().toStack(1));
        }
    }

    /** Lifts everything standing on this block off whatever run has it, and hands it over. */
    public List<Cargo> takeCargo() {
        BeltRun run = run();
        List<Cargo> taken = run == null ? stored : takeFrom(run);
        stored = List.of();
        return taken;
    }

    /** Puts a lifted load onto this belt, now or as soon as it has a run. */
    public void giveCargo(List<Cargo> cargo) {
        if (cargo.isEmpty()) {
            return;
        }
        stored = List.copyOf(cargo);
        handOverStored();
    }

    private List<Cargo> takeFrom(BeltRun run) {
        List<Cargo> taken = new ArrayList<>();
        for (BeltRun.Parked item : run.takeOn(worldPosition)) {
            taken.add(new Cargo(item.lane(), item.offset(), item.item()));
        }
        return taken;
    }

    /**
     * Puts what was saved onto the run this belt belongs to, replacing whatever it had here.
     *
     * <p>Replacing rather than adding, so that this is idempotent: the same list arrives both from
     * disk and, on a client, from the update tag as the chunk is sent, and a belt that is already
     * in a run must end up with what was written rather than with two copies of it.
     */
    private void handOverStored() {
        if (stored.isEmpty() || level == null) {
            return;
        }
        BeltRun run = run();
        if (run == null) {
            return;
        }
        int index = run.indexOf(worldPosition);
        if (index < 0) {
            return;
        }
        run.takeOn(worldPosition);
        for (Cargo item : stored) {
            run.place(item.lane(), run.frontEdge(index) + item.offset(), item.item());
        }
        stored = List.of();
        BeltLines.of(level).markActive(run);
    }

    // --- disk and wire ---------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        List<Cargo> items = cargo();
        if (!items.isEmpty()) {
            output.store("Cargo", Cargo.LIST, items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stored = input.read("Cargo", Cargo.LIST).orElse(List.of());
        // A block entity that is already in a run is being re-read - a client update tag, most
        // likely - so hand the items straight over rather than waiting for a load that has been.
        handOverStored();
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
