package com.beishan.bscoins.item;

import java.util.List;

import com.beishan.bscoins.Config;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;

/**
 * 收藏 - 合成部件 (书与笔+末影珍珠)。
 * 右键方块 = 收藏/取消收藏该坐标; 潜行右键 = 报出收藏夹 (存在玩家持久数据里, 最多 favoriteMax 个)。
 */
public class FavoriteItem extends Item {
    private static final String TAG_FAVORITES = "bscoins_favorites";
    private static final int MAX_LISTED = 16;

    public FavoriteItem() {
        super(new Item.Properties().stacksTo(64));
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        if (!Config.ENABLE_FAVORITE.get()) return InteractionResult.PASS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;

        ListTag list = player.getPersistentData().getList(TAG_FAVORITES, Tag.TAG_STRING);

        // 潜行右键: 只报点, 不改动
        if (player.isShiftKeyDown()) {
            if (list.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.bscoins.favorite_empty")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                player.sendSystemMessage(Component.translatable("message.bscoins.favorite_list", list.size())
                        .withStyle(ChatFormatting.GOLD));
                for (int i = 0; i < Math.min(list.size(), MAX_LISTED); i++) {
                    player.sendSystemMessage(Component.literal("  " + (i + 1) + ". " + list.getString(i))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        String entry = context.getLevel().dimension().location()
                + " " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();

        ListTag updated = new ListTag();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            String existing = list.getString(i);
            if (!removed && existing.equals(entry)) {
                removed = true;
                continue;
            }
            updated.add(StringTag.valueOf(existing));
        }
        if (!removed) {
            int max = Config.FAVORITE_MAX.get();
            if (updated.size() >= max) {
                player.sendSystemMessage(Component.translatable("message.bscoins.favorite_full", max)
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            updated.add(StringTag.valueOf(entry));
        }

        player.getPersistentData().put(TAG_FAVORITES, updated);
        player.sendSystemMessage(Component
                .translatable(removed ? "message.bscoins.favorite_removed" : "message.bscoins.favorite_added", entry)
                .withStyle(removed ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.6F, removed ? 0.8F : 1.5F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.favorite").withStyle(ChatFormatting.GRAY));
    }
}
