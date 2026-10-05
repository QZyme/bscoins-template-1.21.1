package com.beishan.bscoins.item;

import java.util.List;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;
import com.beishan.bscoins.entity.AirdropCrateEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 结构空投 - 右键从天空空投一个装满物资的箱子。
 * 投放的是一个 {@link AirdropCrateEntity} (自己的实体 + 渲染器, 天上掉下来的是真正的箱子模型),
 * 它会以 {@code airdropFallSpeed} 的速度<b>慢慢从空中降落</b>, 落地后变成真正的箱子并填好战利品。
 *
 * <p>早期版本用的是装着箱子方块的 {@code FallingBlockEntity}, 结果天上看不见任何东西 ——
 * 原版箱子没有方块模型 (只有 {@code textures.particle}), 是靠方块实体渲染器画的,
 * 而 {@code FallingBlockRenderer} 只渲染方块模型, 所以只剩一路粒子。
 */
public class StructureAirdropItem extends Item {
    /** 投放高度兜底上限: 按下降速度折算, 免得配置写太离谱导致箱子要在天上飞几分钟 */
    private static final double MAX_FALL_TICKS = 600.0;

    public StructureAirdropItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // 从玩家正上方找一个可落地的位置 (箱子的目标格)
            BlockPos land = findLandingSpot(serverLevel, player.blockPosition());

            double speed = Math.max(0.005, Config.AIRDROP_FALL_SPEED.get());
            int height = (int) Math.min(Config.AIRDROP_DROP_HEIGHT.get(), speed * MAX_FALL_TICKS);
            height = Math.max(1, height);
            // 出生点只要在建筑高度以内就行 (我们的实体不会像 FallingBlockEntity 那样挖掉出生点的方块)
            int spawnY = Math.min(land.getY() + height, serverLevel.getMaxBuildHeight() - 2);
            height = Math.max(1, spawnY - land.getY());

            AirdropCrateEntity crate = new AirdropCrateEntity(BSCoins.AIRDROP_CRATE_TYPE.get(), serverLevel,
                    new Vec3(land.getX() + 0.5, spawnY, land.getZ() + 0.5),
                    player.getUUID(), player.getYRot(), serverLevel.random.nextLong(), land.getY(), speed);
            serverLevel.addFreshEntity(crate);

            serverLevel.playSound(null, player.blockPosition(), SoundEvents.PARROT_AMBIENT,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
            player.sendSystemMessage(Component.translatable("message.bscoins.airdrop", height)
                    .withStyle(ChatFormatting.AQUA));
            if (!player.getAbilities().instabuild) {
                player.getItemInHand(hand).shrink(1);
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private static BlockPos findLandingSpot(ServerLevel level, BlockPos origin) {
        int x = origin.getX();
        int z = origin.getZ();
        // 从玩家头顶 30 格往下找第一个可替换/空气方块且下方有支撑
        int y = Math.min(origin.getY() + 30, level.getMaxBuildHeight() - 2);
        for (; y > level.getMinBuildHeight() + 1; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (level.isEmptyBlock(p) && !level.isEmptyBlock(p.below())) {
                return p;
            }
        }
        return origin.above();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.structure_airdrop").withStyle(ChatFormatting.GRAY));
    }
}
