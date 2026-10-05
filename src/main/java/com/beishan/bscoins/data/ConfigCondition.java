package com.beishan.bscoins.data;

import com.beishan.bscoins.Config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * 配方条件 {@code bscoins:config} —— 让 Config 里的"合成部件开关"真正生效。
 *
 * <p>用法 (写在配方 JSON 里):
 * <pre>
 * "neoforge:conditions": [ { "type": "bscoins:config", "value": "enableLike" } ]
 * </pre>
 * 关掉开关后, 对应配方在数据包加载时就不再注册 —— 也就是"这个部件被禁用了"。
 * 条件只在加载时求值, 所以改完配置要 /reload 或重启游戏才生效。
 */
public record ConfigCondition(String value) implements ICondition {
    public static final MapCodec<ConfigCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("value").forGetter(ConfigCondition::value)
    ).apply(instance, ConfigCondition::new));

    @Override
    public boolean test(IContext context) {
        return Config.recipeSwitch(this.value);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "bscoins:config[" + this.value + "]";
    }
}
