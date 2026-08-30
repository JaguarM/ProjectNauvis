package com.jaguarm.nauvismining.registry;

import com.jaguarm.nauvismining.NauvisMining;
import com.jaguarm.nauvismining.machine.miner.MinerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NauvisMining.MODID);

    /**
     * Built through NeoForge's factory so the server can send the miner's position when the
     * menu opens. The client needs it to draw the dig-area preview; a vanilla MenuType has
     * no channel for that.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MinerMenu>> MINER =
            MENUS.register("miner", () -> IMenuTypeExtension.create(
                    (windowId, inventory, data) -> new MinerMenu(windowId, inventory, data.readBlockPos())));

    private ModMenus() {}
}
