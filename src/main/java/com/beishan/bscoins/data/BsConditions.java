package com.beishan.bscoins.data;

import com.beishan.bscoins.BSCoins;
import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 条件序列化器注册。
 * 注册进去以后, 配方/数据包 JSON 才能写 {@code "type": "bscoins:config"}。
 */
public final class BsConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, BSCoins.MODID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigCondition>> CONFIG =
            CONDITION_CODECS.register("config", () -> ConfigCondition.CODEC);

    private BsConditions() {
    }

    public static void register(IEventBus modEventBus) {
        CONDITION_CODECS.register(modEventBus);
    }
}
