package com.beishan.bscoins.client;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;
import com.beishan.bscoins.network.ModNetwork;
import com.beishan.bscoins.network.ModPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import org.joml.Vector3f;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端交互事件: Q/V 键弹射投币, 潜行Q测试功能, G 键开发助手, 长按右键3秒团建圈, 手持三色粒子。
 */
public class ClientEvents {
    private static final String CATEGORY = "key.categories.bscoins";

    /** 备用投币键: Q 依然能投币, V 给一个和"丢弃物品"不冲突的键位。 */
    public static final KeyMapping THROW_KEY = new KeyMapping("key.bscoins.throw",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    /** 开发助手快捷键: 等价于"手持北山币潜行右键空气"。 */
    public static final KeyMapping ASSISTANT_KEY = new KeyMapping("key.bscoins.assistant",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    /**
     * "一次物理按下只触发一次"的锁存器。
     *
     * <p>为什么需要它: 原版 {@code KeyboardHandler#keyPress} 对<b>按住不放</b>产生的
     * GLFW_REPEAT (action == 2) 也会调 {@code KeyMapping.click()}, 也就是按住一个键会不停往
     * click 队列里塞点击。而这里每个客户端 tick 都会 {@code consumeClick()},
     * 于是"按住 Q"= 每 tick 给服务器发一次潜行Q(服务器只有 2 tick 冷却) ——
     * 表现就是测试平台一层一层往上叠、测试假人一串一串往外冒。
     *
     * <p>现在: 这一 tick 有点击且还没锁 → 触发并锁住; 只有在"手松开了且没有新点击"时才解锁。
     */
    private static final class PressLatch {
        private boolean latched;

        boolean fired(KeyMapping key) {
            boolean clicked = false;
            while (key.consumeClick()) {
                clicked = true;
            }
            boolean fire = clicked && !this.latched;
            if (fire) {
                this.latched = true;
            }
            if (!key.isDown() && !clicked) {
                this.latched = false;
            }
            return fire;
        }

        void reset() {
            this.latched = false;
        }
    }

    private final PressLatch dropLatch = new PressLatch();
    private final PressLatch throwLatch = new PressLatch();
    private final PressLatch assistantLatch = new PressLatch();

    private long holdStartTick = -1;
    private boolean circleTriggered = false;
    private int particleTick = 0;
    private int assistantCooldown = 0;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(THROW_KEY);
        event.register(ASSISTANT_KEY);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.level == null) return;

        Player player = mc.player;
        ItemStack main = player.getMainHandItem();
        // 副手拿着币也该能扔 (服务端 heldCoin 就认副手, 客户端以前只认主手 -> 副手扔不出去)
        ItemStack coinStack = isCoin(main) ? main
                : (isCoin(player.getOffhandItem()) ? player.getOffhandItem() : ItemStack.EMPTY);
        boolean coin = !coinStack.isEmpty();
        // 冷却期间不再发包: 服务端也会拒, 但客户端挡掉更干脆 (还能看见物品栏那圈冷却动画)
        boolean cooling = coin && player.getCooldowns().isOnCooldown(coinStack.getItem());
        if (assistantCooldown > 0) assistantCooldown--;

        // ── 手持三色粒子 ─────────────────────────────
        if (coin && Config.ENABLE_HELD_PARTICLES.get()) {
            spawnHeldParticles(mc, player);
        }

        // 按键队列每 tick 都要读一遍(锁存器内部会消费), 免得切到币的瞬间补触发
        boolean throwPressed = throwLatch.fired(THROW_KEY);
        boolean assistantPressed = assistantLatch.fired(ASSISTANT_KEY);

        // ── Q 键弹射投币 / 潜行Q ─────────────────────
        // 在 Pre 阶段消费 drop 键 (Q 默认键), 阻止原版丢弃, 改为投掷/测试功能。
        // 手上没币时不去消费它, 原版"丢东西"照旧。
        if (coin) {
            if (dropLatch.fired(mc.options.keyDrop)) {
                if (player.isShiftKeyDown()) {
                    // 传 coinStack 而不是 main: 副手拿开发版币时也要走 /reload 分支
                    handleSneakQ(player, coinStack);
                } else if (!cooling) {
                    ModNetwork.sendToServer(new ModPayloads.ThrowCoinPayload());
                }
            }
        } else {
            dropLatch.reset();
        }

        // ── V 键投币 / G 键开发助手 ───────────────────
        if (coin && throwPressed && !cooling) {
            ModNetwork.sendToServer(new ModPayloads.ThrowCoinPayload());
        }
        if (coin && assistantPressed && Config.ENABLE_DEV_ASSISTANT.get() && assistantCooldown == 0) {
            assistantCooldown = 10;
            ModNetwork.sendToServer(new ModPayloads.OpenAssistantPayload());
        }

        // ── 长按右键3秒生成团建圈 ─────────────────────
        handleHold(mc, player, coin);
    }

