package com.jaguarm.nauvislogistics.net;

import com.jaguarm.nauvislogistics.NauvisLogistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * S2C: something that is not a belt has put an item on the belt at {@code belt}.
 *
 * <p>One of only two messages a belt ever sends. The client runs the same belt simulation the
 * server does, out of the same block states, so everything a belt does by itself needs no message
 * at all - it is only the boundary with the rest of the world, an inserter or a hopper, that the
 * client cannot predict.
 *
 * <p>The place is named as a block and an offset into it rather than as a distance along a run,
 * because the two sides need not agree on where a run begins: a client only has the belts in its
 * loaded chunks. A block and an offset mean the same thing on both.
 */
public record BeltItemAddedPayload(BlockPos belt, int lane, int offset, ItemResource item)
        implements CustomPacketPayload {

    public static final Type<BeltItemAddedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "belt_item_added"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeltItemAddedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BeltItemAddedPayload::belt,
                    ByteBufCodecs.VAR_INT, BeltItemAddedPayload::lane,
                    ByteBufCodecs.VAR_INT, BeltItemAddedPayload::offset,
                    ItemResource.STREAM_CODEC, BeltItemAddedPayload::item,
                    BeltItemAddedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
