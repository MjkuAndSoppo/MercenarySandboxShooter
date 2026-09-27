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
 * HUD 计分板（GuiGraphics 自绘，P0 零重 UI 库，docs/02 §3.7）。
 * 渲染：控制区信息、结算倒计时、三方分数（阵营色）。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MsbHudOverlay {
    private static final int PANEL_WIDTH = 170;
    private static final int PANEL_TOP = 4;
    private static final int TEXT_PAD_X = 6;

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

        int left = (screenWidth - PANEL_WIDTH) / 2;
        int rowH = font.lineHeight + 6;
        int panelH = rowH * 5 + 8; // 控制区 + 倒计时 + 三方分数
        int top = PANEL_TOP;

        // 半透明底
        g.fill(left, top, left + PANEL_WIDTH, top + panelH, 0xC0101010);

        int y = top + 4;
        g.drawString(font, Component.translatable("msb.hud.zone",
                state.zoneCenterX(), state.zoneCenterZ(), state.zoneRadius()), left + TEXT_PAD_X, y, 0xFFFFFF);
        y += rowH;
        g.drawString(font, Component.translatable("msb.hud.settle", state.countdownSeconds()),
                left + TEXT_PAD_X, y, 0xFFFFFF);
        y += rowH;
        g.drawString(font, factionLine(Faction.LONESTAR, state.lonestarScore()), left + TEXT_PAD_X, y, rgb(Faction.LONESTAR));
        y += rowH;
        g.drawString(font, factionLine(Faction.VALKYRA, state.valkyraScore()), left + TEXT_PAD_X, y, rgb(Faction.VALKYRA));
        y += rowH;
        g.drawString(font, factionLine(Faction.MANTICORE, state.manticoreScore()), left + TEXT_PAD_X, y, rgb(Faction.MANTICORE));
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
