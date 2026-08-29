package com.jaguarm.nauvis.data;

import com.jaguarm.nauvis.Nauvis;

import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The pack mod's own strings.
 *
 * <p>There is very little here and there should be: the pack mod is policy, and policy is data
 * files rather than content. What it does own is the name of the datapack that carries that
 * policy, which a player sees in the world creation screen and may well be deciding whether to
 * turn off.
 *
 * <p>Run with {@code ./gradlew :nauvis:runClientData}.
 */
@EventBusSubscriber(modid = Nauvis.MODID)
public final class NauvisData {

    private NauvisData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider((PackOutput output) -> new Lang(output));
    }

    private static class Lang extends LanguageProvider {

        Lang(PackOutput output) {
            super(output, Nauvis.MODID, "en_us");
        }

        @Override
        protected void addTranslations() {
            // Named for what turning it off would do, not for what it contains. It is on by
            // default and it is the pack's progression - see ModPacks and data/removals.json.
            add("pack.nauvis.vanilla_replacement",
                    "Project Nauvis: Factorio progression (turn off to restore vanilla recipes)");
        }
    }
}
