package com.jaguarm.nauvisfluids;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Ships the crafting-table recipes as a datapack that is present but switched off.
 *
 * <p>The timed recipes are the real ones; a bench recipe would hand the player a way to skip every
 * craft time in the pack. Both exist and this one has to be turned on, with
 * {@code /datapack enable "file/mod/nauvis_fluids:crafting_table"} or from the world creation
 * screen. The directory needs a {@code pack.mcmeta} or this throws a bare NPE naming nothing.
 */
public final class ModPacks {

    private ModPacks() {}

    static void addPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(
                Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "crafting_table"),
                PackType.SERVER_DATA,
                Component.translatable("pack.nauvis_fluids.crafting_table"),
                PackSource.FEATURE,
                false,
                Pack.Position.TOP);
    }
}