    private boolean isCoin(ItemStack stack) {
        return stack.is(BSCoins.BS_COIN.get()) || stack.is(BSCoins.DEV_BS_COIN.get());
    }

    private void handleSneakQ(Player player, ItemStack main) {
        boolean dev = main.is(BSCoins.DEV_BS_COIN.get());
        if (dev && Config.ENABLE_RELOAD.get()) {
            ModNetwork.sendToServer(new ModPayloads.SneakQPayload(
                    ModPayloads.SneakQPayload.ACTION_RELOAD, 0, 0, 0));
            return;
        }
        // 一键搭测试场地: 脚下一层平台 + 面前一个假人, 合计只花 1 枚币。
        // 位置全部由服务端按玩家自己算, 所以客户端不再需要按"看向方块还是空气"分支。
        ModNetwork.sendToServer(new ModPayloads.SneakQPayload(
                ModPayloads.SneakQPayload.ACTION_SETUP, 0, 0, 0));
    }

    private void handleHold(Minecraft mc, Player player, boolean coin) {
        boolean holdingUse = mc.options.keyUse.isDown();
        boolean sneak = player.isShiftKeyDown();
        if (coin && !sneak && holdingUse) {
            if (holdStartTick < 0) {
                holdStartTick = 0;
                circleTriggered = false;
            } else {
                holdStartTick++;
            }
            int holdMs = Config.CIRCLE_HOLD_MS.get();
            if (!circleTriggered && holdStartTick >= Math.max(1, holdMs / 50)) {
                circleTriggered = true;
                ModNetwork.sendToServer(new ModPayloads.CreateCirclePayload());
            }
        } else {
            holdStartTick = -1;
            circleTriggered = false;
        }
    }

    /** 粒子间隔 (tick): 5 = 每秒 4 次 (以前是每 2 tick 一次, 太密) */
    private static final int PARTICLE_INTERVAL_TICKS = 5;
    /** 环绕半径: 稍微放大, 让粒子离准星更远 */
    private static final double PARTICLE_RADIUS = 0.95;

    /** 手持北山币时在<b>脚边</b>画一圈三色粒子 (数量/大小/高度都收敛过, 尽量不挡第一人称视野)。 */
    private void spawnHeldParticles(Minecraft mc, Player player) {
        if (++particleTick % PARTICLE_INTERVAL_TICKS != 0) return;
        double x = player.getX();
        // 以前在 y+1.8 (正好是准星高度), 现在压到脚边: 平视/抬头都看不见它, 低头才看得到
        double y = player.getY() + 0.25;
        double z = player.getZ();
        double t = System.currentTimeMillis() / 1000.0;
        int count = Math.max(1, Config.PARTICLE_COUNT.get());
        for (int i = 0; i < count; i++) {
            double angle = t * 1.4 + (double) i * (Math.PI * 2.0 / count);
            double px = x + Math.cos(angle) * PARTICLE_RADIUS;
            double pz = z + Math.sin(angle) * PARTICLE_RADIUS;
            Vector3f color = switch (i % 3) {
                case 0 -> new Vector3f(1.0F, 0.31F, 0.27F); // 红
                case 1 -> new Vector3f(0.29F, 0.62F, 1.0F); // 蓝
                default -> new Vector3f(1.0F, 0.84F, 0.0F); // 金
            };
            mc.level.addParticle(new DustParticleOptions(color, 0.5F),
                    px, y + Math.sin(t * 1.4 + i) * 0.08, pz, 0.0, 0.0, 0.0);
        }
    }
}
