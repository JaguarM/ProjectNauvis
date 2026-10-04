package com.jaguarm.nauvismining.registry;

import com.jaguarm.nauvismining.NauvisMining;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {

    private ModTags() {}

    /**
     * The ores a drill takes when it is left to itself: iron, copper and coal, the ores Factorio's
     * patches are made of. A pack adds an ore by tagging it.
     */
    public static final TagKey<Block> FACTORIO_ORES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(NauvisMining.MODID, "factorio_ores"));
}
