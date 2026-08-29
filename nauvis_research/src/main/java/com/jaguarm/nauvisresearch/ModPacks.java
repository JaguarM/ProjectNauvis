package com.jaguarm.nauvisresearch;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Ships the crafting-table recipes as a datapack that is present but switched off.
 *
 * <p>The directory needs a {@code pack.mcmeta} or this throws a bare NPE naming neither the mod
 * nor the directory - see {@code docs/PITFALLS.md}.
 */
public final class ModPacks {

    private ModPacks() {}

    static void addPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(
                Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "crafting_table"),
                PackType.SERVER_DATA,
                Component.translatable("pack.nauvis_research.crafting_table"),
                PackSource.FEATURE,
                false,
                Pack.Position.TOP);
    }
}
