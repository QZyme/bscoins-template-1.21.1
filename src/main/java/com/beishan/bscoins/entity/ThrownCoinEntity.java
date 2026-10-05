package com.beishan.bscoins.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;
import com.beishan.bscoins.handler.CoinUsageTracker;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 投掷币 - Q键/V键弹射投币实体。
 *
 * <p>手感 (像箭, 不像雪球):
 * <ul>
 *   <li>初速很高 ({@code throwSpeed}, 默认8.0格/tick), 之后每一 tick 乘 {@code throwDrag} 受空气阻力
 *       慢慢减速, 所以是"出手最快, 越飞越慢";</li>
 *   <li>伤害跟着<b>命中那一刻的实际速率</b>走: 满速 = {@code throwDamage}, 飞久了变慢就变低;
 *       抛物线下坠段重力会把竖直速度补回来, 所以高抛命中反而更疼 (上限 {@code throwDamageMax});</li>
 *   <li>撞到方块就<b>像箭一样插在原地</b>不再弹跳, 停稳 {@value #STUCK_PICKUP_DELAY} tick 后,
 *       主人走进 {@code throwPickupRadius} 格内才会被拾取;</li>
 *   <li>普通北山币水平射程超过 {@code throwBurnDistance}(默认50格) 会当场烧毁, 币就没了;
 *       开发版北山币是特制的: 射程无限、永不烧毁;</li>
 *   <li>兜底存活 {@code throwMaxLifetimeSeconds} 秒, 防止投掷物永久残留。</li>
 * </ul>
 */
public class ThrownCoinEntity extends ThrowableItemProjectile {
    /** 插在地上之后还要等这么久才允许被拾取, 免得刚落地就被主人吸走 */
    private static final int STUCK_PICKUP_DELAY = 10;
    /**
     * 插住时沿命中面法线的小偏移。
     * <p>别调大: 投掷物渲染器 (ThrownItemRenderer) 自己会把模型往上抬 0.15 格, 这里再抬就会"浮"在地面上;
     * 0.05 刚好让币的下缘埋进土里, 看起来是插在地上而不是躺在地上。
     */
    private static final double STUCK_LIFT = 0.05;
    /**
     * 自己画多远。
     * 投掷物默认的可视距离 = 碰撞箱尺寸(0.25) × 64 = 16 格, 所以币飞出16格以后客户端就完全不画了,
     * 玩家看到的就是"扔出去/插在地上就没了"。箭(AbstractArrow)就是靠重写这个方法来放大到320格的, 这里照做。
     */
    private static final double RENDER_DISTANCE = 160.0;

    private static final String NBT_OWNER = "BsCoinOwner";
    private static final String NBT_SPECIAL = "BsCoinSpecial";
    private static final String NBT_ORIGIN = "BsCoinOrigin";
    private static final String NBT_STUCK = "BsCoinStuck";
    /** 插住瞬间的水平朝向 (渲染时固定朝向用, 否则币会一直对着玩家) */
    private static final String NBT_STUCK_YAW = "BsCoinStuckYaw";
    /** 插在哪个面上 (UP = 地面), 渲染时决定"立多直 / 怎么歪" */
    private static final String NBT_STUCK_FACE = "BsCoinStuckFace";

    // ── 同步数据: 插住状态必须走 SynchedEntityData, 不能只存磁盘 NBT ─────────────
    // 只存 NBT 的话: 服务端知道自己插住了, 但"后进入视野的客户端"(或区块重载后重新收到这只币的客户端)
    // 只看得到位置包, 本地永远不会触发 onHitBlock -> isStuck() 恒为 false ->
    // 币会被渲染成还在飞的翻滚姿态, 而且客户端本地 tick 还在按重力往下沉, 看起来在抖/沉进方块。
    private static final EntityDataAccessor<Boolean> DATA_STUCK =
            SynchedEntityData.defineId(ThrownCoinEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_STUCK_YAW =
            SynchedEntityData.defineId(ThrownCoinEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_STUCK_FACE =
            SynchedEntityData.defineId(ThrownCoinEntity.class, EntityDataSerializers.BYTE);

    @Nullable
    private UUID owner;
    /** 特制币 (开发版北山币): 射程无限且永不烧毁 */
    private boolean special = false;
    /** 出手点, 用来算射程 */
    private Vec3 origin = Vec3.ZERO;
    /** 插地之后的计时, 用于拾取延迟 (纯服务端逻辑, 不需要同步) */
    private int stuckTicks = 0;

    @Override
    protected void defineSynchedData(@NotNull SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);   // 父类要注册物品等数据, 不能漏
        builder.define(DATA_STUCK, false);
        builder.define(DATA_STUCK_YAW, 0.0F);
        builder.define(DATA_STUCK_FACE, (byte) Direction.UP.get3DDataValue());
    }

    public ThrownCoinEntity(EntityType<? extends ThrownCoinEntity> type, Level level) {
        super(type, level);
    }

    public ThrownCoinEntity(EntityType<? extends ThrownCoinEntity> type, LivingEntity shooter, Level level,
                            ItemStack coin, boolean special) {
        super(type, shooter, level);
        this.owner = shooter.getUUID();
        this.setItem(coin.copyWithCount(1));
        this.special = special;
        this.origin = this.position();
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return BSCoins.BS_COIN.get();
    }

    /**
     * 下坠重力 (默认 0.055)。
     * <p>以前是 0.04 + 阻力 0.94: 末端下坠速度被阻力卡在 0.63 格/tick (约12格/秒), 45° 高抛要飘
     * <b>9.8 秒</b>才落地、落地伤害只有 0.8 —— 现在 0.055 + 0.97 让末端下坠回到约 1.8 格/tick,
     * 高抛 1~2 秒就砸下来, 平抛约 65 格落地。
     */
    @Override
    protected double getDefaultGravity() {
        return Config.THROW_GRAVITY.get();
    }

    // ── 伤害: 完全由命中瞬间的速率决定 ────────────────────────────────

    /** 命中那一刻的速率 (格/tick), 已经包含下坠带来的竖直分量。 */
    private double impactSpeed() {
        return this.getDeltaMovement().length();
    }

    /**
     * 伤害随速率线性变化: 贴脸满速 = throwDamage, 飞远了被空气阻力拖慢就变轻。
     * 下坠会把一点竖直速度补回来, 但补不回阻力吃掉的那部分, 所以整体是"越远越轻"。
     */
    private float damageFor(double speed) {
        double launch = Math.max(0.05, Config.THROW_SPEED.get());
        double damage = Config.THROW_DAMAGE.get() * (speed / launch);
        return (float) Math.max(0.0, Math.min(Config.THROW_DAMAGE_MAX.get(), damage));
    }

    /** 命中速率 / 初速, 0~1。用来缩放音效音量、粒子数量与击退力度。 */
    private double impactFraction() {
        double launch = Math.max(0.05, Config.THROW_SPEED.get());
        return Math.min(1.0, this.impactSpeed() / launch);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        if (this.level().isClientSide) return;
        Entity target = result.getEntity();

        Vec3 motion = this.getDeltaMovement();
        double fraction = this.impactFraction();

        // 自己扔出去砸到自己(比如对着脚底下丢): 只减速, 绝不掉血
        if (this.owner != null && this.owner.equals(target.getUUID())) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.25));
            this.hasImpulse = true;
            return;
        }

        float damage = this.damageFor(this.impactSpeed());

        switch (target) {
            case Player hitPlayer when !hitPlayer.getUUID().equals(this.owner) -> {
                // 远程打赏玩家: 鸡血 + 老板 Buff (友军不掉血)
                hitPlayer.addEffect(new MobEffectInstance(BSCoins.GOAD, Config.TIP_DURATION_SECONDS.get() * 20, Config.GOAD_LEVEL.get()));
                hitPlayer.addEffect(new MobEffectInstance(BSCoins.BOSS, Config.TIP_DURATION_SECONDS.get() * 20, Config.BOSS_LEVEL.get()));
                hitPlayer.sendSystemMessage(Component.translatable("message.bscoins.tip"));
                if (hitPlayer instanceof ServerPlayer sp) {
                    CoinUsageTracker.markCoinUsed(sp.serverLevel(), sp);
                }
            }
            // 村民/流浪商人只轻拍一下, 免得把北山小卖部打没了
            case AbstractVillager villager -> {
                villager.hurt(this.damageSources().thrown(this, this.getOwner()), 1.0F);
                villager.knockback(0.2 + 0.35 * fraction, -motion.x, -motion.z);
            }
            case Mob mob -> {
                mob.hurt(this.damageSources().thrown(this, this.getOwner()), damage);
                mob.addEffect(new MobEffectInstance(BSCoins.STUN, Config.STUN_DURATION_SECONDS.get() * 20, 0));
                // 击退: 沿币的飞行方向把怪推出去, 手感上"被打飞了一下"
                mob.knockback(0.25 + 0.45 * fraction, -motion.x, -motion.z);
                if (this.getOwner() instanceof ServerPlayer sp2) {
                    CoinUsageTracker.markCoinUsed(sp2.serverLevel(), sp2);
                }
            }
            case LivingEntity living -> {
                living.hurt(this.damageSources().thrown(this, this.getOwner()), damage);
                living.knockback(0.25 + 0.45 * fraction, -motion.x, -motion.z);
            }
            default -> {
            }
        }

        // 命中反馈: 撞击声 + 火花 (音量/数量跟着速率走)
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_HIT,
                    SoundSource.PLAYERS, (float) (0.35 + 0.4 * fraction), (float) (0.8 + 0.5 * fraction));
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY(), this.getZ(), 3 + (int) (7 * fraction), 0.12, 0.12, 0.12, 0.06);
        }

        // 命中实体后大幅减速 (动能被吃掉了), 但不消失也不回包
        this.setDeltaMovement(this.getDeltaMovement().scale(0.25));
        this.hasImpulse = true;
    }

    // ── 落地: 像箭一样插住 ──────────────────────────────────────────

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        // 注意: 客户端也要插住 (和箭的 inGround 一样, 两端各自判定)。
        // 以前这里 isClientSide 直接 return: 服务端的币冻住了, 客户端的币却还在自己往下掉;
        // 而服务端"没位移"就不会发位置包 (ServerEntity 只在位移超过阈值或每 60 tick 才发),
        // 于是客户端那只币一路沉进方块里、看不见了 —— 这就是"扔出去直接掉进地里消失"的真正原因。
        if (this.isStuck()) return;
        this.stickInto(result);
    }

    /**
     * 插在命中的方块上: 速度清零、往命中面外侧抬一点点、记下插住状态。
     * 之后每 tick 都会把速度重新清零并跳过移动, 所以它会稳稳停在原地 (等于箭的 inGround)。
     */
    private void stickInto(BlockHitResult result) {
        this.entityData.set(DATA_STUCK, true);
        this.stuckTicks = 0;

        // 把"撞击方向的水平角"固定下来当成永久朝向。
        // 注意要在清速度之前取: 之后 updateRotation() 会因为速度变 0 把 yaw 慢慢拉回 0 (正北),
        // 所以不能直接读 getYRot(), 也不能让它每 tick 重算 —— 必须在这里定格。
        Vec3 motion = this.getDeltaMovement();
        double launch = Math.max(0.05, Config.THROW_SPEED.get());
        double fraction = Math.min(1.0, motion.length() / launch);
        float yaw = motion.horizontalDistanceSqr() > 1.0E-4
                ? (float) (Math.atan2(motion.x, motion.z) * 180.0 / Math.PI)
                : this.getYRot();
        this.entityData.set(DATA_STUCK_YAW, yaw);

        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;

        Direction face = result.getDirection();
        this.entityData.set(DATA_STUCK_FACE, (byte) face.get3DDataValue());
        Vec3 hit = result.getLocation();
        this.setPos(hit.x + face.getStepX() * STUCK_LIFT,
                hit.y + face.getStepY() * STUCK_LIFT,
                hit.z + face.getStepZ() * STUCK_LIFT);

        if (this.level() instanceof ServerLevel serverLevel) {
            // 撞击声与火花跟着命中速率走: 轻轻碰到"叮"一声, 满速砸上去是"当"一下
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS, (float) (0.2 + 0.25 * fraction), 1.8F);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    hit.x, hit.y, hit.z, 3 + (int) (8 * fraction), 0.1, 0.1, 0.1, 0.05);
        }
    }

    /**
     * 空气阻力 + <b>重力顺序修正</b>。
     *
     * <p>{@code super.tick()} 里是"先乘 0.99 再扣重力", 我们再补一个系数就等于连重力一起缩小了
     * ({@code (0.99v - g) * e = drag*v - drag*g}, 重力被打了折)。这里把差额补回去, 让
     * {@code throwDrag} / {@code throwGravity} 严格等于配置值 —— 否则配置里写的
     * "0.055 平抛约65格"在游戏里其实是 0.052。
     */
    private void applyCoinDrag() {
        double vanillaDrag = this.isInWater() ? 0.8 : 0.99;
        double extra = Config.THROW_DRAG.get() / vanillaDrag;
        if (extra >= 1.0) return;   // 水里原版阻力本来就更大, 不再额外加
        Vec3 v = this.getDeltaMovement();
        double gravity = this.getDefaultGravity() * (1.0 - extra);
        this.setDeltaMovement(v.x * extra, v.y * extra - gravity, v.z * extra);
        this.hasImpulse = true;
    }

    /**
     * 飞行拖尾: 沿<b>这一 tick 的位移</b>均匀铺粒子, 所以速度越快拖尾越长 ——
     * 初速 9 格/tick 时一 tick 就飞 9 格, 不铺满就只会看到一串稀疏的点。
     */
    private void tickFlightTrail() {
        if (!Config.THROW_TRAIL.get()) return;
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        if (this.tickCount % 2 != 0) return;
        Vec3 step = this.getDeltaMovement();
        double length = step.length();
        if (length < 0.05) return;
        Vec3 to = this.position();
        Vec3 from = to.subtract(step);
        int count = Mth.clamp((int) Math.ceil(length * 1.2), 1, 10);
        ParticleOptions particle = this.special ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK;
        for (int i = 0; i < count; i++) {
            Vec3 p = from.add(step.scale(count == 1 ? 1.0 : (double) i / (count - 1)));
            serverLevel.sendParticles(particle, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** 插在地上的币每 2 秒冒一颗微光, 免得掉进草丛/方块缝里找不回来 (32 格内有玩家才发)。 */
    private void tickStuckMarker() {
        if (!Config.THROW_TRAIL.get() || this.stuckTicks % 40 != 0) return;
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getNearestPlayer(this, 32.0) == null) return;
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                this.getX(), this.getY() + 0.25, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    @Override
    public void tick() {
        if (this.isStuck()) {
            // 插地: 两端都要锁住。
            // 父类 tick() 最后会 applyGravity() 把速度改成 -重力, 下一 tick 就会往下沉一截,
            // 所以必须在 super.tick() 之后再清零一次。客户端不清零的话, 客户端那只币会一路沉进方块里。
            super.tick();
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.level().isClientSide) {
                this.stuckTicks++;
                this.checkPickup();
                this.tickStuckMarker();
            }
            return;
        }

        super.tick();
        if (this.level().isClientSide) return;

        // 空气阻力 (见 applyCoinDrag: 顺带修正 super.tick() 带来的重力偏差)
        this.applyCoinDrag();
        this.tickFlightTrail();

        // 射程限制 (按水平距离算, 高抛的竖直高度不算射程): 普通币飞过头就烧毁 (特制币免疫)
        if (!this.special) {
            double burn = Config.THROW_BURN_DISTANCE.get();
            double dx = this.getX() - this.origin.x;
            double dz = this.getZ() - this.origin.z;
            if (dx * dx + dz * dz >= burn * burn) {
                this.burnUp(true);
                return;
            }
        }

        // 兜底存活时间: 防止投掷物永久残留在世界里
        if (this.tickCount >= Config.THROW_MAX_LIFETIME_SECONDS.get() * 20) {
            if (this.special) {
                this.dropAsItem();
            } else {
                this.burnUp(false);
            }
        }
    }

    /**
     * 拾取判定: 插稳一段时间后, 主人(或任何人)走进 throwPickupRadius 格内就吸回背包。
     *
     * @return true 表示这只币已经被处理掉 (拾取走了)
     */
    private boolean checkPickup() {
        if (this.stuckTicks < STUCK_PICKUP_DELAY) return false;
        double radius = Config.THROW_PICKUP_RADIUS.get();
        double radiusSqr = radius * radius;
        boolean ownerOnly = Config.THROW_PICKUP_OWNER_ONLY.get();

        Player picker = null;
        if (this.owner != null) {
            Player ownerPlayer = this.level().getPlayerByUUID(this.owner);
            if (ownerPlayer != null && ownerPlayer.distanceToSqr(this) < radiusSqr) {
                picker = ownerPlayer;
            }
        }
        if (picker == null && !ownerOnly) {
            Player nearest = this.level().getNearestPlayer(this, radius);
            if (nearest != null) {
                picker = nearest;
            }
        }
        if (picker == null) return false;

        this.returnToPlayer(picker);
        return true;
    }

    /** 射程到顶: 烧毁, 货币直接损失 (特制币不会走到这里)。 */
    private void burnUp(boolean notify) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    this.getX(), this.getY() + 0.1, this.getZ(), 16, 0.15, 0.15, 0.15, 0.02);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 0.15, this.getZ(), 6, 0.15, 0.15, 0.15, 0.01);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.7F, 1.3F);
            if (notify && this.owner != null) {
                Player ownerPlayer = serverLevel.getPlayerByUUID(this.owner);
                if (ownerPlayer != null) {
                    ownerPlayer.sendSystemMessage(Component.translatable("message.bscoins.coin_burned")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
        this.discard();
    }

    /** 特制币兜底超时: 变成地上可拾取的掉落物, 绝不销毁。 */
    private void dropAsItem() {
        this.spawnAtLocation(this.getItem().copyWithCount(1), 0.25F);
        this.discard();
    }

    /**
     * 把币还给玩家。优先放回当前手持槽(这样才算"回到手上"), 其次同类币堆, 再找空槽;
     * 背包塞不下就掉在玩家脚下。任何情况下都不会把币吞掉。
     */
    private void returnToPlayer(Player player) {
        if (this.level().isClientSide || this.isRemoved()) return;
        ItemStack coin = this.getItem().copyWithCount(1);
        if (coin.isEmpty()) {
            coin = new ItemStack(BSCoins.BS_COIN.get(), 1);
        }
        if (!giveBackToHand(player, coin)) {
            // 背包实在塞不下: 掉在玩家脚下, 而不是币落地的地方
            player.drop(coin, false);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.5F);
        this.discard();
    }

    /**
     * 放回玩家背包, 优先手持槽。
     * 不能用 Inventory#add: 创造模式下它装不下时会把物品 setCount(0) 直接删掉并返回 true,
     * 调用方会以为成功, 币就凭空消失了。
     */
    private static boolean giveBackToHand(Player player, ItemStack coin) {
        Inventory inventory = player.getInventory();

        // 1) 当前手持槽 (扔出去以后这里通常是空的 -> 币直接回到手上)
        int hand = inventory.selected;
        ItemStack inHand = inventory.getItem(hand);
        if (inHand.isEmpty()) {
            inventory.setItem(hand, coin);
            return true;
        }
        if (ItemStack.isSameItemSameComponents(inHand, coin) && inHand.getCount() < inHand.getMaxStackSize()) {
            inHand.grow(coin.getCount());
            return true;
        }

        // 2) 可叠加的同类币堆
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, coin) && stack.getCount() < stack.getMaxStackSize()) {
                stack.grow(coin.getCount());
                return true;
            }
        }

        // 3) 空槽
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, coin);
                return true;
            }
        }
        return false;
    }

    /** 这只币是不是特制币 (开发版北山币)。 */
    public boolean isSpecial() {
        return this.special;
    }

    /** 是否已经插在方块上 (插住后渲染要用固定朝向, 不做公告板)。已同步, 后进视野的客户端也拿得到。 */
    public boolean isStuck() {
        return this.entityData.get(DATA_STUCK);
    }

    /** 插住时固定的水平朝向 (度), 由撞击方向决定, 之后永不变。 */
    public float getStuckYaw() {
        return this.entityData.get(DATA_STUCK_YAW);
    }

    /** 插在哪个面上 (UP = 地面)。渲染时按这个决定"立多直、往哪歪"。 */
    public Direction getStuckFace() {
        return Direction.from3DDataValue(this.entityData.get(DATA_STUCK_FACE));
    }

    /**
     * 放大可视距离, 否则 0.25 的碰撞箱只有 {@code 0.25 * 64 = 16} 格可视距离,
     * 币一飞远就整只不画了 (插在地上自然也看不见)。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // 不存 owner 的话, 区块卸载/重载或服务器重启后 owner 变 null, 币会被静默吞掉
        if (this.owner != null) {
            tag.putUUID(NBT_OWNER, this.owner);
        }
        tag.putBoolean(NBT_SPECIAL, this.special);
        tag.putBoolean(NBT_STUCK, this.isStuck());
        tag.putFloat(NBT_STUCK_YAW, this.getStuckYaw());
        tag.putInt(NBT_STUCK_FACE, this.getStuckFace().get3DDataValue());
        tag.putDouble(NBT_ORIGIN + "X", this.origin.x);
        tag.putDouble(NBT_ORIGIN + "Y", this.origin.y);
        tag.putDouble(NBT_ORIGIN + "Z", this.origin.z);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(NBT_OWNER)) {
            this.owner = tag.getUUID(NBT_OWNER);
        }
        this.special = tag.getBoolean(NBT_SPECIAL);
        // 写进同步数据 (而不是私有字段), 读档后同样会通过生成包发给客户端
        this.entityData.set(DATA_STUCK, tag.getBoolean(NBT_STUCK));
        if (tag.contains(NBT_STUCK_YAW)) {
            this.entityData.set(DATA_STUCK_YAW, tag.getFloat(NBT_STUCK_YAW));
        }
        if (tag.contains(NBT_STUCK_FACE)) {
            this.entityData.set(DATA_STUCK_FACE, (byte) tag.getInt(NBT_STUCK_FACE));
        }
        // 旧存档没有出手点: 退化成"当前位置", 这样射程从重载点重新开始算, 不会瞬间烧毁
        if (tag.contains(NBT_ORIGIN + "X")) {
            this.origin = new Vec3(tag.getDouble(NBT_ORIGIN + "X"),
                    tag.getDouble(NBT_ORIGIN + "Y"), tag.getDouble(NBT_ORIGIN + "Z"));
        } else {
            this.origin = this.position();
        }
        if (this.isStuck()) {
            // 读档时插地的币直接当成已经插稳, 不用再等拾取延迟
            this.stuckTicks = STUCK_PICKUP_DELAY;
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public boolean mayInteract(@NotNull Level level, net.minecraft.core.@NotNull BlockPos pos) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }
}
