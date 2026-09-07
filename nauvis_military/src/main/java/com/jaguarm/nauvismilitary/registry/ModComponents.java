package com.jaguarm.nauvismilitary.registry;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What a gun carries on itself: the magazine it has loaded, and how many rounds are left in it.
 *
 * <p>Factorio's gun has an ammunition slot beside it; a Minecraft item has components. A gun
 * with a {@link Loaded} fires from it until it is empty, then takes the next magazine out of the
 * inventory. That is what lets magazines be plain stackable items - a half-used magazine is a
 * number on the gun, not a damaged item that refuses to stack with its neighbours.
 */
public final class ModComponents {

    private ModComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, NauvisMilitary.MODID);

    /** The magazine in the gun, and the rounds left in it. */
    public record Loaded(Holder<Item> magazine, int rounds) {

        public static final Codec<Loaded> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("magazine").forGetter(Loaded::magazine),
                Codec.INT.fieldOf("rounds").forGetter(Loaded::rounds))
                .apply(i, Loaded::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Loaded> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.holderRegistry(Registries.ITEM), Loaded::magazine,
                ByteBufCodecs.VAR_INT, Loaded::rounds,
                Loaded::new);

        /** The same magazine with one round fewer. */
        public Loaded fired() {
            return new Loaded(magazine, rounds - 1);
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Loaded>> LOADED =
            COMPONENTS.registerComponentType("loaded",
                    builder -> builder.persistent(Loaded.CODEC).networkSynchronized(Loaded.STREAM_CODEC));
}
