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
        if (first) {
            BSCoins.LOGGER.debug("北山小卖部: 村民 {} 已获得永久 -{}% 交易", villager.getUUID(), percent);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.bscoins.villager")
                        .withStyle(ChatFormatting.GREEN));
            }
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
    }

    public static void reapply(AbstractVillager villager, int percent) {
        if (villager.level().isClientSide) return;
        MerchantOffers offers = villager.getOffers();
        if (offers == null) return;
        for (MerchantOffer offer : offers) {
            // 原版折扣机制: 在基础上叠加固定负 diff, 与 demand 浮动并存 (同英雄村庄)
            int base = offer.getBaseCostA().getCount();
            int discount = (int) Math.ceil(base * percent / 100.0);
            // 防止折后价 <= 0
            int diff = -Math.min(discount, base - 1);
            // 刻意"覆盖"而不是和已有的 diff 取 min:
            // 原版 startTrading() 是 先 updateSpecialPrices(叠加声望/英雄折扣) 再 setTradingPlayer,
            // 而本类的兜底只会在"没人在交易"时重贴, 所以声望折扣永远是叠在我们这个 -percent% 之上,
            // 不会被抹掉; 反过来若取 min, 万一手上的 diff 已经叠过声望, 重贴时就会把它算进去再叠一遍。
            if (offer.getSpecialPriceDiff() != diff) {
                offer.setSpecialPriceDiff(diff);
            }
        }
    }

    public static boolean isDiscounted(AbstractVillager villager) {
        return villager.getPersistentData().getBoolean(TAG_DISCOUNTED);
    }
}
