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
 * <h2>Which lane, and why giving and taking are not the same rule</h2>
 *
 * <p>Whoever is asking is standing on one side of the belt, and that side is the one the
 * capability was looked up with. From there Factorio's two rules pull in opposite directions, and
 * conflating them is what made a single inserter fill a whole belt here:
 *
 * <ul>
 *   <li><b>Giving is the far lane, and only the far lane.</b> "Inserters only place items onto one
 *       side of the belt, either the far side from the inserter's perspective or, if the belt is
 *       going the same or the opposite direction as the inserter, the right side from the belt's
 *       perspective." A full far lane is a <em>wait</em>, never a fall back to the near one - that
 *       is what lets one belt feed two rows of machines, and why a player puts inserters down both
 *       sides of a bus.</li>
 *   <li><b>Taking prefers the <em>near</em> lane.</b> The opposite way round, and for a reason
 *       that is nothing to do with throughput: the arm has less distance to travel, so "this
 *       favors inserters taking from the inner lane". The far lane is still taken from when the
 *       near one is empty.</li>
 * </ul>
 *
 * <p>Both quotes are the Factorio wiki's, and both cases have a <em>third</em> reading for an
 * asker whose side tells us nothing about a near and a far:
 *
 * <ul>
 *   <li><b>In line with the belt</b> - facing along its axis rather than across it, where there is
 *       no near side. Factorio names an absolute lane instead: the belt's right to give to, the
 *       belt's left to take from.</li>
 *   <li><b>No side at all</b> - a hopper above or below, or a capability looked up with
 *       {@code null}. It gets both lanes both ways. Factorio has no hoppers so nothing is being
 *       contradicted, and a hopper that could only ever half fill a belt would just be broken.</li>
 * </ul>
 */
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
