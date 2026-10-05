package com.beishan.bscoins.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.beishan.bscoins.BSCoins;
import com.beishan.bscoins.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 空投箱实体 —— 从高空缓缓降落的"北山结构空投", 落地后变成装好战利品的箱子。
 *
 * <p>以前空投用的是原版 {@code FallingBlockEntity} 装一个箱子方块状态, 结果<b>天上什么都看不见</b>:
 * {@code models/block/chest.json} 里只有 {@code textures.particle}, 箱子根本没有方块模型
 * (它一直是靠 {@code ChestBlockRenderer} 画的), 而 {@code FallingBlockRenderer} 只会渲染方块模型 ——
 * 所以玩家只能看到一路云粒子。现在换成自己的实体 + 渲染器, 直接调用原版 {@code BlockEntityWithoutLevelRenderer}
 * 画出<b>真正的箱子模型</b> (1 格大小、底面正好落在脚底), 大小/旋转/摇摆都能自己定。
 *
 * <p>下落不用重力, 而是每 tick 固定下降 {@code airdropFallSpeed} 格 (配置里的含义就是"每 tick 多少格"),
 * 并且速度在投放瞬间定格, 中途改配置不会影响已经飞在天上的箱子。所有状态都写进 NBT,
 * 所以服务器重启/区块重载后它会接着往下掉, 不会卡在半空。
 */
public class AirdropCrateEntity extends Entity {
    private static final String NBT_OWNER = "BsCrateOwner";
    private static final String NBT_YAW = "BsCrateYaw";
    private static final String NBT_SEED = "BsCrateSeed";
    private static final String NBT_GROUND_Y = "BsCrateGroundY";
    private static final String NBT_SPEED = "BsCrateSpeed";
    private static final String NBT_FLARE_DONE = "BsCrateFlareDone";

