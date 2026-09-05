package com.jaguarm.nauvisresearch.client;

import org.jetbrains.annotations.Nullable;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.jaguarm.nauvisresearch.research.ClientResearch;
import com.jaguarm.nauvisresearch.research.ModTechnologies;
import com.jaguarm.nauvisresearch.research.Technology;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * What is being researched, in the corner, all the time.
 *
 * <p>Factorio has this and it is doing more work than it looks like: research is the one thing in
 * the game that progresses while you are not looking at it, so a screen you have to open to check
 * on it is a screen you forget to open. <b>And when there is nothing to show it says so</b> - the
 * prompt to go and pick something is the most useful state this widget has, because "nothing is
 * being researched" is otherwise indistinguishable from "something is being researched slowly".
 *
 * <h2>Three states, and the middle one is the interesting one</h2>
 *
 * <ol>
 *   <li>a technology is being researched - its name and how far through its units it is;</li>
 *   <li>nothing is, but a <b>triggered</b> technology is part-way there - its name and how many of
 *       the thing it wants have been made. This is what a new world sees for its whole opening,
 *       and without it the first half hour would show the prompt below while research was in fact
 *       happening every time the player took a plate out of a furnace;</li>
 *   <li>neither - the prompt, naming whatever key the player has actually bound.</li>
 * </ol>
 */
@EventBusSubscriber(modid = NauvisResearch.MODID, value = Dist.CLIENT)
public final class ResearchHud {

    private ResearchHud() {}

    /**
     * Wide enough for the prompt, and it grows for a long technology name.
     *
     * <p>Factorio's is a fixed slab because its font and its strings are its own; ours has to hold
     * whatever a datapack calls a technology, in whatever language, so the box is measured from
     * the text rather than the text squeezed into the box.
     */
    private static final int MIN_TEXT = 108;

    private static final int ICON = 16;
    private static final int PADDING = 4;
    private static final int BAR_HEIGHT = 4;
    private static final int MARGIN = 4;

    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_BACKGROUND = 0xA0141414;
    private static final int COLOR_TRACK = 0xFF2A2A2A;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF909090;
    /** The same blue the lab's bar and the research screen use for the same thing. */
    private static final int COLOR_PROGRESS = 0xFF6FC3DF;
    /** A trigger is not science, and looking different is the point. */
    private static final int COLOR_TRIGGER = 0xFF6FDF8F;

    /** The flask beside it, which is what a player's eye actually finds in the corner. */
    private static final Identifier ICON_ITEM =
            Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "science_pack_1");

    @SubscribeEvent
    static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(NauvisResearch.MODID, "research_progress"),
                ResearchHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // No F1 check: a layer registered through RegisterGuiLayersEvent is part of the HUD pass,
        // which vanilla skips wholesale when the interface is hidden. Facrafting's craft bar makes
        // the same assumption.
        if (player == null || minecraft.level == null) {
            return;
        }

        Component label;
        Component detail = null;
        float progress = -1.0f;
        int colour = COLOR_PROGRESS;

        ResourceKey<Technology> current = ClientResearch.current();
        Technology technology = current == null ? null : technologyOf(minecraft, current);

        if (technology != null) {
            label = technology.title(current);
            detail = Component.literal(ClientResearch.units() + " / " + technology.units());
            progress = technology.units() <= 0 ? 0.0f
                    : ClientResearch.units() / (float) technology.units();
        } else {
            Holder.Reference<Technology> waiting = closestTrigger(minecraft);
            if (waiting == null) {
                // The prompt, and it names the real key rather than a hard-coded letter - a player
                // who rebound it would otherwise be told to press something that does nothing.
                // The key is coloured out of the sentence the way Factorio's is, because the whole
                // job of this line is to be read at a glance while doing something else.
                label = Component.translatable("hud.nauvis_research.no_research",
                        ResearchKey.OPEN_RESEARCH.getTranslatedKeyMessage().copy()
                                .withStyle(ChatFormatting.AQUA));
            } else {
                Technology.Trigger trigger = waiting.value().trigger().orElseThrow();
                int made = Math.min(ClientResearch.tally(trigger), trigger.count());
                label = waiting.value().title(waiting.key());
                detail = Component.literal(made + " / " + trigger.count());
                progress = made / (float) trigger.count();
                colour = COLOR_TRIGGER;
            }
        }

        int text = minecraft.font.width(label)
                + (detail == null ? 0 : 8 + minecraft.font.width(detail));
        int textWidth = Math.max(text, MIN_TEXT);
        int width = PADDING + ICON + PADDING + textWidth + PADDING;

        int rows = minecraft.font.lineHeight + (progress < 0 ? 0 : BAR_HEIGHT + 3);
        int height = Math.max(rows, ICON) + PADDING * 2;

        int x = graphics.guiWidth() - width - MARGIN;
        int y = MARGIN;

        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, COLOR_FRAME);
        graphics.fill(x, y, x + width, y + height, COLOR_BACKGROUND);

        graphics.item(new ItemStack(BuiltInRegistries.ITEM.getValue(ICON_ITEM)),
                x + PADDING, y + (height - ICON) / 2);

        int textX = x + PADDING + ICON + PADDING;
        int textY = y + (height - rows) / 2;
        graphics.text(minecraft.font, label, textX, textY,
                progress < 0 ? COLOR_TEXT : COLOR_TEXT, false);
        if (detail != null) {
            graphics.text(minecraft.font, detail,
                    x + width - PADDING - minecraft.font.width(detail), textY, COLOR_MUTED, false);
        }
        if (progress >= 0) {
            int barY = textY + minecraft.font.lineHeight + 3;
            int barRight = x + width - PADDING;
            graphics.fill(textX, barY, barRight, barY + BAR_HEIGHT, COLOR_TRACK);
            int filled = Math.round((barRight - textX) * Math.clamp(progress, 0.0f, 1.0f));
            graphics.fill(textX, barY, textX + filled, barY + BAR_HEIGHT, colour);
        }
    }

    /**
     * The triggered technology closest to firing, of the ones that could fire next.
     *
     * <p>Closest by fraction rather than by count, so "one lab of one" outranks "forty iron plates
     * of fifty" - the one about to happen is the one worth watching. Ones with no progress at all
     * are ignored, or a fresh world would advertise a technology nobody has started working on.
     */
    private static @Nullable Holder.Reference<Technology> closestTrigger(Minecraft minecraft) {
        Holder.Reference<Technology> best = null;
        float bestFraction = 0.0f;

        for (Holder.Reference<Technology> holder : ModTechnologies.all(minecraft.level.registryAccess())) {
            Technology technology = holder.value();
            if (!technology.isTriggered() || ClientResearch.isCompleted(holder.key())) {
                continue;
            }
            if (!ClientResearch.isAvailable(minecraft.level.registryAccess(), holder.key())) {
                continue;
            }
            Technology.Trigger trigger = technology.trigger().orElseThrow();
            float fraction = Math.min(ClientResearch.tally(trigger), trigger.count())
                    / (float) trigger.count();
            if (fraction > bestFraction) {
                best = holder;
                bestFraction = fraction;
            }
        }
        return best;
    }

    private static @Nullable Technology technologyOf(Minecraft minecraft, ResourceKey<Technology> key) {
        return ModTechnologies.registry(minecraft.level.registryAccess()).get(key)
                .map(Holder.Reference::value).orElse(null);
    }
}
