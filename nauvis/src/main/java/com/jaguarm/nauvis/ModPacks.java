package com.jaguarm.nauvis;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Ships the vanilla-recipe removals as a built-in datapack that is always on. */
public final class ModPacks {

    private ModPacks() {}

    static void addPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(
                Identifier.fromNamespaceAndPath(Nauvis.MODID, "vanilla_replacement"),
                PackType.SERVER_DATA,
                Component.translatable("pack.nauvis.vanilla_replacement"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP);
    }
}
