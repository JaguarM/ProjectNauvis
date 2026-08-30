package com.jaguarm.nauvismining.machine.miner;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.jaguarm.nauvismining.Config;
import com.jaguarm.nauvismining.NauvisMining;
import com.jaguarm.nauvismining.machine.MachineTier;
import com.jaguarm.nauvismining.machine.ModuleItem;
import com.jaguarm.nauvismining.machine.ModuleType;
import com.jaguarm.nauvismining.machine.Spiral;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import com.jaguarm.nauvismining.registry.ModBlockEntities;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.LimitingEnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The miner digs a square spiral of columns centred on itself, each column running from
 * the block directly beneath the machine down to the configured floor.
 *
 * <p>It needs solid fuel and a pickaxe, and — unless disabled in the config — one
 * cobblestone per block mined, which it uses to backfill the hole. The shovel slot is
 * optional: it only makes dirt, sand and gravel fast, since those drop by hand anyway.
 * Tools take durability damage per block and respect Silk Touch, Fortune and Efficiency.
 */
public class MinerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SLOT_FUEL = 0;
    public static final int SLOT_COBBLE = 1;
    public static final int SLOT_PICKAXE = 2;
    public static final int SLOT_SHOVEL = 3;
    /** Module slots are always allocated; the tier decides how many are unlocked. */
    public static final int SLOT_MODULE_START = 4;
    public static final int MODULE_SLOTS = MachineTier.MAX_MODULE_SLOTS;
    public static final int SLOT_OUTPUT_START = SLOT_MODULE_START + MODULE_SLOTS;
    public static final int OUTPUT_SLOTS = 9;
    public static final int SLOT_COUNT = SLOT_OUTPUT_START + OUTPUT_SLOTS;

    /** Which tool, if any, is needed for the block currently being dug. */
    private enum ToolChoice {
        /** Cannot be mined with the equipment present. */
        NONE,
        /** Breakable by hand, no tool damage. */
        HAND,
        PICKAXE,
        SHOVEL
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final MachineTier tier;

    private int burnTime;
    private int burnTimeTotal;

    /** 1-based spiral index of the column currently being dug. */
    private int columnIndex = 1;
    private int currentY = Integer.MIN_VALUE;
    private int elapsedTicks;
    private int requiredTicks;
    private MinerStatus status = MinerStatus.NO_PICKAXE;
    private DigMode digMode = DigMode.ORE_ONLY;

    /** How often the miner scans its neighbours to push output. */
    private static final int PUSH_INTERVAL_TICKS = 20;

    /** Last range pushed to clients, so an unchanged range costs no packets. */
    private int lastSyncedRange = -1;

    /**
     * Who placed this machine. The miner acts through a fake player carrying this identity,
     * so land-protection mods judge it as its owner rather than as an anonymous machine.
     * Null on machines placed before this was tracked, or by non-players.
     */
    private @Nullable UUID ownerId;

    /**
     * Fixed name paired with the owner's UUID. Protection mods key on the UUID, and a
     * constant name makes the machine recognisable in logs rather than impersonating the
     * player outright.
     */
    private static final String FAKE_PLAYER_NAME = "[NauvisMining]";

    /** How many blocks the target search may examine in one tick. */
    private static final int SCAN_BUDGET_PER_TICK = 512;

    /** Set when the search stopped on the budget rather than on running out of area. */
    private boolean scanBudgetExhausted;

    /**
     * Set when the search passed over a block that needs a shovel for its drops while the
     * shovel slot could not supply one. Only then is a missing shovel worth reporting;
     * vanilla's shovel blocks all drop by hand, so an empty slot is normally fine.
     */
    private boolean shovelSkipped;

    /** Block currently showing our break overlay, so it can be cleared when we move on. */
    private @Nullable BlockPos visualTarget;

    /**
     * Energy buffer. Present on every tier so the field stays simple, but only drawn from
     * by electric ones; a burner miner never touches it and exposes no capability.
     *
     * <p>Unrestricted so the machine can spend from it. External access goes through
     * {@link #cableView} instead.
     */
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(
            Config.ENERGY_CAPACITY.get(), Config.ENERGY_CAPACITY.get(), Config.ENERGY_CAPACITY.get());

    /**
     * What cables see: insertion only. A miner is a consumer, not a battery, so a network
     * must not be able to pull its charge back out. The buffer itself has to stay
     * extractable or the machine could not spend its own energy.
     */
    private final EnergyHandler cableView =
            new LimitingEnergyHandler(energy, Config.ENERGY_CAPACITY.get(), 0);

    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINER.get(), pos, state);
        // One block entity type backs all four miner blocks; the tier comes from whichever
        // block this entity was placed for.
        this.tier = state.getBlock() instanceof MinerBlock miner ? miner.tier() : MachineTier.BURNER;
    }

    public MachineTier tier() {
        return tier;
    }

    /** Records the placer so the machine can act on their behalf. */
    public void setOwner(@Nullable LivingEntity placer) {
        if (placer instanceof Player player) {
            ownerId = player.getUUID();
            setChanged();
        }
    }

    /**
     * The identity this machine mines as.
     *
     * <p>Mining through a fake player rather than calling world methods directly is what
     * makes the machine visible to the rest of the game: protection mods can refuse it,
     * other mods see a break event, and drops and tool damage are attributed to somebody.
     */
    private FakePlayer fakePlayer(ServerLevel level) {
        UUID id = ownerId != null ? ownerId : FALLBACK_OWNER;
        return FakePlayerFactory.get(level, new GameProfile(id, FAKE_PLAYER_NAME));
    }

    /** Used when the placer is unknown, e.g. a machine placed by another machine. */
    private static final UUID FALLBACK_OWNER = UUID.nameUUIDFromBytes("nauvis_mining:miner".getBytes());

    public NonNullList<ItemStack> items() {
        return items;
    }

    public boolean isBurning() {
        return burnTime > 0;
    }

    // -- Ticking ----------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, MinerBlockEntity miner) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        miner.tick(serverLevel);
    }

    private void tick(ServerLevel level) {
        if (currentY == Integer.MIN_VALUE) {
            currentY = worldPosition.getY() - 1;
        }

        boolean changed = false;

        if (burnTime > 0) {
            burnTime--;
            changed = true;
        }

        MinerStatus newStatus = runMining(level);
        if (newStatus != MinerStatus.RUNNING) {
            // Stopped for any reason: out of fuel, area finished, tool pulled. Drop the
            // break overlay so a halted machine does not leave a half-cracked block behind
            // suggesting it is still working.
            clearWorkingOn(level);
        }
        if (newStatus != status) {
            status = newStatus;
            changed = true;
        }

        if (pushOutput(level)) {
            changed = true;
        }

        syncLitState(level);
        syncRangeToClients(level);

        if (changed) {
            setChanged();
        }
    }

    /**
     * Pushes mined items into any adjacent inventory, so a chest beside the miner is
     * enough and no hopper is needed.
     *
     * <p>Throttled rather than run every tick: a full scan of six neighbours 20 times a
     * second is wasted work when the machine produces a block every few seconds at best.
     *
     * @return true if anything moved
     */
    private boolean pushOutput(ServerLevel level) {
        if (level.getGameTime() % PUSH_INTERVAL_TICKS != 0) {
            return false;
        }

        boolean moved = false;
        for (Direction direction : Direction.values()) {
            if (isOutputEmpty()) {
                break;
            }
            ResourceHandler<ItemResource> target = level.getCapability(
                    Capabilities.Item.BLOCK,
                    worldPosition.relative(direction),
                    direction.getOpposite());
            if (target == null) {
                continue;
            }
            moved |= pushInto(target);
        }
        return moved;
    }

    private boolean pushInto(ResourceHandler<ItemResource> target) {
        boolean moved = false;
        for (int slot = SLOT_OUTPUT_START; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            // Transactional insert: nothing leaves this machine unless the destination
            // actually took it, so a full chest cannot void the stack.
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = target.insert(ItemResource.of(stack), stack.getCount(), transaction);
                if (inserted <= 0) {
                    continue;
                }
                transaction.commit();
                stack.shrink(inserted);
                if (stack.isEmpty()) {
                    items.set(slot, ItemStack.EMPTY);
                }
                moved = true;
            }
        }
        return moved;
    }

    private boolean isOutputEmpty() {
        for (int slot = SLOT_OUTPUT_START; slot < SLOT_COUNT; slot++) {
            if (!items.get(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs one step of mining and reports why it could or could not proceed. Every early
     * return is a distinct reason so the GUI can tell the player what is missing rather
     * than sitting silently idle.
     */
    private MinerStatus runMining(ServerLevel level) {
        if (items.get(SLOT_PICKAXE).isEmpty()) {
            return MinerStatus.NO_PICKAXE;
        }
        if (needsBackfill() && items.get(SLOT_COBBLE).isEmpty()) {
            return MinerStatus.NO_COBBLE;
        }
        if (!drawPower(level)) {
            return tier.isElectric() ? MinerStatus.NO_ENERGY : MinerStatus.NO_FUEL;
        }

        BlockPos target = findNextTarget(level);
        if (target == null) {
            // No target because the scan budget ran out is not the same as no target
            // because the area is finished.
            if (scanBudgetExhausted) {
                return MinerStatus.RUNNING;
            }
            // Finished, but something in the area insisted on a shovel we do not have, so
            // say so rather than claiming the area is cleared.
            return shovelSkipped ? MinerStatus.NO_SHOVEL : MinerStatus.COMPLETE;
        }

        advanceMining(level, target);
        return MinerStatus.RUNNING;
    }

    /**
     * Keeps the LIT blockstate in step with the machine, so the model shows activity.
     *
     * <p>Driven by whether the drill is actually working, not by whether fuel happens to
     * be burning. An electric drill has no burn time at all and so never lit up, and a
     * burner with fuel but no pickaxe used to glow while doing nothing.
     */
    private void syncLitState(ServerLevel level) {
        BlockState state = getBlockState();
        boolean lit = status == MinerStatus.RUNNING;
        if (!state.hasProperty(MinerBlock.LIT) || state.getValue(MinerBlock.LIT) == lit) {
            return;
        }
        if (!(state.getBlock() instanceof MinerBlock drill)) {
            return;
        }

        // Every block of the drill, not just this one. The block that shows the lit face is not
        // the block that holds the block entity - on an electric drill the face is the output
        // head at the front and the block entity is in the middle - so lighting only this one
        // would light nothing a player can see. Setting them all also keeps the drill from
        // rendering half-lit, which is what happens the moment they disagree.
        Direction facing = drill.facing(state);
        for (int part = 0; part < drill.shape().cellCount(); part++) {
            BlockPos cell = drill.shape().cellPos(worldPosition, part, facing);
            BlockState cellState = level.getBlockState(cell);
            if (cellState.getBlock() == drill && cellState.getValue(MinerBlock.LIT) != lit) {
                level.setBlockAndUpdate(cell, cellState.setValue(MinerBlock.LIT, lit));
            }
        }
    }

    /**
     * Takes whatever this tier runs on for one tick of work.
     *
     * <p>Electric miners draw FE only while actually mining, so an idle one costs nothing
     * to leave powered. Burner tiers keep the furnace model of consuming a whole fuel item
     * up front and spending it down.
     *
     * @return false if there is nothing to run on
     */
    private boolean drawPower(ServerLevel level) {
        if (!tier.isElectric()) {
            return burnTime > 0 || tryConsumeFuel(level);
        }

        int cost = energyPerTick();
        if (cost <= 0) {
            return true;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (energy.extract(cost, transaction) < cost) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    /** FE per tick after modules; the same trade speed and efficiency make with fuel. */
    private int energyPerTick() {
        float factor = moduleFactor(ModuleType.SPEED, true) * moduleFactor(ModuleType.EFFICIENCY, true);
        return Math.max(1, Math.round(Config.ENERGY_PER_TICK.get() * factor));
    }

    /** The insert-only view for cables, or null on tiers that do not use energy. */
    public @Nullable EnergyHandler cableView() {
        return tier.isElectric() ? cableView : null;
    }

    public int storedEnergy() {
        return energy.getAmountAsInt();
    }

    private boolean tryConsumeFuel(ServerLevel level) {
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty()) {
            return false;
        }
        int itemBurnTime = fuel.getBurnTime(RecipeType.SMELTING, level.fuelValues());
        if (itemBurnTime <= 0) {
            return false;
        }
        int scaled = scaledBurnTime(itemBurnTime);
        burnTime = scaled;
        burnTimeTotal = scaled;
        fuel.shrink(1);
        return true;
    }

    // -- Mining -----------------------------------------------------------------

    private void advanceMining(ServerLevel level, BlockPos target) {
        BlockState state = level.getBlockState(target);
        ToolChoice tool = toolFor(state, target);
        if (tool == ToolChoice.NONE) {
            return;
        }

        int needed = miningDuration(level, state, target, tool);
        if (needed == Integer.MAX_VALUE) {
            // Effectively unbreakable: hardness below zero, or a mod vetoing the break
            // speed. Step past instead of counting toward a total we will never reach.
            currentY--;
            return;
        }
        if (requiredTicks != needed) {
            // The target changed under us (another miner, a player, a falling block).
            // Re-time rather than breaking a block we never spent the ticks on.
            requiredTicks = needed;
            elapsedTicks = 0;
            return;
        }

        if (elapsedTicks < requiredTicks) {
            elapsedTicks++;
            showWorkingOn(level, target, state);
            MinerFeedback.chug(level, worldPosition, target, state, elapsedTicks);
            return;
        }

        MinerFeedback.broke(level, worldPosition, target, state);
        clearWorkingOn(level);
        mineBlock(level, target, state, tool);
        elapsedTicks = 0;
        requiredTicks = 0;
    }

    /**
     * Draws break progress on the block currently being worked.
     *
     * <p>This is the single clearest signal that a miner is alive: without it the machine
     * does nothing visible until a block silently vanishes. Reuses vanilla's crack overlay,
     * so it works on any block including modded ones, and needs no model or texture.
     */
    private void showWorkingOn(ServerLevel level, BlockPos target, BlockState state) {
        // Leave ore alone. Ore is where other mods most often take over a break — Crumbling
        // Ore paints its own wear overlay on exactly these blocks — and two crack overlays
        // on one block fight. Losing progress feedback on ore is the cheaper of the two,
        // and cannot be decided by asking, since we deliberately depend on nothing.
        if (state.is(Tags.Blocks.ORES)) {
            return;
        }
        if (!target.equals(visualTarget)) {
            clearWorkingOn(level);
            visualTarget = target.immutable();
        }
        int stage = requiredTicks <= 0 ? 0 : (int) ((long) elapsedTicks * 9 / requiredTicks);
        level.destroyBlockProgress(breakerId(), target, Math.clamp(stage, 0, 9));
    }

    private void clearWorkingOn(ServerLevel level) {
        if (visualTarget != null) {
            level.destroyBlockProgress(breakerId(), visualTarget, -1);
            visualTarget = null;
        }
    }

    /**
     * A stable id derived from the machine's position, so two miners working nearby do not
     * overwrite each other's progress display, and so it cannot collide with a player's own
     * break animation.
     */
    private int breakerId() {
        return 2_000_000 + (worldPosition.hashCode() & 0x00FF_FFFF);
    }

    /**
     * Walks down the current column, then outward along the spiral, until it finds a
     * block this miner can actually break. Returns null when the whole area is done.
     */
    private BlockPos findNextTarget(ServerLevel level) {
        int floor = Math.max(Config.MINE_FLOOR.get(), level.getMinY());
        int totalColumns = Spiral.columnsForRadius(range());
        int budget = SCAN_BUDGET_PER_TICK;
        scanBudgetExhausted = false;

        while (columnIndex <= totalColumns) {
            Spiral.Offset offset = Spiral.offset(columnIndex);
            BlockPos column = worldPosition.offset(offset.x(), 0, offset.z());

            while (currentY >= floor) {
                // A filtered miner skips everything that is not an ore, so it can walk
                // whole columns without finding work. Cap the walk per tick and resume
                // where it left off, rather than stalling the server thread.
                if (budget-- <= 0) {
                    scanBudgetExhausted = true;
                    return null;
                }
                BlockPos candidate = new BlockPos(column.getX(), currentY, column.getZ());
                if (toolFor(level.getBlockState(candidate), candidate) != ToolChoice.NONE
                        && level.mayInteract(fakePlayer(level), candidate)) {
                    return candidate;
                }
                currentY--;
            }

            columnIndex++;
            currentY = worldPosition.getY() - 1;
        }
        return null;
    }

    /** Decides which tool breaks this block, or NONE if the miner cannot handle it. */
    private ToolChoice toolFor(BlockState state, BlockPos pos) {
        if (state.isAir()) {
            return ToolChoice.NONE;
        }
        // Never chew through bedrock or other unbreakable blocks.
        if (state.getDestroySpeed(level, pos) < 0) {
            return ToolChoice.NONE;
        }
        // Skip liquids; the original treated them as unmineable without a filler upgrade.
        if (!state.getFluidState().isEmpty()) {
            return ToolChoice.NONE;
        }
        // Don't re-mine our own backfill.
        if (state.is(Blocks.COBBLESTONE) && needsBackfill()) {
            return ToolChoice.NONE;
        }

        // Ore only, unless the mode says otherwise. Skipped blocks are never broken, so no
        // hole forms, no fill is spent and no stone reaches the output. This is the default
        // because it is the behaviour that survives a player's first hour.
        if (!digMode.breaksEverything() && !state.is(Tags.Blocks.ORES)) {
            return ToolChoice.NONE;
        }

        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return items.get(SLOT_PICKAXE).isCorrectToolForDrops(state) ? ToolChoice.PICKAXE : ToolChoice.NONE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            if (items.get(SLOT_SHOVEL).isCorrectToolForDrops(state)) {
                return ToolChoice.SHOVEL;
            }
            // The shovel slot is optional. Dirt, sand and gravel drop when broken by hand,
            // so an empty slot costs speed — vanilla's wrong-tool penalty — instead of
            // stopping the machine. Only a block that insists on a shovel for its drops,
            // which is a modded thing to do, is skipped.
            if (state.requiresCorrectToolForDrops()) {
                shovelSkipped = true;
                return ToolChoice.NONE;
            }
            return ToolChoice.HAND;
        }
        return ToolChoice.HAND;
    }

    /**
     * Ticks to break a block, using vanilla's own timing.
     *
     * <p>Previously this used the 1.12.2 mod's approximation, {@code hardness * 1.5 * 20}
     * with Efficiency at 1.3x per level. Both are wrong for modern Minecraft, so a machine
     * visibly disagreed with a player mining the same block with the same pickaxe. Vanilla
     * computes progress per tick as {@code speed / hardness / modifier} and breaks the
     * block once that accumulates to 1.
     *
     * <p>Module and config multipliers are applied on top of the vanilla result rather than
     * replacing it, so tool tier, hardness and Efficiency keep behaving as players expect.
     */
    private int miningDuration(ServerLevel level, BlockState state, BlockPos pos, ToolChoice tool) {
        float progressPerTick = destroyProgress(level, state, pos, tool);
        if (progressPerTick <= 0.0f) {
            return Integer.MAX_VALUE;
        }

        float ticks = 1.0f / progressPerTick
                * miningTimeFactor()
                / Config.MINING_SPEED_MULTIPLIER.get().floatValue();
        return Math.max((int) Math.ceil(ticks), 1);
    }

    /** Vanilla's per-tick break progress: {@code speed / hardness / modifier}. */
    private float destroyProgress(ServerLevel level, BlockState state, BlockPos pos, ToolChoice tool) {
        float hardness = state.getDestroySpeed(this.level, pos);
        if (hardness < 0.0f) {
            return -1.0f;
        }
        if (hardness == 0.0f) {
            // Instant-break blocks; dividing by zero hardness would be infinite progress.
            return 1.0f;
        }

        ItemStack toolStack = tool == ToolChoice.HAND ? ItemStack.EMPTY : items.get(slotFor(tool));
        // 30 with the right tool, 100 without: vanilla's own penalty for bare hands or the
        // wrong tool, which is why punching stone takes so long.
        float modifier = toolStack.isCorrectToolForDrops(state) ? 30.0f : 100.0f;
        return destroySpeed(level, state, pos, toolStack) / hardness / modifier;
    }

    private float destroySpeed(ServerLevel level, BlockState state, BlockPos pos, ItemStack toolStack) {
        float speed = toolStack.getDestroySpeed(state);
        if (speed > 1.0f) {
            int efficiency = enchantmentLevel(toolStack, Enchantments.EFFICIENCY);
            if (efficiency > 0) {
                // Vanilla's curve, not a flat multiplier: level squared plus one.
                speed += efficiency * efficiency + 1;
            }
        }
        // Lets mob effects and other mods adjust the rate, as they would for a player.
        // Largely academic for a stationary machine, but it costs nothing to be correct.
        return EventHooks.getBreakSpeed(fakePlayer(level), state, speed, pos);
    }

    private void mineBlock(ServerLevel level, BlockPos target, BlockState state, ToolChoice tool) {
        BlockEntity targetEntity = level.getBlockEntity(target);
        ItemStack toolStack = tool == ToolChoice.HAND ? ItemStack.EMPTY : items.get(slotFor(tool));
        FakePlayer miner = fakePlayer(level);

        // Announce the break the same way a player's would be. Without this the machine is
        // invisible to everything else: protection mods, block-break logging, and any mod
        // that reacts to mining never hear about it.
        BreakBlockEvent breakEvent = new BreakBlockEvent(level, target, state, miner);
        if (NeoForge.EVENT_BUS.post(breakEvent).isCanceled()) {
            // Somebody else handled or refused this break. Collect anything they dropped
            // in our place, damage the tool as though we had swung it, and leave the block
            // alone — a mod that cancelled the break has decided what stands there now.
            //
            // This is how the drill cooperates with mods that intercept mining, Crumbling
            // Ore among them: an ore that wears down over several hits cancels the break
            // each time and drops one harvest, and the drill simply keeps working it.
            boolean handled = absorbDrops(level, target);
            if (tool != ToolChoice.HAND) {
                damageTool(level, slotFor(tool));
            }
            if (!handled) {
                // Nothing came of it, so this was a refusal rather than somebody else
                // doing the work. Step past instead of retrying forever.
                //
                // Whether anything dropped is the signal, not whether the block changed:
                // an ore that wears down over several hits keeps the very same blockstate
                // throughout, holding its progress elsewhere, so "unchanged" would have
                // read every hit as a refusal and walked away after the first.
                currentY--;
            }
            return;
        }

        List<ItemStack> drops;
        if (enchantmentLevel(toolStack, Enchantments.SILK_TOUCH) > 0) {
            drops = List.of(new ItemStack(state.getBlock()));
        } else {
            drops = Block.getDrops(state, level, target, targetEntity, null, toolStack);
        }
        for (ItemStack drop : drops) {
            storeOrDrop(level, drop);
        }

        replaceMinedBlock(level, target);

        if (tool != ToolChoice.HAND) {
            damageTool(level, slotFor(tool));
        }

        // Always move down after taking a block, rather than letting the next scan decide.
        // A position can refill: lava meeting water makes cobblestone, and in a mode that
        // leaves holes the drill would mine that same spot forever, generating stone and
        // never reaching the bottom.
        currentY--;
    }

    /**
     * What counts as backfill.
     *
     * <p>Cobbled deepslate as well as cobblestone, because below y=0 stone stops dropping
     * cobblestone and starts dropping cobbled deepslate. Accepting only cobblestone meant
     * a miner refilled itself down to y=0 and then quietly starved for the rest of the
     * shaft, which is the least intuitive place for it to stop.
     */
    private static boolean isFillMaterial(ItemStack stack) {
        return stack.is(Blocks.COBBLESTONE.asItem()) || stack.is(Blocks.COBBLED_DEEPSLATE.asItem());
    }

    /** Backfills with whatever fill is loaded, or leaves air if this drill does not fill. */
    private void replaceMinedBlock(ServerLevel level, BlockPos target) {
        if (!needsBackfill()) {
            level.removeBlock(target, false);
            return;
        }

        ItemStack fill = items.get(SLOT_COBBLE);
        // Place what is actually in the slot rather than assuming cobblestone, so a miner
        // running on deepslate backfills with deepslate.
        BlockState placed = fill.getItem() instanceof BlockItem blockItem
                ? blockItem.getBlock().defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
        level.setBlockAndUpdate(target, placed);
        fill.shrink(1);
    }

    private void damageTool(ServerLevel level, int slot) {
        ItemStack toolStack = items.get(slot);
        // Damage through the fake player rather than a null breaker, so Unbreaking and any
        // mod hooks on durability see an entity and behave as they would for a player.
        toolStack.hurtAndBreak(1, level, fakePlayer(level), item -> {
            if (Config.DESTROY_TOOLS.get()) {
                items.set(slot, ItemStack.EMPTY);
            }
        });
    }

    /**
     * Sweeps up items lying at a position the drill just worked.
     *
     * <p>When another mod handles a break itself it drops the yield on the ground, because
     * it has no idea a machine is standing by. Rather than special-casing each such mod,
     * the drill collects whatever ended up there. Anything it cannot fit is left lying, as
     * it would be for a player.
     */
    private boolean absorbDrops(ServerLevel level, BlockPos target) {
        AABB area = new AABB(target).inflate(0.5);
        boolean collected = false;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (entity.isRemoved()) {
                continue;
            }
            ItemStack stack = entity.getItem();
            storeOrDrop(level, stack);
            collected = true;
            if (stack.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(stack);
            }
        }
        return collected;
    }

    /** Puts a stack in the output slots, dropping any remainder into the world. */
    private void storeOrDrop(ServerLevel level, ItemStack stack) {
        // Top the fill slot up first. A miner digging stone produces exactly the
        // cobblestone it needs to backfill the hole it just made, so feeding its own
        // output back in makes it self-sustaining instead of something you hand-feed
        // cobble to. Only the seed stack is needed to get it started.
        refillFromOutput(stack);

        for (int i = SLOT_OUTPUT_START; i < SLOT_COUNT && !stack.isEmpty(); i++) {
            ItemStack slotStack = items.get(i);
            if (slotStack.isEmpty()) {
                items.set(i, stack.copyAndClear());
                return;
            }
            if (ItemStack.isSameItemSameComponents(slotStack, stack)) {
                int room = slotStack.getMaxStackSize() - slotStack.getCount();
                int moved = Math.min(room, stack.getCount());
                slotStack.grow(moved);
                stack.shrink(moved);
            }
        }
        if (!stack.isEmpty()) {
            Block.popResource(level, worldPosition.above(), stack);
        }
    }

    /**
     * Moves as much of {@code stack} into the fill slot as it will take.
     *
     * <p>Only when backfilling is on: with it off there is nothing to feed, and diverting
     * cobble into a slot the machine never draws from would just lose it from the output.
     */
    private void refillFromOutput(ItemStack stack) {
        if (!needsBackfill() || stack.isEmpty() || !isFillMaterial(stack)) {
            return;
        }

        ItemStack fill = items.get(SLOT_COBBLE);
        if (fill.isEmpty()) {
            items.set(SLOT_COBBLE, stack.split(stack.getCount()));
            return;
        }
        if (!ItemStack.isSameItemSameComponents(fill, stack)) {
            return;
        }

        int room = fill.getMaxStackSize() - fill.getCount();
        int moved = Math.min(room, stack.getCount());
        fill.grow(moved);
        stack.shrink(moved);
    }

    private int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty() || level == null) {
            return 0;
        }
        Holder<Enchantment> holder = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    private static int slotFor(ToolChoice tool) {
        return tool == ToolChoice.PICKAXE ? SLOT_PICKAXE : SLOT_SHOVEL;
    }

    /**
     * How many modules of a type are installed in unlocked slots.
     *
     * <p>Counts stack sizes, not slots, and always re-checks that the slot is unlocked, so
     * a module that reached a locked slot by some route other than canPlaceItem still has
     * no effect.
     */
    private int moduleCount(ModuleType type) {
        int count = 0;
        for (int i = 0; i < MODULE_SLOTS; i++) {
            if (!tier.hasModuleSlot(i)) {
                continue;
            }
            ItemStack stack = items.get(SLOT_MODULE_START + i);
            if (ModuleItem.typeOf(stack) == type) {
                // One module per slot, Factorio-style. Counting stack sizes let three
                // slots of 64 range modules reach radius 193, which is 148k columns and
                // effectively unbounded. The menu caps the slot; this caps the effect,
                // so a stack arriving by any other route still cannot exceed one.
                count++;
            }
        }
        return count;
    }

    public DigMode digMode() {
        return digMode;
    }

    /** Advances to the next mode. Called from the screen's button, through the menu. */
    public void cycleDigMode() {
        digMode = digMode.next();
        // A drill that had finished its area is done for good until something changes what
        // it is looking for. Switching from ore to clearing everything is exactly that, and
        // without a restart the machine would sit reporting "Area cleared" over untouched
        // stone.
        restartScan();
        setChanged();
    }

    /** Sends the search back to the top of the first column. */
    private void restartScan() {
        columnIndex = 1;
        currentY = worldPosition.getY() - 1;
        elapsedTicks = 0;
        requiredTicks = 0;
        shovelSkipped = false;
    }

    /**
     * Whether this drill fills the holes it makes, and therefore needs fill material.
     *
     * <p>Only in clear-and-fill mode. Ore-only mode takes ore out of solid rock and leaves
     * pockets nobody will ever see, and charging fill for those would starve the machine:
     * it never breaks stone, so it never produces the cobblestone to refill itself.
     */
    private boolean needsBackfill() {
        return digMode.fills() && Config.REQUIRE_COBBLE_BACKFILL.get();
    }

    /** Radius in blocks: the configured base, widened by range modules. */
    public int range() {
        int bonus = moduleCount(ModuleType.RANGE) * ModuleType.RANGE.bonusRadius();
        return Config.INITIAL_RANGE.get() + bonus * Config.UPGRADE_RANGE.get();
    }

    /** Compounded module factor, e.g. 0.8^n for n speed modules. */
    private float moduleFactor(ModuleType type, boolean forFuel) {
        float per = forFuel ? type.fuelUseFactor() : type.miningTimeFactor();
        return (float) Math.pow(per, moduleCount(type));
    }

    /** Mining time multiplier from speed and efficiency modules combined. */
    private float miningTimeFactor() {
        return moduleFactor(ModuleType.SPEED, false) * moduleFactor(ModuleType.EFFICIENCY, false);
    }

    /**
     * How long one fuel item lasts, after modules.
     *
     * <p>Scaled here rather than by draining more per tick: burn time is an int, and
     * rounding a 1.4x drain back to 1 would silently discard the whole speed-module
     * penalty. Dividing the item's burn time keeps the trade-off exact.
     */
    private int scaledBurnTime(int itemBurnTime) {
        float factor = moduleFactor(ModuleType.SPEED, true) * moduleFactor(ModuleType.EFFICIENCY, true);
        return Math.max(1, Math.round(itemBurnTime / factor));
    }

    // -- Container --------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        // Supplying the shovel that the search had been walking past changes what the
        // machine is looking for, so send it back over the area it gave up on.
        if (slot == SLOT_SHOVEL && shovelSkipped && !stack.isEmpty()) {
            restartScan();
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /** Keeps players from dropping a pickaxe into the fuel slot and wondering why nothing burns. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return acceptsInSlot(slot, stack, level,
                new SlotRules(tier.moduleSlots(), tier.isElectric(), needsBackfill()));
    }

    /**
     * Whether a slot accepts a stack, as a pure function of the stack.
     *
     * <p>Static and container-independent on purpose. The client's menu is backed by a
     * plain SimpleContainer, which accepts anything, so a rule that consulted the container
     * would say "yes" on the client and "no" on the server. Shift-clicking then visibly
     * dropped the item into the first slot for a frame before the server corrected it.
     * Both sides now evaluate the same rule and agree immediately.
     *
     * @param unlockedModuleSlots how many module slots the machine's tier allows; the
     *                            client reads this from synced container data
     */
    public static boolean acceptsInSlot(int slot, ItemStack stack, @Nullable Level level, SlotRules rules) {
        return switch (slot) {
            // Electric tiers have no use for fuel, so the slot refuses it outright rather
            // than accepting coal that would sit there doing nothing.
            case SLOT_FUEL -> !rules.electric()
                    && level != null
                    && stack.getBurnTime(RecipeType.SMELTING, level.fuelValues()) > 0;
            // Only a boring drill fills its holes, so only a boring drill takes fill.
            case SLOT_COBBLE -> rules.needsFill() && isFillMaterial(stack);
            case SLOT_PICKAXE -> stack.is(ItemTags.PICKAXES);
            case SLOT_SHOVEL -> stack.is(ItemTags.SHOVELS);
            default -> {
                int moduleIndex = slot - SLOT_MODULE_START;
                yield moduleIndex >= 0
                        && moduleIndex < MODULE_SLOTS
                        && moduleIndex < rules.unlockedModuleSlots()
                        && ModuleItem.typeOf(stack) != null;
            }
        };
    }

    /**
     * The machine facts a slot rule needs, bundled so client and server can be handed the
     * same thing. The client reads all three out of synced container data.
     */
    public record SlotRules(int unlockedModuleSlots, boolean electric, boolean needsFill) {}

    // -- Automation faces -------------------------------------------------------

    private static final int[] OUTPUT_SLOTS_BY_INDEX =
            java.util.stream.IntStream.range(SLOT_OUTPUT_START, SLOT_COUNT).toArray();
    private static final int[] INPUT_SLOTS_BY_INDEX = java.util.stream.IntStream
            .concat(
                    java.util.stream.IntStream.of(SLOT_FUEL, SLOT_COBBLE, SLOT_PICKAXE, SLOT_SHOVEL),
                    java.util.stream.IntStream.range(SLOT_MODULE_START, SLOT_OUTPUT_START))
            .toArray();

    /**
     * A hopper underneath should drain the mined output, not steal the fuel.
     *
     * <p>Without this, automation sees a flat 14-slot container and works left to right
     * from slot 0, which is the fuel slot. Exposing only the output grid downward and only
     * the input slots elsewhere makes the obvious setup behave the way it looks.
     */
    @Override
    public int[] getSlotsForFace(Direction direction) {
        return direction == Direction.DOWN ? OUTPUT_SLOTS_BY_INDEX : INPUT_SLOTS_BY_INDEX;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        // Only finished product leaves the machine. Fuel, cobble and tools stay put,
        // otherwise a hopper would strip the pickaxe straight back out again.
        return slot >= SLOT_OUTPUT_START;
    }

    // -- MenuProvider -----------------------------------------------------------

    @Override
    public Component getDisplayName() {
        // Use the block's own name so the GUI title says "Stone Miner", not a generic label.
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MinerMenu(containerId, inventory, this, dataAccess, worldPosition);
    }

    /** Syncs burn progress to the client so the screen can draw a fuel indicator. */
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> burnTimeTotal;
                case 2 -> elapsedTicks;
                case 3 -> requiredTicks;
                case 4 -> status.ordinal();
                case 5 -> tier.moduleSlots();
                case 6 -> range();
                case 7 -> tier.isElectric() ? 1 : 0;
                case 8 -> energy.getAmountAsInt();
                case 9 -> energy.getCapacityAsInt();
                case 10 -> needsBackfill() ? 1 : 0;
                case 11 -> digMode.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> burnTimeTotal = value;
                case 2 -> elapsedTicks = value;
                case 3 -> requiredTicks = value;
                case 4 -> status = MinerStatus.byOrdinal(value);
                // index 5 (module slot count) is derived from the block, never assigned
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 12;
        }
    };

    // -- Client sync ------------------------------------------------------------

    /**
     * Sends the machine's state to clients so the dig-area preview can be drawn for a
     * miner the player is merely looking at, without opening its screen.
     *
     * <p>Only pushed when the range actually changes, which happens when a module is
     * inserted or pulled. Mining alone changes nothing the client needs.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncRangeToClients(ServerLevel level) {
        int current = range();
        if (current == lastSyncedRange) {
            return;
        }
        // A wider area is new ground to cover, so a drill that had finished goes back to
        // work. Only on a genuine change, and not on the first tick after loading, where
        // lastSyncedRange starts unset and would otherwise restart every drill in the world.
        if (lastSyncedRange >= 0 && current > lastSyncedRange) {
            restartScan();
        }
        lastSyncedRange = current;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    // -- Persistence ------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnTimeTotal", burnTimeTotal);
        output.putInt("ColumnIndex", columnIndex);
        output.putInt("CurrentY", currentY);
        output.putInt("ElapsedTicks", elapsedTicks);
        output.putInt("RequiredTicks", requiredTicks);
        if (ownerId != null) {
            output.putString("OwnerId", ownerId.toString());
        }
        energy.serialize(output.child("Energy"));
        output.putString("DigMode", digMode.id());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        burnTime = input.getIntOr("BurnTime", 0);
        burnTimeTotal = input.getIntOr("BurnTimeTotal", 0);
        columnIndex = input.getIntOr("ColumnIndex", 1);
        currentY = input.getIntOr("CurrentY", Integer.MIN_VALUE);
        elapsedTicks = input.getIntOr("ElapsedTicks", 0);
        requiredTicks = input.getIntOr("RequiredTicks", 0);
        ownerId = input.getString("OwnerId").map(UUID::fromString).orElse(null);
        input.child("Energy").ifPresent(energy::deserialize);
        // Stored by name, not ordinal, so reordering the enum cannot silently change what
        // an existing machine does.
        String mode = input.getStringOr("DigMode", DigMode.ORE_ONLY.id());
        for (DigMode candidate : DigMode.values()) {
            if (candidate.id().equals(mode)) {
                digMode = candidate;
                break;
            }
        }
    }
}
