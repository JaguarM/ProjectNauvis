package com.jaguarm.nauvislogistics.belt;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** How everything else in the game meets a belt. */
public final class BeltAccess implements ResourceHandler<ItemResource> {

    /** Left and right, in that order. Shared because nothing here ever writes through it. */
    private static final int[] BOTH = {Belts.LEFT, Belts.RIGHT};

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
        for (int lane : lanesToTakeFrom(run, block)) {
            var positions = run.lane(lane).positions();
            for (int i = 0; i < positions.size(); i++) {
                if (run.blockAt(positions.getInt(i)) == block) {
                    found.add(new Where(lane, i));
                }
            }
        }
        return found;
    }

    /**
     * The one lane this asker may put things on. See the class note.
     *
     * <p>Deliberately not the same method as {@link #lanesToTakeFrom} with a flag. They are two
     * rules that happen to be computed from the same two facts, and the whole bug this replaced
     * was one rule being used for both jobs. A flag would let that quietly grow back.
     */
    private int[] lanesToGiveTo(BeltRun run, int block) {
        if (side == null || !side.getAxis().isHorizontal()) {
            return BOTH;
        }
        int near = Belts.laneFor(run.travelAt(block), side);
        if (near < 0) {
            // In line with the belt rather than beside it: the belt's own right-hand lane.
            return new int[] {Belts.RIGHT};
        }
        return new int[] {far(near)};
    }

    /** The near lane first, then the far one. See the class note. */
    private int[] lanesToTakeFrom(BeltRun run, int block) {
        if (side != null && side.getAxis().isHorizontal()) {
            int near = Belts.laneFor(run.travelAt(block), side);
            if (near >= 0) {
                return new int[] {near, far(near)};
            }
        }
        return BOTH;
    }

    private static int far(int lane) {
        return lane == Belts.LEFT ? Belts.RIGHT : Belts.LEFT;
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

        int[] lanes = lanesToGiveTo(run, block);
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
