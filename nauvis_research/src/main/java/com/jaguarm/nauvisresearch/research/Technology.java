package com.jaguarm.nauvisresearch.research;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;

/**
 * One technology: what it costs, what it needs first, and what it hands over.
 *
 * <p>This is Factorio's model and not a simplification of it. A technology is worth <b>N units</b>;
 * a unit consumes <b>one of each</b> science pack it names and takes a fixed time; a lab works on
 * whichever technology the world is researching and reports units as it finishes them; at N units
 * the technology completes and its recipes unlock. Nothing here is a per-player fact - see
 * {@link ResearchState} for why research belongs to the world.
 *
 * <p><b>Or it has a {@link Trigger} instead, and no cost at all.</b> "Craft fifty iron plates",
 * "craft a lab" - it finishes the moment that happens, with no lab and no packs. That is what
 * makes the opening work: the first technologies are triggered, so a new world researches its way
 * to the boiler and the lab with a pickaxe and a furnace, and only then does science become a
 * thing you build for. A technology has exactly one of the two, which
 * {@link #isTriggered()} answers.
 *
 * <p><b>Every field is generated.</b> {@code tools/gen_technologies.py} writes these from Wube's
 * own prototype data, the same way recipes are written from the recipe dump, and
 * {@code checkTechnologies} in the build fails if a file on disk disagrees with it. A research
 * cost is identity in exactly the sense an ingredient list is: it lives in world saves and in the
 * player's head. If you are about to edit one of these files by hand, you are doing it wrong.
 *
 * <h2>Why three of these fields are loose ids rather than registry objects</h2>
 *
 * <p>{@link #packs} is a list of item ids and not a list of {@code Item}s, and that is load-bearing.
 * A registry codec <em>throws</em> on an id nothing has registered, and it throws while loading the
 * file - so a single technology naming {@code nauvis_research:science_pack_2}, which no mod
 * registers yet, would fail to load. The whole tree ships from the first commit, most of it ahead
 * of the items it names, so the tree has to survive naming things that are not there. It does:
 * a technology whose packs do not all exist simply cannot be researched, which is the truth.
 *
 * <p>{@link #unlocks} and {@link #prerequisites} are keys for the same reason and get it for free -
 * a {@code ResourceKey} is a name and validates nothing.
 */
public record Technology(
        String name,
        String order,
        List<ResourceKey<Technology>> prerequisites,
        int units,
        int ticksPerUnit,
        List<Identifier> packs,
        Optional<Trigger> trigger,
        List<ResourceKey<Recipe<?>>> unlocks) {

    /**
     * Finish this technology when the world has made {@code count} of {@code item}.
     *
     * <p>Counted across everything that makes an item - a bench, a furnace, a machine, the
     * crafting panel - because in Factorio all four are "crafting" and a player who smelted fifty
     * iron plates has plainly done the thing the trigger is asking about.
     */
    public record Trigger(Identifier item, int count) {

        public static final Codec<Trigger> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("item").forGetter(Trigger::item),
                Codec.intRange(1, 100_000).optionalFieldOf("count", 1).forGetter(Trigger::count))
                .apply(i, Trigger::new));
    }

    /** Ten minutes a unit. Long enough for anything Factorio has, short enough to catch a typo. */
    private static final int MAX_TICKS_PER_UNIT = 20 * 60 * 10;

    /** Factorio's dearest finite technology is 5000 units; the cap is far past it. */
    private static final int MAX_UNITS = 1_000_000;

    public static final Codec<Technology> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Technology::name),
            Codec.STRING.optionalFieldOf("order", "").forGetter(Technology::order),
            ResourceKey.codec(ModTechnologies.REGISTRY).listOf().optionalFieldOf("prerequisites", List.of())
                    .forGetter(Technology::prerequisites),
            Codec.intRange(0, MAX_UNITS).optionalFieldOf("units", 0).forGetter(Technology::units),
            Codec.intRange(0, MAX_TICKS_PER_UNIT).optionalFieldOf("ticks_per_unit", 0)
                    .forGetter(Technology::ticksPerUnit),
            Identifier.CODEC.listOf().optionalFieldOf("packs", List.of()).forGetter(Technology::packs),
            Trigger.CODEC.optionalFieldOf("trigger").forGetter(Technology::trigger),
            ResourceKey.codec(net.minecraft.core.registries.Registries.RECIPE).listOf()
                    .optionalFieldOf("unlocks", List.of()).forGetter(Technology::unlocks))
            .apply(i, Technology::new));

    /**
     * What the screen draws.
     *
     * <p>Wube publishes no locale files with its prototype data, so the generator derives an
     * English string by rule and ships it as the <em>fallback</em> here. Anything that rule got
     * wrong is one lang entry - {@code technology.nauvis_research.<path>} - away from being right,
     * in any language, without regenerating the tree.
     */
    public Component title(ResourceKey<Technology> key) {
        return Component.translatableWithFallback(
                "technology." + key.identifier().getNamespace() + "." + key.identifier().getPath(), name);
    }

    /** True when this finishes by somebody crafting something rather than by a lab. */
    public boolean isTriggered() {
        return trigger.isPresent();
    }

    /**
     * The pack items this technology wants, or null when one of them is not registered.
     *
     * <p>Null rather than a short list on purpose: a technology whose packs are half-registered is
     * not researchable at a discount, it is not researchable at all. Most of the tree is in that
     * state today and will be until the packs above red science exist.
     */
    public List<Item> packItems() {
        List<Item> items = new java.util.ArrayList<>(packs.size());
        for (Identifier id : packs) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
                return null;
            }
            items.add(item);
        }
        return items;
    }

    /**
     * Whether this technology can be worked on at all.
     *
     * <p>A triggered one always can - there is nothing to hold. A costed one needs every science
     * pack it names to be a registered item, which is the check that keeps a lab from being
     * pointed at something asking for a pack no mod has built yet.
     */
    public boolean isResearchable() {
        return isTriggered() || (!packs.isEmpty() && packItems() != null);
    }
}
