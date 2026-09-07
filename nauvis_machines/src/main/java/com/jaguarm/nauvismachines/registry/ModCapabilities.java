package com.jaguarm.nauvismachines.registry;

import java.util.function.Function;

import com.jaguarm.nauvismachines.NauvisMachines;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlock;
import com.jaguarm.nauvismachines.machine.assembler.AssemblerBlockEntity;
import com.jaguarm.nauvismachines.machine.assembler.AssemblingMachine2Shape;
import com.jaguarm.nauvismachines.machine.furnace.FurnaceBlockEntity;
import com.jaguarm.nauvismachines.machine.radar.RadarBlockEntity;
import com.jaguarm.nauvislib.multiblock.Multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * What automation sees when it looks at a machine, from any of the blocks it is made of.
 *
 * <p>The view published here is never the machine's own inventory: it accepts insertions into
 * the input slots only and allows extraction from the output slots only. Without that, a hopper
 * under an assembler would drain the ingredients it was just fed, and one under a furnace would
 * take its coal back.
 *
 * <h2>Why this is {@code registerBlock} and not {@code registerBlockEntity}</h2>
 *
 * <p>An assembler is ten blocks and one block entity. {@code registerBlockEntity} would publish
 * the inventory at the middle of the machine only - a block a player can barely reach and an
 * inserter can never stand next to, since it is surrounded by the machine's own deck. Registering
 * against the <em>block</em> lets any cell answer, resolving the anchor by arithmetic first.
 *
 * <p>The result is the Factorio behaviour and the reason the footprint was worth having: a machine
 * is fed or emptied anywhere along its perimeter, and on top of its deck, rather than at one
 * privileged spot.
 *
 * <p>Energy is published only by the machines that spend it. A burner furnace offers no energy
 * capability at all, so a pole beside a stone furnace never counts it as something to supply.
 *
 * <p>Nothing invalidates caches by hand here. NeoForge invalidates a position when its block
 * changes, and every cell of a machine is placed and removed as its own block change, so each
 * cell's entry goes stale exactly when it stops being true.
 */
@EventBusSubscriber(modid = NauvisMachines.MODID)
public final class ModCapabilities {

    private ModCapabilities() {}

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        anywhere(event, Capabilities.Item.BLOCK, AssemblerBlockEntity.class, AssemblerBlockEntity::automationView,
                ModBlocks.ASSEMBLING_MACHINE_1.get(), ModBlocks.ASSEMBLING_MACHINE_2.get());

        // Insert only, and NeoForge's capability rather than ours - so a power pole from
        // nauvis_power fills an assembler without either mod knowing the other exists, and so
        // would a cable from any other mod. That is the whole reason PLAN.md chose FE.
        anywhere(event, Capabilities.Energy.BLOCK, AssemblerBlockEntity.class, AssemblerBlockEntity::gridView,
                ModBlocks.ASSEMBLING_MACHINE_1.get(), ModBlocks.ASSEMBLING_MACHINE_2.get());

        anywhere(event, Capabilities.Item.BLOCK, FurnaceBlockEntity.class, FurnaceBlockEntity::automationView,
                ModBlocks.STONE_FURNACE.get(), ModBlocks.STEEL_FURNACE.get(), ModBlocks.ELECTRIC_FURNACE.get());
        anywhere(event, Capabilities.Energy.BLOCK, FurnaceBlockEntity.class, FurnaceBlockEntity::gridView,
                ModBlocks.ELECTRIC_FURNACE.get());

        // A radar takes power and nothing else: no items in, none out.
        anywhere(event, Capabilities.Energy.BLOCK, RadarBlockEntity.class, RadarBlockEntity::gridView,
                ModBlocks.RADAR.get());

        // The second machine's fluid boxes, at the two faces its shape names and nowhere else: a
        // pipe on the north edge fills the input, one on the south edge drains the output, and a
        // pipe on a flank finds nothing - which is Factorio's assembler exactly.
        AssemblerBlock second = ModBlocks.ASSEMBLING_MACHINE_2.get();
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
            String port = second.shape().portAt(Multiblock.part(second, state), side, second.facing(state));
            if (port == null) {
                return null;
            }
            BlockPos anchor = Multiblock.anchorPos(second, state, pos);
            if (!level.isLoaded(anchor) || !(level.getBlockEntity(anchor) instanceof AssemblerBlockEntity machine)) {
                return null;
            }
            return AssemblingMachine2Shape.FLUID_IN.equals(port) ? machine.fluidIn() : machine.fluidOutView();
        }, second);
    }

    /**
     * Offered by every block of the machine, on every face, answered by the one block entity.
     *
     * <p>Every tier of a machine at once: the anchor is found through the state's own block, so
     * one provider serves them all.
     */
    private static <T, C extends @Nullable Object, E extends BlockEntity> void anywhere(
            RegisterCapabilitiesEvent event, BlockCapability<T, C> capability, Class<E> type,
            Function<E, @Nullable T> view, Block... blocks) {
        event.registerBlock(capability, (level, pos, state, blockEntity, context) -> {
            if (!(state.getBlock() instanceof Multiblock.MachineBlock block)) {
                return null;
            }
            BlockPos anchor = Multiblock.anchorPos(block, state, pos);
            // Never getBlockEntity on an unloaded chunk - asking loads it, and a machine at the
            // edge of the loaded world would drag its neighbour in. See docs/PITFALLS.md.
            if (!level.isLoaded(anchor)) {
                return null;
            }
            BlockEntity found = level.getBlockEntity(anchor);
            return type.isInstance(found) ? view.apply(type.cast(found)) : null;
        }, blocks);
    }
}
