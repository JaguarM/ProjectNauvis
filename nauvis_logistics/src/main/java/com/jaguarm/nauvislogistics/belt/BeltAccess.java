package com.jaguarm.nauvislogistics.belt;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * How everything else in the game meets a belt.
 *
 * <p>A belt publishes {@code Capabilities.Item.BLOCK}, so an inserter, a hopper or another mod's
 * machine can put things on it and take things off without knowing what a belt is. That is the
 * same joint every other pair of blocks in this pack meets at, and it is the reason the belt needs
 * no compile-time dependency on anything.
 *
 * <p><b>The belt never pushes.</b> Its side of this is entirely passive: it answers, and waits to
 * be asked. A belt running into a chest backs up rather than filling it, which is what Factorio
 * does and the reason inserters exist. See {@link BeltRun}.
 *
 * <h2>Indices are items, not slots</h2>
 *
 * <p>There is no fixed grid of slots to expose - an item on a belt is at a distance along it, not
 * in a hole. So the size is however many items are standing on this one block, and index 0 is the
 * one nearest the far end. {@code ResourceHandler} asks implementations to be lenient about a size
 * that changes, and this is exactly that case.
 *
 * <h2>Which lane, and why the far one</h2>
 *
 * <p>Whoever is asking is standing on one side of the belt, and that side is the one the
 * capability was looked up with. Factorio's inserters reach across to the <em>far</em> lane, both
 * to take and to give - it is what lets one belt feed two rows of machines - so the far lane is
 * offered first, and the near lane only when the far one cannot help. A hopper above or below has
 * no side, and gets the left lane first.
 */
public final class BeltAccess implements ResourceHandler<ItemResource> {

    private final BeltBlockEntity belt;
    private final @Nullable Direction side;

    BeltAccess(BeltBlockEntity belt, @Nullable Direction side) {
        this.belt = belt;
        this.side = side;
    }

    /** One item on this block: which lane it is on, and where it is in that lane's ordering. */
    private record Where(int lane, int index) {}

    private List<Where> here() {
        BeltRun run = belt.run();
        if (run == null) {
            return List.of();
        }
        int block = run.indexOf(belt.getBlockPos());
        if (block < 0) {
            return List.of();
        }

        List<Where> found = new ArrayList<>();
        for (int lane : lanePreference(run, block)) {
            var positions = run.lane(lane).positions();
            for (int i = 0; i < positions.size(); i++) {
                if (run.blockAt(positions.getInt(i)) == block) {
                    found.add(new Where(lane, i));
                }
            }
        }
        return found;
    }

    /** The far lane first, then the near one. See the class note. */
    private int[] lanePreference(BeltRun run, int block) {
        if (side != null && side.getAxis().isHorizontal()) {
            int near = Belts.laneFor(run.travelAt(block), side);
            if (near >= 0) {
                return new int[] {near == Belts.LEFT ? Belts.RIGHT : Belts.LEFT, near};
            }
        }
        return new int[] {Belts.LEFT, Belts.RIGHT};
    }

    @Override
    public int size() {
        return here().size();
    }

    @Override
    public ItemResource getResource(int index) {
        List<Where> items = here();
        if (index < 0 || index >= items.size()) {
            return ItemResource.EMPTY;
        }
        BeltRun run = belt.run();
        Where where = items.get(index);
        return run == null ? ItemResource.EMPTY : run.lane(where.lane()).item(where.index());
    }

    /** One. An item on a belt is one item; there is no stack on a belt in Factorio either. */
    @Override
    public long getAmountAsLong(int index) {
        return index >= 0 && index < size() ? 1 : 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return 1;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return true;
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        BeltRun run = belt.run();
        if (run == null || amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        int block = run.indexOf(belt.getBlockPos());
        if (block < 0) {
            return 0;
        }

        int[] lanes = lanePreference(run, block);
        int placed = 0;
        while (placed < amount) {
            boolean any = false;
            for (int lane : lanes) {
                if (run.insert(belt.getBlockPos(), lane, resource, transaction)) {
                    placed++;
                    any = true;
                    break;
                }
            }
            if (!any) {
                break;
            }
        }
        return placed;
    }

    /**
     * The index is ignored, deliberately.
     *
     * <p>An index here names an item that is already on the belt, so it cannot name where a new
     * one should go - the belt decides that, from the free space on the block. Callers that use
     * the index-addressed form get the same behaviour as the plain one, which is the only sensible
     * reading of "insert into slot 3 of something that has no slots".
     */
    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (amount < 1 || resource.isEmpty()) {
            return 0;
        }
        List<Where> items = here();
        if (index < 0 || index >= items.size()) {
            return 0;
        }
        BeltRun run = belt.run();
        if (run == null) {
            return 0;
        }
        Where where = items.get(index);
        if (!run.lane(where.lane()).item(where.index()).equals(resource)) {
            return 0;
        }
        run.extract(where.lane(), where.index(), transaction);
        return 1;
    }

    /**
     * Overridden because the inherited one walks a fixed set of indices, and taking an item off a
     * belt renumbers the ones behind it.
     */
    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        int taken = 0;
        while (taken < amount) {
            List<Where> items = here();
            BeltRun run = belt.run();
            if (run == null) {
                break;
            }
            int found = -1;
            for (int i = 0; i < items.size(); i++) {
                if (run.lane(items.get(i).lane()).item(items.get(i).index()).equals(resource)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                break;
            }
            Where where = items.get(found);
            run.extract(where.lane(), where.index(), transaction);
            taken++;
        }
        return taken;
    }
}
