package com.jaguarm.nauvispower.registry;

import com.jaguarm.nauvispower.NauvisPower;
import com.jaguarm.nauvispower.generator.BoilerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NauvisPower.MODID);

    /**
     * A plain vanilla menu type, unlike the assembler's.
     *
     * <p>The assembler needs NeoForge's factory because its position has to travel to the client,
     * which reads the chosen recipe off the block entity. A boiler has no such thing: everything
     * the screen draws arrives as ints in a ContainerData, so the client half is built from
     * nothing at all.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<BoilerMenu>> BOILER =
            MENUS.register("boiler",
                    () -> new MenuType<>(BoilerMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
