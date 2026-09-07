package com.jaguarm.nauvismining.machine.miner;

import com.jaguarm.nauvislib.pollution.Pollution;
import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvislib.module.ModuleEffect;
import com.jaguarm.nauvislib.module.ModuleSlots;
import com.jaguarm.nauvislib.module.Productivity;
import com.jaguarm.nauvislib.transfer.MachineAccess;
import com.jaguarm.nauvislib.transfer.MachinePower;
import com.jaguarm.nauvislib.transfer.PowerAccess;
import com.jaguarm.nauvismining.Config;
import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvismining.registry.ModBlockEntities;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A mining drill: it stands on the ground, takes the ore out from under it, and puts what it took
 * down in front of itself. Factorio's drill, on Minecraft's ground.
 *
 * <h2>The ore patch is whatever ore is under the drill</h2>
 *
 * <p>Factorio's ore is a number painted on the tiles; Minecraft's is blocks in the ground, at
 * every depth. So the drill's area is its columns - the footprint under a burner, the footprint
 * and a ring around it under an electric, see {@link DigArea} - and its patch is every ore block
 * in those columns, from just under the machine down to the floor. It reaches down through the
 * ground for them and <b>leaves everything else standing</b>: no hole, no shaft, no cobblestone
 * to deal with. A spent ore block becomes the rock it was in, the way a spent Factorio tile
 * becomes bare ground.
 *
 * <p>Each ore is mined the way a player would mine it, through a {@link BreakBlockEvent} on a fake
 * player holding the drill's pickaxe. That is what makes the drill visible to the rest of the game
 * - protection mods can refuse it, and Crumbling Ore, the pack's answer to a patch that runs out,
 * takes over the break and hands back one harvest of eight - and it is why the pickaxe matters:
 * its tier decides which ores the drill can take at all, Fortune multiplies the yield, and every
 * ore costs a point of durability. Silk Touch is stripped, because a drill makes ore and never
 * the block.
 *
 * <h2>The rate is Factorio's</h2>
 *
 * <p>An ore takes a second at mining speed one; a burner drill mines at a quarter of that and an
 * electric at half, so an ore every four seconds and every two. Speed modules and Efficiency on
 * the pickaxe shorten it, mining productivity research and productivity modules bank a free ore
 * now and then, exactly as they do in an assembler.
 *
 * <h2>It outputs to the front</h2>
 *
 * <p>What it mines goes into the block in front of its output head - a belt, a chest, an
 * inserter's reach - and nowhere else, which is what makes a drill-and-belt line a thing you lay
 * out. Until something takes it, it waits in the drill's one output slot, and when that fills the
 * drill stops.
 *
 * <h2>It sleeps</h2>
 *
 * <p>No ticker. A tick is scheduled while there is mining to do and nothing is scheduled
 * otherwise; it wakes on its inventory changing, on a neighbour changing, and on electricity
 * arriving. A drill that has walked its whole area and found nothing left goes quiet until its
 * pickaxe is changed, since a better one reaches ores the old one could not.
 */
