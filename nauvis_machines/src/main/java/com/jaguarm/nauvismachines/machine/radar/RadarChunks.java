package com.jaguarm.nauvismachines.machine.radar;

import com.jaguarm.nauvismachines.NauvisMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/**
 * The tickets a radar holds on the chunks around it.
 *
 * <p>Factorio's radar keeps the seven-by-seven chunks around it continuously charted, and the
 * nearest thing Minecraft has to a chunk that is kept current is a chunk that is kept loaded and
 * ticking - so that is what a radar does here, through NeoForge's forced chunks, with the radar's
 * own position as the owner of every ticket. Seven by seven at Minecraft's chunk size, the same
 * rule pollution follows: Factorio's number, on the smaller chunk.
 *
 * <p>Tickets outlive the world being closed, which is the point of them, and so they can outlive
 * the radar: a world closed without saving, a radar broken by something that never reached
 * {@code preRemoveSideEffects}. The validation callback runs as a world's tickets are loaded and
 * drops any whose owner is a loaded position with no radar on it. An owner in a chunk that is
 * not loaded yet is left alone - the ticket is what loads it, and the radar there will take its
 * own tickets back or let them go when it ticks.
 */
public final class RadarChunks {

    private RadarChunks() {}

    public static final TicketController CONTROLLER = new TicketController(
            Identifier.fromNamespaceAndPath(NauvisMachines.MODID, "radar"), RadarChunks::validate);

    /** From the mod constructor, on the mod bus. */
    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    private static void validate(ServerLevel level, TicketHelper helper) {
        for (BlockPos owner : helper.getBlockTickets().keySet()) {
            if (level.isLoaded(owner) && !(level.getBlockEntity(owner) instanceof RadarBlockEntity)) {
                helper.removeAllTickets(owner);
            }
        }
    }
}
