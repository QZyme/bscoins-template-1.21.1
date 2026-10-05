package com.beishan.bscoins.item;

import java.util.List;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 点赞 - 合成部件 (红石+纸)。
 * 右键给自己和附近玩家点个赞: 每人一段鸡血 Buff + 红心粒子 (有冷却, 不消耗物品)。
 */
public class LikeItem extends Item {
    public LikeItem() {
        super(new Item.Properties().stacksTo(64));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!Config.ENABLE_LIKE.get()) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);

        double radius = Config.LIKE_RADIUS.get();
        int seconds = Config.LIKE_BUFF_SECONDS.get();
        int affected = 0;
        for (Player target : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(radius))) {
            if (target.distanceToSqr(player) > radius * radius) continue;
            target.addEffect(new MobEffectInstance(BSCoins.GOAD, seconds * 20, 0, false, true));
            affected++;
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        target.getX(), target.getY() + 2.2, target.getZ(), 3, 0.3, 0.2, 0.3, 0.01);
            }
        }

        player.getCooldowns().addCooldown(this, Config.LIKE_COOLDOWN_SECONDS.get() * 20);
        player.sendSystemMessage(Component.translatable("message.bscoins.like", affected)
                .withStyle(ChatFormatting.RED));
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.like").withStyle(ChatFormatting.GRAY));
    }
}
