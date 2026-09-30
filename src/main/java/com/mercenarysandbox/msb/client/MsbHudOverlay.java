package com.mercenarysandbox.msb.client;

import java.util.Locale;

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
 * 全宽顶部条：结算倒计时「xx S」贴左边，三方分数（阵营色分段）整体居中，
 * 右侧显示财产「当前花销$ | 总资产$」（本人钱包，S2C WalletPayload 下发）。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MsbHudOverlay {
    /** 顶栏文字贴靠屏幕上边缘（y=2）；背景条高度贴合文字（y + 行高 + 下边距） */
    private static final int TEXT_Y = 2;

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

        // 全宽顶部条（高度贴合文字：y + 行高 + 下边距，不再用固定 16 高）
        g.fill(0, 0, screenWidth, TEXT_Y + font.lineHeight + 2, 0xC0101010);

        // 结算倒计时「xx S」贴左（y 贴靠上边缘）
        Component cd = Component.literal(state.countdownSeconds() + " S");
        g.drawString(font, cd, 6, TEXT_Y, 0xFFFFFF);

        // 三色分数整体居中
        Component ls = factionLine(Faction.LONESTAR, state.lonestarScore());
        Component va = factionLine(Faction.VALKYRA, state.valkyraScore());
        Component mt = factionLine(Faction.MANTICORE, state.manticoreScore());
        int mid = Math.max(font.width(cd) + 18 * 2,
                screenWidth / 2 - (font.width(ls) + font.width(va) + font.width(mt) + 12) / 2);
        int x = mid;
        x = drawSegment(g, font, x, ls, rgb(Faction.LONESTAR));
        x = drawSegment(g, font, x, va, rgb(Faction.VALKYRA));
        drawSegment(g, font, x, mt, rgb(Faction.MANTICORE));

        // 右侧财产：本条命赚的钱$ | 总资产$（右对齐，y 贴靠上边缘）
        Component wallet = Component.literal(ClientMatchState.getWalletEarned() + "$ | "
                + ClientMatchState.getWalletTotal() + "$");
        g.drawString(font, wallet, screenWidth - 6 - font.width(wallet), TEXT_Y, 0xFFFFFF);

        // 资产左侧：负重（随身目录物品，≥60% 琥珀、≥100% 红；docs/02 §3.4 负重系统）
        double weight = ClientShopData.playerWeight(player);
        double limit = ClientShopData.weightLimit();
        double ratio = limit <= 0.0D ? 0.0D : weight / limit;
        int weightColor = ratio >= 1.0D ? 0xFF5A4D
                : ratio >= ClientShopData.weightWarnRatio() ? 0xF2B13C : 0xFFFFFF;
        Component weightText = Component.translatable("msb.hud.weight",
                String.format(Locale.ROOT, "%.1f", weight), String.format(Locale.ROOT, "%.1f", limit));
        int walletX = screenWidth - 6 - font.width(wallet);
        g.drawString(font, weightText, walletX - 8 - font.width(weightText), TEXT_Y, weightColor);
    }

    /** 绘制一段文本并返回下一个绘制 x（用于分段着色，y 贴靠上边缘） */
    private static int drawSegment(GuiGraphics g, Font font, int x, Component text, int color) {
        int w = font.width(text);
        g.drawString(font, text, x, TEXT_Y, color);
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
