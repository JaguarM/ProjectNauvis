package com.jaguarm.nauvisfluids.registry;

import com.jaguarm.nauvisfluids.pumpjack.PumpjackMenu;
import com.jaguarm.nauvisfluids.NauvisFluids;
import com.jaguarm.nauvisfluids.chemicalplant.ChemicalPlantMenu;
import com.jaguarm.nauvisfluids.refinery.OilRefineryMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The two machine menus, built through NeoForge's factory so the machine's position travels with
 * the menu: the client reads the chosen recipe off the block entity, because a recipe key is not
 * an int and so cannot be a data slot.
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NauvisFluids.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<OilRefineryMenu>> OIL_REFINERY =
            MENUS.register("oil_refinery", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new OilRefineryMenu(windowId, inventory, data.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<ChemicalPlantMenu>> CHEMICAL_PLANT =
            MENUS.register("chemical_plant", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new ChemicalPlantMenu(windowId, inventory, data.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<PumpjackMenu>> PUMPJACK =
            MENUS.register("pumpjack", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new PumpjackMenu(windowId, inventory, data.readBlockPos())));

    private ModMenus() {}
}
