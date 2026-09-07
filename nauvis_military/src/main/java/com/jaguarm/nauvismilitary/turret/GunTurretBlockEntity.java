package com.jaguarm.nauvismilitary.turret;

import com.jaguarm.nauvislib.bonus.Bonuses;
import com.jaguarm.nauvislib.transfer.MachineAccess;
import com.jaguarm.nauvismilitary.registry.ModBlockEntities;
import com.jaguarm.nauvismilitary.registry.ModDamageTypes;
import com.jaguarm.nauvismilitary.weapon.Bullets;
import com.jaguarm.nauvismilitary.weapon.GunItem;
import com.jaguarm.nauvismilitary.weapon.MagazineItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * A gun turret: given magazines, it shoots whatever hostile comes within eighteen blocks.
 *
 * <p>Factorio's numbers: a range of eighteen, ten rounds a second, the magazine's damage raised by
 * both physical projectile damage research - {@code ammo-damage} for bullets - and the turret's
 * own {@code turret-attack} bonus, which is what the {@code physical-projectile-damage} ladder
 * grants and what its screen has promised since the tree was transcribed.
 *
 * <p>What it shoots is anything Minecraft calls an enemy: the hostiles pollution brings, and any
 * other that wanders in. Never a player, never an animal.
 *
 * <h2>It sleeps, mostly</h2>
 *
 * <p>A turret with no ammunition schedules nothing and costs nothing; a magazine arriving wakes
 * it. A turret with ammunition looks around twice a second - an entity search in a box, which is
 * the cheapest thing a server does with entities - and while it has a target it ticks at its rate
 * of fire. That look is the one bounded poll in this mod: Minecraft does not tell a block when a
 * zombie walks into range, and a turret that waited to be told would be a wall.
 */
public class GunTurretBlockEntity extends BlockEntity implements MenuProvider {

    public static final int AMMO_SLOT = 0;
    public static final int SLOT_COUNT = 1;

    /** Factorio's gun turret: eighteen tiles, ten shots a second. */
    public static final double RANGE = 18.0;
    public static final int COOLDOWN_TICKS = 6;

    /** How often a loaded turret with nothing in sight looks again. */
    public static final int SCAN_TICKS = 10;

    /** Factorio's modifier type for a turret's own damage bonus, and this turret's name in it. */
    public static final String TURRET_ATTACK = "turret-attack";
    public static final String GUN_TURRET = "gun-turret";

    public enum Status {
        /** Nothing in the slot. */
        NO_AMMO,
        /** Loaded, and nothing in range. */
        WATCHING,
        /** Shooting. */
        FIRING;

        public static Status of(int ordinal) {
            Status[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NO_AMMO;
        }
    }

    private final TurretInventory inventory = new TurretInventory(this::onInventoryChanged);

    /** What inserters see: magazines in, nothing out. */
    private final ResourceHandler<ItemResource> automationView = new MachineAccess(inventory, SLOT_COUNT);

    private @Nullable LivingEntity target;
    private Status status = Status.NO_AMMO;

    /** Rounds fired since it was placed, for the readout. */
    private int shots;

    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case GunTurretMenu.DATA_STATUS -> status.ordinal();
                case GunTurretMenu.DATA_ROUNDS -> roundsLeft();
                case GunTurretMenu.DATA_SHOTS -> shots;
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {
            // Server-authoritative.
        }

        @Override
        public int getCount() {
            return GunTurretMenu.DATA_COUNT;
        }
    };

