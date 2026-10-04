package com.mercenarysandbox.msb.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.network.FactionSelectPayload;
import com.mercenarysandbox.msb.onboarding.FundingTier;
import com.mercenarysandbox.msb.onboarding.OnboardingManager;

/**
 * 雇佣兵手册（右键手册由服务端 S2C 打开）：开局阵营选择界面（自绘，docs 开局流程）。
 * 阵营三选一（含四项阵营增益展示）+ 初始资金三档 + 初始 AI 数量 5~15 + 基地状态 + 确认加入。
 * 已开局玩家再次打开时显示教程占位页（预留）。
 */
public final class HandbookScreen extends Screen {

    // ===== 布局（GUI 单位） =====
    private static final int WIN_W = 420;
    private static final int WIN_H = 232;
    private static final int HEADER_H = 20;

    private static final int CARD_X0 = 12;
    private static final int CARD_Y = 26;
    private static final int CARD_W = 128;
    private static final int CARD_H = 92;
    private static final int CARD_GAP = 6;

    private static final int TIER_BTN_W = 116;
    private static final int TIER_BTN_H = 18;
    private static final int TIER_BTN_Y = 136;

    private static final int ROW_H = 18;
    private static final int AI_ROW_Y = 160;
    private static final int BASE_ROW_Y = 182;
    private static final int CONFIRM_H = 20;
    private static final int CONFIRM_Y = 204;

    private static final float SCALE_FILL = 0.94F;
    private static final float SCALE_MIN = 0.6F;
    private static final float SCALE_MAX = 1.75F;

    // ===== 色板 =====
    private static final int C_DIM = 0xB0101010;
    private static final int C_WIN_BG = 0xF00A0C10;
    private static final int C_PANEL = 0xFF12161E;
    private static final int C_PANEL_HOVER = 0xFF1A2029;
    private static final int C_BORDER = 0x402A3240;
    private static final int C_BORDER_STRONG = 0x80303C4E;
    private static final int C_SEL_BG = 0x382A1F0A;
    private static final int C_TEXT = 0xFFE8EDF4;
    private static final int C_TEXT_SUB = 0xFF8B98AB;
    private static final int C_TEXT_DIM = 0xFF5A667A;
    private static final int C_GOLD = 0xFFF2B13C;
    private static final int C_GREEN = 0xFF4ADE80;
    private static final int C_RED = 0xFFFF5A4D;
    private static final int C_BTN_TEXT = 0xFF14161C;

    private float uiScale = 1.0F;
    private float originX;
    private float originY;

    /** 是否处于开局选择模式（已开局则显示教程占位页） */
    private final boolean selectMode;
    private Faction selected = Faction.LONESTAR;
    private FundingTier tier = FundingTier.TIER_MID;
    private int aiCount = 8;

