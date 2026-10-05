package com.beishan.bscoins.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 老板 - 打赏给玩家的防御型 Buff
 */
public class BossEffect extends MobEffect {
    private static final ResourceLocation HEALTH_ID = ResourceLocation.withDefaultNamespace("bscoins_boss_health");
    private static final ResourceLocation ARMOR_ID = ResourceLocation.withDefaultNamespace("bscoins_boss_armor");

    public BossEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFBC02D);
        addAttributeModifier(Attributes.MAX_HEALTH, HEALTH_ID, 6.0,
                AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.ARMOR, ARMOR_ID, 4.0,
                AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
