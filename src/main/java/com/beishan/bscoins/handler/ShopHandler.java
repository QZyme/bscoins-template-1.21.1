package com.beishan.bscoins.handler;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/**
 * 北山小卖部 - 替换流浪商人交易, 出售:
 * 催更大喇叭、鸽子诱捕器、基建狂魔镐(效率V耐久III)、北山签名照、NBT查看器、结构空投。
 */
public class ShopHandler {

    @SubscribeEvent
    public void onWandererTrades(WandererTradesEvent event) {
        if (!Config.ENABLE_SHOP.get()) return;

        // 替换默认交易
        event.getGenericTrades().clear();
        event.getRareTrades().clear();

        // 基建狂魔镐: 效率V 耐久III
        ItemStack pickaxe = new ItemStack(BSCoins.INFRA_PICKAXE.get());
        var enchantRegistry = event.getRegistryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        pickaxe.enchant(enchantRegistry.getOrThrow(Enchantments.EFFICIENCY), 5);
        pickaxe.enchant(enchantRegistry.getOrThrow(Enchantments.UNBREAKING), 3);

        event.getGenericTrades().add(new BasicItemListing(Config.SHOP_PRICE_MEGAPHONE.get(),
                new ItemStack(BSCoins.NAG_MEGAPHONE.get()), 3, 10, 0.05F));
        event.getGenericTrades().add(new BasicItemListing(Config.SHOP_PRICE_PIGEON_TRAP.get(),
                new ItemStack(BSCoins.PIGEON_TRAP.get()), 4, 10, 0.05F));
        event.getGenericTrades().add(new BasicItemListing(Config.SHOP_PRICE_PICKAXE.get(),
                pickaxe, 1, 20, 0.1F));
        event.getGenericTrades().add(new BasicItemListing(Config.SHOP_PRICE_PHOTO.get(),
                new ItemStack(BSCoins.BEISHAN_PHOTO.get()), 5, 5, 0.0F));
        event.getGenericTrades().add(new BasicItemListing(Config.SHOP_PRICE_NBT_VIEWER.get(),
                new ItemStack(BSCoins.NBT_VIEWER.get()), 2, 15, 0.05F));
        event.getRareTrades().add(new BasicItemListing(Config.SHOP_PRICE_AIRDROP.get(),
                new ItemStack(BSCoins.STRUCTURE_AIRDROP.get()), 2, 25, 0.1F));

        BSCoins.LOGGER.debug("北山小卖部: 已替换流浪商人交易");
    }
}
