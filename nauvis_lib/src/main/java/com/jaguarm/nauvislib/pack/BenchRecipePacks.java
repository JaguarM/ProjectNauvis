package com.jaguarm.nauvislib.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Ships a mod's crafting-table recipes as a datapack that is present but switched off. */
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
