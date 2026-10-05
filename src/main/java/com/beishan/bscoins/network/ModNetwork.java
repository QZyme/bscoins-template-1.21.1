package com.beishan.bscoins.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;
import com.beishan.bscoins.entity.TeamCircleEntity;
import com.beishan.bscoins.entity.TestDummyEntity;
import com.beishan.bscoins.entity.ThrownCoinEntity;
import com.beishan.bscoins.handler.CoinUsageTracker;
import com.beishan.bscoins.item.BSCoinItem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.resource.ResourcePackLoader;

/**
 * 北山币 - 服务端网络分包处理。
 * 注意: PayloadRegistrar 默认把处理逻辑放到主线程执行, 因此可直接访问世界/玩家。
 */
public final class ModNetwork {
    public static final String CHANNEL_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::registerPayloads);
    }

    /** 客户端 → 服务端发送载荷 (仅客户端调用)。 */
    public static void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CHANNEL_VERSION);

        // Q键弹射投币 (冷却: 用原版物品冷却, 顺带在物品栏上画出那圈白色冷却动画)
        registrar.playToServer(ModPayloads.ThrowCoinPayload.TYPE, ModPayloads.ThrowCoinPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    if (player == null || !Config.ENABLE_THROW.get()) return;

                    // 冷却先判: 否则按太快会"扣了币却没扔出去"
                    int cooldown = Config.THROW_COOLDOWN_TICKS.get();
                    if (cooldown > 0) {
                        ItemStack held = heldCoin(player);
                        if (!held.isEmpty() && player.getCooldowns().isOnCooldown(held.getItem())) return;
                    }

                    // 先确定扔的是哪种币, 再把消耗量扣掉
                    ItemStack coinStack = takeCoinStack(player, Config.THROW_CONSUME.get());
                    if (coinStack == null) return;
                    if (cooldown > 0) {
                        player.getCooldowns().addCooldown(coinStack.getItem(), cooldown);
                    }
                    boolean special = Config.ENABLE_DEV_COIN.get() && coinStack.is(BSCoins.DEV_BS_COIN.get());

                    ServerLevel level = player.serverLevel();
                    ThrownCoinEntity coin = new ThrownCoinEntity(BSCoins.THROWN_COIN_TYPE.get(), player, level,
                            coinStack, special);
                    // 从眼睛稍前一点出手: 完全放在玩家正中心时, 贴脸对着墙会一出手就插在脚边
                    Vec3 look = player.getLookAngle();
                    coin.setPos(player.getX() + look.x * 0.3, player.getEyeY() - 0.15, player.getZ() + look.z * 0.3);
                    // 初速很高, 散布给小一点, 后面靠空气阻力自己减速 (见 ThrownCoinEntity#tick)
                    coin.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                            (float) (double) Config.THROW_SPEED.get(), 0.05F);
                    level.addFreshEntity(coin);

                    // 出手音效: 没有它的时候投掷是"哑的", 听不出有没有扔出去
                    level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                            SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.55F, 1.35F);
                    CoinUsageTracker.markCoinUsed(level, player);
                });

        // 潜行Q: 测试平台 / 测试假人 / reload (带冷却)
        registrar.playToServer(ModPayloads.SneakQPayload.TYPE, ModPayloads.SneakQPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    if (player == null) return;
                    ServerLevel level = player.serverLevel();
                    long now = level.getGameTime();
                    long last = player.getPersistentData().getLong("bscoins_sneakq_time");
                    if (now - last < 2) return;
                    player.getPersistentData().putLong("bscoins_sneakq_time", now);

                    switch (payload.action()) {
                        case ModPayloads.SneakQPayload.ACTION_SETUP -> {
                            // 一键搭测试场地: 1 枚币同时搞定"脚下一层平台" + "面前一个假人"
                            boolean platform = Config.ENABLE_TEST_PLATFORM.get();
                            boolean dummy = Config.ENABLE_TEST_DUMMY.get();
                            if (!platform && !dummy) return;
                            if (takeCoinStack(player, 1) == null) return;
                            if (platform) {
                                buildFloorUnder(level, player);
                            }
                            if (dummy) {
                                spawnTestDummy(level, player);
                            }
                            CoinUsageTracker.markCoinUsed(level, player);
                        }
                        case ModPayloads.SneakQPayload.ACTION_RELOAD -> {
                            if (!Config.ENABLE_RELOAD.get()) return;
                            // 重载是个重操作, 单独给 1 秒冷却, 防止拿着开发版币连点
                            long lastReload = player.getPersistentData().getLong("bscoins_reload_time");
                            if (now - lastReload < 20) return;
                            // 只认"开发版币所在的那只手": 客户端就是按这枚币决定发这个包的。
                            // (原来走 takeCoinStack, 它是主手优先 —— 主手普通币 + 副手开发版币时
                            //  会去扣那枚普通币, 和玩家看到的不是同一枚)
                            if (heldDevCoin(player).isEmpty()) return;
                            player.getPersistentData().putLong("bscoins_reload_time", now);

                            MinecraftServer server = player.getServer();
                            if (server == null) return;

                            // 和原版 /reload 走同一条重载路径, 但绕开命令解析。
                            // 原因: /reload 的注册带 hasPermission(2), 没开作弊的单人存档 / 没 op 的
                            // 服务器上, 命令会在解析阶段被拒("未知或不完整的命令"), 而那样一来
                            // 既拿不到失败信号、又会白扣一枚币。这里直接看 future 的结果。
                            server.reloadResources(discoverDataPacks(server)).whenComplete((unused, error) -> {
                                if (error != null) {
                                    BSCoins.LOGGER.warn("北山币: 开发版币触发的数据包重载失败", error);
                                    player.sendSystemMessage(
                                            Component.translatable("message.bscoins.reload_failed"));
                                    return;   // 失败: 不扣币, 也不报"重载完成"
                                }
                                // 重新取一次手里的币, 避免重载期间玩家把币丢了/换手了还照扣
                                ItemStack coin = heldDevCoin(player);
                                if (!coin.isEmpty() && !player.getAbilities().instabuild) {
                                    coin.shrink(1);
                                }
                                CoinUsageTracker.markCoinUsed(player.serverLevel(), player);
                                player.sendSystemMessage(Component.translatable("message.bscoins.reload_done"));
                            });
                        }
                        default -> {
                        }
                    }
                });

        // 潜行右键空气 / 按G -> 消耗1币, 打开开发助手 (带冷却防刷)
        registrar.playToServer(ModPayloads.OpenAssistantPayload.TYPE, ModPayloads.OpenAssistantPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    if (player == null || !Config.ENABLE_DEV_ASSISTANT.get() || !Config.ENABLE_BS_COIN.get()) return;
                    long now = System.currentTimeMillis();
                    long last = player.getPersistentData().getLong("bscoins_assistant_time");
                    if (now - last < 200) return;
                    player.getPersistentData().putLong("bscoins_assistant_time", now);
                    if (takeCoinStack(player, Config.ASSISTANT_CONSUME.get()) == null) return;
                    CoinUsageTracker.markCoinUsed(player.serverLevel(), player);
                    PacketDistributor.sendToPlayer(player, new ModPayloads.OpenAssistantResponsePayload());
                });

        // 长按右键3秒 -> 北山团建圈 (1秒冷却防重复)
        registrar.playToServer(ModPayloads.CreateCirclePayload.TYPE, ModPayloads.CreateCirclePayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    if (player == null || !Config.ENABLE_TEAM_CIRCLE.get()) return;
                    ServerLevel level = player.serverLevel();
                    long now = level.getGameTime();
                    long last = player.getPersistentData().getLong("bscoins_circle_time");
                    if (now - last < 20) return;
                    player.getPersistentData().putLong("bscoins_circle_time", now);
                    if (takeCoinStack(player, Config.CIRCLE_CONSUME.get()) == null) return;
                    TeamCircleEntity circle = new TeamCircleEntity(BSCoins.TEAM_CIRCLE_TYPE.get(), level);
                    circle.setPos(player.getX(), player.getY() + 0.5, player.getZ());
                    level.addFreshEntity(circle);
                    CoinUsageTracker.markCoinUsed(level, player);
                    player.sendSystemMessage(Component.translatable("message.bscoins.circle_spawned"));
                });
    }

    /**
     * 在玩家<b>脚下那一层</b>铺一层测试平台: 以玩家所在格为中心, 边长 {@code platformSize},
     * 直接覆盖原有方块 (就是"覆盖脚下一层")。
     */
    private static void buildFloorUnder(ServerLevel level, ServerPlayer player) {
        int size = Config.PLATFORM_SIZE.get();
        int half = Math.max(1, size / 2);
        BlockState floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockPos feet = player.blockPosition();
        int y = feet.getY() - 1;   // 玩家脚下方块那一层
        for (int dx = -half; dx < size - half; dx++) {
            for (int dz = -half; dz < size - half; dz++) {
                level.setBlock(new BlockPos(feet.getX() + dx, y, feet.getZ() + dz), floor, 3);
            }
        }
    }

    private static void spawnTestDummy(ServerLevel level, ServerPlayer player) {
        TestDummyEntity dummy = new TestDummyEntity(BSCoins.TEST_DUMMY_TYPE.get(), level);
        var look = player.getLookAngle();
        dummy.setPos(player.getX() + look.x() * 3.0, player.getY(), player.getZ() + look.z() * 3.0);
        var maxHp = dummy.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(Config.DUMMY_MAX_HEALTH.get());
        }
        dummy.setHealth((float) (double) Config.DUMMY_MAX_HEALTH.get());
        // 面朝召唤者站好
        dummy.setYRot(player.getYRot() + 180.0F);
        dummy.setYHeadRot(dummy.getYRot());
        dummy.setSummoner(player);
        level.addFreshEntity(dummy);
    }

    /**
     * 手上(主手优先)那枚北山币, 没有币时返回空栈。只读, 不扣数量。
     */
    static ItemStack heldCoin(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof BSCoinItem) return main;
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof BSCoinItem ? off : ItemStack.EMPTY;
    }

    /**
     * 手上那枚<b>开发版</b>北山币(主手优先, 其次副手), 没有则返回空栈。只读, 不扣数量。
     */
    static ItemStack heldDevCoin(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(BSCoins.DEV_BS_COIN.get())) return main;
        ItemStack off = player.getOffhandItem();
        return off.is(BSCoins.DEV_BS_COIN.get()) ? off : ItemStack.EMPTY;
    }

    /**
     * 复刻原版 {@code ReloadCommand#discoverNewPacks}: 重新扫描数据包目录(这样往
     * {@code world/datapacks} 或 {@code mods/} 里新丢进去的包也能被认出来), 再把
     * "没被禁用且尚未选中"的包补进列表, 最后交给 NeoForge 把新发现的包排到正确位置
     * (顺序错了会让数据包之间的覆盖关系反过来, 所以这一步不能省)。
     */
    private static Collection<String> discoverDataPacks(MinecraftServer server) {
        PackRepository repository = server.getPackRepository();
        Collection<String> selected = new ArrayList<>(repository.getSelectedIds());
        repository.reload();
        List<String> ids = new ArrayList<>(selected);
        Collection<String> disabled = server.getWorldData().getDataConfiguration().dataPacks().getDisabled();
        for (String id : repository.getAvailableIds()) {
            if (!disabled.contains(id) && !ids.contains(id)) {
                ids.add(id);
            }
        }
        ResourcePackLoader.reorderNewlyDiscoveredPacks(ids, selected, repository);
        return ids;
    }

    /**
     * 从玩家手上取出一枚北山币(主手优先, 其次副手), 并按 amount 扣数量。
     *
     * @return 币的模板(数量1, 用来决定投出去的是普通币还是开发版币); 手上没有币时返回 null 并提示玩家。
     */
    @Nullable
    static ItemStack takeCoinStack(ServerPlayer player, int amount) {
        ItemStack source = heldCoin(player);
        if (source.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.bscoins.no_coin"));
            return null;
        }
        // 先留模板再扣数量, 否则 amount >= count 时模板会变成空物品
        ItemStack template = source.copyWithCount(1);
        if (amount > 0) {
            source.shrink(Math.min(amount, source.getCount()));
        }
        return template;
    }
}
