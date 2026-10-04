package com.mercenarysandbox.msb.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.mercenarysandbox.msb.faction.Faction;

/**
 * 信息栏（Tab 键开关，自绘；docs 开局流程）。页签：阵营 / 战绩 / 队伍 / 教程。
 * 本版本为预留骨架：阵营页展示本人阵营与四项增益，其余页签为占位行，后续填充。
 */
public final class InfoScreen extends Screen {

    private static final int WIN_W = 380;
    private static final int WIN_H = 220;
    private static final int HEADER_H = 22;
    private static final int TAB_H = 16;
    private static final int TAB_W = 88;
    private static final int TAB_GAP = 4;
    private static final int TAB_Y = 26;
    private static final int BODY_Y = 50;

    private static final float SCALE_FILL = 0.94F;
    private static final float SCALE_MIN = 0.6F;
    private static final float SCALE_MAX = 1.75F;

    private static final int C_DIM = 0xB0101010;
    private static final int C_WIN_BG = 0xF00A0C10;
    private static final int C_PANEL = 0xFF12161E;
    private static final int C_BORDER = 0x402A3240;
    private static final int C_BORDER_STRONG = 0x80303C4E;
    private static final int C_SEL_BG = 0x382A1F0A;
    private static final int C_TEXT = 0xFFE8EDF4;
    private static final int C_TEXT_SUB = 0xFF8B98AB;
    private static final int C_TEXT_DIM = 0xFF5A667A;
    private static final int C_GOLD = 0xFFF2B13C;
    private static final int C_GREEN = 0xFF4ADE80;
    private static final int C_RED = 0xFFFF5A4D;

    private static final String[] TAB_KEYS = {
            "msb.info.tab.faction", "msb.info.tab.stats", "msb.info.tab.team", "msb.info.tab.tutorial"
    };

    private float uiScale = 1.0F;
    private float originX;
    private float originY;
    private int tab;

