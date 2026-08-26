package com.jaguarm.nauvispower;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Ships the crafting-table recipes as a datapack that is present but switched off. */
public final class ModPacks {

    private ModPacks() {}

    static void addPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(
                Identifier.fromNamespaceAndPath(NauvisPower.MODID, "crafting_table"),
                PackType.SERVER_DATA,
                Component.translatable("pack.nauvis_power.crafting_table"),
                PackSource.FEATURE,
                false,
                Pack.Position.TOP);
    }
}
