package com.jaguarm.nauvismining;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config for Project Nauvis: Mining. One number.
 *
 * <p>A drill's footprint, its reach, its speed and its module slots are Factorio's and are not
 * configurable; see {@code MachineTier}. What is left is how deep under itself a drill reaches
 * for ore, which is a fact about Minecraft's ground rather than about Factorio's drill.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MINE_FLOOR = BUILDER
            .comment(
                    "Lowest Y level a drill reaches down to for ore.",
                    "Clamped to the dimension's own floor, so -64 means 'all the way down'",
                    "in the overworld but stops at bedrock in the nether.")
            .defineInRange("miner.mineFloor", -64, -2048, 2048);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
