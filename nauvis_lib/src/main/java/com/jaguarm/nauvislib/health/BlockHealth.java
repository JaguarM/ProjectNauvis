package com.jaguarm.nauvislib.health;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jaguarm.nauvislib.NauvisLib;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The damage taken by blocks that have no block entity to keep it in: a stone wall, a vanilla
 * block, a machine that has not learnt {@link Damageable}.
 *
 * <p>One per level, a map of position to how much has been taken off and which block it was taken
 * off. The block is remembered so that a wall broken and rebuilt starts whole: an entry whose
 * block no longer stands at its position is stale and is dropped the next time it is looked at.
 * An undamaged block has no entry, so the map is the wounds and not the world.
 */
public class BlockHealth extends SavedData {

    private record Entry(int x, int y, int z, Identifier block, float damage) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Entry::x),
                Codec.INT.fieldOf("y").forGetter(Entry::y),
                Codec.INT.fieldOf("z").forGetter(Entry::z),
                Identifier.CODEC.fieldOf("block").forGetter(Entry::block),
                Codec.FLOAT.fieldOf("damage").forGetter(Entry::damage))
                .apply(i, Entry::new));
    }

    private record Wound(Identifier block, float damage) {}

    public static final Codec<BlockHealth> CODEC = Entry.CODEC.listOf().xmap(BlockHealth::new, BlockHealth::entries);

    public static final SavedDataType<BlockHealth> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(NauvisLib.MODID, "block_health"), BlockHealth::new, CODEC);

    private final Map<Long, Wound> wounds = new HashMap<>();

    public BlockHealth() {}

    private BlockHealth(List<Entry> entries) {
        for (Entry entry : entries) {
            wounds.put(BlockPos.asLong(entry.x(), entry.y(), entry.z()), new Wound(entry.block(), entry.damage()));
        }
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>(wounds.size());
        wounds.forEach((key, wound) -> out.add(new Entry(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key),
                wound.block(), wound.damage())));
        return out;
    }

    public static BlockHealth get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** How much this block has taken, or nothing for a block that is whole or has been replaced since. */
    public float damage(BlockPos pos, Block block) {
        Wound wound = wounds.get(pos.asLong());
        if (wound == null) {
            return 0;
        }
        if (!wound.block().equals(BuiltInRegistries.BLOCK.getKey(block))) {
            wounds.remove(pos.asLong());
            setDirty();
            return 0;
        }
        return wound.damage();
    }

    /** Sets how much this block has taken. Nothing, or less, forgets it. */
    public void setDamage(BlockPos pos, Block block, float damage) {
        if (damage <= 0) {
            wounds.remove(pos.asLong());
        } else {
            wounds.put(pos.asLong(), new Wound(BuiltInRegistries.BLOCK.getKey(block), damage));
        }
        setDirty();
    }

    /** Forgets a position: the block there is gone. */
    public void clear(BlockPos pos) {
        if (wounds.remove(pos.asLong()) != null) {
            setDirty();
        }
    }
}
