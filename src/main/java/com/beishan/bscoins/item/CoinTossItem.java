package com.beishan.bscoins.item;

import java.util.List;

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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 投币 - 合成部件 (绿宝石+铁锭)。
 * 右键掷硬币: 正面幸运, 反面霉运, 各带一段时间的 Buff (有冷却, 不消耗物品)。
 */
public class CoinTossItem extends Item {
    public CoinTossItem() {
        super(new Item.Properties().stacksTo(64));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!Config.ENABLE_TOSS.get()) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);

        boolean heads = player.getRandom().nextBoolean();
        int seconds = Config.TOSS_LUCK_SECONDS.get();
        player.addEffect(new MobEffectInstance(heads ? MobEffects.LUCK : MobEffects.UNLUCK,
                seconds * 20, 0, false, true));
        player.getCooldowns().addCooldown(this, Config.TOSS_COOLDOWN_SECONDS.get() * 20);
        player.sendSystemMessage(Component
                .translatable(heads ? "message.bscoins.toss_heads" : "message.bscoins.toss_tails")
                .withStyle(heads ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(heads ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.6, player.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.PLAYERS, 0.7F, heads ? 1.6F : 0.7F);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.coin_toss").withStyle(ChatFormatting.GRAY));
    }
}
