package com.jaguarm.nauvismilitary.registry;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.jaguarm.nauvismilitary.turret.GunTurretMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NauvisMilitary.MODID);

    /** Built through NeoForge's factory so the server can send the turret's position when the menu opens. */
    public static final DeferredHolder<MenuType<?>, MenuType<GunTurretMenu>> GUN_TURRET = MENUS.register("gun_turret",
            () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new GunTurretMenu(windowId, inventory, data.readBlockPos())));

    private ModMenus() {}
}
