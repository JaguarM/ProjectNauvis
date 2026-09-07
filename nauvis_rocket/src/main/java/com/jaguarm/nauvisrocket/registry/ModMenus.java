package com.jaguarm.nauvisrocket.registry;

import com.jaguarm.nauvisrocket.NauvisRocket;
import com.jaguarm.nauvisrocket.silo.RocketSiloMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NauvisRocket.MODID);

    /**
     * A plain vanilla menu type. Everything the silo's screen draws arrives as ints in a
     * {@code ContainerData} - the silo chooses nothing, so there is no recipe key to carry - and
     * the client half is built from nothing, the lab's arrangement.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<RocketSiloMenu>> ROCKET_SILO =
            MENUS.register("rocket_silo", () -> new MenuType<>(RocketSiloMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
