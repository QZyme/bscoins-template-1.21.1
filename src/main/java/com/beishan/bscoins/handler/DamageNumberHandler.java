package com.beishan.bscoins.handler;

import java.util.Locale;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 伤害数字 - 假人被打时在头顶飘一个数字, 一眼看出这一下打了多少。
 *
 * <p>实现用原版 1.21 就有的 {@link Display.TextDisplay} 实体 (不需要客户端渲染器, 所有客户端都看得见):
 * <ul>
 *   <li>文字/朝向/透明底靠 NBT 设置 —— TextDisplay 的 setter 全是 private, 只能走 {@link net.minecraft.world.entity.Entity#load};</li>
 *   <li><b>寿命写在 {@code getPersistentData()} 里, 不能混进 load 用的那个 NBT</b>:
 *       {@code Entity#load} 只认它自己认识的键 (自定义数据得放 "NeoForgeData" 子标签), 顶层随手塞的键会被丢掉,
 *       结果就是飘字永远不消失;</li>
 *   <li>寿命由 {@link #onEntityTickPre} 每 tick 推进: 一边往上飘一边倒计时, 到点 {@code discard()};</li>
 *   <li>服务端驱动位置, 客户端只管画 (EntityTickEvent 在两端都会触发, 所以这里显式跳过客户端)。</li>
 * </ul>
 */
public class DamageNumberHandler {
    /** 持久数据里的剩余寿命 (tick) */
    private static final String TAG_LIFE = "bscoins_damage_number";
    private static final int LIFETIME_TICKS = 22;
    private static final double RISE_PER_TICK = 0.035;
    /** 大伤害的金色门槛 */
    private static final float BIG_HIT = 20.0F;
    /** 连续命中时最多往上叠几层 */
    private static final int MAX_STACK = 4;
    /** 每层往上的高度 / 往外转的角度 */
    private static final double STACK_HEIGHT = 0.28;
    private static final double STACK_ANGLE = 2.4;
    private static final double STACK_RADIUS = 0.34;

    /**
     * 在目标头顶飘一个伤害数字。
     *
     * @param killed 这一下把人打死了 (标红加粗)
     * @param stack  连续命中的排队序号 (0 = 第一个); 大一点就往更高/更外的地方生成, 避免数字互相压在一起
     */
    public static void spawn(ServerLevel level, LivingEntity target, float damage, boolean killed, int stack) {
        Display.TextDisplay number = EntityType.TEXT_DISPLAY.create(level);
        if (number == null) return;

        int step = Math.max(0, Math.min(MAX_STACK, stack));

        CompoundTag tag = new CompoundTag();
        // text 必须是 JSON 文本组件字符串; 内容只有数字和颜色名, 不需要转义
        tag.putString("text", "{\"text\":\"" + format(damage) + "\",\"color\":\""
                + (killed ? "red" : (damage >= BIG_HIT ? "gold" : "yellow")) + "\""
                + (killed ? ",\"bold\":true" : "") + "}");
        tag.putString("billboard", "center");  // 永远正对玩家
        tag.putInt("background", 0);           // 透明底, 只要数字本身
        tag.putBoolean("shadow", true);        // 带阴影, 亮背景下也看得清
        tag.putBoolean("see_through", false);
        number.load(tag);
        // 寿命: 必须写在 load() 之后再塞进持久数据 (见类注释)
        number.getPersistentData().putInt(TAG_LIFE, LIFETIME_TICKS);

        // 连打时螺旋往上错开, 单个时就在头顶随机偏一点点
        double angle = step * STACK_ANGLE;
        double radius = step == 0 ? (level.random.nextDouble() - 0.5) * 0.5 : STACK_RADIUS;
        number.setPos(target.getX() + Math.cos(angle) * radius,
                target.getY() + 2.2 + step * STACK_HEIGHT,
                target.getZ() + Math.sin(angle) * radius);
        level.addFreshEntity(number);
    }

    /** 整数就不显示小数点, 否则保留一位。 */
    private static String format(float damage) {
        float rounded = Math.round(damage);
        return Math.abs(damage - rounded) < 0.05F
                ? Integer.toString((int) rounded)
                : String.format(Locale.ROOT, "%.1f", damage);
    }

    @SubscribeEvent
    public void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Display.TextDisplay number)) return;
        Level level = number.level();
        if (level.isClientSide) return;

        CompoundTag data = number.getPersistentData();
        if (!data.contains(TAG_LIFE)) return;

        int life = data.getInt(TAG_LIFE) - 1;
        if (life <= 0) {
            number.discard();
            return;
        }
        data.putInt(TAG_LIFE, life);
        number.setPos(number.getX(), number.getY() + RISE_PER_TICK, number.getZ());
    }
}
