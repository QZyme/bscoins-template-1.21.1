package com.beishan.bscoins.item;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;
import com.beishan.bscoins.handler.CoinUsageTracker;
import com.beishan.bscoins.handler.VillagerDiscountOps;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 北山币 - 核心交互道具。
 * 右键玩家: 打赏鸡血+老板 Buff
 * 右键怪物: 眩晕或内讧
 * 右键村民(Villager): 永久8折交易 (正常右键时由 VillagerDiscountHandler 的
 *     PlayerInteractEvent.EntityInteract 抢先处理, 因为原版会先打开交易界面)
 * 潜行右键空气: 打开北山开发助手 (消耗1币)
 */
public class BSCoinItem extends Item {
    public BSCoinItem() {
        super(new Item.Properties().stacksTo(64));
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, Player player, @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.PASS;
        // enableBsCoin = 北山币总开关 (同时也是 bscoins:config 配方条件)
        if (!Config.ENABLE_BS_COIN.get()) return InteractionResult.PASS;

        boolean acted = false;
        // 是否真的动用了一枚币 (已打过折的村民重新贴折扣不算), 决定要不要记入催更计时
        boolean usedCoin = false;
        int consume = 0;
        switch (target) {
            case Player targetPlayer when Config.ENABLE_TIP.get() -> {
                int dur = Config.TIP_DURATION_SECONDS.get() * 20;
                targetPlayer.addEffect(new MobEffectInstance(BSCoins.GOAD, dur, Config.GOAD_LEVEL.get()));
                targetPlayer.addEffect(new MobEffectInstance(BSCoins.BOSS, dur, Config.BOSS_LEVEL.get()));
                targetPlayer.sendSystemMessage(Component.translatable("message.bscoins.tip")
                        .withStyle(ChatFormatting.GOLD));
                consume = Config.TIP_CONSUME.get();
                usedCoin = true;
                acted = true;
            }
            // 村民必须排在 Mob 前面: Villager 本身也是 Mob, 否则永远匹配不到这一支
            // (正常右键时原版会先打开交易界面, 走的是 VillagerDiscountHandler 的事件)
            case Villager villager when Config.ENABLE_VILLAGER_DISCOUNT.get() -> {
                // 仅村民, 不含流浪商人(北山小卖部本体)
                if (VillagerDiscountOps.grantPermanentDiscount(villager, player, Config.VILLAGER_DISCOUNT_PERCENT.get())) {
                    consume = Config.VILLAGER_CONSUME.get();
                    usedCoin = true;
                }
                acted = true;
            }
            // 村民/流浪商人不能被币砸晕 (流浪商人就是北山小卖部本体)
            case Mob mob when Config.ENABLE_MONSTER_ATTACK.get() && !(target instanceof AbstractVillager) -> {
                hitMonster(player, mob);
                consume = Config.MONSTER_CONSUME.get();
                usedCoin = true;
                acted = true;
            }
            default -> {
            }
        }

        if (acted) {
            if (usedCoin) {
                CoinUsageTracker.markCoinUsed((ServerLevel) player.level(), (ServerPlayer) player);
            }
            if (!player.getAbilities().instabuild && consume > 0) {
                stack.shrink(consume);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void hitMonster(Player player, Mob mob) {
        double chance = Config.STUN_CHANCE.get();
        if (player.getRandom().nextDouble() < chance) {
            mob.addEffect(new MobEffectInstance(BSCoins.STUN, Config.STUN_DURATION_SECONDS.get() * 20, 0));
        } else {
            int radiusSq = Config.INFIGHT_RADIUS.get() * Config.INFIGHT_RADIUS.get();
            Mob nearest = null;
            double best = Double.MAX_VALUE;
            for (Mob e : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(Config.INFIGHT_RADIUS.get()))) {
                if (e == mob || e instanceof AbstractVillager) continue;
                double d = mob.distanceToSqr(e);
                if (d < best && d <= radiusSq) {
                    best = d;
                    nearest = e;
                }
            }
            if (nearest != null) {
                mob.setTarget(nearest);
                nearest.setTarget(mob);
                if (player.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                            mob.getX(), mob.getY() + 1, mob.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        // 潜行右键空气 -> 开发助手 (客户端判定目标为空气后才向服务器请求)
        if (player.isShiftKeyDown() && Config.ENABLE_BS_COIN.get() && Config.ENABLE_DEV_ASSISTANT.get()) {
            if (level.isClientSide) {
                // 具体实现放在 client 包里: 通用类不能直接引用客户端类, 否则专用服务器会崩
                com.beishan.bscoins.client.CoinAssistantClient.tryOpenAssistant();
            }
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.bs_coin").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.bscoins.bs_coin_2").withStyle(ChatFormatting.DARK_GRAY));
    }
}