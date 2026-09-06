package com.jaguarm.nauvisfluids.compat.jade;

import com.jaguarm.facrafting.client.ClientRecipes;
import com.jaguarm.facrafting.recipe.FacraftRecipe;
import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.processing.PortTank;
import com.jaguarm.nauvisfluids.processing.ProcessingBlockEntity;
import com.jaguarm.nauvisfluids.processing.ProcessingStatus;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What a refinery or a chemical plant says: what it is making, whether it is running - and if
 * not, why not - and what is in each tank that has anything in it or is waiting for something.
 *
 * <p>Factorio's machine window is those things and the energy bar, and Jade's own universal
 * provider draws the energy bar off the capability with no help from here. One readout for both
 * machines, since they are one machine with two sets of numbers.
 *
 * <p>Two classes, a data half and a {@code Client} half, because Jade throws at registration if
 * one object is both.
 */
public class ProcessingReadout implements IServerDataProvider<BlockAccessor> {
    public static final ProcessingReadout INSTANCE = new ProcessingReadout();
    static final Identifier UID = Identifier.fromNamespaceAndPath(NauvisFluids.MODID, "processing");
    static final String STATUS = "Status";
    static final String RECIPE = "Recipe";
    static final String TANKS = "Tanks";
    static final String AMOUNT = "Amount";
    static final String FLUID = "Fluid";

    private ProcessingReadout() {}

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ProcessingBlockEntity machine)) {
            return;
        }
        data.putInt(STATUS, machine.status().ordinal());
        if (machine.recipeKey() != null) {
            data.putString(RECIPE, machine.recipeKey().identifier().toString());
        }
        int count = machine.layout().tankCount();
        data.putInt(TANKS, count);
        for (int index = 0; index < count; index++) {
            PortTank tank = machine.tank(index);
            if (tank == null) {
                continue;
            }
            data.putInt(AMOUNT + index, tank.getAmountAsInt(0));
            data.putInt(FLUID + index, BuiltInRegistries.FLUID.getId(tank.shownFluid()));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** The client half: the recipe, the status, and a line per tank in use. */
    public static class Client implements IBlockComponentProvider {
        public static final Client INSTANCE = new Client();

        private Client() {}

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(STATUS)) {
                return;
            }
            ProcessingStatus status = ProcessingStatus.byOrdinal(data.getIntOr(STATUS, 0));
            Identifier recipeId = data.getString(RECIPE).map(Identifier::tryParse).orElse(null);
            if (recipeId != null) {
                RecipeHolder<FacraftRecipe> holder = ClientRecipes.byId(ResourceKey.create(Registries.RECIPE, recipeId));
                if (holder != null) {
                    tooltip.add(Component.translatable("jade.nauvis_fluids.processing.making",
                            holder.value().displayName()));
                }
            }
            tooltip.add(Component.translatable(switch (status) {
                case WORKING -> "jade.nauvis_fluids.processing.working";
                case NO_RECIPE -> "jade.nauvis_fluids.processing.idle";
                case NO_INGREDIENTS -> "jade.nauvis_fluids.processing.no_ingredients";
                case OUTPUT_FULL -> "jade.nauvis_fluids.processing.output_full";
                case NO_POWER -> "jade.nauvis_fluids.processing.no_power";
            }));
            int count = data.getIntOr(TANKS, 0);
            for (int index = 0; index < count; index++) {
                Fluid fluid = BuiltInRegistries.FLUID.byId(data.getIntOr(FLUID + index, 0));
                if (fluid == Fluids.EMPTY) {
                    continue;
                }
                tooltip.add(Component.translatable("jade.nauvis_fluids.processing.tank",
                        fluid.getFluidType().getDescription(), data.getIntOr(AMOUNT + index, 0),
                        ProcessingBlockEntity.TANK_CAPACITY));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
