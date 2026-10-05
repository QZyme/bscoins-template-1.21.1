package com.beishan.bscoins.handler;

import com.beishan.bscoins.BSCoins;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * 村民永久8折交易工具。
 * 在村民 persistent NBT 上打标记, 并立即把当前所有交易改为打折价。
 */
public final class VillagerDiscountOps {
    public static final String TAG_DISCOUNTED = "bscoins_discounted";

    private VillagerDiscountOps() {
    }

    /**
     * 给村民上永久 8 折: 打标记 + 把当前所有交易改成折后价。
     *
     * @return true 表示这次是"首次"打折 (调用方应该扣掉那枚币); false 表示只是重新贴一次, 免费。
     */
    public static boolean grantPermanentDiscount(AbstractVillager villager, Player player, int percent) {
        if (villager.level().isClientSide) return false;
        boolean first = !isDiscounted(villager);
        applyDiscount(villager, percent);
        // 只在首次提示, 否则每次打开交易界面都会刷屏
        if (first && player != null) {
            player.sendSystemMessage(Component.translatable("message.bscoins.villager")
                    .withStyle(ChatFormatting.GREEN));
        }
        return first;
    }

    /**
     * 对村民所有交易应用降价。percent 例如 20 表示打 8 折 (减 20%)。
     */
    public static void applyDiscount(AbstractVillager villager, int percent) {
        if (villager.level().isClientSide) return;
        CompoundTag nbt = villager.getPersistentData();
        nbt.putBoolean(TAG_DISCOUNTED, true);
        reapply(villager, percent);
        logApplied(villager);
    }

    public static void reapply(AbstractVillager villager, int percent) {
        MerchantOffers offers = villager.getOffers();
        if (offers == null) return;
        for (MerchantOffer offer : offers) {
            // 原版折扣机制: 在基础上叠加固定负 diff, 与 demand 浮动并存 (同英雄村庄)
            int base = offer.getBaseCostA().getCount();
            int discount = (int) Math.ceil(base * percent / 100.0);
            // 防止折后价 <= 0
            int diff = -Math.min(discount, base - 1);
            if (offer.getSpecialPriceDiff() != diff) {
                offer.setSpecialPriceDiff(diff);
            }
        }
    }

    public static boolean isDiscounted(AbstractVillager villager) {
        return villager.getPersistentData().getBoolean(TAG_DISCOUNTED);
    }

    public static void logApplied(AbstractVillager villager) {
        BSCoins.LOGGER.debug("北山小卖部: 村民 {} 已获得永久 {} 折交易", villager.getUUID(), "8");
    }
}
