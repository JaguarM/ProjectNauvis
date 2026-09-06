package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.NauvisFluids;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

/** The tags this mod reads. What is in each is data, in {@code NauvisFluidsData}. */
public final class ModTags {

    /**
     * What an offshore pump may stand at and draw from.
     *
     * <p>Ships holding {@code nauvis_fluids:water} and nothing else - the still water of a lake or
     * the sea, and not a bucket's, which is the rule that makes water something you build out to.
     * A tag rather than a check on the fluid so that a pack wanting Minecraft's infinite water
     * back can add {@code minecraft:water} to it and change no code.
     */
    public static final TagKey<Fluid> OFFSHORE_PUMPABLE = TagKey.create(Registries.FLUID,
            Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "offshore_pumpable"));

    private ModTags() {}
}
