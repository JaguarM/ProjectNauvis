package com.jaguarm.nauvislogistics.registry;

import com.jaguarm.nauvislogistics.NauvisLogistics;
import com.jaguarm.nauvislogistics.transport.BurnerInserterMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NauvisLogistics.MODID);

    /**
     * A plain vanilla menu type: everything the screen draws arrives as ints in a ContainerData,
     * so the client half is built from nothing and no position has to travel with it.
     *
     * <p>Only the burner. The electric inserter has no slot, so a screen for it would be a panel
     * containing one bar - which is the hover display's job, not a container's.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<BurnerInserterMenu>> BURNER_INSERTER =
            MENUS.register("burner_inserter",
                    () -> new MenuType<>(BurnerInserterMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
