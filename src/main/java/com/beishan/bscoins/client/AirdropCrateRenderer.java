package com.beishan.bscoins.client;

import com.beishan.bscoins.entity.AirdropCrateEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

/**
 * 空投箱渲染器 —— 天上掉下来的就是一个<b>真正的箱子</b>。
 *
 * <p>踩过的坑:
 * <ul>
 *   <li>最早空投用的是装着箱子方块的 {@code FallingBlockEntity}: 原版箱子没有方块模型
 *       ({@code models/block/chest.json} 里只有 {@code textures.particle}), 箱子一直是靠方块实体渲染器画的,
 *       所以 {@code FallingBlockRenderer} 什么都画不出来, 天上只有粒子;</li>
 *   <li>后来改成渲染"结构空投"那个道具 —— 那是**手持空投装置**的模型, 不是箱子 (用户: 我要的是箱子的模型)。</li>
 * </ul>
 *
 * <p>现在直接调用原版 {@link BlockEntityWithoutLevelRenderer}: 箱子物品的模型是 {@code builtin/entity},
 * 它会拿一个虚拟的 {@code ChestBlockEntity} 交给 {@code ChestRenderer} 画出真正的箱子。
 *
 * <p>为什么不走 {@code ItemRenderer#renderStatic}: 那会把道具的 {@code display.ground} 变换也应用上
 * (箱子物品在 ground 下是 0.25 倍), 掉出来会变成四分之一大小的小箱子。这里自己控制 pose,
 * 箱子模型占满 1×1×1、<b>底面就在 pose 原点</b> (见 {@code ChestRenderer} 的 ±0.5 居中与
 * {@code addBox(0,0,1,15,10,14)} 的模型盒子), 而实体的 {@code getY()} 是脚底 ——
 * 于是落地时箱子正好坐在地上, 和紧接着放在同一格的箱子方块严丝合缝 (1 格大小, 不悬空、不陷地)。
 * 注意模型坐标是以方块<b>角</b>为原点的, 所以最内层还要 {@code translate(-0.5, -PIVOT_Y, -0.5)} 把它挪正。
 *
 * <p>姿态顺序 (mulPose 后调用的先作用在模型上): 摇摆(世界空间, 最外) → 自转 → 缩放 → 挪半格(最内)。
 * 摇摆写在最外层才能保证摆轴固定在世界里, 是"左右轻摆"而不是"被自转拖着倒向一边"。
 */
public class AirdropCrateRenderer extends EntityRenderer<AirdropCrateEntity> {
    /** 1.0 = 和落地后那个箱子方块一样大 */
    private static final float CRATE_SCALE = 1.0F;
    /** 自转速度 (度/tick): 0.9 ≈ 每 20 秒一圈 (很慢, 只是让它不像贴图) */
    private static final float SPIN_PER_TICK = 0.9F;
    /**
     * 摇摆幅度 (度): 在世界空间里绕 X / Z 各摆一点, 箱子顶端画出一个小圆 ——
     * 像吊在降落伞下轻轻晃, 从任何角度看都是在动, 不会"定在某个方向偏着"。
     */
    private static final float SWAY_DEGREES = 6.5F;
    /** 摇摆角速度 (rad/tick): 0.125 ≈ 2.5 秒一个来回 */
    private static final float SWAY_SPEED = 0.125F;
    /**
     * 旋转支点的高度 (格, 从箱子底面往上算)。
     * <p>箱子模型的坐标是以方块<b>角</b>为原点的 (模型盒子 x/z 中心在 0.5), 而实体原点是脚底正中;
     * 所以必须先把模型挪半个格子, 否则箱子整体偏 (+0.5, +0.5), 而且自转/摇摆会绕"方块角"转 ——
     * 箱子会绕着看不见的点画一个直径 1.4 格的圈。
     * <p>支点取在箱子顶上方 ({@value #PIVOT_Y} 格 ≈ 粒子云的位置), 摇摆起来才像吊着晃;
     * 支点只影响旋转, 箱子的位置/底面不受影响 (零旋转时底面仍在实体脚底)。
     */
    private static final float PIVOT_Y = 0.9F;

    /** 只读的展示用物品: 在构造函数里建一次, 免得每帧 new 一个 ItemStack。 */
    private final ItemStack crateStack = new ItemStack(Items.CHEST);
    /** 原版箱子渲染器 (箱子物品的 builtin/entity 模型就是靠它画的) */
    private final BlockEntityWithoutLevelRenderer crateRenderer;

    public AirdropCrateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.crateRenderer = context.getItemRenderer().getBlockEntityRenderer();
        this.shadowRadius = 0.55F;
        this.shadowStrength = 0.75F;
    }

    @Override
    public void render(@NotNull AirdropCrateEntity entity, float entityYaw, float partialTicks,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        float t = entity.tickCount + partialTicks;

        poseStack.pushPose();
        // 不抬高: 箱子的底面要正好落在实体脚底 (= 着地后的地面)。
        //
        // 变换链 (mulPose/translate 都是"后调用的先作用在模型上", 所以下面的书写顺序 = 从外到内):
        //   1. 支点挪回  2. 摇摆  3. 自转  4. 缩放  5. 支点挪到原点  6. 方块角 -> 箱子中心
        // 其中 5+6 是最内层: 先把"方块角原点"的模型挪成"以箱子中心/支点为原点", 旋转才是绕箱子自己转,
        // 而不是绕方块角转 (那会让箱子绕着看不见的点画一个直径 1.4 格的圈); 1 是配对的回程平移,
        // 保证零旋转时箱子位置分毫不动 (底面仍在脚底)。
        poseStack.translate(0.0F, PIVOT_Y, 0.0F);
        float swayZ = Mth.sin(t * SWAY_SPEED) * SWAY_DEGREES;   // 左右
        float swayX = Mth.cos(t * SWAY_SPEED) * SWAY_DEGREES;   // 前后 (合起来是个小圆)
        poseStack.mulPose(Axis.ZP.rotationDegrees(swayZ));
        poseStack.mulPose(Axis.XP.rotationDegrees(swayX));
        poseStack.mulPose(Axis.YP.rotationDegrees(initialYaw(entity) + t * SPIN_PER_TICK));
        poseStack.scale(CRATE_SCALE, CRATE_SCALE, CRATE_SCALE);
        poseStack.translate(0.0F, -PIVOT_Y, 0.0F);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        this.crateRenderer.renderByItem(this.crateStack, ItemDisplayContext.NONE, poseStack, buffer,
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** 每只箱子的起始朝向不同, 免得同时投放的两个箱子同步旋转。 */
    private static float initialYaw(AirdropCrateEntity entity) {
        return (float) Math.floorMod(entity.getUUID().getLeastSignificantBits(), 360L);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull AirdropCrateEntity entity) {
        // 箱子贴图在方块图集里 (原版 ChestRenderer 用的是 Sheets 里的箱子材质)
        return InventoryMenu.BLOCK_ATLAS;
    }
}
