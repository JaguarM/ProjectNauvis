package com.jaguarm.nauvispower.grid;

import java.util.Arrays;
import java.util.List;

import com.mojang.serialization.Codec;

import org.jspecify.annotations.Nullable;

import com.jaguarm.nauvispower.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * A pole's membership of a network, and the wires it draws. One class and one block entity type
 * for every tier, because a tier differs by how far it reaches and not by anything saved here.
 */
public class ElectricPoleBlockEntity extends BlockEntity {

    private static final long[] NONE = new long[0];

    /**
     * The tallest and widest any pole gets, in blocks, used as the allowance at both ends of every
     * wire in {@link #wireBounds()}.
     *
     * <p>The tier at the far end is not read. This is a culling box - being too big costs a pole
     * that is drawn when it need not have been, being too small costs a wire that vanishes while
     * you are looking at it - so one number covering every tier is the right trade, and it saves a
     * block state read per link on a path that runs for every pole on screen.
     */
    private static final int TALLEST = 6;

    private static final Codec<List<Long>> LINKS_CODEC = Codec.LONG.listOf();

    /**
     * Packed positions of the poles this one is wired to, both ways round.
     *
     * <p>Saved with the chunk only because that is what puts it in the update tag; it is derived
     * data and {@link PowerNetworkManager} overwrites it the moment the pole loads.
     */
    private long[] links = NONE;

    public ElectricPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_POLE.get(), pos, state);
    }

    public long[] links() {
        return links;
    }

    /** A box containing this pole and every wire hanging off it. */
    public AABB wireBounds() {
        double minX = worldPosition.getX();
        double minY = worldPosition.getY();
        double minZ = worldPosition.getZ();
        double maxX = minX + TALLEST;
        double maxY = minY + TALLEST;
        double maxZ = minZ + TALLEST;

        for (long link : links) {
            BlockPos other = BlockPos.of(link);
            minX = Math.min(minX, other.getX());
            minY = Math.min(minY, other.getY());
            minZ = Math.min(minZ, other.getZ());
            maxX = Math.max(maxX, other.getX() + TALLEST);
            maxY = Math.max(maxY, other.getY() + TALLEST);
            maxZ = Math.max(maxZ, other.getZ() + TALLEST);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Called by the manager, and by nothing else.
     *
     * <p>Silent when the set has not actually changed. A pole in the middle of a run has its links
     * recomputed every time any neighbour is touched, and every one of those would otherwise be a
     * block update sent to every player watching the chunk.
     */
    void setLinks(long[] wired) {
        if (Arrays.equals(links, wired)) {
            return;
        }
        links = wired;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Placement and chunk load arrive here the same way, and both mean the same thing. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel
                && getBlockState().getBlock() instanceof ElectricPoleBlock pole) {
            PowerNetworkManager.of(serverLevel).polePlaced(worldPosition, pole);
        }
    }

    /**
     * Broken, replaced, or the chunk being cleared.
     *
     * <p>Leaving is the expensive half of a pole's life - it can split one network into two - but
     * it only happens when a pole actually goes, which is rare next to twenty ticks a second.
     */
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
        if (level instanceof ServerLevel serverLevel) {
            PowerNetworkManager.of(serverLevel).poleRemoved(worldPosition);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (links.length > 0) {
            // ValueOutput has putIntArray and no long equivalent, so the codec does it.
            output.store("Links", LINKS_CODEC, Arrays.stream(links).boxed().toList());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        links = input.read("Links", LINKS_CODEC)
                .map(stored -> stored.stream().mapToLong(Long::longValue).toArray())
                .orElse(NONE);
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
