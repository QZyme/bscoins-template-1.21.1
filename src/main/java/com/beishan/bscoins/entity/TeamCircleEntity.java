package com.beishan.bscoins.entity;

import com.beishan.bscoins.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;

import java.util.List;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/**
 * 北山团建圈 - 长按右键3秒生成, 持续10分钟, 半径10格。
 * 提供: 急迫、缓降、作物加速与额外经验球增益。
 */
public class TeamCircleEntity extends Entity {
    private static final int EFFECT_TICK_INTERVAL = 20;
    private static final int XP_TICK_INTERVAL = 40;
    private static final int CROP_TICK_INTERVAL = 20;

    private int lifeTicks;
    private int radius;

    public TeamCircleEntity(EntityType<? extends TeamCircleEntity> type, Level level) {
        super(type, level);
        this.lifeTicks = Config.CIRCLE_DURATION_SECONDS.get() * 20;
        this.radius = Config.CIRCLE_RADIUS.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        // 团建圈在客户端不渲染任何东西 (注册的是 NoopRenderer), 效果全部在服务端 tick 里结算,
        // 所以这里没有需要同步给客户端的字段。留空是这个类的正常状态, 不是没写完。
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (--this.lifeTicks <= 0) {
            this.discard();
            return;
        }

        ServerLevel serverLevel = (ServerLevel) this.level();

        // 每 20 tick 应用 急迫+缓降
        if (this.tickCount % EFFECT_TICK_INTERVAL == 0) {
            List<Player> players = serverLevel.getEntitiesOfClass(Player.class,
                    new AABB(this.blockPosition()).inflate(this.radius));
            for (Player player : players) {
                if (player.distanceToSqr(this) <= (double) this.radius * this.radius) {
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, EFFECT_TICK_INTERVAL * 3, 0, false, true));
                    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, EFFECT_TICK_INTERVAL * 3, 0, false, true));
                }
            }
        }

        // 每 40 tick 给圈内玩家额外经验球增益 (限速, 避免实体刷屏)
        if (Config.CIRCLE_XP_BOOST.get() && this.tickCount % XP_TICK_INTERVAL == 0) {
            List<Player> players = serverLevel.getEntitiesOfClass(Player.class,
                    new AABB(this.blockPosition()).inflate(this.radius));
            for (Player player : players) {
                if (player.distanceToSqr(this) <= (double) this.radius * this.radius) {
                    // 直接给经验, 而非刷实体, 更省性能
                    player.giveExperiencePoints(1);
                }
            }
        }

        // 每 20 tick 随机加速圈内作物
        if (Config.CIRCLE_CROP_BOOST.get() && this.tickCount % CROP_TICK_INTERVAL == 0) {
            RandomSource rand = this.random;
            int x0 = this.blockPosition().getX() - this.radius;
            int z0 = this.blockPosition().getZ() - this.radius;
            for (int i = 0; i < 4; i++) {
                int bx = x0 + rand.nextInt(this.radius * 2 + 1);
                int bz = z0 + rand.nextInt(this.radius * 2 + 1);
                int by = this.blockPosition().getY() + rand.nextInt(5) - 2;
                BlockPos pos = new BlockPos(bx, by, bz);
                BlockState state = serverLevel.getBlockState(pos);
                if (state.getBlock() instanceof CropBlock) {
                    state.randomTick(serverLevel, pos, rand);
                }
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 老存档/缺失字段时不能留下 0, 否则 tick 里 --lifeTicks 立刻 <= 0, 读档瞬间就消散了
        int savedLife = tag.getInt("LifeTicks");
        this.lifeTicks = savedLife > 0 ? savedLife : Config.CIRCLE_DURATION_SECONDS.get() * 20;
        int savedRadius = tag.getInt("Radius");
        this.radius = savedRadius > 0 ? savedRadius : Config.CIRCLE_RADIUS.get();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeTicks", this.lifeTicks);
        tag.putInt("Radius", this.radius);
    }
}