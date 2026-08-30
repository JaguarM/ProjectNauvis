package com.jaguarm.nauvismining.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jaguarm.nauvismining.NauvisMining;
import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvismining.machine.miner.BurnerDrillBlock;
import com.jaguarm.nauvismining.machine.miner.ElectricDrillBlock;
import com.jaguarm.nauvismining.machine.miner.MinerBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(NauvisMining.MODID);

    /** The two drills, keyed by tier and in progression order. */
    public static final Map<MachineTier, DeferredBlock<MinerBlock>> DRILLS = new LinkedHashMap<>();

    static {
        register(MachineTier.BURNER, MapColor.STONE, SoundType.STONE, 3.5f);
        register(MachineTier.ELECTRIC, MapColor.METAL, SoundType.METAL, 4.5f);
    }

    private static void register(MachineTier tier, MapColor colour, SoundType sound, float strength) {
        DRILLS.put(tier, BLOCKS.registerBlock(
                tier.id(),
                properties -> tier == MachineTier.BURNER
                        ? new BurnerDrillBlock(properties)
                        : new ElectricDrillBlock(properties),
                properties -> properties
                        .mapColor(colour)
                        .strength(strength)
                        .sound(sound)
                        // Glows while working, like a lit furnace. Dimmer than one, since
                        // a drill's face is a warning light rather than an open firebox.
                        .lightLevel(state -> state.getValue(MinerBlock.LIT) ? 8 : 0)));
    }

    private ModBlocks() {}
}
