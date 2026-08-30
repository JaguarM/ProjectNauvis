package com.jaguarm.nauvismining;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config for Project Nauvis: Mining.
 *
 * <p>Option names and defaults follow the original 1.12.2 mod where the mechanic still
 * makes sense on modern Minecraft. {@link #MINE_FLOOR} is new: 1.12.2 worlds bottomed out
 * at y=0, so the original hardcoded that, but modern worlds reach y=-64.
 *
 * <p>{@link #EXTRA_RINGS} replaced that mod's {@code initialRange}, which was a radius with the
 * machine's own size playing no part in it. A drill covers its footprint now, so what is left to
 * configure is how far past that it reaches - and the default for that is nothing.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // -- Range ------------------------------------------------------------------

    public static final ModConfigSpec.IntValue EXTRA_RINGS = BUILDER
            .comment(
                    "Rings of blocks a machine covers *beyond its own footprint*, with no",
                    "upgrades installed. Zero means a drill mines exactly what it stands on:",
                    "2x2 under a burner mining drill, 3x3 under an electric one, which is what",
                    "Factorio's drills do and what makes a mining field something you lay out",
                    "by placing drills. One means a 4x4 and a 5x5, and so on.")
            .defineInRange("range.extraRings", 0, 0, 128);

    public static final ModConfigSpec.IntValue RING_PER_MODULE = BUILDER
            .comment("Additional rings granted per range module.")
            .defineInRange("range.ringsPerModule", 1, 1, 128);

    // -- Miner ------------------------------------------------------------------

    public static final ModConfigSpec.IntValue MINE_FLOOR = BUILDER
            .comment(
                    "Lowest Y level the miner will dig to.",
                    "Clamped to the dimension's own floor, so -64 means 'all the way down'",
                    "in the overworld but stops at bedrock in the nether.")
            .defineInRange("miner.mineFloor", -64, -2048, 2048);

    public static final ModConfigSpec.BooleanValue REQUIRE_COBBLE_BACKFILL = BUILDER
            .comment(
                    "Whether the miner backfills each mined block with cobblestone,",
                    "consuming one cobblestone per block. Turning this off makes the miner",
                    "leave air behind and removes the cobble requirement entirely, which",
                    "also makes the cobble generator upgrade pointless.")
            .define("miner.requireCobbleBackfill", true);

    public static final ModConfigSpec.DoubleValue MINING_SPEED_MULTIPLIER = BUILDER
            .comment(
                    "How fast machines mine, relative to a player swinging the same tool.",
                    "1.0 matches vanilla exactly: a machine with an iron pickaxe takes as",
                    "long on stone as you would. Above 1.0 is faster, below is slower.",
                    "Applied on top of vanilla's formula rather than replacing it, so tool",
                    "tier, block hardness and Efficiency all still behave as expected.")
            .defineInRange("miner.speedMultiplier", 1.0, 0.05, 100.0);

    public static final ModConfigSpec.BooleanValue DESTROY_TOOLS = BUILDER
            .comment("Whether tools are consumed when their durability runs out.")
            .define("miner.destroyTools", true);

    // -- Energy -----------------------------------------------------------------

    public static final ModConfigSpec.IntValue ENERGY_CAPACITY = BUILDER
            .comment("How much FE an electric miner can hold.")
            .defineInRange("energy.capacity", 40_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue ENERGY_PER_TICK = BUILDER
            .comment(
                    "FE drawn per tick while an electric miner is actively mining.",
                    "Idle machines draw nothing. Speed modules raise this and efficiency",
                    "modules lower it, the same trade the burner tiers make with fuel.")
            .defineInRange("energy.perTick", 40, 0, 1_000_000);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
