package com.jaguarm.nauvisresearch.research;

import java.util.List;
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
 * S2C: the whole research state.
 *
 * <p>The whole of it, every time, rather than a delta. It is a list of finished technology keys,
 * one optional key and one int; the list grows to a couple of hundred entries over a whole
 * playthrough and is sent when a unit of research finishes, which is at best once every five
 * seconds. A delta would save a few hundred bytes and cost the property that makes this easy to
 * reason about: <b>a client is either exactly up to date or exactly one message behind.</b>
 */
public record ResearchSyncPayload(
        List<ResourceKey<Technology>> completed,
        Optional<ResourceKey<Technology>> current,
        int units) implements CustomPacketPayload {

    public static final Type<ResearchSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "research_sync"));

    /**
     * A technology key on the wire is its {@code Identifier}, not a registry index.
     *
     * <p>Which is what makes this survivable: a client whose datapack does not have a technology
     * the server has finished reads the key, finds nothing in its own registry, and draws
     * nothing - rather than the whole packet failing to decode because an index was out of range.
     */
    private static final StreamCodec<ByteBuf, ResourceKey<Technology>> KEY_CODEC =
            ResourceKey.streamCodec(ModTechnologies.REGISTRY);

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    KEY_CODEC.apply(ByteBufCodecs.list()), ResearchSyncPayload::completed,
                    ByteBufCodecs.optional(KEY_CODEC), ResearchSyncPayload::current,
                    ByteBufCodecs.VAR_INT, ResearchSyncPayload::units,
                    ResearchSyncPayload::new);

    public static ResearchSyncPayload of(ResearchState state) {
        return new ResearchSyncPayload(
                List.copyOf(state.completed()),
                Optional.ofNullable(state.current()),
                state.units());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
