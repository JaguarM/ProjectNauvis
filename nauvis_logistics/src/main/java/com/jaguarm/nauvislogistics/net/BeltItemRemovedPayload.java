package com.jaguarm.nauvislogistics.net;

import com.jaguarm.nauvislogistics.NauvisLogistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: something has taken an item off the belt at {@code belt}.
 *
 * <p>The other half of {@link BeltItemAddedPayload}. A client never invents a removal of its own -
 * it cannot know whether the chest an inserter is filling had room - so an item that reaches the
 * end of a line simply waits there until this arrives, which is also exactly what the server's
 * copy of it is doing.
 *
 * <p>The offset says which item, rather than the client assuming it is the leading one: the two
 * sides can be a tick apart, and picking the nearest item to a named place is right whether or not
 * they are.
 */
public record BeltItemRemovedPayload(BlockPos belt, int lane, int offset) implements CustomPacketPayload {

    public static final Type<BeltItemRemovedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "belt_item_removed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeltItemRemovedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BeltItemRemovedPayload::belt,
                    ByteBufCodecs.VAR_INT, BeltItemRemovedPayload::lane,
                    ByteBufCodecs.VAR_INT, BeltItemRemovedPayload::offset,
                    BeltItemRemovedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
