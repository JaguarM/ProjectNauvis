package com.jaguarm.nauvisresearch.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.Research;
import com.jaguarm.nauvisresearch.research.ResearchState;
import com.jaguarm.nauvisresearch.research.Technology;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /research}: the tree, by hand.
 *
 * <p>Research is per world and the only way to move it in game is to build a lab, power it, feed
 * it packs and wait - which is the right way round for playing and hopeless for looking at
 * anything. Half of what this pack has built is behind a technology, so <b>every playtest of a
 * machine was previously a playtest of the whole early game first</b>. That is what this is for.
 *
 * <p>It is not a gameplay path and does not pretend to be: gamemaster permission, the same level
 * {@code /time} and {@code /gamemode} want, so a survival world's players cannot reach it.
 *
 * <h2>Grant and forget both cascade, in opposite directions</h2>
 *
 * <p>{@code grant} completes a technology <em>and every prerequisite it has</em>, because what a
 * person testing wants is "put me where I can build a solar panel" and not a list of eleven names
 * in dependency order. {@code forget} drops a technology <em>and everything that depends on it</em>
 * for the mirror-image reason, and because the alternative is a tree that says a technology is
 * researched while its prerequisite is not - a state nothing else in the pack produces, so making
 * it easy to produce by accident would mean every odd screen afterwards had two possible causes.
 *
 * <p>Both report how many technologies moved rather than just the one named, so a cascade that did
 * more than you expected says so at the time.
 */
@EventBusSubscriber(modid = NauvisResearch.MODID)
public final class ResearchCommand {

    private ResearchCommand() {}

    private static final String ARGUMENT = "technology";

    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.nauvis_research.research.unknown", id));

