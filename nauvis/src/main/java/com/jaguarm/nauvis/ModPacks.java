package com.jaguarm.nauvis;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Ships the vanilla-recipe removals as a built-in datapack that is always on.
 *
 * <h2>Why this is a datapack and not just resources</h2>
 *
 * <p>A removal is a file at {@code data/minecraft/recipe/<name>.json} that shadows Minecraft's
 * own. Putting it in this mod's plain resources works for most recipes and <b>silently fails for
 * about three hundred and eighty of them</b>, because <b>NeoForge ships its own copy of them</b> -
 * retagged versions using {@code #c:ingots/iron} and friends so that other mods' metals work in
 * vanilla recipes - and NeoForge's resources are applied after ours.
 *
 * <p>The hopper is one of the three hundred and eighty, which is how this was found: three of the
 * four removals took effect and the hopper did not, from four byte-identical files.
 * {@code nauvis:vanilla_recipes_are_replaced} is the test that caught it, and it is the reason
 * that test asserts against a running recipe manager rather than reading the files.
 *
 * <p>A pack added here sits above every mod's resources, so it outranks NeoForge as well as
 * vanilla. {@code alwaysActive} is <b>true</b>, unlike the {@code crafting_table} packs in the
 * subsystem mods: those exist so a pack author can opt back into bench crafting, and this one is
 * the pack itself. A player who turns it off in the world creation screen has turned Project
 * Nauvis's progression off, which is their business.
 */
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
