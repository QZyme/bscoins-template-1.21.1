package com.beishan.bscoins.client;

import com.beishan.bscoins.entity.ThrownCoinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 投掷币渲染器 —— 修掉"插在地上还一直转过来看玩家"的问题。
 *
 * <p>原版 {@code ThrownItemRenderer} 只有一种画法: 用 {@code cameraOrientation()} 做公告板,
 * 也就是永远正对镜头。对飞行中的雪球/鸡蛋没问题, 但币"插在地上"以后还跟着镜头转, 就不像插住了。
 *
 * <p>这里分两种状态:
 * <ul>
 *   <li><b>飞行中</b>: 按飞行方向摆正 + 绕飞行轴翻滚 (不再是永远正对镜头的公告板);</li>
 *   <li><b>插住后</b>: 用 {@link ThrownCoinEntity#getStuckYaw()} 这个"撞击瞬间定格的飞行水平角"来摆朝向,
 *       与相机完全无关; 同时把本来平躺的模型立起来插进土里, 再叠一组由 UUID 决定、永不变的
 *       随机姿态 (倾角 / 侧歪 / 朝向偏转 / 埋深) —— 每枚币角度都不一样, 但谁都不会变。</li>
 * </ul>
 */
public class ThrownCoinRenderer extends EntityRenderer<ThrownCoinEntity> {
    /**
     * 插住时"立起来"的基础角 (度): 90 = 完全竖直。
     * <p>砸在地面上会躺一点 (看起来是插进土里), 钉在墙上会立一点, 再各自叠 {@value #TILT_SPREAD}° 的随机量。
     */
    private static final float FLOOR_TILT = 68.0F;
    private static final float WALL_TILT = 80.0F;
    private static final float TILT_SPREAD = 18.0F;
    /** 左右歪的幅度 (度) */
    private static final float ROLL_SPREAD = 22.0F;
    /** 朝向偏转幅度 (度): 让"插的方向"也不完全等于飞行方向 */
    private static final float YAW_SPREAD = 28.0F;
    /** 插住时的抬高: 小一点让币的下缘埋进土里 */
    private static final float STUCK_LIFT_Y = 0.02F;
    /** 额外埋深 (0~该值), 每枚币不同 */
    private static final float MAX_SINK = 0.035F;
    /** 飞行中的自转速度 (度/tick): 34 ≈ 每秒 1.7 圈 */
    private static final float SPIN_DEGREES_PER_TICK = 34.0F;
    /** 叠加的摇摆幅度 (度): 让翻滚看起来不那么机械 */
    private static final float WOBBLE_DEGREES = 12.0F;
    /** 原版逻辑: 刚扔出去的前 2 tick 如果贴脸就不画, 免得糊在屏幕上 */
    private static final double MIN_CAMERA_DISTANCE_SQR = 12.25;

    private final ItemRenderer itemRenderer;

    public ThrownCoinRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.6F;
    }

    @Override
    public void render(@NotNull ThrownCoinEntity entity, float entityYaw, float partialTicks,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;
        if (entity.tickCount < 2
                && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < MIN_CAMERA_DISTANCE_SQR) {
            return;
        }

        poseStack.pushPose();
        if (entity.isStuck()) {
            // 插住的姿态。顺序很关键: mulPose 是"后调用的先作用在模型上", 所以下面这几行的实际
            // 作用顺序是 立起来 -> (天花板才翻转) -> 侧歪 -> 转朝向。
            // 以前把 YP 写在最后, 等于"先转朝向、再立起来", 立起来这一步会把朝向抹平 ——
            // 结果所有币的朝向都固定是正南, 只剩 ±12° 的倾角差, 看起来就是"插的角度全一样"。
            Direction face = entity.getStuckFace();
            boolean onFloor = face.getAxis() == Direction.Axis.Y;
            float tilt = hashFloat(entity, 1, -TILT_SPREAD, TILT_SPREAD);
            float roll = hashFloat(entity, 2, -ROLL_SPREAD, ROLL_SPREAD);
            float yaw = entity.getStuckYaw() + hashFloat(entity, 3, -YAW_SPREAD, YAW_SPREAD);
            float sink = hashFloat(entity, 4, 0.0F, MAX_SINK);

            poseStack.translate(0.0F, STUCK_LIFT_Y - sink, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
            if (face == Direction.DOWN) {
                poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));   // 吸在天花板上: 反过来朝下
            }
            poseStack.mulPose(Axis.XP.rotationDegrees((onFloor ? FLOOR_TILT : WALL_TILT) + tilt));
        } else {
            // 飞行中: 像被掷出的硬币一样翻滚。
            // 原来是原版公告板 (永远正对镜头): 初速 9 格/tick 的币在屏幕上就是一张"静止的贴图",
            // 完全看不出速度; 现在按飞行方向摆正, 再绕飞行轴自转。
            // 注意 mulPose 是"后调用的先作用在模型上", 所以这里从内到外的顺序是
            // 摇摆 -> 自转 -> 抬俯仰 -> 转水平 (和上面代码的书写顺序相反)。
            Vec3 motion = entity.getDeltaMovement();
            Vec3 dir = motion.lengthSqr() > 1.0E-6
                    ? motion.normalize()
                    : Vec3.directionFromRotation(entity.getXRot(), entity.getYRot());
            float yaw = (float) (Mth.atan2(dir.x, dir.z) * 180.0 / Math.PI);
            float tilt = (float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * 180.0 / Math.PI);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(tilt));
            // 自转相位只跟时间走: 若用"当前速率"当转速, 币一减速相位就会往回跳 (看起来像卡住)。
            // 起始相位由 UUID 决定: 否则每一枚币都是"出手瞬间侧对镜头", 出一根线, 很出戏。
            float phase = initialPhase(entity) + (entity.tickCount + partialTicks) * SPIN_DEGREES_PER_TICK;
            poseStack.mulPose(Axis.ZP.rotationDegrees(phase));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(phase * 0.31F) * WOBBLE_DEGREES));
        }
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * 由 UUID 派生一个稳定的随机量 (同一枚币每次渲染都一样, 不同币不同)。
     * <p>直接用低位取模会让相邻 UUID 的结果很像, 所以先和常数混一下再取。
     */
    private static float hashFloat(ThrownCoinEntity entity, int salt, float min, float max) {
        long bits = entity.getUUID().getLeastSignificantBits();
        bits ^= (long) salt * 0x9E3779B97F4A7C15L;
        bits *= 0xBF58476D1CE4E5B9L;
        bits ^= (bits >>> 29);
        int steps = Math.max(1, Math.round((max - min) * 100.0F));
        return min + (float) Math.floorMod(bits, (long) steps + 1L) / 100.0F;
    }

    /** 飞行自转的起始相位 (0~359°): 每枚币不同, 避免所有币都是同一个角度出手。 */
    private static float initialPhase(ThrownCoinEntity entity) {
        long bits = entity.getUUID().getMostSignificantBits();
        return (float) Math.floorMod(bits, 360L);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ThrownCoinEntity entity) {
        // 币是用方块图集里的贴图 (bs_coin.obj 的 mtl 指向 bscoins:block/bs_coin)
        return InventoryMenu.BLOCK_ATLAS;
    }
}
