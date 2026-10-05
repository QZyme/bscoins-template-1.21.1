package com.beishan.bscoins.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * NBT查看器 - 右键目标显示 NBT 到聊天栏; 右键生物显示生物 NBT。
 */
public class NbtViewerItem extends Item {
    public NbtViewerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (player != null) {
            player.sendSystemMessage(Component.literal("[BlockNBT] " + state.getBlock().getName())
                    .withStyle(ChatFormatting.AQUA));
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                sendNbt(player, be.saveWithFullMetadata(level.registryAccess()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, Player player, @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (target instanceof Player tp && tp.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        player.sendSystemMessage(Component.literal("[EntityNBT] " + target.getType().getDescription())
                .withStyle(ChatFormatting.AQUA));
        sendNbt(player, target.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        return InteractionResult.SUCCESS;
    }

    private static void sendNbt(Player player, net.minecraft.nbt.Tag tag) {
        player.sendSystemMessage(Component.literal(tag.toString()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.nbt_viewer").withStyle(ChatFormatting.GRAY));
    }
}
