package com.jaguarm.nauvislib.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Ships a mod's crafting-table recipes as a datapack that is present but switched off.
 *
 * <p>The timed recipes are the real ones; a shaped recipe on a bench would hand the player a way
 * to skip every craft time in the pack. Rather than choosing for the pack author, both exist and
 * this one has to be turned on - in the world creation screen's datapack list, or with
 * {@code /datapack enable "file/mod/<modid>:crafting_table"}. Every mod that ships timed recipes
 * ships one of these, from its {@code crafting_table/} resource directory, which needs a
 * {@code pack.mcmeta} or the finder throws a bare NPE naming neither the mod nor the directory.
 *
 * <p>Both the recipes and this arrangement come from Nauvis Materials, which did it
 * first; {@code tools/gen_recipes.py} generates all three files for every item that can fit a
 * crafting grid.
 *
 * <h2>Switched off entirely while gametests run</h2>
 *
 * <p><b>{@code GameTestServer} force-enables every datapack it can see.</b> Vanilla selects
 * {@code getAvailableIds()}, so the {@code false} below - which is what keeps this pack off in a
 * real world until somebody asks for it - means nothing there, and a bench copy of a recipe ships
 * under the <em>same id</em> as the timed one it replaces. The suite was quietly testing shapeless
 * recipes: nothing failed, and what was lost was every craft time in the pack.
 *
 * <p>So the pack is not offered at all when {@link #PROPERTY} is {@code false}, which every
 * {@code gameTestServer} run in the pack sets and nothing else does. Not offered rather than
 * offered-and-off, because being offered is the whole of the problem. A player never sets the
 * property, so a real world is exactly as it was.
 */
public final class BenchRecipePacks {

    /**
     * The property that keeps every bench pack out of a gametest run. {@code false} means "do
     * not offer the bench recipes at all"; anything else, including its absence, is the ordinary
     * behaviour. One property for every mod, because a run enables them all together and turning
     * off one of seven would be worse than turning off none.
     */
    public static final String PROPERTY = "jaguarm.benchRecipePacks";

    private BenchRecipePacks() {}

    /**
     * Offers {@code modid}'s bench pack, titled by {@code pack.<modid>.crafting_table}. Call from
     * a mod bus listener on {@link AddPackFindersEvent}.
     */
    public static void add(AddPackFindersEvent event, String modid) {
        if ("false".equalsIgnoreCase(System.getProperty(PROPERTY))) {
            return;
        }

        event.addPackFinders(
                Identifier.fromNamespaceAndPath(modid, "crafting_table"),
                PackType.SERVER_DATA,
                Component.translatable("pack." + modid + ".crafting_table"),
                PackSource.FEATURE,
                false,
                Pack.Position.TOP);
    }
}
