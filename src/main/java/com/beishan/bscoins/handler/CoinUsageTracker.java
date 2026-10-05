package com.beishan.bscoins.handler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 北山币使用追踪: 记录玩家最后一次使用/合成任意币的时间（MC 天）。
 */
public final class CoinUsageTracker {
    public static final String TAG_LAST_DAY = "bscoins_last_coin_day";
    public static final String TAG_PENALIZED = "bscoins_penalized";

    private CoinUsageTracker() {
    }

    public static void markCoinUsed(ServerLevel level, ServerPlayer player) {
        if (player == null || level == null) return;
        long day = level.getDayTime() / 24000L;
        player.getPersistentData().putLong(TAG_LAST_DAY, day);
        // 使用任意币即可解除惩罚
        PressureHandler.clearPenalty(level, player);
    }

    public static long lastUsedDay(ServerPlayer player) {
        return player.getPersistentData().getLong(TAG_LAST_DAY);
    }

    public static boolean isPenalized(ServerPlayer player) {
        return player.getPersistentData().getBoolean(TAG_PENALIZED);
    }
}
