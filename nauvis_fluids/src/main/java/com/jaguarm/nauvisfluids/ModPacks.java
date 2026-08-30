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
 *
 * <h2>Switched off entirely while gametests run</h2>
 *
 * <p><b>{@code GameTestServer} force-enables every datapack it can see.</b> Vanilla selects
 * {@code getAvailableIds()}, so the {@code false} below - which is what keeps this pack off in a
 * real world until somebody asks for it - means nothing there, and a bench copy of a recipe ships
 * under the <em>same id</em> as the timed one it replaces. The suite was quietly testing shapeless
 * recipes: nothing failed, and what was lost was every craft time in the pack.
 *
 * <p>So the pack is not offered at all when {@link #BENCH_RECIPES} is {@code false}, which the
 * {@code gameTestServer} run in {@code build.gradle} sets and nothing else does. Not offered rather
 * than offered-and-off, because being offered is the whole of the problem. A player never sets the
 * property, so a real world is exactly as it was.
 */
public final class ModPacks {

    /**
     * The property that keeps this pack out of a gametest run. {@code false} means "do not offer
     * the bench recipes at all"; anything else, including its absence, is the ordinary behaviour.
     *
     * <p>Shared by every mod here that ships one of these packs, because a run enables them all
     * together and turning off one of seven would be worse than turning off none.
     */
    public static final String BENCH_RECIPES = "jaguarm.benchRecipePacks";

    private ModPacks() {}

    static void addPackFinders(AddPackFindersEvent event) {
        if ("false".equalsIgnoreCase(System.getProperty(BENCH_RECIPES))) {
            return;
        }

        event.addPackFinders(
                Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "crafting_table"),
                PackType.SERVER_DATA,
                Component.translatable("pack.nauvis_fluids.crafting_table"),
                PackSource.FEATURE,
                false,
                Pack.Position.TOP);
    }
}