    public GunTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUN_TURRET.get(), pos, state);
    }

    public TurretInventory inventory() {
        return inventory;
    }

    public ResourceHandler<ItemResource> automationView() {
        return automationView;
    }

    public Status status() {
        return status;
    }

    public int shots() {
        return shots;
    }

    /** Rounds left in the magazine in the slot, or none. */
    public int roundsLeft() {
        if (inventory.getAmountAsInt(AMMO_SLOT) <= 0) {
            return 0;
        }
        ItemStack magazine = inventory.getResource(AMMO_SLOT).toStack(1);
        return magazine.getMaxDamage() - magazine.getDamageValue();
    }

    /** Called by {@link GunTurretBlock}, and only ever on a tick this turret asked for. */
    public void serverTick(ServerLevel level) {
        if (inventory.getAmountAsInt(AMMO_SLOT) <= 0) {
            target = null;
            settle(Status.NO_AMMO);
            return;
        }

        Vec3 muzzle = muzzle();
        if (!canShoot(level, target, muzzle)) {
            target = findTarget(level, muzzle);
        }
        if (target == null) {
            status = Status.WATCHING;
            setChanged();
            level.scheduleTick(worldPosition, getBlockState().getBlock(), SCAN_TICKS);
            return;
        }

        fire(level, muzzle, target);
        status = Status.FIRING;
        setChanged();
        level.scheduleTick(worldPosition, getBlockState().getBlock(), COOLDOWN_TICKS);
    }

    /** Where the shots come from: the middle of the turret, just over the barrels. */
    public Vec3 muzzle() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof GunTurretBlock block)) {
            return Vec3.atCenterOf(worldPosition).add(0, 0.6, 0);
        }
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (BlockPos pos : block.shape().positions(worldPosition, block.facing(state))) {
            sum = sum.add(Vec3.atCenterOf(pos));
            count++;
        }
        return sum.scale(1.0 / count).add(0, 0.6, 0);
    }

    /** The nearest enemy in range that the turret can see, or null. */
    private @Nullable LivingEntity findTarget(ServerLevel level, Vec3 muzzle) {
        AABB reach = new AABB(muzzle, muzzle).inflate(RANGE);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, reach, entity -> entity instanceof Enemy)) {
            double distance = candidate.distanceToSqr(muzzle);
            if (distance < nearestDistance && canShoot(level, candidate, muzzle)) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** Alive, in range, and nothing solid between the muzzle and it. */
    private static boolean canShoot(ServerLevel level, @Nullable LivingEntity entity, Vec3 muzzle) {
        if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.isSpectator()) {
            return false;
        }
        if (entity.distanceToSqr(muzzle) > RANGE * RANGE) {
            return false;
        }
        return level.clip(new ClipContext(muzzle, entity.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    private void fire(ServerLevel level, Vec3 muzzle, LivingEntity at) {
        ItemStack magazine = inventory.getResource(AMMO_SLOT).toStack(1);
        if (!(magazine.getItem() instanceof MagazineItem rounds)) {
            return;
        }
        double bonus = Bonuses.of(level, GunItem.AMMO_DAMAGE, GunItem.BULLET)
                + Bonuses.of(level, TURRET_ATTACK, GUN_TURRET);
        float damage = (float) (rounds.damage() * (1 + bonus));

        Vec3 aim = at.getEyePosition().subtract(muzzle);
        Bullets.fire(level, null, muzzle, aim, RANGE + 1, damage, ModDamageTypes.turret(level, muzzle));
        Bullets.crack(level, muzzle, 0.8F);
        shots++;

        // One round off the magazine; the last round empties the slot.
        int used = magazine.getDamageValue() + 1;
        if (used >= magazine.getMaxDamage()) {
            inventory.set(AMMO_SLOT, ItemResource.EMPTY, 0);
        } else {
            magazine.setDamageValue(used);
            inventory.set(AMMO_SLOT, ItemResource.of(magazine), 1);
        }
    }

    private void settle(Status why) {
        status = why;
        setChanged();
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

    private void onInventoryChanged() {
        setChanged();
        wake();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GunTurretMenu(containerId, playerInventory, inventory, menuData, worldPosition);
    }

    /** The magazine spills when the turret is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        int amount = inventory.getAmountAsInt(AMMO_SLOT);
        if (amount > 0) {
            ItemResource resource = inventory.getResource(AMMO_SLOT);
            inventory.set(AMMO_SLOT, ItemResource.EMPTY, 0);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), resource.toStack(amount));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("Inventory"));
        output.putInt("Shots", shots);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Inventory").ifPresent(inventory::deserialize);
        shots = input.getIntOr("Shots", 0);
    }
}