    public InfoScreen() {
        super(Component.translatable("msb.info.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateLayout() {
        float k = Math.min((width * SCALE_FILL) / WIN_W, (height * SCALE_FILL) / WIN_H);
        k = Mth.clamp(k, SCALE_MIN, SCALE_MAX);
        uiScale = Math.round(k * 8.0F) / 8.0F;
        originX = (width - WIN_W * uiScale) / 2.0F;
        originY = (height - WIN_H * uiScale) / 2.0F;
    }

    private double toLocalX(double screenX) {
        return (screenX - originX) / uiScale;
    }

    private double toLocalY(double screenY) {
        return (screenY - originY) / uiScale;
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawStr(GuiGraphics g, Component value, int x, int y, int color) {
        if (uiScale == 1.0F) {
            g.drawString(font, value, x, y, color);
            return;
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(1.0F / uiScale, 1.0F / uiScale, 1.0F);
        g.drawString(font, value, 0, 0, color);
        pose.popPose();
    }

    private static int factionColor(Faction f) {
        return switch (f) {
            case LONESTAR -> C_RED;
            case VALKYRA -> 0xFF4D8BFF;
            case MANTICORE -> C_GREEN;
            default -> C_TEXT_SUB;
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        updateLayout();
        int lx = (int) toLocalX(mouseX);
        int ly = (int) toLocalY(mouseY);

        g.fill(0, 0, width, height, C_DIM);
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0.0F);
        g.pose().scale(uiScale, uiScale, 1.0F);

        g.fill(0, 0, WIN_W, WIN_H, C_WIN_BG);
        frame(g, 0, 0, WIN_W, WIN_H, C_BORDER_STRONG);
        drawStr(g, Component.translatable("msb.info.title"), 10, (HEADER_H - 8) / 2, C_GOLD);

        drawTabs(g, lx, ly);
        g.fill(8, BODY_Y - 6, WIN_W - 8, BODY_Y - 5, C_BORDER);
        switch (tab) {
            case 0 -> drawFactionTab(g);
            case 1 -> drawPlaceholder(g, "stats");
            case 2 -> drawPlaceholder(g, "team");
            default -> drawPlaceholder(g, "tutorial");
        }
        g.pose().popPose();
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        int total = TAB_KEYS.length * TAB_W + (TAB_KEYS.length - 1) * TAB_GAP;
        int startX = (WIN_W - total) / 2;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int bx = startX + i * (TAB_W + TAB_GAP);
            boolean active = i == tab;
            boolean hover = hit(mx, my, bx, TAB_Y, TAB_W, TAB_H);
            g.fill(bx, TAB_Y, bx + TAB_W, TAB_Y + TAB_H, active ? C_SEL_BG : C_PANEL);
            frame(g, bx, TAB_Y, TAB_W, TAB_H, active ? C_GOLD : C_BORDER);
            Component label = Component.translatable(TAB_KEYS[i]);
            int tx = bx + (TAB_W - Math.round(font.width(label) / uiScale)) / 2;
            drawStr(g, label, tx, TAB_Y + (TAB_H - 8) / 2, active ? C_GOLD : C_TEXT_SUB);
        }
    }

    /** 阵营页：本人阵营 + 四项增益（仅展示） */
    private void drawFactionTab(GuiGraphics g) {
        Faction own = Faction.byId(ClientMatchState.getOwnFactionId());
        if (own == Faction.NONE) {
            drawStr(g, Component.translatable("msb.info.faction.none"), 16, BODY_Y + 6, C_TEXT_SUB);
            return;
        }
        drawStr(g, Component.translatable("msb.info.faction.current",
                Component.translatable(own.getDisplayKey())), 16, BODY_Y + 4, factionColor(own));
        int y = BODY_Y + 26;
        statRow(g, y, own, "msb.faction.stat.manpower", own.getManpower());
        statRow(g, y + 16, own, "msb.faction.stat.firepower", own.getFirepower());
        statRow(g, y + 32, own, "msb.faction.stat.supply", own.getSupply());
        statRow(g, y + 48, own, "msb.faction.stat.cap", own.getCap());
        // 增益为预览数据（实际效果待后续版本，docs/02 §3.1）
        drawStr(g, Component.translatable("msb.faction.stat.preview"), 16, y + 64, C_TEXT_DIM);
    }

    private void statRow(GuiGraphics g, int y, Faction own, String key, int value) {
        drawStr(g, Component.translatable(key), 16, y, C_TEXT_SUB);
        int pipX = 76;
        for (int i = 0; i < 5; i++) {
            int c = i < value ? C_GOLD : 0x33FFFFFF;
            g.fill(pipX + i * 9, y + 1, pipX + i * 9 + 6, y + 7, c);
        }
        drawStr(g, Component.literal(Integer.toString(value)), pipX + 52, y, C_TEXT);
    }

    /** 占位页：标题 + 若干「待填充」行 */
    private void drawPlaceholder(GuiGraphics g, String sectionKey) {
        drawStr(g, Component.translatable("msb.info." + sectionKey + ".title"), 16, BODY_Y + 4, C_GOLD);
        int y = BODY_Y + 24;
        for (int i = 1; i <= 4; i++) {
            drawStr(g, Component.translatable("msb.info." + sectionKey + ".line" + i), 16, y, C_TEXT_DIM);
            y += 16;
        }
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        if (button != 0) {
            return super.mouseClicked(screenX, screenY, button);
        }
        double mx = toLocalX(screenX);
        double my = toLocalY(screenY);
        int total = TAB_KEYS.length * TAB_W + (TAB_KEYS.length - 1) * TAB_GAP;
        int startX = (WIN_W - total) / 2;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int bx = startX + i * (TAB_W + TAB_GAP);
            if (hit(mx, my, bx, TAB_Y, TAB_W, TAB_H)) {
                tab = i;
                return true;
            }
        }
        return true;
    }
}