package com.beishan.bscoins.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 鸡血 - 打赏给玩家的攻击增强 Buff
 */
public class GoadEffect extends MobEffect {
    private static final ResourceLocation ATTACK_ID = ResourceLocation.withDefaultNamespace("bscoins_goad_attack");

    public GoadEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE53935);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, ATTACK_ID, 2.0,
                AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.ATTACK_SPEED, ATTACK_ID, 0.2,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