public class MinerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int FUEL_SLOT = 0;
    public static final int PICKAXE_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final int SLOT_COUNT = 3;

    /** Factorio's modifier for mining productivity research, read through the library's hook. */
    public static final String MINING_PRODUCTIVITY = "mining-drill-productivity-bonus";

    /** A second: what one ore takes at mining speed one, on an ore whose mining time is one. */
    public static final int TICKS_PER_ORE = 20;

    /** How much faster each level of Efficiency on the pickaxe makes the drill. */
    public static final double EFFICIENCY_PER_LEVEL = 0.1;

    /** How many ticks of a buffer an electric drill carries: five seconds of its own draw. */
    private static final int BUFFER_TICKS = 100;

    /** How many blocks the search for the next ore may look at in one tick. */
    private static final int SCAN_BUDGET_PER_TICK = 512;

    /** How often a drill with nowhere to put its ore looks again: a belt clearing is not a block change. */
    private static final int OUTPUT_RETRY_TICKS = 20;

    private static final String FAKE_PLAYER_NAME = "[NauvisMining]";
    private static final UUID FALLBACK_OWNER = UUID.nameUUIDFromBytes("nauvis_mining:miner".getBytes());

    private final MachineTier tier;
    private final DrillInventory inventory;

    /** What hoppers and inserters see: fuel and a pickaxe in, ore out. Never the raw inventory. */
    private final ResourceHandler<ItemResource> automationView;

    /** The tier's module slots - none on a burner, three on an electric - and the free ore they earn. */
    private final ModuleSlots modules;
    private final Productivity productivity = new Productivity();

    /** An electric drill's buffer, unrestricted because the machine spends from it. Null for a burner. */
    private final @Nullable MachinePower energy;

    /** Insert only, for the grid. Null for a burner, which no pole should think it supplies. */
    private final @Nullable EnergyHandler gridView;

    private int progress;
    private int cycleTicks;

    /** The draw under the modules read at the start of the cycle in progress. */
    private int draw;

    private int burnTime;
    private int burnTimeTotal;
    private MinerStatus status = MinerStatus.IDLE;

    /** The ore being worked, or null while the drill is looking for one. */
    private @Nullable BlockPos target;

    /** What is at the target, for the screen and the hover readout. Synced. */
    private @Nullable Block mining;

    /** Where the search for the next ore stands: a 1-based column of the area and a height in it. */
    private int columnIndex = 1;
    private int currentY = Integer.MIN_VALUE;

    /** Set when the search ran out of budget rather than out of area. */
    private boolean searching;

    /** Whether the columns want walking again from the top: after a load, and after the pickaxe changes. */
    private boolean rescan = true;

    /** What was mined and has not found a home yet. Rarely more than one stack. */
    private final List<ItemStack> hand = new ArrayList<>();

    /** See {@link #digArea()}. Rebuilt when the block state stops matching. */
    private @Nullable DigArea cachedArea;
    private @Nullable BlockState cachedAreaState;

    /**
     * Who placed this machine. The drill mines through a fake player carrying this identity, so
     * land-protection mods judge it as its owner rather than as an anonymous machine.
     */
    private @Nullable UUID ownerId;

    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case MinerMenu.DATA_PROGRESS -> progress;
                case MinerMenu.DATA_CYCLE_TICKS -> cycleTicks;
                case MinerMenu.DATA_BURN_TIME -> burnTime;
                case MinerMenu.DATA_BURN_TIME_TOTAL -> burnTimeTotal;
                case MinerMenu.DATA_ENERGY -> energyStored();
                case MinerMenu.DATA_ENERGY_PER_TICK -> currentEnergyPerTick();
                case MinerMenu.DATA_ENERGY_CAPACITY -> energyCapacity();
                case MinerMenu.DATA_STATUS -> status.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            // Server-authoritative: the client is told, never asked.
        }

        @Override
        public int getCount() {
            return MinerMenu.DATA_COUNT;
        }
    };

    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINER.get(), pos, state);
        // One block entity type backs both drills; the tier comes from whichever block this
        // entity was placed for.
        tier = state.getBlock() instanceof MinerBlock miner ? miner.tier() : MachineTier.BURNER;

        inventory = new DrillInventory(this::onInventoryChanged,
                tier.isElectric() ? resource -> false : this::burnable);
        automationView = new MachineAccess(inventory, OUTPUT_SLOT);
        // Ore is an intermediate product, so every module is welcome in a drill - Factorio's rule.
        modules = new ModuleSlots(tier.moduleSlots(), this::onModulesChanged);
        draw = tier.energyPerTick();

        if (tier.isElectric()) {
            energy = new MachinePower(energyCapacity(), this::onPowerChanged);
            gridView = new PowerAccess(energy);
        } else {
            energy = null;
            gridView = null;
        }
    }

    public MachineTier tier() {
        return tier;
    }

    public DrillInventory inventory() {
        return inventory;
    }

    public ResourceHandler<ItemResource> automationView() {
        return automationView;
    }

    public ModuleSlots modules() {
        return modules;
    }

    public Productivity productivity() {
        return productivity;
    }

    /** What a power pole fills, or null for a burner. Registered as {@code Capabilities.Energy.BLOCK}. */
    public @Nullable EnergyHandler gridView() {
        return gridView;
    }

    public int energyCapacity() {
        return tier.energyPerTick() * BUFFER_TICKS;
    }

    public int energyStored() {
        return energy == null ? 0 : energy.getAmountAsInt();
    }

    /** The tier's draw under the modules in it right now, for the screen. */
    public int currentEnergyPerTick() {
        return modules.effect().scaleEnergy(tier.energyPerTick());
    }

    public MinerStatus status() {
        return status;
    }

    public int progress() {
        return progress;
    }

    public int cycleTicks() {
        return cycleTicks;
    }

    /** The ore the drill is working, or null when it is not. Synced, so the screen can name it. */
    public @Nullable Block mining() {
        return mining;
    }

    /** Records the placer so the machine can act on their behalf. */
    public void setOwner(@Nullable LivingEntity placer) {
        if (placer instanceof Player player) {
            ownerId = player.getUUID();
            setChanged();
        }
    }

    /**
     * Ticks per ore: Factorio's mining time over the tier's speed, shortened by speed modules
     * and by Efficiency on the pickaxe.
     */
    public static int cycleTicksFor(MachineTier tier, ModuleEffect effect, int efficiencyLevel) {
        double speed = tier.miningSpeed() * effect.speedFactor() * (1 + EFFICIENCY_PER_LEVEL * efficiencyLevel);
        return Math.max(1, (int) Math.round(TICKS_PER_ORE / speed));
    }

    /**
     * The columns this drill mines, in world coordinates: its footprint and the tier's reach.
     *
     * <p>Cached, because the search asks for it every tick it runs and building one walks the
     * machine's cells. The block state is what can change the answer - it carries the facing -
     * and it is an interned singleton, so {@code !=} is the whole check.
     */
    public DigArea digArea() {
        BlockState state = getBlockState();
        if (cachedArea == null || cachedAreaState != state) {
            cachedArea = MinerBlock.digArea(state, worldPosition);
            cachedAreaState = state;
        }
        return cachedArea;
    }

    // -- Ticking ----------------------------------------------------------------

    /** Called by {@link MinerBlock}, and only ever on a tick this drill asked for. */
    public void serverTick(ServerLevel level) {
        // What was mined goes out first, so a drill that had nowhere to put its ore clears the
        // moment a chest is put in front of it.
        deliver(level);
        if (!hand.isEmpty()) {
            settle(level, MinerStatus.OUTPUT_FULL);
            level.scheduleTick(worldPosition, getBlockState().getBlock(), OUTPUT_RETRY_TICKS);
            return;
        }

        if (inventory.getAmountAsInt(PICKAXE_SLOT) <= 0) {
            progress = 0;
            settle(level, MinerStatus.NO_PICKAXE);
            return;
        }

        if (progress == 0) {
            if (status == MinerStatus.NO_ORE && !rescan) {
                // Walked the whole area already and nothing about what it can mine has changed.
                // A neighbour changing is not a reason to walk three thousand blocks again.
                return;
            }
            BlockPos ore = findOre(level);
            if (ore == null) {
                if (searching) {
                    status = MinerStatus.SEARCHING;
                    setChanged();
                    level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
                    return;
                }
                settle(level, MinerStatus.NO_ORE);
                return;
            }
            // The modules and the pickaxe are read as the cycle starts and hold for the cycle,
            // which is Factorio's rule for modules.
            ModuleEffect effect = modules.effect();
            cycleTicks = cycleTicksFor(tier, effect, enchantmentLevel(level, pickaxe(), Enchantments.EFFICIENCY));
            draw = effect.scaleEnergy(tier.energyPerTick());
        }

        if (!spendable(level)) {
            settle(level, tier.isElectric() ? MinerStatus.NO_POWER : MinerStatus.NO_FUEL);
            return;
        }

        progress++;
        spend();
        Pollution.emitTick(level, worldPosition, tier.pollutionPerMinute(), modules.effect().energyFactor());
        MinerFeedback.chug(level, worldPosition, plume(), progress);

        if (progress >= cycleTicks) {
            progress = 0;
            mine(level);
        }

        status = MinerStatus.MINING;
        setLit(level, true);
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /**
     * The next ore to work: the one already chosen if it is still there, else the next one down
     * the columns.
     *
     * <p>The walk is budgeted and resumes where it stopped, so a drill over bare rock costs a few
     * ticks of looking rather than one long one. A drill that walks the whole area and finds
     * nothing puts the cursor back at the top for the next time it is asked, and forgets what it
     * was mining.
     */
    private @Nullable BlockPos findOre(ServerLevel level) {
        searching = false;
        ItemStack pickaxe = pickaxe();
        FakePlayer miner = fakePlayer(level);
        if (target != null && minable(level, target, pickaxe, miner)) {
            return target;
        }
        target = null;

        if (rescan || currentY == Integer.MIN_VALUE) {
            columnIndex = 1;
            currentY = worldPosition.getY() - 1;
            rescan = false;
        }

        DigArea area = digArea();
        int floor = Math.max(Config.MINE_FLOOR.get(), level.getMinY());
        int budget = SCAN_BUDGET_PER_TICK;
        while (columnIndex <= area.columns()) {
            DigArea.Column column = area.column(columnIndex);
            if (column == null) {
                break;
            }
            while (currentY >= floor) {
                if (budget-- <= 0) {
                    searching = true;
                    return null;
                }
                BlockPos candidate = new BlockPos(column.x(), currentY, column.z());
                if (minable(level, candidate, pickaxe, miner)) {
                    target = candidate;
                    setMining(level.getBlockState(candidate).getBlock());
                    return target;
                }
                currentY--;
            }
            columnIndex++;
            currentY = worldPosition.getY() - 1;
        }

        columnIndex = 1;
        currentY = worldPosition.getY() - 1;
        setMining(null);
        return null;
    }

    /**
     * Whether this drill can take the block here: ore, in a loaded chunk, that this pickaxe
     * mines, where this drill's owner is allowed to dig.
     *
     * <p>Never {@code getBlockState} on an unloaded chunk - asking loads it, and an electric
     * drill's ring can cross a chunk edge. See {@code docs/PITFALLS.md}.
     */
    private static boolean minable(ServerLevel level, BlockPos pos, ItemStack pickaxe, FakePlayer miner) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(Tags.Blocks.ORES)
                && state.getDestroySpeed(level, pos) >= 0
                && pickaxe.isCorrectToolForDrops(state)
                && level.mayInteract(miner, pos);
    }

    /**
     * Takes one ore out of the target.
     *
     * <p>Announced as a break by the fake player, holding the pickaxe less its Silk Touch. When
     * something else handles the break - Crumbling Ore taking one harvest and dropping it where
     * the ore stands - whatever landed there is swept up; when nothing does, the ore's ordinary
     * drops are taken and the block becomes the rock it was in. Either way the ground stays
     * closed: a spent ore under Crumbling Ore is destroyed to air by that mod, and the drill puts
     * the rock back.
     */
    private void mine(ServerLevel level) {
        BlockPos ore = target;
        if (ore == null) {
            return;
        }
        ItemStack pickaxe = pickaxe();
        FakePlayer miner = fakePlayer(level);
        if (!minable(level, ore, pickaxe, miner)) {
            // Changed under us: another drill, a player. Look again next cycle.
            target = null;
            return;
        }
        BlockState state = level.getBlockState(ore);
        List<ItemStack> drops = new ArrayList<>();

        miner.setItemInHand(InteractionHand.MAIN_HAND, withoutSilkTouch(level, pickaxe));
        try {
            BreakBlockEvent event = new BreakBlockEvent(level, ore, state, miner);
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                drops.addAll(sweep(level, ore));
                if (drops.isEmpty()) {
                    // Nothing came of it, so this was a refusal rather than somebody doing the
                    // work in our place. Step past instead of trying forever.
                    target = null;
                    currentY--;
                    return;
                }
            } else {
                drops.addAll(Block.getDrops(state, level, ore, level.getBlockEntity(ore), miner,
                        miner.getMainHandItem()));
                level.setBlock(ore, hostRock(state), Block.UPDATE_ALL);
            }
        } finally {
            miner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        if (level.getBlockState(ore).isAir()) {
            level.setBlock(ore, hostRock(state), Block.UPDATE_ALL);
        }

        damagePickaxe(level, miner);
        hand.addAll(drops);
        MinerFeedback.broke(level, worldPosition, plume(), state);

        // Productivity: every ore earns a fraction of a free one, from the modules and from the
        // world's mining productivity research; a whole one is handed over when it is owed.
        productivity.earn(modules.effect().productivityBonus() + Bonuses.of(level, MINING_PRODUCTIVITY));
        if (productivity.owed() && !drops.isEmpty()) {
            productivity.pay();
            hand.add(drops.get(0).copy());
        }
    }

    /** The rock an ore sits in, which is what is left when the ore is gone. */
    static BlockState hostRock(BlockState ore) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(ore.getBlock());
        if (id.getPath().startsWith("deepslate_")) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (id.getPath().startsWith("nether_") || ore.is(Blocks.ANCIENT_DEBRIS)) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    /**
     * Sweeps up items lying where the drill just worked.
     *
     * <p>When another mod handles a break itself it drops the yield on the ground, because it has
     * no idea a machine is standing by. Rather than special-casing each such mod, the drill
     * collects whatever ended up there.
     */
    private static List<ItemStack> sweep(ServerLevel level, BlockPos pos) {
        List<ItemStack> swept = new ArrayList<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.5))) {
            if (entity.isRemoved() || entity.getItem().isEmpty()) {
                continue;
            }
            swept.add(entity.getItem().copy());
            entity.discard();
        }
        return swept;
    }

    /**
     * Puts what was mined down: the hand into the output slot, the output slot into whatever is
     * in front of the drill. Nothing leaves this machine unless the destination actually took it.
     */
    private void deliver(ServerLevel level) {
        Iterator<ItemStack> held = hand.iterator();
        while (held.hasNext()) {
            ItemStack stack = held.next();
            try (Transaction transaction = Transaction.openRoot()) {
                int put = inventory.insert(OUTPUT_SLOT, ItemResource.of(stack), stack.getCount(), transaction);
                if (put > 0) {
                    transaction.commit();
                    stack.shrink(put);
                }
            }
            if (stack.isEmpty()) {
                held.remove();
            }
        }

        int amount = inventory.getAmountAsInt(OUTPUT_SLOT);
        if (amount <= 0) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof MinerBlock drill)) {
            return;
        }
        Direction facing = drill.facing(state);
        ResourceHandler<ItemResource> front = level.getCapability(Capabilities.Item.BLOCK,
                drill.outputPos(state, worldPosition), facing.getOpposite());
        if (front == null) {
            return;
        }
        ItemResource resource = inventory.getResource(OUTPUT_SLOT);
        try (Transaction transaction = Transaction.openRoot()) {
            int accepted = front.insert(resource, amount, transaction);
            if (accepted > 0 && inventory.extract(OUTPUT_SLOT, resource, accepted, transaction) == accepted) {
                transaction.commit();
            }
        }
    }

    private ItemStack pickaxe() {
        return inventory.getResource(PICKAXE_SLOT).toStack(Math.max(1, inventory.getAmountAsInt(PICKAXE_SLOT)));
    }

    /** One point of durability, through the fake player so Unbreaking and any hooks on durability behave. */
    private void damagePickaxe(ServerLevel level, FakePlayer miner) {
        ItemStack worn = pickaxe();
        worn.hurtAndBreak(1, level, miner, item -> {});
        inventory.set(PICKAXE_SLOT, worn.isEmpty() ? ItemResource.EMPTY : ItemResource.of(worn),
                worn.isEmpty() ? 0 : 1);
    }

    private static ItemStack withoutSilkTouch(ServerLevel level, ItemStack tool) {
        if (enchantmentLevel(level, tool, Enchantments.SILK_TOUCH) == 0) {
            return tool.copy();
        }
        Holder<Enchantment> silkTouch = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        ItemStack copy = tool.copy();
        EnchantmentHelper.updateEnchantments(copy, enchantments -> enchantments.removeIf(silkTouch::equals));
        return copy;
    }

    private static int enchantmentLevel(ServerLevel level, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) {
            return 0;
        }
        Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    /**
     * The identity this machine mines as.
     *
     * <p>Mining through a fake player rather than calling world methods directly is what makes
     * the machine visible to the rest of the game: protection mods can refuse it, other mods see a
     * break event, and drops and tool damage are attributed to somebody.
     */
    private FakePlayer fakePlayer(ServerLevel level) {
        UUID id = ownerId != null ? ownerId : FALLBACK_OWNER;
        return FakePlayerFactory.get(level, new GameProfile(id, FAKE_PLAYER_NAME));
    }

    /** Where the drill's dust comes out: above its head, or its chimney. */
    private BlockPos plume() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof MinerBlock drill)) {
            return worldPosition.above();
        }
        return drill.shape().cellPos(worldPosition, drill.outputCell(), drill.facing(state))
                .above(drill.shape().height());
    }

    // -- Fuel and power ---------------------------------------------------------

    private boolean burnable(ItemResource resource) {
        return level == null || resource.toStack(1).getBurnTime(null, level.fuelValues()) > 0;
    }

    /** Whether there is something to pay this tick with: burning fuel, or charge. */
    private boolean spendable(ServerLevel level) {
        if (!tier.isElectric()) {
            return burnTime > 0 || refuel(level);
        }
        return energy != null && energy.getAmountAsInt() >= draw;
    }

    /** Fuel is spent only while mining, which is Factorio's rule: an idle burner keeps its coal. */
    private void spend() {
        if (!tier.isElectric()) {
            burnTime--;
        } else if (energy != null) {
            energy.set(energy.getAmountAsInt() - draw);
        }
    }

    private boolean refuel(ServerLevel level) {
        ItemResource candidate = inventory.getResource(FUEL_SLOT);
        if (candidate.isEmpty()) {
            return false;
        }
        int worth = candidate.toStack(1).getBurnTime(null, level.fuelValues());
        if (worth <= 0) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (inventory.extract(FUEL_SLOT, candidate, 1, transaction) != 1) {
                return false;
            }
            transaction.commit();
        }
        burnTime = worth;
        burnTimeTotal = worth;
        setChanged();
        return true;
    }

    // -- State ------------------------------------------------------------------

    /** Stops, for the given reason, keeping whatever progress was made. Nothing is scheduled. */
    private void settle(ServerLevel level, MinerStatus why) {
        status = why;
        setLit(level, false);
        setChanged();
    }

    /**
     * Turns the working light on or off across the whole machine.
     *
     * <p>Every cell carries {@code lit}, so every cell is set - on a transition rather than a
     * tick. The block that shows the lit face is not the block that holds the block entity: on an
     * electric drill the face is the output head at the front and the block entity is in the
     * middle, so lighting only this one would light nothing a player can see.
     */
    private void setLit(ServerLevel level, boolean lit) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof MinerBlock drill) || state.getValue(MinerBlock.LIT) == lit) {
            return;
        }
        for (BlockPos pos : drill.shape().positions(worldPosition, drill.facing(state))) {
            BlockState cell = level.getBlockState(pos);
            if (cell.getBlock() == drill && cell.getValue(MinerBlock.LIT) != lit) {
                level.setBlock(pos, cell.setValue(MinerBlock.LIT, lit),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    /** Records what is being mined and tells the clients watching, who name it on the screen. */
    private void setMining(@Nullable Block block) {
        if (mining == block) {
            return;
        }
        mining = block;
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private void wake() {
        if (level == null || level.isClientSide() || isRemoved()) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            level.scheduleTick(worldPosition, block, 1);
        }
    }

    /** Coal, a pickaxe, or ore taken away. A new pickaxe may reach ores the old one could not. */
    private void onInventoryChanged() {
        rescan = true;
        setChanged();
        wake();
    }

    private void onModulesChanged() {
        setChanged();
        wake();
    }

    /** Electricity arrived, or was spent. The wake is the half that matters. */
    private void onPowerChanged() {
        setChanged();
        wake();
    }

    // -- Menu -------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MinerMenu(containerId, playerInventory, inventory, modules, menuData, worldPosition, tier);
    }

    /** Spilled when the machine is broken: the slots, the modules and whatever was in hand. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        spill(inventory, pos);
        spill(modules, pos);
        for (ItemStack stack : hand) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
        hand.clear();
    }

    private void spill(net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler slots, BlockPos pos) {
        for (int slot = 0; slot < slots.size(); slot++) {
            int amount = slots.getAmountAsInt(slot);
            if (amount <= 0) {
                continue;
            }
            ItemResource resource = slots.getResource(slot);
            slots.set(slot, ItemResource.EMPTY, 0);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), resource.toStack(amount));
        }
    }

    // -- Persistence ------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("Inventory"));
        modules.serialize(output.child("Modules"));
        productivity.save(output);
        if (energy != null) {
            energy.serialize(output.child("Energy"));
        }
        output.putInt("Progress", progress);
        output.putInt("CycleTicks", cycleTicks);
        output.putInt("Draw", draw);
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnTimeTotal", burnTimeTotal);
        output.putInt("ColumnIndex", columnIndex);
        output.putInt("CurrentY", currentY);
        if (target != null) {
            output.putIntArray("Target", new int[] {target.getX(), target.getY(), target.getZ()});
        }
        if (mining != null) {
            output.putString("Mining", BuiltInRegistries.BLOCK.getKey(mining).toString());
        }
        if (ownerId != null) {
            output.putString("OwnerId", ownerId.toString());
        }
        ValueOutput.TypedOutputList<ItemStack> held = output.list("Hand", ItemStack.CODEC);
        for (ItemStack stack : hand) {
            held.add(stack);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Inventory").ifPresent(inventory::deserialize);
        input.child("Modules").ifPresent(modules::deserialize);
        productivity.load(input);
        if (energy != null) {
            input.child("Energy").ifPresent(energy::deserialize);
        }
        progress = input.getIntOr("Progress", 0);
        cycleTicks = input.getIntOr("CycleTicks", 0);
        draw = input.getIntOr("Draw", tier.energyPerTick());
        burnTime = input.getIntOr("BurnTime", 0);
        burnTimeTotal = input.getIntOr("BurnTimeTotal", 0);
        columnIndex = input.getIntOr("ColumnIndex", 1);
        currentY = input.getIntOr("CurrentY", Integer.MIN_VALUE);
        target = input.getIntArray("Target")
                .filter(xyz -> xyz.length == 3)
                .map(xyz -> new BlockPos(xyz[0], xyz[1], xyz[2]))
                .orElse(null);
        mining = input.getString("Mining")
                .map(Identifier::tryParse)
                .map(BuiltInRegistries.BLOCK::getValue)
                .filter(block -> block != Blocks.AIR)
                .orElse(null);
        ownerId = input.getString("OwnerId").map(UUID::fromString).orElse(null);
        hand.clear();
        input.list("Hand", ItemStack.CODEC).ifPresent(held -> held.forEach(hand::add));
        // A loaded drill walks its columns from the top: the cursor is kept for a drill loaded
        // mid-search, but a drill that had run out looks again in case the world changed.
        rescan = status == MinerStatus.NO_ORE || target == null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
