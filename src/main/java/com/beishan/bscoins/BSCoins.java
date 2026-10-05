package com.beishan.bscoins;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.beishan.bscoins.data.BsConditions;
import com.beishan.bscoins.effect.BossEffect;
import com.beishan.bscoins.effect.GoadEffect;
import com.beishan.bscoins.effect.StunEffect;
import com.beishan.bscoins.entity.AirdropCrateEntity;
import com.beishan.bscoins.entity.TeamCircleEntity;
import com.beishan.bscoins.entity.TestDummyEntity;
import com.beishan.bscoins.entity.ThrownCoinEntity;
import com.beishan.bscoins.handler.DamageNumberHandler;
import com.beishan.bscoins.handler.PressureHandler;
import com.beishan.bscoins.handler.ShopHandler;
import com.beishan.bscoins.handler.VillagerDiscountHandler;
import com.beishan.bscoins.item.BeishanPhotoItem;
import com.beishan.bscoins.item.BSCoinItem;
import com.beishan.bscoins.item.CoinTossItem;
import com.beishan.bscoins.item.DevBSCoinItem;
import com.beishan.bscoins.item.FavoriteItem;
import com.beishan.bscoins.item.InfraPickaxeItem;
import com.beishan.bscoins.item.LikeItem;
import com.beishan.bscoins.item.NagMegaphoneItem;
import com.beishan.bscoins.item.NbtViewerItem;
import com.beishan.bscoins.item.PigeonTrapItem;
import com.beishan.bscoins.item.PressureLetterItem;
import com.beishan.bscoins.item.StructureAirdropItem;
import com.beishan.bscoins.network.ModNetwork;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BSCoins.MODID)
public class BSCoins {
    public static final String MODID = "bscoins";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ── 合成部件 ────────────────────────────────────────────────
    public static final DeferredHolder<Item, LikeItem> LIKE = ITEMS.register("like", LikeItem::new);
    public static final DeferredHolder<Item, CoinTossItem> COIN_TOSS = ITEMS.register("coin_toss", CoinTossItem::new);
    public static final DeferredHolder<Item, FavoriteItem> FAVORITE = ITEMS.register("favorite", FavoriteItem::new);

    // ── 北山币 ────────────────────────────────────────────────
    public static final DeferredHolder<Item, BSCoinItem> BS_COIN = ITEMS.register("bs_coin", BSCoinItem::new);
    public static final DeferredHolder<Item, DevBSCoinItem> DEV_BS_COIN = ITEMS.register("dev_bs_coin", DevBSCoinItem::new);

    // ── 北山小卖部商品 ──────────────────────────────────────────
    public static final DeferredHolder<Item, NagMegaphoneItem> NAG_MEGAPHONE = ITEMS.register("nag_megaphone", NagMegaphoneItem::new);
    public static final DeferredHolder<Item, PigeonTrapItem> PIGEON_TRAP = ITEMS.register("pigeon_trap", PigeonTrapItem::new);
    public static final DeferredHolder<Item, InfraPickaxeItem> INFRA_PICKAXE = ITEMS.register("infra_pickaxe", InfraPickaxeItem::new);
    public static final DeferredHolder<Item, BeishanPhotoItem> BEISHAN_PHOTO = ITEMS.register("beishan_photo", BeishanPhotoItem::new);
    public static final DeferredHolder<Item, NbtViewerItem> NBT_VIEWER = ITEMS.register("nbt_viewer", NbtViewerItem::new);
    public static final DeferredHolder<Item, StructureAirdropItem> STRUCTURE_AIRDROP = ITEMS.register("structure_airdrop", StructureAirdropItem::new);

    // ── 惩罚道具 ────────────────────────────────────────────────
    public static final DeferredHolder<Item, PressureLetterItem> PRESSURE_LETTER = ITEMS.register("pressure_letter", PressureLetterItem::new);

    // ── 效果 ────────────────────────────────────────────────────
    public static final DeferredHolder<MobEffect, GoadEffect> GOAD = MOB_EFFECTS.register("goad", GoadEffect::new);
    public static final DeferredHolder<MobEffect, BossEffect> BOSS = MOB_EFFECTS.register("boss", BossEffect::new);
    public static final DeferredHolder<MobEffect, StunEffect> STUN = MOB_EFFECTS.register("stun", StunEffect::new);

    // ── 实体 ────────────────────────────────────────────────────
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownCoinEntity>> THROWN_COIN_TYPE =
            ENTITY_TYPES.register("thrown_coin", () -> EntityType.Builder.<ThrownCoinEntity>of(
                            ThrownCoinEntity::new, MobCategory.MISC)
                    // updateInterval=1: 每 tick 同步一次位置。默认的 10 会让客户端的飞行轨迹一格一格跳,
                    // 看起来又慢又不准; 投币速度很快, 必须每 tick 同步才顺。
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1)
                    .build(id("thrown_coin").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<TeamCircleEntity>> TEAM_CIRCLE_TYPE =
            ENTITY_TYPES.register("team_circle", () -> EntityType.Builder.of(
                            TeamCircleEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1)
                    .build(id("team_circle").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<TestDummyEntity>> TEST_DUMMY_TYPE =
            ENTITY_TYPES.register("test_dummy", () -> EntityType.Builder.of(
                            TestDummyEntity::new, MobCategory.MISC)
                    .sized(0.9F, 2.0F).clientTrackingRange(10)
                    .build(id("test_dummy").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<AirdropCrateEntity>> AIRDROP_CRATE_TYPE =
            ENTITY_TYPES.register("airdrop_crate", () -> EntityType.Builder.<AirdropCrateEntity>of(
                            AirdropCrateEntity::new, MobCategory.MISC)
                    // updateInterval=1: 缓降也要每 tick 同步, 否则客户端看到的箱子是一格一格往下跳
                    .sized(0.9F, 0.9F).clientTrackingRange(12).updateInterval(1)
                    .build(id("airdrop_crate").toString()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BSCoins_TAB = CREATIVE_MODE_TABS.register("bscoins_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bscoins"))
                    .icon(() -> new ItemStack(BS_COIN.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(LIKE.get());
                        output.accept(COIN_TOSS.get());
                        output.accept(FAVORITE.get());
                        output.accept(BS_COIN.get());
                        output.accept(DEV_BS_COIN.get());
                        output.accept(NAG_MEGAPHONE.get());
                        output.accept(PIGEON_TRAP.get());
                        output.accept(INFRA_PICKAXE.get());
                        output.accept(BEISHAN_PHOTO.get());
                        output.accept(NBT_VIEWER.get());
                        output.accept(STRUCTURE_AIRDROP.get());
                    }).build());

    public BSCoins(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BsConditions.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAttributes);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        ModNetwork.register(modEventBus);
        NeoForge.EVENT_BUS.register(new PressureHandler());
        NeoForge.EVENT_BUS.register(new ShopHandler());
        NeoForge.EVENT_BUS.register(new VillagerDiscountHandler());
        NeoForge.EVENT_BUS.register(new DamageNumberHandler());
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TEST_DUMMY_TYPE.get(), TestDummyEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("北山币 mod loaded! 三连支持成就已就绪");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
