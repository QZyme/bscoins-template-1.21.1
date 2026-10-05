package com.beishan.bscoins;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.beishan.bscoins.client.AirdropCrateRenderer;
import com.beishan.bscoins.client.ClientEvents;
import com.beishan.bscoins.client.DevAssistantScreen;
import com.beishan.bscoins.client.ThrownCoinRenderer;
import com.beishan.bscoins.network.ModNetwork;
import com.beishan.bscoins.network.ModPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(value = BSCoins.MODID, dist = Dist.CLIENT)
public class BSCoinsClient {
    public static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(BSCoins.MODID + "-client");

    /** 测试假人模型层 key. */
    public static final net.minecraft.client.model.geom.ModelLayerLocation TEST_DUMMY_LAYER =
            new net.minecraft.client.model.geom.ModelLayerLocation(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BSCoins.MODID, "test_dummy"), "main");

    public BSCoinsClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerClientPayloads);
        modEventBus.addListener(this::registerKeyBindings);
        modEventBus.addListener(this::registerEntityRenderers);
        modEventBus.addListener(this::registerLayerDefinitions);
        // 配置界面
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
        // 客户端 tick / 输入事件
        NeoForge.EVENT_BUS.register(new ClientEvents());
    }

    private void registerLayerDefinitions(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TEST_DUMMY_LAYER,
                () -> net.minecraft.client.model.geom.builders.LayerDefinition.create(
                        net.minecraft.client.model.PlayerModel.createMesh(
                                net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false), 64, 64));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("北山币客户端就绪");
    }

    private void registerKeyBindings(RegisterKeyMappingsEvent event) {
        ClientEvents.registerKeys(event);
    }

    private void registerClientPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetwork.CHANNEL_VERSION);
        registrar.playToClient(ModPayloads.OpenAssistantResponsePayload.TYPE,
                ModPayloads.OpenAssistantResponsePayload.STREAM_CODEC,
                this::handleOpenAssistant);
    }

    private void handleOpenAssistant(ModPayloads.OpenAssistantResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new DevAssistantScreen());
        });
    }

    /**
     * 为自定义实体注册客户端渲染器, 否则实体一生成就 NPE 崩溃。
     */
    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Q键投掷币: 飞行中是公告板, 插在地上后换成固定朝向 (见 ThrownCoinRenderer)
        event.registerEntityRenderer(BSCoins.THROWN_COIN_TYPE.get(), ThrownCoinRenderer::new);
        // 团建圈: 不可见区域
        event.registerEntityRenderer(BSCoins.TEAM_CIRCLE_TYPE.get(), NoopRenderer::new);
        // 测试假人: 绘制成人形怪物渲染 (继承 LivingEntityRenderer 的通用实现)
        event.registerEntityRenderer(BSCoins.TEST_DUMMY_TYPE.get(),
                ctx -> new TestDummyRenderer(ctx, 0.5F));
        // 空投箱: 画真正的箱子模型 (原版箱子没有方块模型, 所以以前天上看不见东西, 见 AirdropCrateRenderer)
        event.registerEntityRenderer(BSCoins.AIRDROP_CRATE_TYPE.get(), AirdropCrateRenderer::new);
    }

    /** 测试假人的人形渲染器: 用玩家模型 + 村民皮肤近似. */
    public static class TestDummyRenderer extends net.minecraft.client.renderer.entity.LivingEntityRenderer<
            com.beishan.bscoins.entity.TestDummyEntity,
            net.minecraft.client.model.PlayerModel<com.beishan.bscoins.entity.TestDummyEntity>> {

        public TestDummyRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context ctx, float shadow) {
            super(ctx, new net.minecraft.client.model.PlayerModel<>(
                            ctx.bakeLayer(TEST_DUMMY_LAYER), false),
                    shadow);
        }

        @Override
        public net.minecraft.resources.@NotNull ResourceLocation getTextureLocation(com.beishan.bscoins.entity.@NotNull TestDummyEntity entity) {
            // 必须是"玩家模型 UV 布局"的贴图。以前借用村民贴图, 而村民贴图的胳膊/腿画在别的
            // UV 位置上, 玩家模型采样到的区域是透明的 -> 假人只剩一个头 (四肢不全)。
            return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BSCoins.MODID,
                    "textures/entity/test_dummy.png");
        }

        @Override
        protected void scale(com.beishan.bscoins.entity.@NotNull TestDummyEntity entity,
                             com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick) {
            poseStack.scale(1.0F, 1.0F, 1.0F);
        }
    }
}