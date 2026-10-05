package com.beishan.bscoins.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

/**
 * 北山开发助手 GUI。
 * 显示目标方块注册名/亮度/硬度/合适工具, 目标实体 UUID/血量/NBT,
 * 玩家坐标与主手物品 NBT, 并支持一键复制信息到聊天栏。
 */
public class DevAssistantScreen extends Screen {
    private static final java.text.DecimalFormat FMT = new java.text.DecimalFormat("0.##");
    private static final int MAX_NBT_CHARS = 400;
    private static final int MAX_LINES = 16;

    private final List<Component> lines = new ArrayList<>();
    @Nullable
    private String copyText;

    public DevAssistantScreen() {
        super(Component.translatable("bscoins.dev_assistant"));
        collectInfo();
    }

    private void collectInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            lines.add(Component.literal("§c世界未加载, 无法获取信息"));
            return;
        }

        // ── 目标方块信息 ─────────────────────────────
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hit;
            BlockPos pos = bhr.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            int light = mc.level.getMaxLocalRawBrightness(pos);
            float hardness = state.getDestroySpeed(mc.level, pos);
            boolean requiresTool = state.requiresCorrectToolForDrops();
            String tool = suitableTool(state);

            lines.add(Component.literal("§6[方块] §f" + key));
            lines.add(Component.literal("  坐标: " + pos.toShortString() + "  亮度: " + light
                    + "  硬度: " + formatFloat(hardness)));
            lines.add(Component.literal("  需要正确工具: " + (requiresTool ? "是" : "否") + "  合适工具: " + tool));
        } else {
            lines.add(Component.literal("§7[方块] §f指向空气, 无目标方块"));
        }

        // ── 目标实体信息 ─────────────────────────────
        Entity target = mc.crosshairPickEntity;
        if (target != null) {
            lines.add(Component.literal("§a[实体] §f" + target.getType().getDescription().getString()));
            lines.add(Component.literal("  UUID: " + target.getUUID()));
            if (target instanceof LivingEntity living) {
                lines.add(Component.literal("  血量: " + formatFloat(living.getHealth()) + " / "
                        + formatFloat(living.getMaxHealth())));
            }
            CompoundTag nbt = target.saveWithoutId(new CompoundTag());
            lines.add(Component.literal("  NBT: " + compactNbtString(nbt)));
        } else {
            lines.add(Component.literal("§7[实体] §f指空, 无目标实体"));
        }

        // ── 玩家坐标与主手物品 NBT ────────────────────
        var player = mc.player;
        if (player != null) {
            lines.add(Component.literal("§b[玩家] §f" + player.getName().getString()
                    + "  XYZ: " + formatFloat(player.getX()) + ", " + formatFloat(player.getY())
                    + ", " + formatFloat(player.getZ())));
            ItemStack held = player.getMainHandItem();
            ResourceLocation itemKey = held.isEmpty()
                    ? ResourceLocation.withDefaultNamespace("air")
                    : BuiltInRegistries.ITEM.getKey(held.getItem());
            lines.add(Component.literal("  主手: " + itemKey + " x" + held.getCount()));
            if (held.isEmpty()) {
                lines.add(Component.literal("  主手NBT: (空手)"));
            } else {
                net.minecraft.nbt.Tag tag = held.saveOptional(mc.level.registryAccess());
                lines.add(Component.literal("  主手NBT: " + compactNbtString(tag)));
            }
        }

        // ── 汇总为一键复制文本 ───────────────────────
        StringBuilder sb = new StringBuilder();
        for (Component c : lines) {
            sb.append(c.getString()).append('\n');
        }
        this.copyText = sb.toString();
    }

    private static String compactNbtString(net.minecraft.nbt.Tag tag) {
        String s = tag.toString();
        return s.length() > MAX_NBT_CHARS ? s.substring(0, MAX_NBT_CHARS) + "…" : s;
    }

    private static String suitableTool(BlockState state) {
        if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)) return "镐";
        if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)) return "斧";
        if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) return "锹";
        if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE)) return "锄";
        return "手 (无专属工具)";
    }

    private static String formatFloat(double f) {
        return FMT.format(f);
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 + 40;
        Button copyButton = Button.builder(Component.translatable("bscoins.dev_assistant.copy"),
                        btn -> this.copyToChat())
                .bounds(x, y, 200, 20).build();
        this.addRenderableWidget(copyButton);
    }

    private void copyToChat() {
        if (this.copyText != null && this.minecraft != null) {
            this.minecraft.keyboardHandler.setClipboard(this.copyText);
            this.minecraft.setScreen(new ChatScreen(this.copyText));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        int x = 20;
        int y = 30;
        for (int i = 0; i < Math.min(MAX_LINES, this.lines.size()); i++) {
            graphics.drawString(this.font, this.lines.get(i), x, y, 0xFFFFFF);
            y += 11;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