    /** 信号弹升空的时长 (tick) */
    private static final int FLARE_TICKS = 12;
    /** 下落时每多少 tick 冒一次云粒子 */
    private static final int PARTICLE_INTERVAL = 4;
    /** 兜底: 这么久还没落地就强制落地, 免得因为地形/配置把箱子永远挂在半空 */
    private static final int MAX_LIFETIME_TICKS = 2400;
    /** 空投箱的战利品表: 原版地牢 loot + 小概率的北山币系列 (见 data/bscoins/loot_table/airdrop.json) */
    private static final ResourceKey<LootTable> LOOT_TABLE =
            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("bscoins", "airdrop"));

    @Nullable
    private UUID owner;
    /** 投放者的朝向: 落地的箱子正面朝向他 */
    private float ownerYaw;
    private long lootSeed;
    /** 目标落地高度 (箱子所在那一格的 Y) */
    private double groundY;
    /** 下降速度 (格/tick), 投放瞬间定格 */
    private double fallSpeed = 0.15;
    private boolean landed;
    /**
     * 信号弹已经放完了。
     * <p>要存档: tickCount 在区块重载/服务器重启后会归零, 不看这个标记的话信号弹会重放一次。
     */
    private boolean flareDone;

    public AirdropCrateEntity(EntityType<? extends AirdropCrateEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public AirdropCrateEntity(EntityType<? extends AirdropCrateEntity> type, Level level, Vec3 pos,
                              @Nullable UUID owner, float ownerYaw, long lootSeed, double groundY, double fallSpeed) {
        this(type, level);
        this.setPos(pos.x, pos.y, pos.z);
        this.owner = owner;
        this.ownerYaw = ownerYaw;
        this.lootSeed = lootSeed;
        this.groundY = groundY;
        this.fallSpeed = Math.max(0.005, fallSpeed);
    }

    @Override
    protected void defineSynchedData(@NotNull SynchedEntityData.Builder builder) {
        // 下降/摇摆完全由 tickCount 驱动, 没有需要同步的字段
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.land(serverLevel);
            return;
        }

        this.tickFlare(serverLevel);

        // 匀速下降 (不用重力): 撞到方块时 move() 会自然停下, 之后由 onGround 触发落地
        this.setDeltaMovement(0.0, -this.fallSpeed, 0.0);
        this.move(MoverType.SELF, this.getDeltaMovement());

        if (this.tickCount % PARTICLE_INTERVAL == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.getX(), this.getY() + 0.9, this.getZ(), 3, 0.25, 0.05, 0.25, 0.01);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 0.7, this.getZ(), 1, 0.15, 0.05, 0.15, 0.005);
        }

        // 到达目标高度 / 落在方块上 / 被卡住不动了, 都算落地。
        // 注意 Entity.getY() 是碰撞箱的"脚底"而不是中心, 而 groundY 就是那次地面方块的上表面,
        // 所以脚底落到 groundY 才算真正着地 (正常情况下 move() 先撞到地面, 走 onGround 分支)。
        if (this.getY() <= this.groundY + 0.05 || this.onGround() || this.verticalCollision) {
            this.land(serverLevel);
        }
    }

    /**
     * 信号弹: 出手后的前 {@value #FLARE_TICKS} tick, 从地面往上射一道火线并在到顶时炸开,
     * 用来在很远的地方就能标出空投位置。
     */
    private void tickFlare(ServerLevel level) {
        if (this.flareDone) return;              // 读档后不重放 (tickCount 会被重置)
        int tick = this.tickCount;
        if (tick > FLARE_TICKS) {
            this.flareDone = true;
            return;
        }

        double x = this.getX();
        double z = this.getZ();
        double progress = Math.max(0.0, Math.min(1.0, tick / (double) FLARE_TICKS));
        double prevProgress = Math.max(0.0, Math.min(1.0, (tick - 1) / (double) FLARE_TICKS));
        double headY = this.groundY + (this.getY() - this.groundY) * progress;
        double fromY = this.groundY + (this.getY() - this.groundY) * prevProgress;

        // 火线: 只补"上一 tick 弹头 -> 这一 tick 弹头"那一小段, 自然形成拖尾
        for (double y = fromY; y <= headY; y += 0.7) {
            level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 1, 0.04, 0.02, 0.04, 0.0);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, headY - 0.6, z, 1, 0.15, 0.1, 0.15, 0.0);
        level.sendParticles(ParticleTypes.FLAME, x, headY, z, 3, 0.08, 0.08, 0.08, 0.01);
        level.sendParticles(ParticleTypes.END_ROD, x, headY, z, 2, 0.06, 0.06, 0.06, 0.0);

        if (tick == 1) {
            level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.PLAYERS, 1.4F, 1.0F);
        } else if (tick == FLARE_TICKS) {
            level.sendParticles(ParticleTypes.FLASH, x, this.getY(), z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.FIREWORK, x, this.getY(), z, 40, 0.6, 0.6, 0.6, 0.06);
            level.sendParticles(ParticleTypes.END_ROD, x, this.getY(), z, 16, 0.5, 0.5, 0.4, 0.04);
            level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.PLAYERS, 1.5F, 1.1F);
            this.flareDone = true;
        }
    }

    /**
     * 落地: 放箱子 + 填战利品 + 尘爆特效 + 通知投放者。
     * <p>三级兜底, 保证战利品不丢:
     * 先在目标格附近找地方放箱子; 不行就直接放在箱子自己所在那一格 (那里是空气);
     * 再不行就把战利品表当场抽成掉落物撒出来。
     */
    private void land(ServerLevel level) {
        if (this.landed) return;
        this.landed = true;

        BlockState chestState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.fromYRot(this.ownerYaw).getOpposite());

        BlockPos pos = this.findPlacement(level);
        if (pos == null || !this.placeChest(level, pos, chestState)) {
            BlockPos here = this.blockPosition();
            if (this.placeChest(level, here, chestState)) {
                pos = here;
            } else {
                pos = here;
                this.dropLootInstead(level, pos);
            }
        }

        level.sendParticles(ParticleTypes.CLOUD,
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 26, 0.9, 0.15, 0.9, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 8, 0.7, 0.1, 0.7, 0.02);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.6F);

        if (this.owner != null) {
            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(this.owner);
            if (ownerPlayer != null) {
                ownerPlayer.sendSystemMessage(Component.translatable("message.bscoins.airdrop_landed",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        this.discard();
    }

    /** 在 pos 放一个装了战利品的箱子。返回 false 表示这里放不下。 */
    private boolean placeChest(ServerLevel level, BlockPos pos, BlockState chestState) {
        if (!level.getBlockState(pos).canBeReplaced() && !level.isEmptyBlock(pos)) {
            return false;
        }
        if (!level.setBlock(pos, chestState, 3)) {
            return false;
        }
        // 注意: 方块已经放好了就算成功 —— 万一取不到方块实体 (理论上不会, 箱子一定带 BE),
        // 也不能返回 false, 否则 land() 会再找一次位置、再放一个箱子 (战利品翻倍)。
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(this.lootTableKey(level));
            chest.setLootTableSeed(this.lootSeed);
        } else {
            BSCoins.LOGGER.warn("空投箱放下后取不到 ChestBlockEntity @ {} (战利品表没能设置)", pos);
        }
        return true;
    }

    /**
     * 实在放不下箱子的兜底: 当场把战利品表抽成掉落物撒在脚下, 一件都不吞。
     * 抽不出来 (极端情况下战利品表参数不满足) 也至少掉一个箱子物品。
     */
    private void dropLootInstead(ServerLevel level, BlockPos pos) {
        try {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(this.lootTableKey(level));
            // 箱子类战利品表用 CHEST 参数集 (required: ORIGIN) —— 用 EMPTY 的话
            // 有些按位置取值的条目/函数会直接抽不到东西
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params, this.lootSeed)) {
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(stack, 0.3F);
                }
            }
        } catch (RuntimeException e) {
            BSCoins.LOGGER.warn("空投箱没地方放, 战利品表也抽不出来, 只能掉一个空箱子: {}", e.toString());
            this.spawnAtLocation(new ItemStack(Blocks.CHEST), 0.5F);
        }
    }

    /**
     * 用哪张战利品表: 优先自建的 {@code bscoins:airdrop} (原版地牢 loot + 小概率北山币系列,
     * 见 {@code data/bscoins/loot_table/airdrop.json})。万一这张表没被数据包加载出来
     * (被别的包覆盖 / 改坏了), 退回原版地牢表 —— 不让玩家开出一个空箱子。
     */
    private ResourceKey<LootTable> lootTableKey(ServerLevel level) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(LOOT_TABLE);
        return table == LootTable.EMPTY ? BuiltInLootTables.SIMPLE_DUNGEON : LOOT_TABLE;
    }

    /** 找放箱子的位置: 目标格 -> 往上几格 -> 目标格周围一圈, 返回 null 表示真的没地方放。 */
    @Nullable
    private BlockPos findPlacement(ServerLevel level) {
        BlockPos base = BlockPos.containing(this.getX(), this.groundY, this.getZ());
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos p = base.above(dy);
            if (this.canPlaceChest(level, p)) return p;
        }
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (this.canPlaceChest(level, p)) return p;
                }
            }
        }
        return null;
    }

    private boolean canPlaceChest(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced()) return false;
        return !level.getBlockState(pos.below()).isAir();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (this.owner != null) {
            tag.putUUID(NBT_OWNER, this.owner);
        }
        tag.putFloat(NBT_YAW, this.ownerYaw);
        tag.putLong(NBT_SEED, this.lootSeed);
        tag.putDouble(NBT_GROUND_Y, this.groundY);
        tag.putDouble(NBT_SPEED, this.fallSpeed);
        tag.putBoolean(NBT_FLARE_DONE, this.flareDone);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.hasUUID(NBT_OWNER)) {
            this.owner = tag.getUUID(NBT_OWNER);
        }
        this.ownerYaw = tag.getFloat(NBT_YAW);
        this.lootSeed = tag.getLong(NBT_SEED);
        this.groundY = tag.getDouble(NBT_GROUND_Y);
        this.fallSpeed = Math.max(0.005, tag.contains(NBT_SPEED) ? tag.getDouble(NBT_SPEED) : Config.AIRDROP_FALL_SPEED.get());
        this.flareDone = tag.getBoolean(NBT_FLARE_DONE);
    }
}
