package com.beishan.bscoins.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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

import java.util.List;

/**
 * 北山签名照 - 一张发亮的小卡片。
 *
 * <p>以前右键只给自己发一条聊天消息, 别人完全不知道发生了什么。
 * 现在举起来会"拍一张": 面前闪一下 (FLASH + 末地烛火星) + 快门音效,
 * 半径16格内的其他玩家都会收到"被闪到"的消息 —— 周围人一定能看见。
 */
public class BeishanPhotoItem extends Item {
    private static final double SHOW_RADIUS = 16.0;

    public BeishanPhotoItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(1.2));
            // 闪光灯 + 照片边缘的火星
            serverLevel.sendParticles(ParticleTypes.FLASH, front.x, front.y, front.z, 1, 0.0, 0.0, 0.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.END_ROD, front.x, front.y, front.z, 10, 0.25, 0.25, 0.25, 0.02);
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.PLAYERS, 0.6F, 1.6F);

            player.sendSystemMessage(Component.translatable("message.bscoins.photo")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            for (Player target : serverLevel.getEntitiesOfClass(Player.class,
                    player.getBoundingBox().inflate(SHOW_RADIUS))) {
                if (target == player || target.distanceToSqr(player) > SHOW_RADIUS * SHOW_RADIUS) continue;
                target.sendSystemMessage(Component.translatable("message.bscoins.photo_show", player.getDisplayName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.beishan_photo").withStyle(ChatFormatting.GRAY));
    }
}
