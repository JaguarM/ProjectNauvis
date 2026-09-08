package com.jaguarm.nauvisresearch.research;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;

/** One technology: what it costs, what it needs first, and what it hands over. */
public record Technology(
        String name,
        String order,
        List<ResourceKey<Technology>> prerequisites,
        int units,
        int ticksPerUnit,
        List<Identifier> packs,
        Optional<Trigger> trigger,
        List<ResourceKey<Recipe<?>>> unlocks,
        List<Modifier> modifiers) {

    /**
     * An effect that is not a recipe: so much more of some named thing, once this is
     * researched.
     */
    public record Modifier(String type, Optional<String> target, double modifier) {

        public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(Modifier::type),
                Codec.STRING.optionalFieldOf("target").forGetter(Modifier::target),
                Codec.DOUBLE.fieldOf("modifier").forGetter(Modifier::modifier))
                .apply(i, Modifier::new));

        /**
         * What the screen says for it: a whole number as a count, a fraction as a percentage.
         *
         * <p>The key is {@code modifier.nauvis_research.<type>} with the amount and the target as
         * its arguments; the fallback is the type's own words, so a modifier the lang file has
         * never heard of still reads as something rather than as a key.
         */
        public Component describe() {
            boolean whole = modifier == Math.rint(modifier);
            String amount = (modifier >= 0 ? "+" : "")
                    + (whole ? String.valueOf((long) modifier) : Math.round(modifier * 100) + "%");
            String fallback = type.replace('-', ' ') + " " + amount
                    + target.map(t -> " (" + t + ")").orElse("");
            return Component.translatableWithFallback("modifier.nauvis_research." + type, fallback,
                    amount, target.orElse(""));
        }
    }

    /**
     * Finish this technology when the world has done something {@code count} times over.
     */
    public record Trigger(Kind kind, Identifier target, int count) {

        /** The verb. The tally for each is kept separately - see {@link ResearchState}. */
        public enum Kind {
            CRAFT,
            MINE
        }

        /** The two keys as they appear in a technology file, one of which must be present. */
        private record Raw(Optional<Identifier> item, Optional<Identifier> mine, int count) {}

        private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.optionalFieldOf("item").forGetter(Raw::item),
                Identifier.CODEC.optionalFieldOf("mine").forGetter(Raw::mine),
                Codec.intRange(1, 100_000).optionalFieldOf("count", 1).forGetter(Raw::count))
                .apply(i, Raw::new));

        public static final Codec<Trigger> CODEC = RAW_CODEC.comapFlatMap(
                raw -> {
                    if (raw.item().isPresent() == raw.mine().isPresent()) {
                        return DataResult.error(() -> "a trigger names exactly one of 'item' or 'mine'");
                    }
                    return DataResult.success(raw.item().isPresent()
                            ? new Trigger(Kind.CRAFT, raw.item().get(), raw.count())
                            : new Trigger(Kind.MINE, raw.mine().get(), raw.count()));
                },
                trigger -> new Raw(
                        trigger.kind() == Kind.CRAFT ? Optional.of(trigger.target()) : Optional.empty(),
                        trigger.kind() == Kind.MINE ? Optional.of(trigger.target()) : Optional.empty(),
                        trigger.count()));
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
                    .optionalFieldOf("unlocks", List.of()).forGetter(Technology::unlocks),
            Modifier.CODEC.listOf().optionalFieldOf("modifiers", List.of())
                    .forGetter(Technology::modifiers))
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

    /** True when this finishes by somebody doing something rather than by a lab. */
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
