package com.beishan.bscoins.handler;

import com.beishan.bscoins.Config;
import com.beishan.bscoins.item.BSCoinItem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 村民永久8折:
 * <ul>
 *   <li>{@link PlayerInteractEvent.EntityInteract} —— 真正把折扣发给村民的地方;</li>
 *   <li>{@link EntityTickEvent.Post} —— 兜底: 没有人在交易时把被原版清零的折扣重新贴上。</li>
 * </ul>
 */
public class VillagerDiscountHandler {

    /**
     * 右键村民给出永久 8 折。
     *
     * <p>为什么必须挂在这个事件上, 而不是只写在 {@code BSCoinItem#interactLivingEntity} 里:
     * 原版 {@code Villager#mobInteract} 会自己打开交易界面并返回 {@code consumesAction()},
     * 于是 {@code Player#interactOn} 根本不会再去调用 {@code Item#interactLivingEntity} ——
     * 那段村民分支在正常右键时永远进不去。
     *
     * <p>本事件在 {@code Entity#interact} 与 {@code Item#interactLivingEntity} 之前触发,
     * 所以能抢在原版 {@code updateSpecialPrices}(它是在折扣上"叠加"声望/英雄折扣)之前把折扣打好;
     * 不取消事件, 交易界面照常打开, 玩家立刻就能在界面上看到折后价。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // 别的 mod 已经把这个交互吃掉(比如村民增强类 mod 自己接管了右键)时,
        // 原版就不会打开交易界面了 —— 那这时也不该收钱
        if (event.isCanceled()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.ENABLE_BS_COIN.get() || !Config.ENABLE_VILLAGER_DISCOUNT.get()) return;
        // 潜行右键时原版会走 Mob#mobInteract -> PASS -> Item#interactLivingEntity,
        // 那条路由 BSCoinItem 处理, 这里让开避免重复扣币
        if (player.isSecondaryUseActive()) return;

        // 手上得真有币: 主手副手都算 (客户端允许副手拿币右键, 这里不能只认主手)
        ItemStack stack = coinInEitherHand(player, event.getHand());
        if (stack.isEmpty()) return;

        // 原版在这几种情况下压根不会打开交易界面: 收钱就等于白收一枚币
        if (!villager.isAlive() || villager.isBaby() || villager.isTrading()) return;
        if (villager.getOffers().isEmpty()) {
            // 没职业 / 还没补过货的村民. 给出原因, 不然玩家只会觉得"币被吞了"
            player.displayClientMessage(
                    Component.translatable("message.bscoins.villager_no_trades"), true);
            return;
        }

        // 已经打过折: 免费重新贴一次。原版 Villager#stopTrading() 会 resetSpecialPrices() 清零,
        // 所以每次重开界面都得再贴一遍, 但不能再收钱。
        if (VillagerDiscountOps.isDiscounted(villager)) {
            VillagerDiscountOps.grantPermanentDiscount(villager, player, Config.VILLAGER_DISCOUNT_PERCENT.get());
            return;
        }

        if (VillagerDiscountOps.grantPermanentDiscount(villager, player, Config.VILLAGER_DISCOUNT_PERCENT.get())) {
            int consume = Config.VILLAGER_CONSUME.get();
            if (!player.getAbilities().instabuild && consume > 0) {
                stack.shrink(Math.min(consume, stack.getCount()));
            }
            CoinUsageTracker.markCoinUsed(player.serverLevel(), player);
        }
    }

    /**
     * 取手上那枚币: 优先用这次交互实际用手, 没有就退到另一只手。
     *
     * <p>事件里的 {@code getHand()} 是原版按"主手能交互就主手"挑出来的,
     * 但玩家可能把币拿在副手 —— 那时 {@code getHand()} 是主手, 直接用它就会
     * 判成"手上没币"、白丢一次打折。
     */
    private static ItemStack coinInEitherHand(ServerPlayer player, InteractionHand usedHand) {
        ItemStack used = player.getItemInHand(usedHand);
        if (used.getItem() instanceof BSCoinItem) return used;
        InteractionHand other = usedHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack alt = player.getItemInHand(other);
        return alt.getItem() instanceof BSCoinItem ? alt : ItemStack.EMPTY;
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractVillager villager)) return;
        if (!Config.ENABLE_VILLAGER_DISCOUNT.get()) return;
        if (!VillagerDiscountOps.isDiscounted(villager)) return;
        // 有玩家正在交易时绝对不要动价格: 客户端界面上显示的是"打开界面那一瞬间"发过去的那份
        // offer (原版 MerchantMenu 不会重发), 这时再改服务端价格会让显示的价和实际收的价对不上。
        if (villager.getTradingPlayer() != null) return;
        VillagerDiscountOps.reapply(villager, Config.VILLAGER_DISCOUNT_PERCENT.get());
    }
}
