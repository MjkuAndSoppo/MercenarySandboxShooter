package com.mercenarysandbox.msb.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 自绘耐力条（docs/02 §3.15，GuiGraphics 原生渲染，§3.7）。
 *
 * <p>取消原版饥饿条图层，沿用其槽位（{@code x = 宽/2 + 91}、{@code y = 高 − 39}）画一条线性耐力条：
 * <b>与原版饥饿条同向，从右端向左填充</b>（耐力降低时最左侧先空）；30% 处标上下短突起刻度，
 * 低于 30% 前景转红；创造/旁观隐藏。</p>
 *
 * <p>百分比优先取 {@link ClientMatchState#getStaminaPercent()}（S2C {@code StaminaPayload} 下发的精确浮点），
 * 未收到时回退到 {@code foodLevel / 20}（原版食物包，仅 1/20 量化）。</p>
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MsbStaminaHud {

    private static final int BAR_X_OFFSET = 91;
    private static final int BAR_Y_OFFSET = 39;
    private static final int WIDTH = 81;
    private static final int HEIGHT = 8;
    private static final int TICK_HEIGHT = 2;
    /** 低耐力阈值（30%，与原版冲刺门槛一致） */
    private static final float LOW_THRESHOLD = 0.30F;

    private static final int BORDER = 0xFF303030;
    private static final int BACKGROUND = 0xFF1A1A1A;
    private static final int COLOR_NORMAL = 0xFF6FD44B;
    private static final int COLOR_LOW = 0xFFE03A2F;
    private static final int COLOR_TICK = 0xFFFFFFFF;

    private MsbStaminaHud() {
    }

    /** 取消原版饥饿条图层（由本类自绘耐力条接管） */
    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.FOOD_LEVEL.equals(event.getName())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || player.isCreative() || player.isSpectator()) {
            return;
        }
        // 精确百分比优先（浮点载荷），未收到时回退到 foodLevel/20
        float percent = ClientMatchState.getStaminaPercent();
        if (percent < 0.0F) {
            percent = player.getFoodData().getFoodLevel() / 20.0F;
        }
        percent = Math.max(0.0F, Math.min(1.0F, percent));
        int x = mc.getWindow().getGuiScaledWidth() / 2 + BAR_X_OFFSET;
        int y = mc.getWindow().getGuiScaledHeight() - BAR_Y_OFFSET;

        GuiGraphics g = event.getGuiGraphics();
        // 外框 + 背景
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, BORDER);
        g.fill(x, y, x + WIDTH, y + HEIGHT, BACKGROUND);
        // 前景：与原版饥饿条同向，从右端向左填充（width × percent），低于 30% 转红
        int filled = Math.round(WIDTH * percent);
        if (filled > 0) {
            g.fill(x + WIDTH - filled, y, x + WIDTH, y + HEIGHT, percent < LOW_THRESHOLD ? COLOR_LOW : COLOR_NORMAL);
        }
        // 30% 处上下小突起刻度（随填充方向镜像到距右端 30% 位置）
        int tickX = x + WIDTH - Math.round(WIDTH * LOW_THRESHOLD);
        g.fill(tickX, y - TICK_HEIGHT, tickX + 1, y, COLOR_TICK);
        g.fill(tickX, y + HEIGHT, tickX + 1, y + HEIGHT + TICK_HEIGHT, COLOR_TICK);
    }
}
