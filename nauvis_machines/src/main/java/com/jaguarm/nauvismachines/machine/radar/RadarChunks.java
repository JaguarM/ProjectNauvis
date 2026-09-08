package com.jaguarm.nauvismachines.machine.radar;

import com.jaguarm.nauvismachines.NauvisMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/** The tickets a radar holds on the chunks around it. */
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
