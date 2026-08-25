package com.jaguarm.nauvismachines.machine.assembler;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block half of the assembling machine: placement, breaking, and how a player tells it what
 * to make.
 *
 * <p>Until there is a screen, the controls are the block itself:
 *
 * <ul>
 *   <li><b>Right-click holding an ingredient of what it is making</b> - load it. This is the
 *       only way to feed a machine by hand until inserters exist.
 *   <li><b>Right-click holding anything else</b> - make that. The machine looks for the timed
 *       recipe producing it and takes that as its recipe. Nothing is consumed; the item is a
 *       pointer, not a payment.
 *   <li><b>Right-click empty-handed</b> - say what it is making.
 *   <li><b>Sneak + right-click empty-handed</b> - forget the recipe. Also how you re-target a
 *       machine to make one of its own ingredients.
 * </ul>
 *
 * <p>Building against the machine still works the way it does for a chest or a furnace: sneak
 * while holding the block you are placing.
 */
public class AssemblerBlock extends BaseEntityBlock {

    public static final MapCodec<AssemblerBlock> CODEC = simpleCodec(AssemblerBlock::new);

    public AssemblerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblerBlockEntity(pos, state);
    }

    // No getTicker override on purpose. A ticker would run on every machine every tick whether
    // or not it has work; this block is driven by scheduled ticks it asks for itself, so an
    // idle machine costs nothing. See AssemblerBlockEntity.

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof AssemblerBlockEntity assembler) {
            assembler.serverTick(level);
        }
    }

    /**
     * A neighbour changing is a wake-up. Nothing next door matters to the machine yet, but
     * power will, and a machine that only wakes on its own inventory would sleep through a
     * cable being connected.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof AssemblerBlockEntity assembler)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        // Read the name before anything is taken: a stack emptied by the click would otherwise
        // report itself as Air.
        Component name = stack.getHoverName();

        // Ingredients first. A machine already making gear wheels should take the iron plate it
        // is short of, not decide it would rather make iron plates.
        if (assembler.wants(serverLevel, stack)) {
            int taken = assembler.acceptFromHand(stack);
            if (taken > 0 && !player.hasInfiniteMaterials()) {
                stack.shrink(taken);
            }
            player.sendOverlayMessage(Component.translatable(
                    taken > 0 ? "nauvis_machines.assembler.loaded" : "nauvis_machines.assembler.full",
                    name));
            return InteractionResult.SUCCESS;
        }

        ResourceKey<Recipe<?>> recipe = AssemblerBlockEntity.recipeProducing(serverLevel, stack.getItem());
        if (recipe == null) {
            player.sendOverlayMessage(
                    Component.translatable("nauvis_machines.assembler.no_recipe", name));
            return InteractionResult.SUCCESS;
        }

        assembler.setRecipe(recipe);
        player.sendOverlayMessage(
                Component.translatable("nauvis_machines.assembler.set", name));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof AssemblerBlockEntity assembler)) {
            return InteractionResult.PASS;
        }

        if (player.isSecondaryUseActive()) {
            assembler.setRecipe(null);
            player.sendOverlayMessage(Component.translatable("nauvis_machines.assembler.cleared"));
            return InteractionResult.SUCCESS;
        }

        ResourceKey<Recipe<?>> recipe = assembler.recipeKey();
        player.sendOverlayMessage(recipe == null
                ? Component.translatable("nauvis_machines.assembler.idle")
                : Component.translatable("nauvis_machines.assembler.making",
                        Component.literal(recipe.identifier().toString())));
        return InteractionResult.SUCCESS;
    }

    // Nothing spills its contents here. In 26.2 that is BlockEntity#preRemoveSideEffects, and
    // by the time affectNeighborsAfterRemoval runs the block entity is already gone - which
    // looks like working code and drops nothing. See AssemblerBlockEntity.
}
