package com.beishan.bscoins;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 北山币 - 全功能配置。所有功能均可开关并调整数值。
 *
 * <p>配置按功能分成若干小节 (push/pop), 游戏内 模组列表 → BS Coins → 配置 会显示成
 * 带小标题的分页; 每个选项的标题/说明来自语言文件里的
 * {@code bscoins.configuration.<选项名>} 与 {@code bscoins.configuration.<选项名>.tooltip}
 * (没写 tooltip 时回落到这里的 comment)。
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── 合成部件开关 ──────────────────────────────
    // 这五个开关同时是配方条件 (bscoins:config), 关闭后对应配方不会加载,
    // 并且对应物品的功能也会停用。改完需要 /reload 或重启才生效。
    static {
        BUILDER.comment("合成部件与总开关: 关掉后对应配方不加载, 物品功能也停用 (需要 /reload 或重启)");
        BUILDER.push("parts");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_LIKE = BUILDER.comment("启用 点赞 (红石+纸) 合成部件与它的点赞增益").define("enableLike", true);
    public static final ModConfigSpec.BooleanValue ENABLE_TOSS = BUILDER.comment("启用 投币 (绿宝石+铁锭) 合成部件与它的掷硬币").define("enableToss", true);
    public static final ModConfigSpec.BooleanValue ENABLE_FAVORITE = BUILDER.comment("启用 收藏 (书与笔+末影珍珠) 合成部件与它的坐标收藏夹").define("enableFavorite", true);
    public static final ModConfigSpec.BooleanValue ENABLE_BS_COIN = BUILDER.comment("启用 北山币 (三者无序合成, 堆叠64) 以及它的所有右键交互").define("enableBsCoin", true);
    public static final ModConfigSpec.BooleanValue ENABLE_DEV_COIN = BUILDER.comment("启用 开发版北山币 (北山币+下界之星) 及其特制属性: 永不烧毁/射程无限").define("enableDevCoin", true);

    static {
        BUILDER.pop();
    }

    // ── 三连部件功能 ──────────────────────────────
    static {
        BUILDER.comment("三连部件自身的数值 (点赞 / 投币 / 收藏 / 镐)");
        BUILDER.push("trio");
    }

    public static final ModConfigSpec.IntValue LIKE_RADIUS = BUILDER.comment("点赞作用半径 (格)").defineInRange("likeRadius", 8, 1, 64);
    public static final ModConfigSpec.IntValue LIKE_BUFF_SECONDS = BUILDER.comment("点赞给予的鸡血 Buff 持续 (秒)").defineInRange("likeBuffSeconds", 8, 1, 600);
    public static final ModConfigSpec.IntValue LIKE_COOLDOWN_SECONDS = BUILDER.comment("点赞冷却 (秒)").defineInRange("likeCooldownSeconds", 20, 1, 600);
    public static final ModConfigSpec.IntValue TOSS_LUCK_SECONDS = BUILDER.comment("投币掷出正面/反面的幸运/霉运持续 (秒)").defineInRange("tossLuckSeconds", 60, 1, 3600);
    public static final ModConfigSpec.IntValue TOSS_COOLDOWN_SECONDS = BUILDER.comment("投币冷却 (秒)").defineInRange("tossCooldownSeconds", 2, 1, 600);
    public static final ModConfigSpec.IntValue FAVORITE_MAX = BUILDER.comment("收藏夹最多保存的坐标数量").defineInRange("favoriteMax", 16, 1, 128);
    public static final ModConfigSpec.BooleanValue ENABLE_PICKAXE_AREA_MINE = BUILDER.comment("基建狂魔镐 3x3 范围挖掘 (潜行时恢复单格)").define("enablePickaxeAreaMine", true);

    static {
        BUILDER.pop();
    }

    // ── 手持粒子 ──────────────────────────────
    static {
        BUILDER.comment("手持北山币时的环绕粒子");
        BUILDER.push("particles");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_HELD_PARTICLES = BUILDER.comment("手持北山币时脚边环绕三色粒子 (嫌挡视野可以整个关掉)").define("enableHeldParticles", true);
    public static final ModConfigSpec.IntValue PARTICLE_COUNT = BUILDER.comment("环绕粒子数量 (构成一个环); 挡视野就调小").defineInRange("particleCount", 6, 1, 60);

    static {
        BUILDER.pop();
    }

    // ── 右键交互 ──────────────────────────────
    static {
        BUILDER.comment("北山币右键的三种对象: 玩家打赏 / 怪物眩晕内讧 / 村民折扣");
        BUILDER.push("interact");
    }

    // 玩家
    public static final ModConfigSpec.BooleanValue ENABLE_TIP = BUILDER.comment("右键玩家打赏 Buff (鸡血+老板)").define("enableTip", true);
    public static final ModConfigSpec.IntValue GOAD_LEVEL = BUILDER.comment("鸡血 Buff 等级 (0=1级)").defineInRange("goadLevel", 1, 0, 255);
    public static final ModConfigSpec.IntValue BOSS_LEVEL = BUILDER.comment("老板 Buff 等级 (0=1级)").defineInRange("bossLevel", 0, 0, 255);
    public static final ModConfigSpec.IntValue TIP_DURATION_SECONDS = BUILDER.comment("打赏 Buff 持续时间 (秒)").defineInRange("tipDurationSeconds", 30, 1, 3600);
    public static final ModConfigSpec.IntValue TIP_CONSUME = BUILDER.comment("打赏消耗北山币数量").defineInRange("tipConsume", 1, 0, 64);

    // 怪物
    public static final ModConfigSpec.BooleanValue ENABLE_MONSTER_ATTACK = BUILDER.comment("右键怪物致眩晕或内讧").define("enableMonsterAttack", true);
    public static final ModConfigSpec.DoubleValue STUN_CHANCE = BUILDER.comment("眩晕概率 (0~1, 其余为内讧)").defineInRange("stunChance", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.IntValue STUN_DURATION_SECONDS = BUILDER.comment("眩晕持续 (秒)").defineInRange("stunDurationSeconds", 5, 1, 600);
    public static final ModConfigSpec.IntValue INFIGHT_RADIUS = BUILDER.comment("内讧触发距离 (格)").defineInRange("infightRadius", 16, 1, 64);
    public static final ModConfigSpec.IntValue MONSTER_CONSUME = BUILDER.comment("右键怪物消耗北山币数量").defineInRange("monsterConsume", 1, 0, 64);

    // 村民
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_DISCOUNT = BUILDER.comment("右键村民永久8折交易").define("enableVillagerDiscount", true);
    public static final ModConfigSpec.IntValue VILLAGER_DISCOUNT_PERCENT = BUILDER.comment("折扣百分比 (20 = 8折)").defineInRange("villagerDiscountPercent", 20, 1, 90);
    public static final ModConfigSpec.IntValue VILLAGER_CONSUME = BUILDER.comment("右键村民消耗北山币数量").defineInRange("villagerConsume", 1, 0, 64);

    static {
        BUILDER.pop();
    }

    // ── 北山团建圈 ──────────────────────────────
    static {
        BUILDER.comment("北山团建圈: 长按右键生成的增益圈");
        BUILDER.push("circle");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_TEAM_CIRCLE = BUILDER.comment("长按右键3秒生成北山团建圈").define("enableTeamCircle", true);
    public static final ModConfigSpec.IntValue CIRCLE_HOLD_MS = BUILDER.comment("长按毫秒数, 默认3000ms=3秒").defineInRange("circleHoldMs", 3000, 500, 20000);
    public static final ModConfigSpec.IntValue CIRCLE_DURATION_SECONDS = BUILDER.comment("团建圈持续 (秒), 默认10分钟=600").defineInRange("circleDurationSeconds", 600, 10, 36000);
    public static final ModConfigSpec.IntValue CIRCLE_RADIUS = BUILDER.comment("团建圈半径 (格)").defineInRange("circleRadius", 10, 2, 64);
    public static final ModConfigSpec.BooleanValue CIRCLE_CROP_BOOST = BUILDER.comment("团建圈作物加速").define("circleCropBoost", true);
    public static final ModConfigSpec.BooleanValue CIRCLE_XP_BOOST = BUILDER.comment("团建圈额外经验球增益").define("circleXpBoost", true);
    public static final ModConfigSpec.IntValue CIRCLE_CONSUME = BUILDER.comment("召唤团建圈消耗北山币数量").defineInRange("circleConsume", 1, 0, 64);

    static {
        BUILDER.pop();
    }

    // ── Q键弹射投币 ──────────────────────────────
    static {
        BUILDER.comment("弹射投币 (Q / V) 的物理与手感");
        BUILDER.push("throw");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_THROW = BUILDER.comment("Q键弹射投币").define("enableThrow", true);
    public static final ModConfigSpec.DoubleValue THROW_SPEED = BUILDER.comment("投掷初速度 (格/tick), 只是出手瞬间的速度; 原版雪球是1.5, 箭是3.0。9.0 = 平抛约65格落地 (飞行约0.4秒)").defineInRange("throwSpeed", 9.0, 0.5, 16.0);
    public static final ModConfigSpec.DoubleValue THROW_DRAG = BUILDER.comment("空气阻力: 每 tick 速度乘以该系数。0.97 末端下坠速度约1.8格/tick (看得见弧线); 调小=减速更猛、末端更飘 (0.94 时末端只剩0.63格/tick, 高抛会像羽毛一样飘十几秒)").defineInRange("throwDrag", 0.97, 0.5, 1.0);
    public static final ModConfigSpec.DoubleValue THROW_GRAVITY = BUILDER.comment("投掷物下坠重力 (格/tick²): 原版雪球0.03, 箭0.05。0.055 = 平抛约65格落地, 末端下坠约1.8格/tick; 调大=落地更快、射程更短").defineInRange("throwGravity", 0.055, 0.0, 0.2);
    public static final ModConfigSpec.DoubleValue THROW_DAMAGE = BUILDER.comment("满初速(贴脸)时的命中伤害; 实际伤害 = 该值 × (命中瞬间速率 / 初速), 所以飞得越远越轻 (玩家只吃Buff不吃伤害)").defineInRange("throwDamage", 12.0, 0.0, 200.0);
    public static final ModConfigSpec.DoubleValue THROW_DAMAGE_MAX = BUILDER.comment("伤害安全上限: 命中速率不可能超过初速, 所以实际能打出的最高伤害就是贴脸那一下 (throwDamage); 这一项只是防止改配置后算出离谱数值").defineInRange("throwDamageMax", 48.0, 1.0, 400.0);
    public static final ModConfigSpec.IntValue THROW_BURN_DISTANCE = BUILDER.comment("普通北山币的水平射程超过该距离(格)会烧毁 (仰角抛射会更快撞上限); 开发版北山币免疫。初速9 / 重力0.055 时平抛约65格落地, 130 用来卡住高抛").defineInRange("throwBurnDistance", 130, 5, 512);
    public static final ModConfigSpec.DoubleValue THROW_PICKUP_RADIUS = BUILDER.comment("币插在地上后, 走到多少格以内才会被拾取 (像箭一样要走近)").defineInRange("throwPickupRadius", 1.5, 0.5, 32.0);
    public static final ModConfigSpec.BooleanValue THROW_PICKUP_OWNER_ONLY = BUILDER.comment("只有投掷者本人能拾取地上的币 (关掉则任何人走近都能捡)").define("throwPickupOwnerOnly", true);
    public static final ModConfigSpec.IntValue THROW_MAX_LIFETIME_SECONDS = BUILDER.comment("投掷物的兜底存活时间(秒): 普通币烧毁, 开发版币落地成掉落物").defineInRange("throwMaxLifetimeSeconds", 120, 5, 3600);
    public static final ModConfigSpec.IntValue THROW_CONSUME = BUILDER.comment("投币消耗北山币数量").defineInRange("throwConsume", 1, 0, 64);
    public static final ModConfigSpec.IntValue THROW_COOLDOWN_TICKS = BUILDER.comment("投掷冷却 (tick, 20 = 1秒): 就是物品栏上那圈白色冷却动画; 0 = 可以连发").defineInRange("throwCooldownTicks", 8, 0, 40);
    public static final ModConfigSpec.BooleanValue THROW_TRAIL = BUILDER.comment("投掷拖尾与标记粒子: 飞行时按速率铺一串电火花 (开发版币是白色微光), 插在地上后每2秒冒一颗方便找回 (关掉就只剩币本体)").define("throwTrail", true);

    static {
        BUILDER.pop();
    }

    // ── 开发助手 / 测试功能 ──────────────────────────────
    static {
        BUILDER.comment("调试用: 开发助手 GUI / 一键测试场地 / 测试假人");
        BUILDER.push("dev");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_DEV_ASSISTANT = BUILDER.comment("潜行右键空气/按G 消耗1币开启北山开发助手 GUI").define("enableDevAssistant", true);
    public static final ModConfigSpec.BooleanValue ENABLE_TEST_PLATFORM = BUILDER.comment("潜行Q一键搭场地时, 在脚下一层铺 platformSize x platformSize 的测试平台 (和假人共用 1 枚币)").define("enableTestPlatform", true);
    public static final ModConfigSpec.IntValue PLATFORM_SIZE = BUILDER.comment("测试平台边长").defineInRange("platformSize", 20, 4, 64);
    public static final ModConfigSpec.BooleanValue ENABLE_TEST_DUMMY = BUILDER.comment("潜行Q一键搭场地时, 在面前召唤测试假人(带血条/伤害数字); 和平台共用 1 枚币").define("enableTestDummy", true);
    public static final ModConfigSpec.DoubleValue DUMMY_MAX_HEALTH = BUILDER.comment("测试假人最大生命").defineInRange("dummyMaxHealth", 100.0, 4.0, 2048.0);
    public static final ModConfigSpec.IntValue DUMMY_LIFETIME_SECONDS = BUILDER.comment("测试假人存活时间(秒), 到点自动消失").defineInRange("dummyLifetimeSeconds", 60, 5, 3600);
    public static final ModConfigSpec.BooleanValue ENABLE_RELOAD = BUILDER.comment("开发版北山币潜行Q重载数据包: 战利品表/配方/标签/进度改完立刻生效, 消耗1枚币, 不需要管理员权限(重载失败不扣币)").define("enableReload", true);
    public static final ModConfigSpec.IntValue ASSISTANT_CONSUME = BUILDER.comment("开启开发助手消耗北山币数量").defineInRange("assistantConsume", 1, 0, 64);

    static {
        BUILDER.pop();
    }

    // ── 催更律师函惩罚 ──────────────────────────────
    static {
        BUILDER.comment("催更律师函: 连续多天没用北山币的惩罚");
        BUILDER.push("pressure");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_PRESSURE = BUILDER.comment("催更律师函惩罚 (连续5个MC天未使用/合成北山币)").define("enablePressure", true);
    public static final ModConfigSpec.IntValue PRESSURE_DAYS = BUILDER.comment("触发催更所需的连续未使用天数 (MC天)").defineInRange("pressureDays", 5, 1, 100);
    public static final ModConfigSpec.DoubleValue PRESSURE_SLOW_PERCENT = BUILDER.comment("催更减速 (百分比)").defineInRange("pressureSlowPercent", 5.0, 0.0, 90.0);
    public static final ModConfigSpec.IntValue PRESSURE_PIGEON_TICKS = BUILDER.comment("头顶鸽子粒子间隔 tick").defineInRange("pressurePigeonTicks", 30, 5, 200);

    static {
        BUILDER.pop();
    }

    // ── 北山小卖部 (替换流浪商人) ──────────────────────────────
    static {
        BUILDER.comment("北山小卖部: 替换流浪商人, 卖道具收绿宝石");
        BUILDER.push("shop");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_SHOP = BUILDER.comment("北山小卖部替换流浪商人").define("enableShop", true);
    public static final ModConfigSpec.IntValue SHOP_PRICE_MEGAPHONE = BUILDER.comment("催更大喇叭价格 (绿宝石)").defineInRange("shopPriceMegaphone", 16, 1, 512);
    public static final ModConfigSpec.IntValue SHOP_PRICE_PIGEON_TRAP = BUILDER.comment("鸽子诱捕器价格 (绿宝石)").defineInRange("shopPricePigeonTrap", 12, 1, 512);
    public static final ModConfigSpec.IntValue SHOP_PRICE_PICKAXE = BUILDER.comment("基建狂魔镐价格 (绿宝石)").defineInRange("shopPricePickaxe", 32, 1, 512);
    public static final ModConfigSpec.IntValue SHOP_PRICE_PHOTO = BUILDER.comment("北山签名照价格 (绿宝石)").defineInRange("shopPricePhoto", 8, 1, 512);
    public static final ModConfigSpec.IntValue SHOP_PRICE_NBT_VIEWER = BUILDER.comment("NBT查看器价格 (绿宝石)").defineInRange("shopPriceNbtViewer", 20, 1, 512);
    public static final ModConfigSpec.IntValue SHOP_PRICE_AIRDROP = BUILDER.comment("结构空投价格 (绿宝石)").defineInRange("shopPriceAirdrop", 24, 1, 512);

    static {
        BUILDER.pop();
    }

    // ── 催更大喇叭 / 鸽子诱捕器 ──────────────────────────────
    static {
        BUILDER.comment("催更大喇叭的扇形喊话与鸽子诱捕器");
        BUILDER.push("megaphone");
    }

    public static final ModConfigSpec.IntValue MEGAPHONE_RADIUS = BUILDER.comment("催更大喇叭喊话半径 (格): 视线方向扇形内、且在这个半径内的玩家都会收到催更消息 + 鸡血 Buff").defineInRange("megaphoneRadius", 32, 4, 128);
    public static final ModConfigSpec.IntValue MEGAPHONE_ANGLE_DEGREES = BUILDER.comment("催更大喇叭的扇形张开角度 (度, 左右各一半): 默认90 = 正前方±45°").defineInRange("megaphoneAngleDegrees", 90, 10, 360);
    public static final ModConfigSpec.IntValue MEGAPHONE_BUFF_SECONDS = BUILDER.comment("大喇叭给半径内玩家加的鸡血持续 (秒)").defineInRange("megaphoneBuffSeconds", 20, 1, 600);
    public static final ModConfigSpec.IntValue MEGAPHONE_COOLDOWN_SECONDS = BUILDER.comment("催更大喇叭冷却 (秒), 防止刷屏").defineInRange("megaphoneCooldownSeconds", 5, 0, 600);
    public static final ModConfigSpec.IntValue PIGEON_TRAP_COUNT = BUILDER.comment("鸽子诱捕器一次放出几只鸽子 (活的鹦鹉实体, 会自己飞走)").defineInRange("pigeonTrapCount", 5, 1, 16);

    static {
        BUILDER.pop();
    }

    // ── 结构空投 ──────────────────────────────
    static {
        BUILDER.comment("结构空投: 箱子从高空缓降");
        BUILDER.push("airdrop");
    }

    public static final ModConfigSpec.IntValue AIRDROP_DROP_HEIGHT = BUILDER.comment("空投箱从多高的地方开始往下掉 (格)").defineInRange("airdropDropHeight", 40, 5, 256);
    public static final ModConfigSpec.DoubleValue AIRDROP_FALL_SPEED = BUILDER.comment("空投箱下降速度 (格/tick), 0.15 ≈ 3格/秒; 越大掉得越快").defineInRange("airdropFallSpeed", 0.15, 0.05, 1.0);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static final String KEY_ENABLE_LIKE = "enableLike";
    public static final String KEY_ENABLE_TOSS = "enableToss";
    public static final String KEY_ENABLE_FAVORITE = "enableFavorite";
    public static final String KEY_ENABLE_BS_COIN = "enableBsCoin";
    public static final String KEY_ENABLE_DEV_COIN = "enableDevCoin";

    /**
     * 供配方条件 (bscoins:config) 使用: 把配置键名翻译成开关值。
     * 未知键一律返回 true (不拦配方), 并打一条警告方便排查。
     */
    public static boolean recipeSwitch(String key) {
        return switch (key) {
            case KEY_ENABLE_LIKE -> ENABLE_LIKE.get();
            case KEY_ENABLE_TOSS -> ENABLE_TOSS.get();
            case KEY_ENABLE_FAVORITE -> ENABLE_FAVORITE.get();
            case KEY_ENABLE_BS_COIN -> ENABLE_BS_COIN.get();
            case KEY_ENABLE_DEV_COIN -> ENABLE_DEV_COIN.get();
            default -> {
                BSCoins.LOGGER.warn("未知的配方条件配置键: {} (已按 true 处理)", key);
                yield true;
            }
        };
    }
}
