package com.jaguarm.nauvismachines.registry;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NauvisMachines.MODID);

    /**
     * Built through NeoForge's factory so the machine's position travels with the menu.
     *
     * <p>The client needs it to read the chosen recipe off the block entity - a recipe key is not
     * an int and so cannot be a data slot, and the block entity is already synced.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<AssemblerMenu>> ASSEMBLER =
            MENUS.register("assembler", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new AssemblerMenu(windowId, inventory, data.readBlockPos())));

    private ModMenus() {}
}
