package com.jaguarm.nauvisresearch.registry;

import com.jaguarm.nauvisresearch.NauvisResearch;
import com.jaguarm.nauvisresearch.lab.LabMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NauvisResearch.MODID);

    /**
     * A plain vanilla menu type. Everything the lab's screen draws arrives as ints in a
     * {@code ContainerData}, so the client half is built from nothing - see {@code BoilerMenu},
     * which is the same arrangement for the same reason.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<LabMenu>> LAB =
            MENUS.register("lab", () -> new MenuType<>(LabMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
