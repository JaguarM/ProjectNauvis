package com.jaguarm.nauvismilitary.registry;

import com.jaguarm.nauvislib.multiblock.Multiblock;
import com.jaguarm.nauvismilitary.turret.GunTurretBlock;
import com.jaguarm.nauvismilitary.turret.GunTurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What an inserter sees when it looks at a turret: somewhere to put magazines, from any of the
 * four blocks it is made of. Insert only - a turret's ammunition is not for taking back out -
 * and never the raw slot.
 *
 * <p>Registered against the block, not the block entity, so an inserter beside any edge of the
 * turret feeds it: the same arrangement every machine in the pack has, and the reason the
 * footprint is worth having.
 */
public final class ModCapabilities {

    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        GunTurretBlock block = ModBlocks.GUN_TURRET.get();
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, blockEntity, side) -> {
            BlockPos anchor = Multiblock.anchorPos(block, state, pos);
            // Never ask for a block entity in an unloaded chunk: asking loads it.
            if (!level.isLoaded(anchor)) {
                return null;
            }
            return level.getBlockEntity(anchor) instanceof GunTurretBlockEntity turret
                    ? turret.automationView()
                    : null;
        }, block);
    }
}
