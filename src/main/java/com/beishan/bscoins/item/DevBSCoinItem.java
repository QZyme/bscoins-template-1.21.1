package com.beishan.bscoins.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 开发版北山币 - 右键功能与北山币相同; 潜行Q 可执行 /reload。
 * 与北山币共用同一个 OBJ 模型, 且带附魔闪光(紫色 glint)以示区分。
 */
public class DevBSCoinItem extends BSCoinItem {
    public DevBSCoinItem() {
        super();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.dev_bs_coin").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return true;
    }
}
