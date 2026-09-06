package com.jaguarm.nauvisfluids.processing;

import java.util.List;

import com.jaguarm.facrafting.client.ClientRecipes;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.nauvislib.client.MachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The screen of a refinery or a chemical plant: a bar for every tank, in the fluid's own colour,
 * the progress bar and the charge between them, and one line saying what is going on.
 *
 * <p>No recipe list here - that is the panel's job. Where each bar sits is the subclass's, as
 * constants {@code tools/check_gui_layout.py} can read; what a bar shows and what it says when
 * hovered is the same for both machines and lives here.
 */
public abstract class ProcessingScreen<M extends ProcessingMenu> extends MachineScreen<M> {

    /** One bar, in the panel's frame. */
    public record Box(int x, int y, int width, int height) {}

    protected ProcessingScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** A bar per tank, inputs first, in the order the menu counts them. */
    protected abstract List<Box> tankBars();

    protected abstract Box progressBar();

    protected abstract int chargeX();

    protected abstract int chargeY();

    @Override
    protected void paint(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        List<Box> bars = tankBars();
        for (int index = 0; index < bars.size(); index++) {
            Box bar = bars.get(index);
            fluidBar(graphics, x + bar.x(), y + bar.y(), bar.width(), bar.height(),
                    menu.tankFill(index), menu.tankFluid(index));
        }
        Box progress = progressBar();
        bar(graphics, x + progress.x(), y + progress.y(), progress.width(), progress.height(),
                menu.craftProgress(), COLOR_FILL);
        meter(graphics, BOLT, x + chargeX(), y + chargeY(), menu.charge());
    }

    /** The one line: what it is making, or why it is not. */
    @Override
    protected Component statusLine() {
        ResourceKey<Recipe<?>> selected = menu.selectedRecipe();
        if (selected == null) {
            return Component.translatable("screen.nauvis_fluids.processing.idle");
        }
        RecipeHolder<FacraftRecipe> holder = ClientRecipes.byId(selected);
        if (holder == null) {
            return Component.translatable("screen.nauvis_fluids.processing.unknown");
        }
        return switch (menu.status()) {
            case NO_POWER -> Component.translatable("screen.nauvis_fluids.processing.no_power");
            case NO_INGREDIENTS -> Component.translatable("screen.nauvis_fluids.processing.no_ingredients");
            case OUTPUT_FULL -> Component.translatable("screen.nauvis_fluids.processing.output_full");
            default -> Component.translatable("screen.nauvis_fluids.processing.making",
                    holder.value().displayName());
        };
    }

    /** Hovering a bar names the fluid and the level, which the bar alone cannot. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Box> bars = tankBars();
        for (int index = 0; index < bars.size(); index++) {
            Box bar = bars.get(index);
            if (!within(mouseX, mouseY, leftPos + bar.x(), topPos + bar.y(), bar.width(), bar.height())) {
                continue;
            }
            Fluid fluid = menu.tankFluid(index);
            Component line = fluid == Fluids.EMPTY
                    ? Component.translatable("screen.nauvis_fluids.processing.tank_empty")
                    : Component.translatable("screen.nauvis_fluids.processing.tank",
                            fluid.getFluidType().getDescription(), menu.tankAmount(index),
                            ProcessingBlockEntity.TANK_CAPACITY);
            graphics.setComponentTooltipForNextFrame(font, List.of(line), mouseX, mouseY);
            return;
        }
    }
}
