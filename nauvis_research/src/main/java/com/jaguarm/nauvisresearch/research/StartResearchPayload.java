package com.jaguarm.nauvisresearch.research;

import java.util.Optional;

import com.jaguarm.nauvisresearch.NauvisResearch;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * C2S: "research this next", or an empty key for "stop researching".
 *
 * <p>Carries nothing but the key. There is no container id to check against, unlike Facrafting's
 * recipe selection, because research is not attached to a machine - anyone may change what the
 * world is working on, the same way anyone in Factorio may. The server still refuses a key whose
 * prerequisites are not met; see {@link Research#setCurrent}.
 */
public record StartResearchPayload(Optional<ResourceKey<Technology>> technology)
        implements CustomPacketPayload {

    public static final Type<StartResearchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "start_research"));

    private static final StreamCodec<ByteBuf, ResourceKey<Technology>> KEY_CODEC =
            ResourceKey.streamCodec(ModTechnologies.REGISTRY);

    public static final StreamCodec<RegistryFriendlyByteBuf, StartResearchPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(KEY_CODEC), StartResearchPayload::technology,
                    StartResearchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
