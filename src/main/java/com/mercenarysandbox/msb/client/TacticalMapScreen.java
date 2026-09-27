package com.mercenarysandbox.msb.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.network.MatchStatePayload;
import com.mercenarysandbox.msb.network.UnitPositionsPayload;

/**
 * 全屏战术地图（M 键打开，GuiGraphics 自绘，docs/02 §3.9）。
 * 以玩家当前位置为视口中心：控制区/地图边界/出生点 + 单位点（友蓝/敌红/未分配灰）。
 * 敌方仅显示服务端已下发的「交战真人 + 全部 AI 单位」，客户端不做任何敌情推断。
 */
public final class TacticalMapScreen extends Screen {
    private static final int COLOR_BG = 0xB0101010;
    private static final int COLOR_ZONE = 0x80FFFFFF;
    private static final int COLOR_BORDER = 0x50FFFFFF;
    private static final int COLOR_SPAWN = 0xFFD4AF37;
    private static final int COLOR_FRIENDLY = 0xFF3F9EFF;
    private static final int COLOR_ENEMY = 0xFFFF3F3F;
    private static final int COLOR_NEUTRAL = 0xFF9E9E9E;
    private static final int COLOR_SELF = 0xFFFFFFFF;
    /** 视口占屏系数：地图方形视口边长 = min(屏宽,屏高) × 该系数 */
    private static final float VIEWPORT_RATIO = 0.9F;

    public TacticalMapScreen() {
        super(Component.translatable("msb.tactical_map.title"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        UnitPositionsPayload units = ClientMatchState.getUnitPositions();
        MatchStatePayload state = ClientMatchState.getMatchState();

        g.fill(0, 0, width, height, COLOR_BG);

        if (mc.player == null || units == null) {
            // 服务端未下发地图数据（未入对局）：显示等待提示
            g.drawCenteredString(font, Component.translatable("msb.tactical_map.waiting"), width / 2, height / 2, 0xFFFFFF);
            drawMatchInfo(g, state);
            return;
        }

        BlockPos self = mc.player.blockPosition();
        int viewHalf = (int) (Math.min(width, height) * VIEWPORT_RATIO) / 2;
        // 世界格 → 屏幕像素 缩放
        double scale = (double) viewHalf / Math.max(1, units.mapRadius());
        int cx = width / 2;
        int cy = height / 2;

        // 地图边界圆（中心 = 地图中心，半径 = 边界半径）
        drawRing(g, cx, cy, scale, self.getX(), self.getZ(),
                units.mapCenterX(), units.mapCenterZ(), units.mapRadius(), COLOR_BORDER);
        // 控制区圆
        if (state != null) {
            drawRing(g, cx, cy, scale, self.getX(), self.getZ(),
                    units.mapCenterX(), units.mapCenterZ(), state.zoneRadius(), COLOR_ZONE);
        }
        // 出生点（= 地图中心/控制区圆心）金色方标
        int spawnX = sx(cx, scale, self.getX(), units.mapCenterX());
        int spawnZ = sz(cy, scale, self.getZ(), units.mapCenterZ());
        g.fill(spawnX - 3, spawnZ - 3, spawnX + 3, spawnZ + 3, COLOR_SPAWN);

        // 单位点
        for (UnitPositionsPayload.UnitEntry u : units.units()) {
            int x = sx(cx, scale, self.getX(), u.x());
            int z = sz(cy, scale, self.getZ(), u.z());
            g.fill(x - 2, z - 2, x + 2, z + 2, unitColor(u.factionId()));
        }
        // 玩家本人：中心白点
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, COLOR_SELF);

        drawMatchInfo(g, state);
    }

    /** 单位点颜色：本方阵营蓝、未分配灰、其余红（配色与 docs/02 §3.10 IFF 一致） */
    private int unitColor(int factionId) {
        int own = ClientMatchState.getOwnFactionId();
        if (factionId == own) {
            return COLOR_FRIENDLY;
        }
        if (factionId == Faction.NONE.getId()) {
            return COLOR_NEUTRAL;
        }
        return COLOR_ENEMY;
    }

    /** 世界 X → 屏幕 X */
    private int sx(int cx, double scale, int selfX, int worldX) {
        return cx + (int) Math.round((worldX - selfX) * scale);
    }

    /** 世界 Z → 屏幕 Y */
    private int sz(int cy, double scale, int selfZ, int worldZ) {
        return cy + (int) Math.round((worldZ - selfZ) * scale);
    }

    /** 以指定世界点为圆心画圆环（逐点填充，不依赖任何纹理资源） */
    private void drawRing(GuiGraphics g, int cx, int cy, double scale, int selfX, int selfZ,
            int worldCenterX, int worldCenterZ, int worldRadius, int color) {
        int n = Math.max(24, worldRadius);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            int wx = worldCenterX + (int) Math.round(Math.cos(a) * worldRadius);
            int wz = worldCenterZ + (int) Math.round(Math.sin(a) * worldRadius);
            int px = sx(cx, scale, selfX, wx);
            int pz = sz(cy, scale, selfZ, wz);
            g.fill(px, pz, px + 1, pz + 1, color);
        }
    }

    /** 底部对局信息：三方分数 + 结算倒计时（与 HUD 数据同源） */
    private void drawMatchInfo(GuiGraphics g, MatchStatePayload state) {
        if (state == null) {
            return;
        }
        String line = "LONESTAR " + state.lonestarScore()
                + "  |  VALKYRA " + state.valkyraScore()
                + "  |  MANTICORE " + state.manticoreScore()
                + "  |  " + state.countdownSeconds() + "s";
        g.drawCenteredString(font, line, width / 2, height - 14, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
