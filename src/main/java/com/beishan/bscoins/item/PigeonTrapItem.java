package com.beishan.bscoins.item;

import java.util.List;

import com.beishan.bscoins.Config;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 鸽子诱捕器 - 使用后从笼子里放出一群"鸽子"。
 *
 * <p>以前只有 20 个末地烛粒子 + 一条聊天消息, 看着像什么都没发生。
 * 现在是真的放鸟: 一次放出 {@code pigeonTrapCount}(默认5)只鹦鹉实体, 呈伞状飞散,
 * 每只都叫一声, 加上笼子炸开的烟尘粒子, 效果一眼可见。
 * 放出来的鸟是普通怪 (不设置 persist), 走远以后会像其它怪一样自然消失, 不会越攒越多。
 */
public class PigeonTrapItem extends Item {
    public PigeonTrapItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            int count = releasePigeons(serverLevel, player);
            player.sendSystemMessage(Component.translatable("message.bscoins.pigeon_trap", count)
                    .withStyle(ChatFormatting.WHITE));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    /** @return 实际放出来的鸽子数量 */
    private static int releasePigeons(ServerLevel level, Player player) {
        int count = Math.max(1, Config.PIGEON_TRAP_COUNT.get());
        Vec3 look = player.getLookAngle();
        Vec3 center = player.position().add(look.scale(1.2)).add(0.0, 1.0, 0.0);
        Parrot.Variant[] variants = Parrot.Variant.values();

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Parrot pigeon = EntityType.PARROT.create(level);
            if (pigeon == null) continue;

            // 均匀散开成伞形, 再给一个向外+向上的初速度, 看起来就是"扑棱扑棱飞走"
            double angle = Math.PI * 2.0 * i / count + level.random.nextDouble() * 0.4;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            pigeon.moveTo(center.x + dx * 0.8, center.y, center.z + dz * 0.8,
                    (float) (angle * 180.0 / Math.PI), 0.0F);
            pigeon.setVariant(variants[level.random.nextInt(variants.length)]);
            pigeon.setDeltaMovement(dx * 0.3, 0.35, dz * 0.3);
            level.addFreshEntity(pigeon);
            level.playSound(null, pigeon.blockPosition(), SoundEvents.PARROT_AMBIENT,
                    SoundSource.NEUTRAL, 1.0F, 0.85F + level.random.nextFloat() * 0.3F);
            spawned++;
        }

        // 笼子炸开: 白烟 + 云
        level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z, 30, 0.5, 0.5, 0.5, 0.03);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.2, center.z, 12, 0.4, 0.3, 0.4, 0.02);
        return spawned;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.pigeon_trap").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.bscoins.pigeon_trap_2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
