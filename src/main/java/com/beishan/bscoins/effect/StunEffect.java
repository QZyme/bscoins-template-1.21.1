package com.beishan.bscoins.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * 眩晕 - 目标无法移动
 */
public class StunEffect extends MobEffect {
    public StunEffect() {
        super(MobEffectCategory.HARMFUL, 0x90A4AE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity living, int amplifier) {
        // 每 tick 冻结移动; AI 导航也被停住
        living.setDeltaMovement(living.getDeltaMovement().x() * 0.05, living.getDeltaMovement().y() * 0.1, living.getDeltaMovement().z() * 0.05);
        if (living instanceof Mob mob && mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        return true;
    }
}