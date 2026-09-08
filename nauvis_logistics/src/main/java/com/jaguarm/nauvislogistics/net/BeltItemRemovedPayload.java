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
