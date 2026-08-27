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
 * A pole's membership of a network, and the wires it draws.
 *
 * <p>It never ticks. It exists for three lifecycle hooks a block alone does not get - {@code
 * onLoad} when its chunk arrives, {@code setRemoved} when it is broken, {@code onChunkUnloaded}
 * when its chunk leaves - and, since there are wires, for one piece of state.
 *
 * <p>Only the {@link PolePart#FOOT} of a pole has one of these. The other three blocks are
 * structure.
 *
 * <h2>The wires</h2>
 *
 * <p>{@link #links()} is the poles this one is wired to. It is not a second copy of the graph and
 * nothing reads it on the server: it exists so the <em>client</em> knows what to draw, because a
 * client has no {@link PowerNetworkManager} and working it out over there would mean every pole
 * scanning a fifteen-block cube for other poles and then somehow noticing when one changed.
 *
 * <p>The manager already computes exactly this set whenever a pole is placed or removed, which is
 * the only time it can change, so it pushes it here and this pushes it to the clients watching.
 * That is also why there is no wire coil and no connector to place: poles that can see each other
 * are wired, because the network already said so.
 */
public class SmallElectricPoleBlockEntity extends BlockEntity {

    private static final long[] NONE = new long[0];

    private static final Codec<List<Long>> LINKS_CODEC = Codec.LONG.listOf();

    /**
     * Packed positions of the poles this one is wired to, both ways round.
     *
     * <p>Saved with the chunk only because that is what puts it in the update tag; it is derived
     * data and {@link PowerNetworkManager} overwrites it the moment the pole loads.
     */
    private long[] links = NONE;

    public SmallElectricPoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMALL_ELECTRIC_POLE.get(), pos, state);
    }

    public long[] links() {
        return links;
    }

    /**
     * A box containing this pole and every wire hanging off it.
     *
     * <p><b>This is what decides whether the wires are drawn at all.</b> A block entity renderer is
     * frustum-culled against its render bounding box, and the default is the one-block cube at the
     * block entity - which for a pole is the block at its foot. So the moment that one block leaves
     * the screen every wire attached to it vanished, while the wire itself was still in plain view.
     *
     * <p>It lives here rather than in the renderer so that a headless test can assert it, because
     * the thing it goes wrong as is invisible geometry rather than an exception.
     *
     * <p>The sag needs no allowance: a wire dips at most {@code span * SAG} below the line between
     * two heads, which is under a block, and this box already reaches from the heads down to the
     * feet.
     */
    public AABB wireBounds() {
        double minX = worldPosition.getX();
        double minY = worldPosition.getY();
        double minZ = worldPosition.getZ();
        double maxX = minX + 1;
        double maxY = minY + SmallElectricPoleBlock.HEIGHT;
        double maxZ = minZ + 1;

        for (long link : links) {
            BlockPos other = BlockPos.of(link);
            minX = Math.min(minX, other.getX());
            minY = Math.min(minY, other.getY());
            minZ = Math.min(minZ, other.getZ());
            maxX = Math.max(maxX, other.getX() + 1);
            maxY = Math.max(maxY, other.getY() + SmallElectricPoleBlock.HEIGHT);
            maxZ = Math.max(maxZ, other.getZ() + 1);
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
        if (level instanceof ServerLevel serverLevel) {
            PowerNetworkManager.of(serverLevel).polePlaced(worldPosition);
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
