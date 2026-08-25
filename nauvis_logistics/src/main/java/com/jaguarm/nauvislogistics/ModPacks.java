package com.jaguarm.nauvislogistics;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Ships the crafting-table recipes as a datapack that is present but switched off.
 *
 * <p>The timed recipes are the real ones; a shaped recipe on a bench would hand the player a way
 * to skip every craft time in the pack. Rather than choosing for the pack author, both exist and
 * this one has to be turned on - in the world creation screen's datapack list, or with
 * {@code /datapack enable "file/mod/nauvis_logistics:crafting_table"}.
 *
 * <p>Both the recipes and this arrangement come from Neo Progressive Materials, which did it
 * first; {@code tools/gen_recipes.py} generates all three files for every item that can fit a
 * crafting grid.
 */
public final class ModPacks {

    private ModPacks() {}

    static void addPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(
                Identifier.fromNamespaceAndPath(NauvisLogistics.MODID, "crafting_table"),
                PackType.SERVER_DATA,
                Component.translatable("pack.nauvis_logistics.crafting_table"),
                PackSource.FEATURE,
                false,
                Pack.Position.TOP);
    }
}
