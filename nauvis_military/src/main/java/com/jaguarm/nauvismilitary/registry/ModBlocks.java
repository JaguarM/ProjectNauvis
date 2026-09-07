package com.jaguarm.nauvismilitary.registry;

import com.jaguarm.nauvismilitary.NauvisMilitary;
import com.jaguarm.nauvismilitary.turret.GunTurretBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NauvisMilitary.MODID);

    /** Two tiles by two, Factorio's gun turret. The stone wall is vanilla's cobblestone wall, standing in. */
    public static final DeferredBlock<GunTurretBlock> GUN_TURRET = BLOCKS.registerBlock("gun_turret",
            GunTurretBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(4.0F).sound(SoundType.METAL));

    private ModBlocks() {}
}