    private static String key(String suffix) {
        return "commands.nauvis_research.research." + suffix;
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("research")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), null))
                        .then(Commands.literal("done").executes(c -> list(c.getSource(), State.DONE)))
                        .then(Commands.literal("available").executes(c -> list(c.getSource(), State.AVAILABLE)))
                        .then(Commands.literal("locked").executes(c -> list(c.getSource(), State.LOCKED))))
                .then(Commands.literal("info").then(technology()
                        .executes(context -> info(context.getSource(), named(context)))))
                .then(Commands.literal("grant").then(technology()
                        .executes(context -> grant(context.getSource(), named(context)))))
                .then(Commands.literal("forget").then(technology()
                        .executes(context -> forget(context.getSource(), named(context)))))
                .then(Commands.literal("start").then(technology()
                        .executes(context -> start(context.getSource(), named(context)))))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                .then(Commands.literal("all").executes(context -> all(context.getSource())))
                .then(Commands.literal("reset").executes(context -> reset(context.getSource()))));
    }

    /**
     * The technology argument, which suggests from the registry.
     *
     * <p>{@link ResourceKeyArgument} rather than a plain identifier plus a suggestion provider of
     * our own: the technology registry is a synced datapack registry, so the client already has
     * every key and vanilla's argument type already knows how to offer them.
     */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> technology() {
        return Commands.argument(ARGUMENT, ResourceKeyArgument.key(ModTechnologies.REGISTRY));
    }

    private static ResourceKey<Technology> named(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ResourceKey<Technology> key =
                ResourceKeyArgument.getRegistryKey(context, ARGUMENT, ModTechnologies.REGISTRY, UNKNOWN);
        // The argument type parses any identifier, so an id nothing registered gets this far. The
        // registry is the thing that knows, and saying so beats a silent no-op.
        if (registry(context.getSource()).get(key).isEmpty()) {
            throw UNKNOWN.create(key.identifier());
        }
        return key;
    }

    private static net.minecraft.core.Registry<Technology> registry(CommandSourceStack source) {
        return ModTechnologies.registry(source.getServer().registryAccess());
    }

    // --- what a technology is right now -----------------------------------------------------

    private enum State { DONE, CURRENT, AVAILABLE, LOCKED }

    private static State stateOf(MinecraftServer server, ResourceKey<Technology> key) {
        ResearchState state = Research.state(server);
        if (state.isCompleted(key)) {
            return State.DONE;
        }
        if (key.equals(state.current())) {
            return State.CURRENT;
        }
        return Research.isAvailable(server, key) ? State.AVAILABLE : State.LOCKED;
    }

    private static ChatFormatting colour(State state) {
        return switch (state) {
            case DONE -> ChatFormatting.GREEN;
            case CURRENT -> ChatFormatting.AQUA;
            case AVAILABLE -> ChatFormatting.WHITE;
            case LOCKED -> ChatFormatting.DARK_GRAY;
        };
    }

    private static Component name(ResourceKey<Technology> key, Technology technology, State state) {
        return technology.title(key).copy().withStyle(colour(state));
    }

    // --- the subcommands ---------------------------------------------------------------------

    private static int list(CommandSourceStack source, State only) {
        MinecraftServer server = source.getServer();
        List<Component> names = new ArrayList<>();
        int[] counts = new int[State.values().length];

        for (Holder.Reference<Technology> holder : ModTechnologies.all(server.registryAccess())) {
            State state = stateOf(server, holder.key());
            counts[state.ordinal()]++;
            if (only == null ? state != State.LOCKED : state == only) {
                names.add(name(holder.key(), holder.value(), state));
            }
        }

        source.sendSuccess(() -> Component.translatable(key("counts"),
                counts[State.DONE.ordinal()], counts[State.CURRENT.ordinal()],
                counts[State.AVAILABLE.ordinal()], counts[State.LOCKED.ordinal()]), false);
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(key("none")), false);
        } else {
            source.sendSuccess(() -> Component.translatable(key("names"),
                    ComponentUtils.join(names)), false);
        }
        return names.size();
    }

    private static int info(CommandSourceStack source, ResourceKey<Technology> key) {
        MinecraftServer server = source.getServer();
        Technology technology = registry(source).getValueOrThrow(key);
        State state = stateOf(server, key);

        source.sendSuccess(() -> Component.translatable(key("info.name"),
                name(key, technology, state), key.identifier().toString()), false);
        source.sendSuccess(() -> Component.translatable(key("info.state"),
                Component.translatable(key("state." + state.name().toLowerCase(java.util.Locale.ROOT)))
                        .withStyle(colour(state))), false);

        if (technology.isTriggered()) {
            Technology.Trigger trigger = technology.trigger().orElseThrow();
            source.sendSuccess(() -> Component.translatable(key("info.trigger"),
                    trigger.count(), trigger.item().toString(),
                    Research.state(server).made(trigger.item())), false);
        } else {
            source.sendSuccess(() -> Component.translatable(key("info.cost"),
                    technology.units(), technology.ticksPerUnit() / 20.0F,
                    technology.packs().isEmpty()
                            ? Component.translatable(key("info.no_packs"))
                            : Component.literal(String.join(", ",
                                    technology.packs().stream().map(Object::toString).toList()))),
                    false);
            if (!technology.isResearchable()) {
                source.sendSuccess(() -> Component.translatable(key("info.unbuildable"))
                        .withStyle(ChatFormatting.RED), false);
            }
        }

        source.sendSuccess(() -> Component.translatable(key("info.prerequisites"),
                technology.prerequisites().isEmpty()
                        ? Component.translatable(key("info.none"))
                        : ComponentUtils.join(technology.prerequisites().stream()
                                .map(p -> name(p, registry(source).getValueOrThrow(p), stateOf(server, p)))
                                .toList())), false);
        source.sendSuccess(() -> Component.translatable(key("info.unlocks"),
                technology.unlocks().isEmpty()
                        ? Component.translatable(key("info.none"))
                        : Component.literal(String.join(", ",
                                technology.unlocks().stream()
                                        .map(r -> r.identifier().toString()).toList()))), false);
        return 1;
    }

    private static int grant(CommandSourceStack source, ResourceKey<Technology> key) {
        MinecraftServer server = source.getServer();
        ResearchState state = Research.state(server);

        Set<ResourceKey<Technology>> wanted = withPrerequisites(source, key);
        wanted.removeIf(state::isCompleted);
        if (wanted.isEmpty()) {
            source.sendFailure(Component.translatable(key("already"),
                    registry(source).getValueOrThrow(key).title(key)));
            return 0;
        }

        wanted.forEach(state::complete);
        Research.changedExternally(server);
        source.sendSuccess(() -> Component.translatable(key("granted"),
                registry(source).getValueOrThrow(key).title(key), wanted.size()), true);
        return wanted.size();
    }

    private static int forget(CommandSourceStack source, ResourceKey<Technology> key) {
        MinecraftServer server = source.getServer();
        ResearchState state = Research.state(server);

        Set<ResourceKey<Technology>> wanted = withDependants(source, key);
        wanted.removeIf(candidate -> !state.isCompleted(candidate));
        if (wanted.isEmpty()) {
            source.sendFailure(Component.translatable(key("not_researched"),
                    registry(source).getValueOrThrow(key).title(key)));
            return 0;
        }

        wanted.forEach(state::forget);
        // Whatever the labs were pointed at may now be unreachable, so it stops being current.
        if (state.current() != null && !Research.isAvailable(server, state.current())) {
            state.setCurrent(null);
        }
        Research.changedExternally(server);
        source.sendSuccess(() -> Component.translatable(key("forgot"),
                registry(source).getValueOrThrow(key).title(key), wanted.size()), true);
        return wanted.size();
    }

    private static int start(CommandSourceStack source, ResourceKey<Technology> key) {
        MinecraftServer server = source.getServer();
        if (!Research.setCurrent(server, key)) {
            source.sendFailure(Component.translatable(key("cannot_start"),
                    registry(source).getValueOrThrow(key).title(key)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(key("started"),
                registry(source).getValueOrThrow(key).title(key)), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (Research.state(server).current() == null) {
            source.sendFailure(Component.translatable(key("nothing")));
            return 0;
        }
        Research.setCurrent(server, null);
        source.sendSuccess(() -> Component.translatable(key("stopped")), true);
        return 1;
    }

    /**
     * Everything the tree can currently reach, in one go.
     *
     * <p>A loop rather than one pass, because completing a technology makes its dependants
     * available. It stops when a pass grants nothing, which leaves exactly the technologies that
     * are unreachable for a reason - the ones asking for a science pack no mod registers yet, of
     * which most of the tree is one today.
     */
    private static int all(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ResearchState state = Research.state(server);
        int granted = 0;
        boolean again = true;
        while (again) {
            again = false;
            for (Holder.Reference<Technology> holder : ModTechnologies.all(server.registryAccess())) {
                if (Research.isAvailable(server, holder.key())) {
                    state.complete(holder.key());
                    granted++;
                    again = true;
                }
            }
        }
        if (granted == 0) {
            source.sendFailure(Component.translatable(key("nothing_available")));
            return 0;
        }
        Research.changedExternally(server);
        int total = granted;
        source.sendSuccess(() -> Component.translatable(key("all"), total), true);
        return total;
    }

    private static int reset(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ResearchState state = Research.state(server);
        List<ResourceKey<Technology>> done = List.copyOf(state.completed());
        if (done.isEmpty() && state.current() == null) {
            source.sendFailure(Component.translatable(key("nothing_researched")));
            return 0;
        }
        done.forEach(state::forget);
        state.setCurrent(null);
        Research.changedExternally(server);
        source.sendSuccess(() -> Component.translatable(key("reset"), done.size()), true);
        return done.size();
    }

    // --- walking the tree ---------------------------------------------------------------------

    /** A technology and everything it needs, transitively. */
    private static Set<ResourceKey<Technology>> withPrerequisites(CommandSourceStack source,
            ResourceKey<Technology> key) {
        Set<ResourceKey<Technology>> found = new LinkedHashSet<>();
        Deque<ResourceKey<Technology>> frontier = new ArrayDeque<>(List.of(key));
        while (!frontier.isEmpty()) {
            ResourceKey<Technology> next = frontier.removeFirst();
            if (!found.add(next)) {
                continue;
            }
            registry(source).getOptional(next)
                    .ifPresent(technology -> frontier.addAll(technology.prerequisites()));
        }
        return found;
    }

    /** A technology and everything that needs it, transitively. */
    private static Set<ResourceKey<Technology>> withDependants(CommandSourceStack source,
            ResourceKey<Technology> key) {
        Set<ResourceKey<Technology>> found = new LinkedHashSet<>(List.of(key));
        boolean again = true;
        while (again) {
            again = false;
            for (Holder.Reference<Technology> holder :
                    ModTechnologies.all(source.getServer().registryAccess())) {
                if (found.contains(holder.key())) {
                    continue;
                }
                if (holder.value().prerequisites().stream().anyMatch(found::contains)) {
                    found.add(holder.key());
                    again = true;
                }
            }
        }
        return found;
    }

    /** Comma-separated, because {@code ComponentUtils.formatList} wants a lookup we have not got. */
    private static final class ComponentUtils {

        private ComponentUtils() {}

        static Component join(List<Component> parts) {
            net.minecraft.network.chat.MutableComponent joined = Component.empty();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    joined.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                joined.append(parts.get(i));
            }
            return joined;
        }
    }
}
