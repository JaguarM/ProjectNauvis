package com.jaguarm.nauvismining.registry;

import com.jaguarm.nauvismining.NauvisMining;
import com.jaguarm.nauvismining.machine.MachineTier;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The two drills, and nothing else: the modules a drill takes are Factorio's, which
 * {@code nauvis_machines} owns, and a drill's area is the entity's rather than a module's.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(NauvisMining.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NauvisMining.MODID);

    /** Block items for the drills, keyed by tier. */
    public static final Map<MachineTier, DeferredItem<BlockItem>> DRILLS = new LinkedHashMap<>();

    static {
        ModBlocks.DRILLS.forEach((tier, block) -> DRILLS.put(tier, ITEMS.registerSimpleBlockItem(block)));
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nauvis_mining"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> DRILLS.get(MachineTier.BURNER).get().getDefaultInstance())
                    .displayItems((parameters, output) ->
                            DRILLS.values().forEach(item -> output.accept(item.get())))
                    .build());

    private ModItems() {}
}
