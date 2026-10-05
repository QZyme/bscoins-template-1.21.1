package com.beishan.bscoins.item;

import com.beishan.bscoins.Config;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 基建狂魔镐 - 效率V 耐久III (附魔在商店交易里应用), 并且非潜行时按视线方向 3x3 范围挖掘。
 * 潜行 = 恢复原版单格挖掘; 带方块实体的方块(箱子/刷怪笼等)不会被连锁, 免得拆家。
 */
public class InfraPickaxeItem extends PickaxeItem {
    /** 连锁挖掘的递归保护 (只会在服务端主线程跑) */
    private static boolean areaMining = false;

    public InfraPickaxeItem() {
        super(Tiers.DIAMOND, new Item.Properties()
                .attributes(PickaxeItem.createAttributes(Tiers.DIAMOND, 1.0F, -2.8F)));
    }

    @Override
    public boolean mineBlock(@NotNull ItemStack stack, @NotNull Level level, @NotNull BlockState state,
                             @NotNull BlockPos pos, @NotNull LivingEntity miningEntity) {
        boolean result = super.mineBlock(stack, level, state, pos, miningEntity);

        if (!Config.ENABLE_PICKAXE_AREA_MINE.get() || areaMining || stack.isEmpty()) return result;
        if (!(level instanceof ServerLevel serverLevel)) return result;
        if (!(miningEntity instanceof Player player) || player.isShiftKeyDown()) return result;

        // 以玩家视线的主轴决定 3x3 平面: 朝上/下挖就是水平面, 朝侧面挖就是竖直面
        Direction.Axis axis = Direction.getNearest(player.getLookAngle().x, player.getLookAngle().y,
                player.getLookAngle().z).getAxis();

        areaMining = true;
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    BlockPos target = switch (axis) {
                        case Y -> pos.offset(a, 0, b);
                        case X -> pos.offset(0, a, b);
                        case Z -> pos.offset(a, b, 0);
                    };
                    BlockState targetState = serverLevel.getBlockState(target);
                    if (targetState.isAir() || targetState.hasBlockEntity()) continue;
                    if (!stack.isCorrectToolForDrops(targetState)) continue;
                    serverLevel.destroyBlock(target, true, player);
                }
            }
        } finally {
            areaMining = false;
        }
        return result;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.infra_pickaxe").withStyle(ChatFormatting.GRAY));
    }
}
