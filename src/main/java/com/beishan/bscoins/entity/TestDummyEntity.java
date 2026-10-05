package com.beishan.bscoins.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.beishan.bscoins.Config;
import com.beishan.bscoins.handler.DamageNumberHandler;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 测试假人 - 潜行Q投掷空气召唤, 带血量条(对召唤者显示 Boss 血条, 血条上带倒计时)。
 * 活到 {@code dummyLifetimeSeconds} 秒后自动消散, 不会永久占着世界。
 *
 * <p>血条的生命周期必须和实体严格绑定, 否则会"卡在屏幕上": 死亡动画播完时
 * {@code LivingEntity.tick()} 内部会 {@code remove(KILLED)}, 之后本实体再也不会 tick,
 * 所以 {@link #tick()} 里必须先判断 {@code isRemoved()} / {@code isDeadOrDying()} 再决定要不要建血条;
 * 召唤者下线或走出 {@value #BOSS_BAR_MAX_DISTANCE} 格(区块可能卸载)时也直接收掉。
 */
public class TestDummyEntity extends Mob {
    private static final String NBT_SUMMONER = "Summoner";
    private static final String NBT_LIFE = "LifeTicks";
    /** 召唤者离得比这个还远就把血条收掉: 实体所在区块一旦卸载就不再 tick, 留在屏幕上的血条会永远卡住 */
    private static final double BOSS_BAR_MAX_DISTANCE = 64.0;

    @Nullable
    private UUID summonerId;
    @Nullable
    private ServerBossEvent bossBar;
    /** 剩余存活 tick; 到 0 就消散 */
    private int lifeTicks;
    /** 血条标题上次显示的秒数, 避免每 tick 都发一次改名包 */
    private int shownSeconds = -1;
    /** 伤害飘字的排队序号: 连续命中时用来把数字错开 */
    private int damageNumberStack = 0;
    private int lastDamageNumberTick = -1000;

    public TestDummyEntity(EntityType<? extends TestDummyEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setInvulnerable(false);
        this.lifeTicks = Config.DUMMY_LIFETIME_SECONDS.get() * 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public void setSummoner(ServerPlayer player) {
        this.summonerId = player.getUUID();
    }

    /** 剩余存活时间的描述文字 (血条标题)。 */
    private Component bossBarTitle() {
        int seconds = Math.max(0, (this.lifeTicks + 19) / 20);
        return Component.translatable("entity.bscoins.test_dummy.timer", seconds);
    }

    private void ensureBossBar(ServerPlayer summoner) {
        if (this.bossBar == null) {
            this.bossBar = new ServerBossEvent(this.bossBarTitle(),
                    BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            this.bossBar.setVisible(true);
            this.shownSeconds = Math.max(0, (this.lifeTicks + 19) / 20);
        }
        if (!this.bossBar.getPlayers().contains(summoner)) {
            this.bossBar.addPlayer(summoner);
        }
    }

    private void updateBossBar() {
        if (this.bossBar == null) return;
        this.bossBar.setProgress(Math.max(0.0F, Math.min(1.0F, this.getHealth() / this.getMaxHealth())));
        int seconds = Math.max(0, (this.lifeTicks + 19) / 20);
        if (seconds != this.shownSeconds) {
            this.shownSeconds = seconds;
            this.bossBar.setName(this.bossBarTitle());
        }
    }

    @Override
    protected void registerGoals() {
        // 假人不动也不打人: 故意不注册任何 AI 目标
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // 不靠"远离玩家"消失; 由 lifeTicks 倒计时负责回收
        return false;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        float healthBefore = this.getHealth();
        boolean result = super.hurt(source, amount);
        if (!this.level().isClientSide) {
            this.updateBossBar();
            // 伤害数字: 显示"实际掉了多少血"(含护甲/吸收, 比传入的 amount 更真实), 打死的那一下标红。
            // 0.6 秒内连续命中就把序号往上叠, 让数字错开而不是叠在一起。
            float dealt = healthBefore - this.getHealth();
            if (dealt > 0.0F && this.level() instanceof ServerLevel serverLevel) {
                this.damageNumberStack = this.tickCount - this.lastDamageNumberTick <= 12
                        ? this.damageNumberStack + 1
                        : 0;
                this.lastDamageNumberTick = this.tickCount;
                DamageNumberHandler.spawn(serverLevel, this, dealt, this.getHealth() <= 0.0F,
                        this.damageNumberStack);
            }
        }
        return result;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** 把血条从所有玩家屏幕上摘掉 (Boss 条是服务端对象, 不主动摘就会一直挂在客户端)。 */
    private void clearBossBar() {
        if (this.bossBar != null) {
            this.bossBar.removeAllPlayers();
            this.bossBar = null;
        }
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        this.clearBossBar();
        super.remove(reason);
    }

    @Override
    public void die(@NotNull DamageSource source) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    this.getX(), this.getY() + 1, this.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
        }
        super.die(source);
        this.clearBossBar();
    }

    /** 存活时间耗尽: 冒烟消散, 什么也不掉。 */
    private void expire() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    this.getX(), this.getY() + 1, this.getZ(), 40, 0.4, 0.6, 0.4, 0.06);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.WOOD_BREAK,
                    SoundSource.NEUTRAL, 0.7F, 0.7F);
        }
        this.clearBossBar();
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.summonerId != null) {
            tag.putUUID(NBT_SUMMONER, this.summonerId);
        }
        tag.putInt(NBT_LIFE, this.lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_SUMMONER)) {
            this.summonerId = tag.getUUID(NBT_SUMMONER);
        }
        // 旧存档没有倒计时字段时给满时间, 避免读档即消失
        this.lifeTicks = tag.contains(NBT_LIFE) && tag.getInt(NBT_LIFE) > 0
                ? tag.getInt(NBT_LIFE)
                : Config.DUMMY_LIFETIME_SECONDS.get() * 20;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        // ⚠ 这里是"打死后血条/倒计时永远卡住"的修复点:
        // LivingEntity.tick() 内部 (即上面的 super.tick()) 会在死亡动画播完时调用 tickDeath()
        // → tickDeath() 到第 20 tick 就 remove(KILLED)。实体被移除后再也不会 tick,
        // 如果这时还往下走去 ensureBossBar(), 就会给一个"已经没了"的实体新建一条 Boss 血条,
        // 之后没有任何代码能清理它 —— 客户端就一直挂着那条不再更新的血条(倒计时定格)。
        if (this.isRemoved()) {
            this.clearBossBar();
            return;
        }
        // 死亡动画(20 tick)期间: 不倒计时, 也不要碰血条 —— die() 已经把它摘掉了
        if (this.isDeadOrDying()) {
            return;
        }

        // 存活倒计时: 到点自动消失
        if (this.lifeTicks > 0) {
            this.lifeTicks--;
            if (this.lifeTicks <= 0) {
                this.expire();
                return;
            }
        }

        if (this.summonerId == null || !(this.level() instanceof ServerLevel serverLevel)) return;
        ServerPlayer summoner = serverLevel.getServer().getPlayerList().getPlayer(this.summonerId);
        if (summoner == null || summoner.distanceToSqr(this) > BOSS_BAR_MAX_DISTANCE * BOSS_BAR_MAX_DISTANCE) {
            // 召唤者下线/走远: 收掉血条。走远时实体区块随时会卸载, 卸载后就不再 tick,
            // 留下的血条同样会永远卡在屏幕上。
            this.clearBossBar();
            return;
        }
        this.ensureBossBar(summoner);
        this.updateBossBar();
    }
}
