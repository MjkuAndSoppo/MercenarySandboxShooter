package com.mercenarysandbox.msb.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.network.MatchStatePayload;
import com.mercenarysandbox.msb.network.TeleportRequestPayload;
import com.mercenarysandbox.msb.network.UnitPositionsPayload;

/**
 * 全屏战术地图（M 键打开，GuiGraphics 自绘，docs/02 §3.9）。
 *
 * <p>2026-09-29 增强（移除 Xaero 集成后回归自绘）：
 * <ul>
 *   <li>缩放：滚轮，默认整图适配，最大放大 24×；</li>
 *   <li>平移：左/中键拖动（拖动即自动切自由视角）；</li>
 *   <li>跟随/自由：右下角「我」按钮切换，跟随=镜头锁定玩家，自由=手动平移；</li>
 *   <li>锁定北向：右上角 Lock 按钮，锁定后地图上北下南左西右东（不随视角旋转）；
 *       默认跟随配置 {@code tacticalMapRotateWithPlayer}，切换写回配置；</li>
 *   <li>左下角 4 向罗盘（N 红 / E·S·W 白，随地图旋转联动，始终显示）；</li>
 *   <li>朝向旋转：地图随玩家视角旋转、箭头始终指向前方；</li>
 *   <li>单位标注：友 T / 敌 E / 未分配 ? + 距玩家格数；悬停单位显示详情工具条；</li>
 *   <li>光标旁实时 XZ 世界坐标与距玩家距离；图例 + 顶部对局栏（倒计时/三方分数）；</li>
 *   <li>右下角按钮：圈居中 / 我（跟随-自由切换）；我或圈不在屏幕内时在屏幕边缘显示
 *       「我/圈 + 距离」指示器；</li>
 *   <li>距离网格：小块=1 区块（16 格，与 F3+H 区块边界同步），大块=4×4 区块
 *       （64 格）粗线，超大块=每级是上级的 4×4（16×16 区块 = 256 格），
 *       特大块=超大块的 2×2（32×32 区块 = 1024 格）；两级稀疏（防网格过密）：
 *       缩小到 99% 时隐藏小块线、大块线降为小块样式，缩小到 35% 时再隐藏大块线、
 *       超大块线降为小块样式，特大块始终粗线兜底；</li>
 *   <li>右侧竖向缩放条：与滚轮联动，点击/拖动改变缩放并可视化当前倍率（对数刻度）；</li>
 *   <li>圆环渲染：纯白环贴图 blit tint 上色（线宽随屏幕半径等比缩放）。</li>
 * </ul>
 * 敌方仅显示服务端已下发的「交战真人 + 全部 AI 单位」，客户端不做任何敌情推断（服务端权威）。
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
    private static final int COLOR_ARROW = 0xFFFFFFFF;
    private static final int COLOR_BAR = 0xC0101010;
    private static final int COLOR_LEGEND_BG = 0xA00A0A0A;
    private static final int COLOR_BTN = 0xA02A2A2A;
    private static final int COLOR_BTN_HOVER = 0xA0444444;
    private static final int COLOR_TOOLTIP_BG = 0xE0202020;
    /** 距离网格：细线=区块边界（16 格），粗线=4×4 区块（64 格） */
    private static final int COLOR_GRID_MINOR = 0x26FFFFFF;
    private static final int COLOR_GRID_MAJOR = 0x4DFFFFFF;
    /** 屏幕边缘指示器背景 */
    private static final int COLOR_EDGE_TAG_BG = 0xC0101010;

    /** 圆环贴图：512×512 RGBA，纯白圆环（圆心 256,256、外沿贴边），blit 时 tint 上色 */
    private static final ResourceLocation RING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("msb", "textures/gui/ring.png");

    /** 区块边长（格）：网格小块 = 1 区块，与 F3+H 区块边界一致 */
    private static final int CHUNK_SIZE = 16;
    /** 大网格 = 4×4 区块 */
    private static final int MAJOR_GRID_CHUNKS = 4;
    /** 超大网格：每级是上级的 4×4 大块 = 16×16 区块（256 格），沿用大块线宽 */
    private static final int SUPER_GRID_CHUNKS = MAJOR_GRID_CHUNKS * MAJOR_GRID_CHUNKS;
    /** 特大网格：超大块的 2×2 = 32×32 区块（1024 格），缩放到底时唯一兜底网格（始终粗线） */
    private static final int MEGA_GRID_CHUNKS = SUPER_GRID_CHUNKS * 2;

    /** 视口占屏系数：地图方形视口边长 = min(屏宽,屏高) × 该系数 */
    private static final float VIEWPORT_RATIO = 0.9F;
    /** 缩放钳制（整图适配=1.0；下限放宽到 10%，缩小到 99%/35% 触发两级稀疏网格） */
    private static final double ZOOM_MIN = 0.1D;
    private static final double ZOOM_MAX = 24.0D;

    /** 顶部对局栏高 */
    private static final int TOP_BAR_H = 16;
    private static final int BTN_W = 78;
    private static final int BTN_H = 16;

    /** 右侧竖向缩放条（与滚轮联动，可视化缩放倍率） */
    private static final int SLIDER_TRACK_W = 4;
    private static final int SLIDER_HANDLE_W = 14;
    private static final int SLIDER_HANDLE_H = 5;
    private static final int SLIDER_RIGHT = 12;
    /** 缩放条竖直范围（避开右上 Lock 与右下按钮） */
    private static final int SLIDER_MARGIN_TOP = 32;
    private static final int SLIDER_MARGIN_BOTTOM = 40;

    /** 镜头：世界坐标中心（跟随模式每帧取玩家位置；自由模式由拖动平移） */
    private double camX;
    private double camZ;
    /** 缩放倍率（相对整图适配） */
    private double zoom = 1.0D;
    /** 跟随玩家模式；拖动平移时自动切换为自由 */
    private boolean follow = true;

    // 交互状态
    private boolean dragging;
    private boolean draggingSlider;
    private double lastMouseX;
    private double lastMouseY;
    /** 当前指针屏幕坐标（render 每帧更新，供 T 键传送取光标世界坐标） */
    private double cursorX;
    private double cursorY;

    // 渲染期缓存的旋转参数（随视角刷新）
    private boolean rotate;
    private double yawCos = 1.0D;
    private double yawSin = 0.0D;

    public TacticalMapScreen() {
        super(Component.translatable("msb.tactical_map.title"));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_T) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (mc.player.getAbilities().instabuild) {
                    // 光标处世界坐标 → 服务端求该柱最高方块并传送（服务端权威）
                    double scale = currentScale();
                    double wx = camX + worldDx(cursorX - width / 2.0D, cursorY - height / 2.0D, scale);
                    double wz = camZ + worldDz(cursorX - width / 2.0D, cursorY - height / 2.0D, scale);
                    PacketDistributor.sendToServer(new TeleportRequestPayload((int) Math.floor(wx), (int) Math.floor(wz)));
                } else {
                    mc.player.displayClientMessage(Component.translatable("msb.tactical_map.tp.creative_only"), true);
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        cursorX = mouseX;
        cursorY = mouseY;
        UnitPositionsPayload units = ClientMatchState.getUnitPositions();
        MatchStatePayload state = ClientMatchState.getMatchState();

        g.fill(0, 0, width, height, COLOR_BG);

        // 旋转参数（玩家存在即可计算；罗盘在等待态也绘制）
        rotate = Config.TACTICAL_MAP_ROTATE_WITH_PLAYER.get();
        double yawRad = mc.player == null ? 0.0D : Math.toRadians(mc.player.getYRot());
        yawCos = Math.cos(yawRad);
        yawSin = Math.sin(yawRad);

        if (mc.player == null || units == null) {
            // 服务端未下发地图数据（未入对局）：显示等待提示 + 对局栏 + 罗盘
            g.drawCenteredString(font, Component.translatable("msb.tactical_map.waiting"), width / 2, height / 2, 0xFFFFFF);
            drawTopBar(g, state);
            drawCompass(g);
            return;
        }

        int viewHalf = (int) (Math.min(width, height) * VIEWPORT_RATIO) / 2;
        double baseScale = (double) viewHalf / Math.max(1, units.mapRadius());
        double scale = baseScale * zoom;
        int cx = width / 2;
        int cy = height / 2;

        if (follow) {
            camX = mc.player.getX();
            camZ = mc.player.getZ();
        }

        // 距离网格（区块边界 16 格细线 / 64 格粗线，与 F3+H 同步）
        drawGrid(g, cx, cy, scale);

        // 地图边界圆 + 控制区圆 + 出生点（世界坐标变换到屏幕）
        drawRing(g, cx, cy, scale, units.mapCenterX(), units.mapCenterZ(), units.mapRadius(), COLOR_BORDER);
        if (state != null) {
            drawRing(g, cx, cy, scale, units.mapCenterX(), units.mapCenterZ(), state.zoneRadius(), COLOR_ZONE);
        }
        drawSpawn(g, cx, cy, scale, units.mapCenterX(), units.mapCenterZ());
        drawBases(g, cx, cy, scale, state);

        // 单位点 + 标注 + 悬停检测
        int hoverIndex = -1;
        double hoverDistSq = 12.0 * 12.0;
        BlockPos self = mc.player.blockPosition();
        int own = ClientMatchState.getOwnFactionId();
        for (int i = 0; i < units.units().size(); i++) {
            UnitPositionsPayload.UnitEntry u = units.units().get(i);
            int px = sx(cx, scale, u.x(), u.z());
            int pz = sy(cy, scale, u.x(), u.z());
            if (px < -24 || px > width + 24 || pz < -24 || pz > height + 24) {
                continue; // 屏外（含标注余量）
            }
            int color = unitColor(u.factionId());
            g.fill(px - 2, pz - 2, px + 2, pz + 2, color);
            // 标注：类型字母 + 距玩家格数（轴对齐，不随地图旋转）
            int dist = (int) Math.round(Math.hypot(u.x() - self.getX(), u.z() - self.getZ()));
            g.drawString(font, unitLetter(u.factionId()) + dist, px + 4, pz - 5, color);
            // 悬停
            double d2 = (px - mouseX) * (px - mouseX) + (pz - mouseY) * (pz - mouseY);
            if (d2 < hoverDistSq) {
                hoverDistSq = d2;
                hoverIndex = i;
            }
        }

        // 玩家本人：白点 + 朝向箭头（箭头由世界朝向 → 屏幕方向，随旋转自然指向）
        int selfX = sx(cx, scale, (int) self.getX(), (int) self.getZ());
        int selfZ = sy(cy, scale, (int) self.getX(), (int) self.getZ());
        g.fill(selfX - 3, selfZ - 3, selfX + 3, selfZ + 3, COLOR_SELF);
        drawArrow(g, selfX, selfZ, mc.player.getYRot());

        drawTopBar(g, state);
        drawLockButton(g, mouseX, mouseY);
        drawLegend(g);
        drawHint(g);
        drawBottomButtons(g, mouseX, mouseY);
        drawZoomSlider(g, mouseX, mouseY);
        drawEdgeIndicators(g, cx, cy, scale, units, self);
        drawCursorInfo(g, mouseX, mouseY, scale, cx, cy, self);
        drawCompass(g);

        if (hoverIndex >= 0) {
            drawUnitTooltip(g, mouseX, mouseY, units.units().get(hoverIndex));
        }
    }

    // ===== 绘制辅助 =====

    /** 顶部对局栏：倒计时 + 三方分数（阵营色分段，整体居中） */
    private void drawTopBar(GuiGraphics g, MatchStatePayload state) {
        g.fill(0, 0, width, TOP_BAR_H + 2, COLOR_BAR);
        if (state == null) {
            return;
        }
        Component cd = Component.translatable("msb.tactical_map.countdown", state.countdownSeconds());
        Component ls = score(Component.translatable("team.msb.lonestar"), state.lonestarScore());
        Component va = score(Component.translatable("team.msb.valkyra"), state.valkyraScore());
        Component mt = score(Component.translatable("team.msb.manticore"), state.manticoreScore());
        int total = font.width(cd) + font.width(ls) + font.width(va) + font.width(mt) + 18;
        int x = Math.max(8, width / 2 - total / 2);
        x = drawSegment(g, x, cd, 0xFFFFFF, 8);
        x = drawSegment(g, x, ls, 0xFF5555, 8);
        x = drawSegment(g, x, va, 0x55AFFF, 8);
        drawSegment(g, x, mt, 0x55FF55, 8);
    }

    /** 阵营名 + 分数（追加文本，避免 lang 无 %s 时参数被丢弃） */
    private Component score(Component name, int points) {
        return name.copy().append(" " + points);
    }

    /** 绘制一段文本并返回下一个绘制 x（用于分段着色） */
    private int drawSegment(GuiGraphics g, int x, Component text, int color, int y) {
        int w = font.width(text);
        g.drawString(font, text, x, y, color);
        return x + w + 6;
    }

    /** 锁定北向 / 随视角旋转 切换按钮（右上角）：Lock 后地图上北下南左西右东 */
    private void drawLockButton(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = width - BTN_W - 8;
        int y0 = TOP_BAR_H + 8;
        boolean hover = mouseX >= x0 && mouseX <= x0 + BTN_W && mouseY >= y0 && mouseY <= y0 + BTN_H;
        g.fill(x0, y0, x0 + BTN_W, y0 + BTN_H, hover ? COLOR_BTN_HOVER : COLOR_BTN);
        Component label = Component.translatable(rotate ? "msb.tactical_map.lock" : "msb.tactical_map.unlock");
        g.drawCenteredString(font, label, x0 + BTN_W / 2, y0 + (BTN_H - font.lineHeight) / 2 + 1, 0xFFFFFF);
    }

    /** 三阵营基地方块标记（docs/02 §5.1）：阵营色安全区范围圈 + 方块点 + B 字母；坐标来自 state.basePositions（{x,z,y}×3，-1=未放置） */
    private void drawBases(GuiGraphics g, int cx, int cy, double scale, MatchStatePayload state) {
        if (state == null || state.basePositions() == null) {
            return;
        }
        int[] pos = state.basePositions();
        int[] ringColors = {0x66FF5555, 0x6655AFFF, 0x6655FF55}; // LONESTAR / VALKYRA / MANTICORE（含 alpha）
        int[] colors = {0xFF5555, 0x55AFFF, 0x55FF55};
        for (int i = 0; i < 3; i++) {
            if (pos[i * 3] == -1) {
                continue;
            }
            // 安全区范围圈（半径与服务器 Config.BASE_RADIUS 一致；drawRing 自带屏外剔除）
            drawRing(g, cx, cy, scale, pos[i * 3], pos[i * 3 + 1], Config.BASE_RADIUS.get(), ringColors[i]);
            int px = sx(cx, scale, pos[i * 3], pos[i * 3 + 1]);
            int pz = sy(cy, scale, pos[i * 3], pos[i * 3 + 1]);
            if (px < -24 || px > width + 24 || pz < -24 || pz > height + 24) {
                continue; // 屏外
            }
            g.fill(px - 3, pz - 3, px + 3, pz + 3, colors[i]);
            g.drawString(font, "B", px + 4, pz - 5, colors[i]);
        }
    }

    /** 图例（左上角） */
    private void drawLegend(GuiGraphics g) {
        int x0 = 8;
        int y0 = TOP_BAR_H + 10;
        int w = 150;
        int h = 8 * 12 + 4;
        g.fill(x0 - 3, y0 - 3, x0 + w, y0 + h, COLOR_LEGEND_BG);
        drawLegendLine(g, x0, y0, COLOR_SPAWN, "msb.tactical_map.legend.spawn");
        drawLegendLine(g, x0, y0 + 12, COLOR_ZONE, "msb.tactical_map.legend.zone");
        drawLegendLine(g, x0, y0 + 24, COLOR_BORDER, "msb.tactical_map.legend.boundary");
        drawLegendLine(g, x0, y0 + 36, 0xFF5555, "msb.tactical_map.legend.base");
        drawLegendLine(g, x0, y0 + 48, COLOR_FRIENDLY, "msb.tactical_map.legend.friendly");
        drawLegendLine(g, x0, y0 + 60, COLOR_ENEMY, "msb.tactical_map.legend.enemy");
        drawLegendLine(g, x0, y0 + 72, COLOR_NEUTRAL, "msb.tactical_map.legend.neutral");
        drawLegendLine(g, x0, y0 + 84, COLOR_SELF, "msb.tactical_map.legend.self");
    }

    private void drawLegendLine(GuiGraphics g, int x, int y, int color, String key) {
        g.fill(x, y, x + 6, y + 6, color);
        g.drawString(font, Component.translatable(key), x + 10, y - 1, 0xFFFFFF);
    }

    /** 底部操作提示 */
    private void drawHint(GuiGraphics g) {
        g.drawString(font, Component.translatable("msb.tactical_map.hint"), 8, height - 12, 0x7F7F7F);
    }

    /** 右下角两个按钮：圈居中 / 我（跟随-自由切换） */
    private void drawBottomButtons(GuiGraphics g, int mouseX, int mouseY) {
        int gap = 4;
        int x0 = width - 2 * (BTN_W + gap) - 8;
        int y0 = height - BTN_H - 8;
        int x1 = x0 + BTN_W + gap;
        boolean h1 = mouseX >= x0 && mouseX <= x0 + BTN_W && mouseY >= y0 && mouseY <= y0 + BTN_H;
        boolean h2 = mouseX >= x1 && mouseX <= x1 + BTN_W && mouseY >= y0 && mouseY <= y0 + BTN_H;
        g.fill(x0, y0, x0 + BTN_W, y0 + BTN_H, h1 ? COLOR_BTN_HOVER : COLOR_BTN);
        g.drawCenteredString(font, Component.translatable("msb.tactical_map.btn.zone_center"), x0 + BTN_W / 2, y0 + (BTN_H - font.lineHeight) / 2 + 1, 0xFFFFFF);
        g.fill(x1, y0, x1 + BTN_W, y0 + BTN_H, h2 ? COLOR_BTN_HOVER : COLOR_BTN);
        g.drawCenteredString(font, Component.translatable(follow ? "msb.tactical_map.follow" : "msb.tactical_map.free"), x1 + BTN_W / 2, y0 + (BTN_H - font.lineHeight) / 2 + 1, 0xFFFFFF);
    }

    /** 右侧竖向缩放条：与滚轮联动，点击/拖动改变缩放并可视化当前倍率（对数刻度） */
    private void drawZoomSlider(GuiGraphics g, int mouseX, int mouseY) {
        int trackX = width - SLIDER_RIGHT - SLIDER_TRACK_W;
        int y0 = sliderY0();
        int y1 = sliderY1();
        double t = zoomT();
        int knobY = y1 - (int) Math.round(t * (y1 - y0)); // 顶部=最大放大，底部=最小缩小
        // 轨道
        g.fill(trackX, y0, trackX + SLIDER_TRACK_W, y1, COLOR_BTN);
        // 进度填充（自底部最小缩放至当前）
        if (t > 0.001) {
            g.fill(trackX, y1 - (int) Math.round(t * (y1 - y0)), trackX + SLIDER_TRACK_W, y1, 0x70FFFFFF);
        }
        // 滑块
        int hx0 = trackX - (SLIDER_HANDLE_W - SLIDER_TRACK_W) / 2;
        boolean hover = mouseX >= hx0 && mouseX <= hx0 + SLIDER_HANDLE_W
                && mouseY >= knobY - SLIDER_HANDLE_H && mouseY <= knobY + SLIDER_HANDLE_H;
        g.fill(hx0, knobY - SLIDER_HANDLE_H, hx0 + SLIDER_HANDLE_W, knobY + SLIDER_HANDLE_H,
                hover ? COLOR_BTN_HOVER : COLOR_BTN);
        g.fill(hx0, knobY - 1, hx0 + SLIDER_HANDLE_W, knobY + 1, 0xFFFFFFFF);
        // 顶部百分比（放大程度可视化）
        String pct = Math.round(zoom * 100) + "%";
        g.drawCenteredString(font, pct, trackX + SLIDER_TRACK_W / 2, y0 - 12, 0xFFFFFF);
    }

    /** 缩放条顶部 Y */
    private int sliderY0() {
        return TOP_BAR_H + SLIDER_MARGIN_TOP;
    }

    /** 缩放条底部 Y */
    private int sliderY1() {
        return height - BTN_H - SLIDER_MARGIN_BOTTOM;
    }

    /** 当前缩放的对数归一化 0..1（与滚轮乘法缩放一致，拖动更均匀） */
    private double zoomT() {
        double logMin = Math.log(ZOOM_MIN);
        double logMax = Math.log(ZOOM_MAX);
        return (Math.log(zoom) - logMin) / (logMax - logMin);
    }

    /** 滑块 Y → 缩放倍率（对数反插值） */
    private double zoomFromY(double mouseY) {
        int y0 = sliderY0();
        int y1 = sliderY1();
        double t = Math.clamp((y1 - mouseY) / (double) (y1 - y0), 0.0, 1.0); // 顶部=最大放大
        double logMin = Math.log(ZOOM_MIN);
        double logMax = Math.log(ZOOM_MAX);
        return Math.exp(logMin + t * (logMax - logMin));
    }

    /** 缩放条命中区域（含滑块扩展宽与轨道整段高） */
    private boolean inSlider(double mouseX, double mouseY) {
        int trackX = width - SLIDER_RIGHT - SLIDER_TRACK_W;
        int hx0 = trackX - (SLIDER_HANDLE_W - SLIDER_TRACK_W) / 2;
        return mouseX >= hx0 && mouseX <= hx0 + SLIDER_HANDLE_W
                && mouseY >= sliderY0() - 6 && mouseY <= sliderY1() + 6;
    }

    /** 屏幕边缘指示器：我 / 圈中心不在屏幕内时，在边缘显示「名称 + 距离」 */
    private void drawEdgeIndicators(GuiGraphics g, int cx, int cy, double scale, UnitPositionsPayload units, BlockPos self) {
        drawEdgeTag(g, cx, cy, scale, (int) self.getX(), (int) self.getZ(), "msb.tactical_map.edge.me", COLOR_SELF);
        drawEdgeTag(g, cx, cy, scale, units.mapCenterX(), units.mapCenterZ(), "msb.tactical_map.edge.zone", COLOR_ZONE);
    }

    /** 单个边缘指示器：目标世界点 → 屏幕位置，超出屏幕则投影到边缘显示标签 */
    private void drawEdgeTag(GuiGraphics g, int cx, int cy, double scale, int wx, int wz, String key, int color) {
        int margin = 40;
        int px = sx(cx, scale, wx, wz);
        int py = sy(cy, scale, wx, wz);
        // 在屏幕内（含边缘余量）则不显示
        if (px >= margin && px <= width - margin && py >= TOP_BAR_H + margin && py <= height - margin) {
            return;
        }
        // 沿中心→目标方向投影到屏幕边缘（等比钳制）
        double dx = px - cx;
        double dy = py - cy;
        double maxDx = width / 2.0 - margin;
        double maxDy = height / 2.0 - margin;
        double t = 1.0;
        if (Math.abs(dx) > maxDx) {
            t = Math.min(t, maxDx / Math.abs(dx));
        }
        if (Math.abs(dy) > maxDy) {
            t = Math.min(t, maxDy / Math.abs(dy));
        }
        if (t >= 1.0) {
            return; // 中心区域内，无需指示
        }
        int ix = (int) Math.round(cx + dx * t);
        int iy = (int) Math.round(cy + dy * t);
        int dist = (int) Math.round(Math.hypot(wx - camX, wz - camZ));
        Component label = Component.translatable(key, dist);
        int tw = font.width(label);
        g.fill(ix - tw / 2 - 3, iy - 6, ix + tw / 2 + 3, iy + 5, COLOR_EDGE_TAG_BG);
        g.drawCenteredString(font, label, ix, iy - 4, color);
    }

    /** 距离网格：小块=1 区块（16 格，与 F3+H 区块边界同步），大块=4×4 区块（64 格）粗线，
     *  超大块=每级是上级的 4×4（16×16 区块 = 256 格），特大块=超大块的 2×2（32×32 区块 = 1024 格）；
     *  两级稀疏（防网格过密）：缩小到 99% 时隐藏小块、大块变细；缩小到 35% 时再隐藏大块、超大块变细，
     *  特大块始终粗线兜底 */
    private void drawGrid(GuiGraphics g, int cx, int cy, double scale) {
        int[] b = worldBounds(cx, cy, scale);
        int minX = b[0], maxX = b[1], minZ = b[2], maxZ = b[3];
        boolean sparse1 = zoom <= 0.99D; // 第一级稀疏：缩小到整图适配的 99%
        boolean sparse2 = zoom <= 0.35D; // 第二级稀疏：缩小到整图适配的 35%
        // 竖线：世界 X = 区块边界（16 的倍数）
        for (int k = Math.floorDiv(minX, CHUNK_SIZE); k <= Math.floorDiv(maxX, CHUNK_SIZE); k++) {
            int[] style = gridStyle(k, sparse1, sparse2);
            if (style != null) {
                gridLine(g, k * CHUNK_SIZE, minZ, k * CHUNK_SIZE, maxZ, cx, cy, scale, style[0], style[1]);
            }
        }
        // 横线：世界 Z = 区块边界
        for (int k = Math.floorDiv(minZ, CHUNK_SIZE); k <= Math.floorDiv(maxZ, CHUNK_SIZE); k++) {
            int[] style = gridStyle(k, sparse1, sparse2);
            if (style != null) {
                gridLine(g, minX, k * CHUNK_SIZE, maxX, k * CHUNK_SIZE, cx, cy, scale, style[0], style[1]);
            }
        }
    }

    /** 网格线样式 {颜色, 线宽}；返回 null 表示该线隐藏。
     *  特大块（1024 格倍数）始终粗线兜底；两级稀疏逐级降级/隐藏：
     *  99% 时大块降为细线、小块隐藏；35% 时超大块降为细线、大块隐藏。 */
    private int[] gridStyle(int chunkIndex, boolean sparse1, boolean sparse2) {
        if (chunkIndex % MEGA_GRID_CHUNKS == 0) {
            return new int[]{COLOR_GRID_MAJOR, 2};
        }
        if (chunkIndex % SUPER_GRID_CHUNKS == 0) {
            return sparse2 ? new int[]{COLOR_GRID_MINOR, 1} : new int[]{COLOR_GRID_MAJOR, 2};
        }
        if (chunkIndex % MAJOR_GRID_CHUNKS == 0) {
            if (sparse2) {
                return null;
            }
            return sparse1 ? new int[]{COLOR_GRID_MINOR, 1} : new int[]{COLOR_GRID_MAJOR, 2};
        }
        return sparse1 ? null : new int[]{COLOR_GRID_MINOR, 1};
    }

    /** 屏幕四角逆变换的世界包围盒 [minX, maxX, minZ, maxZ]（网格线覆盖范围） */
    private int[] worldBounds(int cx, int cy, double scale) {
        double[] xs = new double[4];
        double[] zs = new double[4];
        int k = 0;
        for (double px : new double[]{-cx, width - cx}) {
            for (double py : new double[]{-cy, height - cy}) {
                xs[k] = camX + worldDx(px, py, scale);
                zs[k] = camZ + worldDz(px, py, scale);
                k++;
            }
        }
        double minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        double maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        double minZ = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
        double maxZ = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        return new int[]{(int) Math.floor(minX), (int) Math.ceil(maxX), (int) Math.floor(minZ), (int) Math.ceil(maxZ)};
    }

    /** 画一条网格线段：旋转屏幕坐标后一次 fill 矩形（每条线仅 1 次 drawCall，性能恒定） */
    private void gridLine(GuiGraphics g, int wx1, int wz1, int wx2, int wz2, int cx, int cy, double scale, int color, int thickness) {
        int ax = sx(cx, scale, wx1, wz1);
        int ay = sy(cy, scale, wx1, wz1);
        int bx = sx(cx, scale, wx2, wz2);
        int by = sy(cy, scale, wx2, wz2);
        double len = Math.hypot(bx - ax, by - ay);
        if (len < 1.0) {
            return;
        }
        double angle = Math.atan2(by - ay, bx - ax);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(ax, ay, 0);
        pose.mulPose(Axis.ZP.rotation((float) angle));
        g.fill(0, 0, (int) Math.ceil(len) + 1, thickness, color);
        pose.popPose();
    }

    /** 左下角 4 向罗盘（始终显示）：N 恒指世界北方，随地图旋转联动 */
    private void drawCompass(GuiGraphics g) {
        int size = 52;
        int x0 = 8;
        int y0 = height - size - 12 - 8; // hint 之上
        int cx = x0 + size / 2;
        int cy = y0 + size / 2;
        g.fill(x0, y0, x0 + size, y0 + size, COLOR_LEGEND_BG);
        // 轴交叉线（N-S 与 E-W，随旋转映射）
        double[] n = dirToScreen(0, -1); // 北 = 世界 -Z
        double[] e = dirToScreen(1, 0);  // 东 = 世界 +X
        compassAxis(g, cx, cy, n[0], n[1], 15, 0x40FFFFFF);
        compassAxis(g, cx, cy, e[0], e[1], 15, 0x40FFFFFF);
        // 四向字母（N 红、其余白）
        compassLabel(g, cx, cy, n[0], n[1], 21, "N", 0xFFFF3F3F);
        compassLabel(g, cx, cy, -n[0], -n[1], 21, "S", 0xFFFFFFFF);
        compassLabel(g, cx, cy, e[0], e[1], 21, "E", 0xFFFFFFFF);
        compassLabel(g, cx, cy, -e[0], -e[1], 21, "W", 0xFFFFFFFF);
    }

    /** 世界单位方向 (dx,dz) → 屏幕方向 (vx,vy)（与地图渲染同一变换，模长保持 1） */
    private double[] dirToScreen(double dx, double dz) {
        double vx = rotate ? -dx * yawCos - dz * yawSin : dx;
        double vy = rotate ? dx * yawSin - dz * yawCos : dz;
        return new double[]{vx, vy};
    }

    /** 罗盘轴线：沿指定屏幕方向画一条过中心的细线 */
    private void compassAxis(GuiGraphics g, int cx, int cy, double vx, double vy, int halfLen, int color) {
        double len = Math.hypot(vx, vy);
        if (len < 1.0E-4) {
            return;
        }
        double ux = vx / len;
        double uy = vy / len;
        double angle = Math.atan2(uy, ux);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotation((float) angle));
        g.fill(-halfLen, 0, halfLen, 1, color);
        pose.popPose();
    }

    /** 罗盘字母：沿指定屏幕方向距中心 r 处绘制 */
    private void compassLabel(GuiGraphics g, int cx, int cy, double vx, double vy, int r, String ch, int color) {
        double len = Math.hypot(vx, vy);
        if (len < 1.0E-4) {
            return;
        }
        int x = (int) Math.round(cx + vx / len * r);
        int y = (int) Math.round(cy + vy / len * r);
        g.drawCenteredString(font, ch, x, y - 4, color);
    }

    /** 住处旁实时：光标世界坐标 + 距玩家距离 */
    private void drawCursorInfo(GuiGraphics g, int mouseX, int mouseY, double scale, int cx, int cy, BlockPos self) {
        double wx = camX + worldDx(mouseX - cx, mouseY - cy, scale);
        double wz = camZ + worldDz(mouseX - cx, mouseY - cy, scale);
        int dist = (int) Math.round(Math.hypot(wx - self.getX(), wz - self.getZ()));
        Component line = Component.translatable("msb.tactical_map.cursor",
                (int) Math.floor(wx), (int) Math.floor(wz), dist);
        g.drawString(font, line, mouseX + 10, mouseY + 8, 0xFFFFFF);
    }

    /** 悬停单位工具条（光标上方） */
    private void drawUnitTooltip(GuiGraphics g, int mouseX, int mouseY, UnitPositionsPayload.UnitEntry u) {
        Faction f = Faction.fromId(u.factionId());
        int dist = (int) Math.round(Math.hypot(u.x() - camX, u.z() - camZ));
        Component top = Component.translatable(f.getDisplayKey()).append(" ").append(Component.translatable("msb.tactical_map.unit"));
        Component distLine = Component.translatable("msb.tactical_map.distance", dist);
        Component statusLine = Component.translatable("msb.tactical_map.status",
                Component.translatable(u.engaged() ? "msb.tactical_map.engaged" : "msb.tactical_map.hidden"));
        int w = Math.max(font.width(top), Math.max(font.width(distLine), font.width(statusLine))) + 8;
        int h = 12 * 3 + 6;
        int x = Math.min(mouseX + 8, width - w - 2);
        int y = mouseY - h - 8;
        if (y < 0) {
            y = mouseY + 12;
        }
        g.fill(x, y, x + w, y + h, COLOR_TOOLTIP_BG);
        g.drawString(font, top, x + 4, y + 3, f.getChatColor().getColor());
        g.drawString(font, distLine, x + 4, y + 15, 0xFFFFFF);
        g.drawString(font, statusLine, x + 4, y + 27, u.engaged() ? COLOR_ENEMY : COLOR_NEUTRAL);
    }

    /** 玩家朝向箭头：世界前向向量经同一变换映射到屏幕（旋转模式恒指上，固定北向按 yaw 指向） */
    private void drawArrow(GuiGraphics g, int selfX, int selfZ, float yawDeg) {
        double a = Math.toRadians(yawDeg);
        double fX = -Math.sin(a); // 世界前向 X（yaw=0 朝南 +Z）
        double fZ = Math.cos(a);  // 世界前向 Z
        // 屏幕方向 = sx/sy 变换对单位向量的作用，与地图渲染共享同一旋转参数
        double vx = rotate ? -fX * yawCos - fZ * yawSin : fX;
        double vy = rotate ? fX * yawSin - fZ * yawCos : fZ;
        double len = Math.hypot(vx, vy);
        if (len < 1.0E-4) {
            return;
        }
        double dx = vx / len;
        double dy = vy / len;

        // 朝向角（屏幕坐标，y 向下）：0 → +X
        double angle = Math.atan2(dy, dx);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(selfX, selfZ, 0);
        pose.mulPose(Axis.ZP.rotation((float) angle));
        g.fill(1, -1, 9, 1, COLOR_ARROW);
        g.fill(5, -3, 10, 3, COLOR_ARROW);
        pose.popPose();
    }

    // ===== 坐标变换（世界格 → 屏幕像素；旋转由周边视角控制） =====

    /** 世界 X → 屏幕 X（含旋转/平移/缩放） */
    private int sx(int cx, double scale, int wx, int wz) {
        double dx = wx - camX;
        double dz = wz - camZ;
        return rotate
                ? (int) Math.round(cx - (dx * yawCos + dz * yawSin) * scale)
                : (int) Math.round(cx + dx * scale);
    }

    /** 世界 Z → 屏幕 Y */
    private int sy(int cy, double scale, int wx, int wz) {
        double dx = wx - camX;
        double dz = wz - camZ;
        return rotate
                ? (int) Math.round(cy + (dx * yawSin - dz * yawCos) * scale)
                : (int) Math.round(cy + dz * scale);
    }

    /** 屏幕偏移(px,py) → 世界偏移 X 分量（光标/平移共用） */
    private double worldDx(double px, double py, double scale) {
        double nx = px / scale;
        double ny = py / scale;
        return rotate ? -nx * yawCos + ny * yawSin : nx;
    }

    /** 屏幕偏移(px,py) → 世界偏移 Z 分量 */
    private double worldDz(double px, double py, double scale) {
        double nx = px / scale;
        double ny = py / scale;
        return rotate ? -nx * yawSin - ny * yawCos : ny;
    }

    /** 出生点（= 地图中心，按注视范围绘制） */
    private void drawSpawn(GuiGraphics g, int cx, int cy, double scale, int spawnX, int spawnZ) {
        int px = sx(cx, scale, spawnX, spawnZ);
        int pz = sy(cy, scale, spawnX, spawnZ);
        if (px >= -10 && px <= width + 10 && pz >= -10 && pz <= height + 10) {
            g.fill(px - 3, pz - 3, px + 3, pz + 3, COLOR_SPAWN);
        }
    }

    /** 以指定世界点为圆心画圆环：贴图渲染（纯白环 tint 上色，线宽随屏幕半径等比缩放） */
    private void drawRing(GuiGraphics g, int cx, int cy, double scale,
            int worldCenterX, int worldCenterZ, int worldRadius, int color) {
        int px = sx(cx, scale, worldCenterX, worldCenterZ);
        int pz = sy(cy, scale, worldCenterX, worldCenterZ);
        double r = worldRadius * scale;
        if (r < 3.0) {
            return; // 屏幕半径过小，不可见
        }
        // 屏幕外剔除（包围盒粗判）
        if (px + r < -50 || px - r > width + 50 || pz + r < -50 || pz - r > height + 50) {
            return;
        }
        // 贴图外沿贴边：直径 = 屏幕直径 2r，以圆心为中心缩放绘制（tint 上色见 drawRingTexture）
        int size = (int) Math.ceil(2.0 * r);
        drawRingTexture(px - size / 2, pz - size / 2, size, color);
    }

    /**
     * 贴图圆环 tint 绘制：1.21.1 的 GuiGraphics 无带 tint 的 blit 重载，
     * 手动提交带顶点色的 quad（与 GUI 渲染同一 PositionColorTex shader，顶点色即 tint）。
     */
    private void drawRingTexture(int x, int y, int size, int color) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, RING_TEXTURE);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(x, y, 0).setUv(0.0F, 0.0F).setColor(r, g, b, a);
        builder.addVertex(x, y + size, 0).setUv(0.0F, 1.0F).setColor(r, g, b, a);
        builder.addVertex(x + size, y + size, 0).setUv(1.0F, 1.0F).setColor(r, g, b, a);
        builder.addVertex(x + size, y, 0).setUv(1.0F, 0.0F).setColor(r, g, b, a);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /** 单位颜色：本方阵营蓝、未分配灰、其余红（配色与 docs/02 §3.10 IFF 一致） */
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

    /** 单位标注字母：本方 T、未分配 ?、敌方 E（与服务端敌情规则一致） */
    private String unitLetter(int factionId) {
        int own = ClientMatchState.getOwnFactionId();
        if (factionId == own) {
            return "T";
        }
        if (factionId == Faction.NONE.getId()) {
            return "?";
        }
        return "E";
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 右侧竖向缩放条（优先于按钮/平移判断）
            if (inSlider(mouseX, mouseY)) {
                zoom = zoomFromY(mouseY);
                draggingSlider = true;
                return true;
            }
            // 右上角 锁定北向 / 随视角旋转 按钮
            int x0 = width - BTN_W - 8;
            int y0 = TOP_BAR_H + 8;
            if (mouseX >= x0 && mouseX <= x0 + BTN_W && mouseY >= y0 && mouseY <= y0 + BTN_H) {
                rotate = !rotate;
                Config.TACTICAL_MAP_ROTATE_WITH_PLAYER.set(rotate); // 写回配置，跨会话记住
                return true;
            }
            // 右下角 圈居中 / 我（跟随-自由）按钮
            int gap = 4;
            int bx0 = width - 2 * (BTN_W + gap) - 8;
            int by0 = height - BTN_H - 8;
            int bx1 = bx0 + BTN_W + gap;
            UnitPositionsPayload units = ClientMatchState.getUnitPositions();
            if (units != null
                    && mouseX >= bx0 && mouseX <= bx0 + BTN_W && mouseY >= by0 && mouseY <= by0 + BTN_H) {
                // 圈居中：切自由视角并把镜头移到控制区中心
                follow = false;
                camX = units.mapCenterX();
                camZ = units.mapCenterZ();
                return true;
            }
            if (mouseX >= bx1 && mouseX <= bx1 + BTN_W && mouseY >= by0 && mouseY <= by0 + BTN_H) {
                follow = !follow; // 我：跟随-自由切换（镜头居中玩家 / 自由平移）
                return true;
            }
        }
        if (button == 0 || button == 2) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider) {
            zoom = zoomFromY(mouseY);
            return true;
        }
        if (dragging) {
            if (follow) {
                follow = false; // 拖动即切自由视角
            }
            double px = mouseX - lastMouseX;
            double py = mouseY - lastMouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            camX -= worldDx(px, py, currentScale());
            camZ -= worldDz(px, py, currentScale());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider) {
            draggingSlider = false;
            return true;
        }
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        zoom = Math.clamp(zoom * (deltaY > 0 ? 1.15D : 1 / 1.15D), ZOOM_MIN, ZOOM_MAX);
        return true;
    }

    /** 当前缩放换算：整图适配基础 × 缩放倍率（需与渲染同参，此处按本帧推断） */
    private double currentScale() {
        Minecraft mc = Minecraft.getInstance();
        int viewHalf = (int) (Math.min(width, height) * VIEWPORT_RATIO) / 2;
        UnitPositionsPayload units = ClientMatchState.getUnitPositions();
        double base = units == null ? viewHalf : (double) viewHalf / Math.max(1, units.mapRadius());
        return base * zoom;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}