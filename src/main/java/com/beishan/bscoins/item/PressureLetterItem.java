package com.beishan.bscoins.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

/**
 * 《北山催更函》- 催更律师函惩罚道具。
 */
public class PressureLetterItem extends Item {
    public PressureLetterItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.pressure_letter").withStyle(ChatFormatting.DARK_RED));
        tooltipComponents.add(Component.translatable("tooltip.bscoins.pressure_letter_2").withStyle(ChatFormatting.GRAY));
    }
}
