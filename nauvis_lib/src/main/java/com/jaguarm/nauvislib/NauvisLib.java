package com.jaguarm.nauvislib;

import net.neoforged.fml.common.Mod;

/**
 * The code every machine in Project Nauvis is built on, and nothing a player ever sees.
 *
 * <p>Non-negotiable #3 keeps the subsystem mods from depending on each other, and for a long
 * time that meant the framework they share - the multi-block mechanism, the views a machine
 * publishes to inserters and poles, the screens, the bench-recipe datapack - was copied into
 * each of them and held identical by a checker. Six copies of five files was where that stopped
 * paying for itself. This mod is the one copy. It depends on nothing of ours, every subsystem
 * mod depends on it, and the arrows still point one way.
 *
 * <p>It registers no blocks, items, recipes or tests. What it has is packages:
 * {@code multiblock} for machines made of several blocks, {@code transfer} for what a machine
 * lets the outside world do with its inventory and its charge, {@code client} for the screen
 * every machine is drawn on, {@code pack} for the bench-recipe datapack, and {@code compat.jade}
 * for sending the hover readout to a machine's anchor.
 */
@Mod(NauvisLib.MODID)
public class NauvisLib {

    public static final String MODID = "nauvis_lib";

    public NauvisLib() {}
}
