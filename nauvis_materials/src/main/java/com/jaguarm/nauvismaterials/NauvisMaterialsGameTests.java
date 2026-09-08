package com.jaguarm.nauvismaterials;

import java.util.List;

import com.jaguarm.nauvismaterials.registry.ModItems;
import com.jaguarm.nauvislib.test.GameTests;
import com.jaguarm.nauvislib.test.PackGameTest;
import com.jaguarm.nauvislib.test.PackGameTest.Info;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * One test, inside a real server: a stack is Factorio's size everywhere a stack can be.
 *
 * <p>Run with {@code ./gradlew :nauvis:runGameTestServer}, which puts every mod in the pack on one
 * classpath, or {@code :nauvis_materials:runGameTestServer} for this mod alone.
 */
public final class NauvisMaterialsGameTests {

    private NauvisMaterialsGameTests() {}

    static void register(IEventBus modEventBus) {
        GameTests tests = new GameTests(NauvisMaterials.MODID, modEventBus);
        tests.add("a_stack_is_factorios_size", StackIsFactoriosSizeTest::new, 400, PADDING);
    }

    private static final int PADDING = 4;

    /** Puts items in the way an inserter will: through a handler, in one transaction. */
    private static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /**
     * Two hundred circuits are one stack: in the codec that saves and sends a stack, in a handler
     * of the kind every machine and chest of the pack's is, in the player's own inventory, in a
     * vanilla chest reached the way a hopper reaches it, and on the ground, where two piles of a
     * hundred become one. Each of those is a place Minecraft stops at ninety-nine or sixty-four
     * on its own, and a mixin in {@code nauvis_lib} is what makes each one say two hundred.
     */
    public static class StackIsFactoriosSizeTest extends PackGameTest {

        private static final BlockPos CHEST = new BlockPos(1, 1, 1);
        private static final BlockPos GROUND = new BlockPos(0, 1, 0);

        StackIsFactoriosSizeTest(Info info) { super(info); }

        @Override
        public void run(GameTestHelper helper) {
            Item circuit = ModItems.ELECTRONIC_CIRCUIT.get();
            ItemStack stack = new ItemStack(circuit, 200);
            helper.assertValueEqual(stack.getMaxStackSize(), 200, "what a circuit stacks to");

            RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
            Tag saved = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
            helper.assertValueEqual(loaded.getCount(), 200, "circuits after a trip through the stack codec");

            helper.assertValueEqual(insert(new ItemStacksResourceHandler(1), circuit, 200), 200,
                    "circuits one slot of a handler takes");

            Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
            helper.assertTrue(player.getInventory().add(stack.copy()), "the player's inventory refused the stack");
            helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 200, "circuits in the player's first slot");

            // A floor for the chest and the piles: a gametest's ground is only as big as its structure.
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                }
            }
            helper.setBlock(CHEST, Blocks.CHEST);
            ResourceHandler<ItemResource> chest = Capabilities.Item.BLOCK.getCapability(
                    helper.getLevel(), helper.absolutePos(CHEST), null, null, Direction.UP);
            helper.assertTrue(chest != null, "no item capability on a chest");
            helper.assertValueEqual(insert(chest, circuit, 200), 200, "circuits one slot of a vanilla chest takes");

            BlockPos ground = helper.absolutePos(GROUND);
            helper.getLevel().setChunkForced(ground.getX() >> 4, ground.getZ() >> 4, true);
            Vec3 spot = Vec3.atBottomCenterOf(ground);
            for (int pile = 0; pile < 2; pile++) {
                ItemEntity entity = new ItemEntity(helper.getLevel(), spot.x, spot.y, spot.z, new ItemStack(circuit, 100), 0, 0, 0);
                entity.setPickUpDelay(10);
                helper.getLevel().addFreshEntity(entity);
            }
            // A resting item merges every forty ticks, once its chunk ticks entities, which the
            // forced ticket makes true some ticks later; so poll rather than wait a fixed time.
            helper.succeedWhen(() -> {
                List<ItemEntity> piles = helper.getEntities(EntityTypes.ITEM, GROUND, 2.0);
                helper.assertValueEqual(piles.size(), 1, "piles of circuits on the ground");
                helper.assertValueEqual(piles.getFirst().getItem().getCount(), 200, "circuits in the one pile");
            });
        }

    }
}
