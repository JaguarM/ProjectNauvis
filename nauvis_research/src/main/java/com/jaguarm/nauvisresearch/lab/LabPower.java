package com.jaguarm.nauvisresearch.lab;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * The lab's electricity buffer.
 *
 * <p>The callback is the whole reason this is not a bare {@link SimpleEnergyHandler}. A lab that
 * ran dry has stopped scheduling ticks, and the one thing that can give it work again is energy
 * arriving - which happens here, from whatever is filling it, on a thread of control the lab is
 * not part of. Without this a lab that stopped for want of power would sleep through the grid
 * coming back.
 *
 * <p>Unrestricted on purpose: the lab spends from it, so it must be able to. What the outside
 * world gets is {@link LabPowerAccess}.
 *
 * <p>Copied from {@code nauvis_machines}' {@code MachinePower} rather than shared. Facrafting is
 * the only place shared code may live, and putting this there would make this mod require it.
 */
public class LabPower extends SimpleEnergyHandler {

    private final Runnable onChanged;

    public LabPower(int capacity, Runnable onChanged) {
        super(capacity, capacity, capacity);
        this.onChanged = onChanged;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChanged.run();
    }
}
