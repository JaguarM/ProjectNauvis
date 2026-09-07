package com.jaguarm.nauvismilitary.turret;

import com.jaguarm.nauvismilitary.weapon.MagazineItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A turret's one slot: a magazine and nothing else.
 *
 * <p>Factorio's gun turret holds one stack of ammunition; here a magazine has durability and so
 * stacks to one, which is one magazine in the turret at a time. An inserter keeps it topped up.
 */
public class TurretInventory extends ItemStacksResourceHandler {

    private final Runnable onChanged;

    public TurretInventory(Runnable onChanged) {
        super(GunTurretBlockEntity.SLOT_COUNT);
        this.onChanged = onChanged;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return resource.getItem() instanceof MagazineItem;
    }

    /** A magazine arriving is what gives an empty turret something to do. */
    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }
}
