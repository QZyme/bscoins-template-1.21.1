package com.beishan.bscoins.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.beishan.bscoins.BSCoins;
import org.jetbrains.annotations.NotNull;

/**
 * 网络载荷定义。所有 id 统一走 BSCoins.id(...), 避免到处手写命名空间字符串。
 */
public final class ModPayloads {
    private ModPayloads() {
    }

    // ── C2S: Q键/V键 弹射投币 ─────────────────────────────────────
    public record ThrowCoinPayload() implements CustomPacketPayload {
        public static final Type<ThrowCoinPayload> TYPE = new Type<>(BSCoins.id("throw_coin"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ThrowCoinPayload> STREAM_CODEC =
                StreamCodec.unit(new ThrowCoinPayload());
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ── C2S: 潜行Q - 一键搭测试场地/reload ──────────────────────
    public record SneakQPayload(int action, int posX, int posY, int posZ) implements CustomPacketPayload {
        /** 一次性搭好测试场地: 脚下一层平台 + 面前一个假人 (合计只要 1 枚币) */
        public static final int ACTION_SETUP = 0;
        /** 开发版北山币: 执行 /reload */
        public static final int ACTION_RELOAD = 1;

        public static final Type<SneakQPayload> TYPE = new Type<>(BSCoins.id("sneak_q"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SneakQPayload> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT, SneakQPayload::action,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT, SneakQPayload::posX,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT, SneakQPayload::posY,
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT, SneakQPayload::posZ,
                SneakQPayload::new);
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ── C2S: 潜行右键空气 - 开启开发助手 (消耗1币) ─────────────────
    public record OpenAssistantPayload() implements CustomPacketPayload {
        public static final Type<OpenAssistantPayload> TYPE = new Type<>(BSCoins.id("open_assistant"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenAssistantPayload> STREAM_CODEC =
                StreamCodec.unit(new OpenAssistantPayload());
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ── S2C: 服务端确认已扣币, 客户端打开开发助手 GUI ───────────────
    public record OpenAssistantResponsePayload() implements CustomPacketPayload {
        public static final Type<OpenAssistantResponsePayload> TYPE = new Type<>(BSCoins.id("open_assistant_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenAssistantResponsePayload> STREAM_CODEC =
                StreamCodec.unit(new OpenAssistantResponsePayload());
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ── C2S: 长按右键3秒 - 生成北山团建圈 ─────────────────────────
    public record CreateCirclePayload() implements CustomPacketPayload {
        public static final Type<CreateCirclePayload> TYPE = new Type<>(BSCoins.id("create_circle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateCirclePayload> STREAM_CODEC =
                StreamCodec.unit(new CreateCirclePayload());
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
