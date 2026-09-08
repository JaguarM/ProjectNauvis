package com.jaguarm.nauvismaterials;

import java.util.List;

import com.jaguarm.nauvismaterials.registry.ModItems;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
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
@EventBusSubscriber(modid = NauvisMaterials.MODID)
public final class NauvisMaterialsGameTests {

    private NauvisMaterialsGameTests() {}

    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int PADDING = 4;

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, NauvisMaterials.MODID);

    static {
        TEST_TYPES.register("a_stack_is_factorios_size", () -> StackIsFactoriosSizeTest.CODEC);
    }

    static void register(IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(NauvisMaterials.MODID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(Identifier.fromNamespaceAndPath(NauvisMaterials.MODID, "a_stack_is_factorios_size"),
                new StackIsFactoriosSizeTest(new TestData<>(environment, EMPTY_STRUCTURE, 100, 0, true, Rotation.NONE,
                        false, 1, 1, false, PADDING)));
    }

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
    public static class StackIsFactoriosSizeTest extends GameTestInstance {

        public static final MapCodec<StackIsFactoriosSizeTest> CODEC = RecordCodecBuilder.<StackIsFactoriosSizeTest>mapCodec(
                i -> i.group(TestData.CODEC.forGetter(StackIsFactoriosSizeTest::info)).apply(i, StackIsFactoriosSizeTest::new));

        private static final BlockPos CHEST = new BlockPos(1, 1, 1);
        /**
         * The origin: its chunk is the one that ticks entities in a gametest, and an entity in a
         * chunk that does not tick is not even returned by {@code getEntities}. The first version
         * put the piles a block diagonally away and found none.
         */
        private static final BlockPos GROUND = new BlockPos(0, 1, 0);

        public StackIsFactoriosSizeTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

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

            Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(GROUND));
            for (int pile = 0; pile < 2; pile++) {
                ItemEntity entity = new ItemEntity(helper.getLevel(), spot.x, spot.y, spot.z, new ItemStack(circuit, 100), 0, 0, 0);
                entity.setPickUpDelay(10);
                helper.getLevel().addFreshEntity(entity);
            }
            helper.runAfterDelay(60, () -> {
                List<ItemEntity> piles = helper.getEntities(EntityTypes.ITEM, GROUND, 2.0);
                helper.assertValueEqual(piles.size(), 1, "piles of circuits on the ground");
                helper.assertValueEqual(piles.getFirst().getItem().getCount(), 200, "circuits in the one pile");
                helper.succeed();
            });
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("a stack is Factorio's size");
        }
    }
}
