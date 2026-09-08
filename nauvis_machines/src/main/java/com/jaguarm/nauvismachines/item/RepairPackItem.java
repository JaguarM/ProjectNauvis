package com.jaguarm.nauvismachines.item;

import java.util.function.Consumer;

import com.jaguarm.nauvislib.health.Health;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/** Factorio's repair pack: two circuits and two gears, and it mends what the biters chewed. */
public class RepairPackItem extends Item {

    /** How much one pack mends: Factorio's durability of three hundred, taken as health. */
    public static final float MENDS = 300;

    public RepairPackItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("tooltip.nauvis_machines.repair_pack", Math.round(MENDS))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            // The server decides whether anything was mended; the hand swings either way.
            return InteractionResult.SUCCESS;
        }
        float mended = repair(level, context.getClickedPos(), context.getItemInHand(), context.getPlayer());
        return mended > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Spends one pack off the stack on what stands here, if it is damaged.
     *
     * @return how much was mended; nothing means the pack was kept
     */
    public static float repair(ServerLevel level, BlockPos pos, ItemStack stack, Player player) {
        float mended = Health.repair(level, pos, MENDS);
        if (mended <= 0) {
            return 0;
        }
        if (player == null || !player.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
        return mended;
    }
}