    public HandbookScreen() {
        super(Component.translatable("msb.handbook.title"));
        int own = ClientMatchState.getOwnFactionId();
        this.selectMode = own != 0 && own != 1 && own != 2;
        if (!selectMode) {
            this.selected = Faction.byId(own);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===== 几何/缩放 =====

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

    // ===== 文本（保持 1× 字号，避免随 uiScale 放大糊化） =====

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

    private void drawStr(GuiGraphics g, String value, int x, int y, int color) {
        drawStr(g, Component.literal(value), x, y, color);
    }

    private void drawStrC(GuiGraphics g, Component value, int centerX, int y, int color) {
        drawStr(g, value, centerX - sw(value) / 2, y, color);
    }

    private void drawStrC(GuiGraphics g, String value, int centerX, int y, int color) {
        drawStrC(g, Component.literal(value), centerX, y, color);
    }

    private int sw(Component value) {
        return Math.round(font.width(value) / uiScale);
    }

    private int sw(String value) {
        return Math.round(font.width(value) / uiScale);
    }

    private static int factionColor(Faction f) {
        return switch (f) {
            case LONESTAR -> C_RED;
            case VALKYRA -> 0xFF4D8BFF;
            case MANTICORE -> C_GREEN;
            default -> C_TEXT_SUB;
        };
    }

    /** 该阵营基地是否已放置（ClientMatchState；无数据视为未配置） */
    private boolean hasBase(Faction f) {
        var state = ClientMatchState.getMatchState();
        if (state == null || f == Faction.NONE) {
            return false;
        }
        int[] pos = state.basePositions();
        int i = f.ordinal();
        return pos != null && pos.length >= i * 3 + 1 && pos[i * 3] >= 0;
    }

    // ===== 渲染 =====

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

        drawStr(g, Component.translatable("msb.handbook.title"), 10, (HEADER_H - 8) / 2, C_GOLD);

        if (selectMode) {
            // 四项阵营增益为预览数据（实际效果待后续版本，docs/02 §3.1）
            Component note = Component.translatable("msb.faction.stat.preview");
            drawStr(g, note, WIN_W - 10 - sw(note), (HEADER_H - 8) / 2, C_TEXT_DIM);
            drawFactionCards(g, lx, ly);
            drawTierRow(g, lx, ly);
            drawAiRow(g, lx, ly);
            drawBaseRow(g);
            drawConfirm(g, lx, ly);
        } else {
            drawTutorial(g);
        }
        g.pose().popPose();
    }

    private void drawFactionCards(GuiGraphics g, int mx, int my) {
        Faction[] factions = {Faction.LONESTAR, Faction.VALKYRA, Faction.MANTICORE};
        for (int i = 0; i < factions.length; i++) {
            Faction f = factions[i];
            int cx = CARD_X0 + i * (CARD_W + CARD_GAP);
            boolean active = f == selected;
            boolean hover = hit(mx, my, cx, CARD_Y, CARD_W, CARD_H);
            g.fill(cx, CARD_Y, cx + CARD_W, CARD_Y + CARD_H, hover && !active ? C_PANEL_HOVER : C_PANEL);
            frame(g, cx, CARD_Y, CARD_W, CARD_H, active ? C_GOLD : C_BORDER);
            if (active) {
                g.fill(cx, CARD_Y, cx + CARD_W, CARD_Y + 2, C_GOLD);
            }
            int fc = factionColor(f);
            drawStr(g, Component.translatable(f.getDisplayKey()), cx + 8, CARD_Y + 6, fc);
            drawStr(g, f.getAbbr(), cx + 8, CARD_Y + 18, C_TEXT_DIM);
            // 四项阵营增益（仅展示，暂无实际效果）
            drawStat(g, cx + 8, CARD_Y + 34, "msb.faction.stat.manpower", f.getManpower());
            drawStat(g, cx + 8, CARD_Y + 48, "msb.faction.stat.firepower", f.getFirepower());
            drawStat(g, cx + 8, CARD_Y + 62, "msb.faction.stat.supply", f.getSupply());
            drawStat(g, cx + 8, CARD_Y + 76, "msb.faction.stat.cap", f.getCap());
        }
    }

    /** 单行增益：标签 + 5 段 pip + 数值 */
    private void drawStat(GuiGraphics g, int x, int y, String labelKey, int value) {
        drawStr(g, Component.translatable(labelKey), x, y, C_TEXT_SUB);
        int pipX = x + 30;
        for (int i = 0; i < 5; i++) {
            int c = i < value ? C_GOLD : 0x33FFFFFF;
            g.fill(pipX + i * 7, y + 1, pipX + i * 7 + 5, y + 6, c);
        }
        drawStr(g, Integer.toString(value), pipX + 42, y, C_TEXT);
    }

    private void drawTierRow(GuiGraphics g, int mx, int my) {
        drawStr(g, Component.translatable("msb.handbook.funding"), 12, TIER_BTN_Y - 12, C_TEXT_SUB);
        int total = FundingTier.values().length * TIER_BTN_W + (FundingTier.values().length - 1) * 6;
        int startX = (WIN_W - total) / 2;
        FundingTier[] tiers = FundingTier.values();
        for (int i = 0; i < tiers.length; i++) {
            FundingTier t = tiers[i];
            int bx = startX + i * (TIER_BTN_W + 6);
            boolean active = t == tier;
            boolean hover = hit(mx, my, bx, TIER_BTN_Y, TIER_BTN_W, TIER_BTN_H);
            g.fill(bx, TIER_BTN_Y, bx + TIER_BTN_W, TIER_BTN_Y + TIER_BTN_H,
                    active ? C_GOLD : (hover ? C_PANEL_HOVER : C_PANEL));
            frame(g, bx, TIER_BTN_Y, TIER_BTN_W, TIER_BTN_H, active ? C_GOLD : C_BORDER);
            String label = "$" + String.format("%,d", t.money()) + "  ×" + trim(t.killMultiplier());
            drawStrC(g, label, bx + TIER_BTN_W / 2, TIER_BTN_Y + (TIER_BTN_H - 8) / 2,
                    active ? C_BTN_TEXT : C_TEXT);
        }
    }

    private static String trim(double multiplier) {
        return multiplier == Math.floor(multiplier)
                ? Integer.toString((int) multiplier)
                : String.format(java.util.Locale.ROOT, "%.1f", multiplier);
    }

    private void drawAiRow(GuiGraphics g, int mx, int my) {
        drawStr(g, Component.translatable("msb.handbook.ai_count"), 12, AI_ROW_Y + (ROW_H - 8) / 2, C_TEXT_SUB);
        int bx = 176;
        drawStepButton(g, bx, mx, my, "-");
        g.fill(bx + 22, AI_ROW_Y, bx + 62, AI_ROW_Y + ROW_H, C_PANEL);
        frame(g, bx + 22, AI_ROW_Y, 40, ROW_H, C_BORDER);
        drawStrC(g, Integer.toString(aiCount), bx + 42, AI_ROW_Y + (ROW_H - 8) / 2, C_GOLD);
        drawStepButton(g, bx + 66, mx, my, "+");
    }

    private void drawStepButton(GuiGraphics g, int x, int mx, int my, String label) {
        boolean hover = hit(mx, my, x, AI_ROW_Y, ROW_H, ROW_H);
        g.fill(x, AI_ROW_Y, x + ROW_H, AI_ROW_Y + ROW_H, hover ? C_PANEL_HOVER : C_PANEL);
        frame(g, x, AI_ROW_Y, ROW_H, ROW_H, C_BORDER);
        drawStrC(g, label, x + ROW_H / 2, AI_ROW_Y + (ROW_H - 8) / 2, C_TEXT);
    }

    private void drawBaseRow(GuiGraphics g) {
        boolean ready = hasBase(selected);
        Component value = ready
                ? Component.translatable("msb.handbook.base.ready")
                : Component.translatable("msb.handbook.base.none");
        Component line = Component.translatable("msb.handbook.base", value);
        drawStr(g, line, 12, BASE_ROW_Y + (ROW_H - 8) / 2, ready ? C_TEXT_SUB : C_RED);
    }

    private void drawConfirm(GuiGraphics g, int mx, int my) {
        int w = 140;
        int bx = (WIN_W - w) / 2;
        boolean hover = hit(mx, my, bx, CONFIRM_Y, w, CONFIRM_H);
        g.fill(bx, CONFIRM_Y, bx + w, CONFIRM_Y + CONFIRM_H, hover ? C_GOLD : 0xFFD9A033);
        frame(g, bx, CONFIRM_Y, w, CONFIRM_H, C_GOLD);
        drawStrC(g, Component.translatable("msb.handbook.confirm"), bx + w / 2,
                CONFIRM_Y + (CONFIRM_H - 8) / 2, C_BTN_TEXT);
    }

    /** 已开局：教程占位页（后续填充模组游玩教程） */
    private void drawTutorial(GuiGraphics g) {
        drawStr(g, Component.translatable("msb.handbook.joined_as",
                Component.translatable(selected.getDisplayKey())), 12, 40, factionColor(selected));
        int y = 64;
        drawStr(g, Component.translatable("msb.handbook.tutorial.title"), 12, y, C_GOLD);
        drawStr(g, Component.translatable("msb.handbook.tutorial.line1"), 12, y + 16, C_TEXT_SUB);
        drawStr(g, Component.translatable("msb.handbook.tutorial.line2"), 12, y + 30, C_TEXT_SUB);
        drawStr(g, Component.translatable("msb.handbook.tutorial.line3"), 12, y + 44, C_TEXT_SUB);
        drawStr(g, Component.translatable("msb.handbook.tutorial.line4"), 12, y + 58, C_TEXT_SUB);
    }

    // ===== 输入 =====

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        if (button != 0 || !selectMode) {
            return super.mouseClicked(screenX, screenY, button);
        }
        double mx = toLocalX(screenX);
        double my = toLocalY(screenY);

        Faction[] factions = {Faction.LONESTAR, Faction.VALKYRA, Faction.MANTICORE};
        for (int i = 0; i < factions.length; i++) {
            int cx = CARD_X0 + i * (CARD_W + CARD_GAP);
            if (hit(mx, my, cx, CARD_Y, CARD_W, CARD_H)) {
                selected = factions[i];
                return true;
            }
        }

        FundingTier[] tiers = FundingTier.values();
        int total = tiers.length * TIER_BTN_W + (tiers.length - 1) * 6;
        int startX = (WIN_W - total) / 2;
        for (int i = 0; i < tiers.length; i++) {
            int bx = startX + i * (TIER_BTN_W + 6);
            if (hit(mx, my, bx, TIER_BTN_Y, TIER_BTN_W, TIER_BTN_H)) {
                tier = tiers[i];
                return true;
            }
        }

        int bx = 176;
        if (hit(mx, my, bx, AI_ROW_Y, ROW_H, ROW_H)) {
            aiCount = Mth.clamp(aiCount - 1, OnboardingManager.AI_COUNT_MIN, OnboardingManager.AI_COUNT_MAX);
            return true;
        }
        if (hit(mx, my, bx + 66, AI_ROW_Y, ROW_H, ROW_H)) {
            aiCount = Mth.clamp(aiCount + 1, OnboardingManager.AI_COUNT_MIN, OnboardingManager.AI_COUNT_MAX);
            return true;
        }

        int w = 140;
        if (hit(mx, my, (WIN_W - w) / 2, CONFIRM_Y, w, CONFIRM_H)) {
            PacketDistributor.sendToServer(new FactionSelectPayload(selected.getId(), tier.getId(), aiCount));
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return true;
    }
}