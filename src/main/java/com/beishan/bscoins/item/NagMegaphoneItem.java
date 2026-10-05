package com.beishan.bscoins.item;

import java.util.List;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 催更大喇叭 - 朝视线方向喊出一个<b>扇形</b>区域。
 *
 * <p>作用范围 = 以自己为顶点、正前方为轴、张开 {@code megaphoneAngleDegrees}(默认90°) 、
 * 长度 {@code megaphoneRadius}(默认32格) 的扇形 (只按水平方向判角度, 所以站在高台/坑里也算)。
 *
 * <p>能看到的效果:
 * <ol>
 *   <li>号角音效 ({@code RAID_HORN}, 4倍音量);</li>
 *   <li>扇形里从近到远画出一圈圈音符弧线 + 末地烛, 一眼就能看出"这是一个扇形区域";</li>
 *   <li>扇形覆盖到的玩家头顶冒音符, 收到"被催更"的消息, 并获得鸡血 Buff。</li>
 * </ol>
 * 带冷却 ({@code megaphoneCooldownSeconds}), 防止刷屏。
 */
public class NagMegaphoneItem extends Item {
    /** 音浪画几条弧线 */
    private static final int FAN_RINGS = 6;

    public NagMegaphoneItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);

        if (level instanceof ServerLevel serverLevel) {
            shout(serverLevel, player);
        }
        player.getCooldowns().addCooldown(this, Config.MEGAPHONE_COOLDOWN_SECONDS.get() * 20);
        return InteractionResultHolder.success(stack);
    }

    /** 号角 + 扇形音浪 + 扇形范围内的群体催更消息 + 鸡血 Buff。 */
    private static void shout(ServerLevel level, Player user) {
        double radius = Config.MEGAPHONE_RADIUS.get();
        int seconds = Config.MEGAPHONE_BUFF_SECONDS.get();
        double halfAngle = Math.toRadians(Config.MEGAPHONE_ANGLE_DEGREES.get() / 2.0);
        double cosLimit = Math.cos(halfAngle);

        // 水平朝向: 只看视线在水平面上的投影, 这样抬头/低头喊也还是一个水平扇形
        Vec3 look = user.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);

        // 1) 号角
        level.playSound(null, user.blockPosition(), SoundEvents.RAID_HORN.value(),
                SoundSource.PLAYERS, 4.0F, 0.9F);

        // 2) 扇形音浪: 由近到远画出 FAN_RINGS 条弧线, 弧线越长越宽 = 扇形
        Vec3 eye = user.getEyePosition();
        for (int ring = 1; ring <= FAN_RINGS; ring++) {
            double r = radius * ring / FAN_RINGS;
            int dots = 5 + ring * 2;
            for (int i = 0; i < dots; i++) {
                double a = dots <= 1 ? 0.0 : -halfAngle + (2.0 * halfAngle) * i / (dots - 1);
                Vec3 dir = forward.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
                Vec3 p = eye.add(dir.scale(r));
                level.sendParticles(ParticleTypes.NOTE, p.x, p.y, p.z, 1, 0.15, 0.15, 0.15, 0.0);
                if ((i + ring) % 4 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
        }

        // 3) 扇形范围内的玩家: 头顶音符 + 被催更消息 + 鸡血 Buff
        int affected = 0;
        for (Player target : level.getEntitiesOfClass(Player.class, user.getBoundingBox().inflate(radius))) {
            if (target.distanceToSqr(user) > radius * radius) continue;
            if (target != user) {
                Vec3 to = new Vec3(target.getX() - user.getX(), 0.0, target.getZ() - user.getZ());
                if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(forward) < cosLimit) {
                    continue; // 在半径内但不在扇形里
                }
            }
            target.addEffect(new MobEffectInstance(BSCoins.GOAD, seconds * 20, 0, false, true));
            level.sendParticles(ParticleTypes.NOTE,
                    target.getX(), target.getY() + 2.4, target.getZ(), 4, 0.35, 0.25, 0.35, 0.0);
            if (target != user) {
                target.sendSystemMessage(Component.translatable("message.bscoins.megaphone_shout",
                        user.getDisplayName(), seconds).withStyle(ChatFormatting.YELLOW));
            }
            affected++;
        }

        user.sendSystemMessage(Component.translatable("message.bscoins.megaphone", affected)
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.bscoins.nag_megaphone").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.bscoins.nag_megaphone_2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
