package com.beishan.bscoins.handler;

import java.util.List;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;
import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 催更律师函惩罚。
 * 连续 PRESSURE_DAYS 个MC天未使用/合成任意币 -> 背包出现《北山催更函》、
 * 头顶飘鸽子粒子并减速5%。使用或合成任意币即可解除。
 */
public class PressureHandler {
    public static final String TAG_PENALIZED = "bscoins_penalized";
    private static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath(BSCoins.MODID, "pigeon_slow");

    private int tickCounter = 0;

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ItemStack crafted = event.getCrafting();
            if (crafted.is(BSCoins.LIKE.get()) || crafted.is(BSCoins.COIN_TOSS.get())
                    || crafted.is(BSCoins.FAVORITE.get()) || crafted.is(BSCoins.BS_COIN.get())
                    || crafted.is(BSCoins.DEV_BS_COIN.get())) {
                // 合成任意币即可解除惩罚
                CoinUsageTracker.markCoinUsed(serverPlayer.serverLevel(), serverPlayer);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < 20) return;
        tickCounter = 0;

        var server = event.getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (!Config.ENABLE_PRESSURE.get()) {
                clearPenalty(player.serverLevel(), player);
                continue;
            }
            long day = server.overworld().getDayTime() / 24000L;
            long last = CoinUsageTracker.lastUsedDay(player);
            if (!CoinUsageTracker.isPenalized(player)) {
                if (last == 0L) {
                    // 玩家第一次标记
                    player.getPersistentData().putLong(CoinUsageTracker.TAG_LAST_DAY, day);
                    continue;
                }
                if (day - last >= Config.PRESSURE_DAYS.get()) {
                    applyPenalty(player.serverLevel(), player);
                }
            } else {
                // 持续惩罚: 头顶鸽子粒子 + 确保催更函与减速在场
                if (!hasPressureLetter(player)) {
                    player.getInventory().add(new ItemStack(BSCoins.PRESSURE_LETTER.get()));
                }
                spawnPigeonParticles(player.serverLevel(), player);
                ensureSlow(player);
            }
        }
    }

    private boolean hasPressureLetter(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(BSCoins.PRESSURE_LETTER.get())) return true;
        }
        return false;
    }

    private void applyPenalty(ServerLevel level, ServerPlayer player) {
        player.getPersistentData().putBoolean(TAG_PENALIZED, true);
        // 背包出现《北山催更函》书
        if (!hasPressureLetter(player)) {
            player.getInventory().add(new ItemStack(BSCoins.PRESSURE_LETTER.get()));
        }
        ensureSlow(player);
        player.sendSystemMessage(net.minecraft.network.chat.Component
                .translatable("message.bscoins.pressure_start"));
        BSCoins.LOGGER.info("北山币: 玩家 {} 触发催更律师函惩罚", player.getName().getString());
    }

    public static void clearPenalty(ServerLevel level, ServerPlayer player) {
        if (!CoinUsageTracker.isPenalized(player)) return;
        player.getPersistentData().putBoolean(TAG_PENALIZED, false);
        // 移除背包中的《北山催更函》
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(BSCoins.PRESSURE_LETTER.get())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                break;
            }
        }
        // 移除减速
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SLOW_ID) != null) {
            speed.removeModifier(SLOW_ID);
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component
                .translatable("message.bscoins.pressure_cleared"));
    }

    private void ensureSlow(ServerPlayer player) {
        double percent = Config.PRESSURE_SLOW_PERCENT.get();
        if (percent <= 0) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier modifier = speed.getModifier(SLOW_ID);
        double amount = -percent / 100.0;
        if (modifier != null && modifier.amount() == amount) return;
        if (modifier != null) speed.removeModifier(SLOW_ID);
        speed.addTransientModifier(new AttributeModifier(SLOW_ID, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void spawnPigeonParticles(ServerLevel level, ServerPlayer player) {
        int tint = Config.PRESSURE_PIGEON_TICKS.get();
        if (level.getGameTime() % tint != 0) return;
        level.sendParticles(new DustParticleOptions(new Vector3f(0.85F, 0.85F, 0.9F), 1.0F),
                player.getX(), player.getY() + 2.2, player.getZ(), 6, 0.5, 0.2, 0.5, 0.02);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.4F, 0.4F, 0.45F), 1.0F),
                player.getX(), player.getY() + 2.4, player.getZ(), 4, 0.4, 0.2, 0.4, 0.02);
    }
}
