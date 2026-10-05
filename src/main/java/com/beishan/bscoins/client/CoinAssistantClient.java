package com.beishan.bscoins.client;

import com.beishan.bscoins.network.ModNetwork;
import com.beishan.bscoins.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

/**
 * 北山币「潜行右键空气 → 开发助手」的<b>客户端</b>判定逻辑。
 *
 * <p>为什么单独放一个类: 之前这段代码写在通用物品类 {@code BSCoinItem#use} 里, 并且直接
 * {@code Minecraft.getInstance().hitResult} —— 这会让通用类引用客户端类。在专用服务器上,
 * {@code BSCoinItem} 会随物品注册被加载, 校验/解析到这个字段时可能直接
 * {@code NoClassDefFoundError: net/minecraft/client/Minecraft} 崩服。
 *
 * <p>现在通用类只在 {@code level.isClientSide} 分支里调用 {@link #tryOpenAssistant()},
 * 而且这个方法的<b>参数与返回值都是基本类型</b> —— 描述符是 {@code ()V},
 * 类校验不需要解析到本类, 于是专用服务器永远不会加载客户端类。
 */
public final class CoinAssistantClient {
    /** 同一 tick 内连续右键只发一次请求 */
    private static final long DEBOUNCE_MS = 200L;
    private static long lastRequest = 0L;

    private CoinAssistantClient() {
    }

    /** 看向空气时才向服务器请求打开开发助手 (消耗 1 枚币由服务端处理)。 */
    public static void tryOpenAssistant() {
        long now = System.currentTimeMillis();
        HitResult hit = Minecraft.getInstance().hitResult;
        boolean air = hit == null || hit.getType() == HitResult.Type.MISS;
        if (air && now - lastRequest > DEBOUNCE_MS) {
            lastRequest = now;
            ModNetwork.sendToServer(new ModPayloads.OpenAssistantPayload());
        }
    }
}
