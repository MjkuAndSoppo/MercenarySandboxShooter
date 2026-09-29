package com.mercenarysandbox.msb.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.network.MatchStatePayload;

/**
 * HUD 顶部对局栏（GuiGraphics 自绘，P0 零重 UI 库，docs/02 §3.7）。
 * 全宽顶部条：三方分数（阵营色分段）整体居中，结算倒计时右对齐仅显示「xx S」。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MsbHudOverlay {
    private static final int BAR_H = 16;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        MatchStatePayload state = ClientMatchState.getMatchState();
        Player player = Minecraft.getInstance().player;
        if (state == null || player == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        // 全宽顶部条
        g.fill(0, 0, screenWidth, BAR_H + 2, 0xC0101010);

        // 三色分数整体居中
        Component ls = factionLine(Faction.LONESTAR, state.lonestarScore());
        Component va = factionLine(Faction.VALKYRA, state.valkyraScore());
        Component mt = factionLine(Faction.MANTICORE, state.manticoreScore());
        int total = font.width(ls) + font.width(va) + font.width(mt) + 12;
        int x = Math.max(8, screenWidth / 2 - total / 2);
        x = drawSegment(g, font, x, ls, rgb(Faction.LONESTAR));
        x = drawSegment(g, font, x, va, rgb(Faction.VALKYRA));
        drawSegment(g, font, x, mt, rgb(Faction.MANTICORE));

        // 刷新时间（结算倒计时）偏右，仅显示「xx S」
        Component cd = Component.literal(state.countdownSeconds() + " S");
        g.drawString(font, cd, screenWidth - 6 - font.width(cd), 8, 0xFFFFFF);
    }

    /** 绘制一段文本并返回下一个绘制 x（用于分段着色，y 固定 8 与顶栏居中） */
    private static int drawSegment(GuiGraphics g, Font font, int x, Component text, int color) {
        int w = font.width(text);
        g.drawString(font, text, x, 8, color);
        return x + w + 6;
    }

    private static Component factionLine(Faction faction, int score) {
        return Component.translatable(faction.getDisplayKey()).append("  ").append(String.valueOf(score));
    }

    private static int rgb(Faction faction) {
        Integer color = faction.getChatColor().getColor();
        return color == null ? 0xFFFFFF : color;
    }

    private MsbHudOverlay() {
    }
}
