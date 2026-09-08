package com.jaguarm.nauvis.data;

import com.jaguarm.nauvis.ModContent;
import com.jaguarm.nauvis.Nauvis;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** The pack mod's own strings and models. */
@EventBusSubscriber(modid = Nauvis.MODID)
public final class NauvisData {

    private NauvisData() {}

    @SubscribeEvent
    static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(Models::new);
        event.createProvider((PackOutput output) -> new Lang(output));
    }

    /** Flat item icons. The art comes from {@code texture-workshop/make_material_textures.py}. */
    private static class Models extends ModelProvider {

        Models(PackOutput output) {
            super(output, Nauvis.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
            itemModels.generateFlatItem(ModContent.SOLID_FUEL.get(), ModelTemplates.FLAT_ITEM);
        }
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
            addItem(ModContent.SOLID_FUEL, "Solid fuel");
        }
    }
}
