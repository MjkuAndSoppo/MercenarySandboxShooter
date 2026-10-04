package com.mercenarysandbox.msb.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mercenarysandbox.msb.network.ShopDataPayload;
import com.mercenarysandbox.msb.network.ShopResultPayload;
import com.mercenarysandbox.msb.network.ShopStoragePayload;
import com.mercenarysandbox.msb.network.ShopTradePayload;
import com.mercenarysandbox.msb.shop.EquipType;
import com.mercenarysandbox.msb.shop.GunType;
import com.mercenarysandbox.msb.shop.ShopCategory;
import com.mercenarysandbox.msb.shop.ShopCode;
import com.mercenarysandbox.msb.shop.ShopSubtype;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 军火商店（B 键开关，GuiGraphics 自绘；docs §5，原型 ui-prototype/shop 的落地实现）。
 *
 * <p>四区布局（GUI 单位 470×260）：分类栏 72 | 中间栏（商品 6 列滚动格 + 购买/出售面板 + 储存格 5 列滚动格）| 玩家背包映射 168。
 * 绿框 = 可无损卖回（购买后未取出的件数）；购买/出售/取回/存入全部由服务端校验。
 */
public final class ShopScreen extends Screen {

    // ===== 布局（GUI 单位） =====
    private static final int WIN_W = 470;
    private static final int WIN_H = 260;
    private static final int PAD = 3;
    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 20;
    private static final int BODY_H = WIN_H - HEADER_H - FOOTER_H - PAD * 2;
    private static final int LEFT_W = 72;
    private static final int GAP = 6;
    private static final int MID_W = 212;
    private static final int RIGHT_W = 168;
    private static final int MARKET_H = 112;
    private static final int MARKET_HEAD = 11;
    /** 子分类筛选条（枪械栏）：按钮高与行间距 */
    private static final int CHIP_H = 13;
    private static final int CHIP_GAP = 2;
    private static final int LOWER_H = BODY_H - MARKET_H - 4;
    private static final int PANEL_W = 97;
    private static final int STO_W = MID_W - PANEL_W - 4;
    private static final int CELL = 28;
    private static final int CELL_GAP = 4;
    private static final int CELL_COLS = 6;
    private static final int SLOT = 16;
    private static final int SLOT_GAP = 2;
    private static final int STO_COLS = 5;
    private static final int GRID_W = CELL_COLS * CELL + (CELL_COLS - 1) * CELL_GAP;
    private static final int STO_GRID_W = STO_COLS * SLOT + (STO_COLS - 1) * SLOT_GAP;
    private static final int BUTTON_H = 15;
    /** 自适应放大：目标占屏比 + 缩放钳制（1920×1080 下约为 1.25×~1.75×，小窗口自动缩小保证不裁切） */
    private static final float SCALE_FILL = 0.94F;
    private static final float SCALE_MIN = 0.6F;
    private static final float SCALE_MAX = 1.75F;

    // ===== 色板 =====
    private static final int C_DIM = 0x80101010;
    private static final int C_WIN_BG = 0xE00A0C10;
    private static final int C_PANEL = 0xC012161E;
    private static final int C_BORDER = 0x402A3240;
    private static final int C_BORDER_STRONG = 0x80303C4E;
    private static final int C_SLOT = 0xFF14171D;
    private static final int C_SLOT_HOVER = 0xFF1F2733;
    private static final int C_SEL = 0xFFF2B13C;
    private static final int C_SEL_BG = 0x382A1F0A;
    private static final int C_HOVER = 0x14FFFFFF;
    private static final int C_TEXT = 0xFFE8EDF4;
    private static final int C_TEXT_SUB = 0xFF8B98AB;
    private static final int C_TEXT_DIM = 0xFF5A667A;
    private static final int C_GOLD = 0xFFF2B13C;
    private static final int C_GREEN = 0xFF4ADE80;
    private static final int C_HONOR = 0xFFA78BFA;
    private static final int C_RED = 0xFFFF5A4D;
    private static final int C_MASK = 0x90000000;
    private static final int C_BTN_TEXT = 0xFF14161C;

    private enum Kind {
        NONE, CATALOG, STORAGE, PLAYER
    }

    /** 当前选中：商品 / 储存格堆叠 / 玩家栏槽位 */
    private static final class Sel {
        Kind kind = Kind.NONE;
        ResourceLocation item;
        int slot;
        ShopTradePayload.Zone zone;
    }

    private ShopCategory category = ShopCategory.GUNS;
    private final Sel sel = new Sel();
    /** 子分类筛选（null = 取目录中第一个真实子分类；枪械栏 / 装备栏共用） */
    private ShopSubtype subFilter;
    /** 双击判定：上次点击的格标识与时间 */
    private String lastClickKey = "";
    private long lastClickAt;
    private int marketScroll;
    private int storageScroll;
    private int qty = 1;
    /** 绘制用窗口左上（缩放块内恒为 0,0） */
    private int x0;
    private int y0;
    /** 自适应缩放与窗口原点（屏幕像素） */
    private float uiScale = 1.0F;
    private float originX;
    private float originY;

    // 悬停（每帧重置）
    private ShopDataPayload.Entry hoveredEntry;
    private ShopStoragePayload.Stack hoveredStack;
    private int hoveredRefund;
    private ItemStack hoveredPlayerStack = ItemStack.EMPTY;

    public ShopScreen() {
        super(Component.translatable("key.msb.shop"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===== 几何 =====

    private int leftX() {
        return x0 + PAD;
    }

    private int midX() {
        return x0 + PAD + LEFT_W + GAP;
    }

    private int rightX() {
        return x0 + PAD + LEFT_W + GAP + MID_W + GAP;
    }

    private int bodyY() {
        return y0 + HEADER_H + PAD;
    }

    private int marketY() {
        return bodyY();
    }

    private int lowerY() {
        return bodyY() + MARKET_H + 4;
    }

    private int stoX() {
        return midX() + PANEL_W + 4;
    }

    private int marketViewX() {
        return midX() + 3;
    }

    /** 商品格视口顶部：有筛选条时下移（占用其行高） */
    private int marketViewY() {
        int rows = chipRows();
        return rows == 0 ? marketY() + MARKET_HEAD + 1 : chipTop() + rows * (CHIP_H + CHIP_GAP) + 1;
    }

    private int marketViewW() {
        return MID_W - 6;
    }

    private int marketViewH() {
        return marketY() + MARKET_H - 5 - marketViewY();
    }

    // ===== 子分类筛选条（枪械栏 / 装备栏） =====

    /** 该分类是否带子分类筛选条 */
    private static boolean hasSubtypes(ShopCategory category) {
        return category == ShopCategory.GUNS || category == ShopCategory.EQUIPMENT;
    }

    /** 按分类还原子分类枚举（传输序 → 枚举；无子分类的分类返回 null） */
    private static ShopSubtype subtypeOf(ShopCategory category, int ordinal) {
        if (category == ShopCategory.GUNS) {
            return GunType.byOrdinal(ordinal);
        }
        if (category == ShopCategory.EQUIPMENT) {
            return EquipType.byOrdinal(ordinal);
        }
        return null;
    }

    /** 筛选条按钮（顺序：目录中实际存在的子分类；超出宽度自动折行） */
    private List<Chip> chips() {
        List<Chip> list = new ArrayList<>();
        if (!hasSubtypes(category)) {
            return list;
        }
        List<ShopSubtype> ordered = subChips();
        if (ordered.isEmpty()) {
            return list;
        }
        int maxX = midX() + MID_W - 5;
        int x = marketViewX();
        int cy = chipTop();
        for (ShopSubtype type : ordered) {
            int w = tw(chipLabel(type)) + 8;
            if (x + w > maxX && x > marketViewX()) {
                x = marketViewX();
                cy += CHIP_H + CHIP_GAP;
            }
            list.add(new Chip(type, x, cy, w));
            x += w + 3;
        }
        return list;
    }

    /** 当前生效的子分类（未选择时取目录中第一个真实子分类，不再有「全部」档） */
    private ShopSubtype activeFilter() {
        if (subFilter != null) {
            return subFilter;
        }
        List<ShopSubtype> types = subChips();
        return types.isEmpty() ? null : types.get(0);
    }

    /** 筛选条行数（0 = 不显示筛选条） */
    private int chipRows() {
        List<Chip> chips = chips();
        if (chips.isEmpty()) {
            return 0;
        }
        int rows = 1;
        for (Chip chip : chips) {
            rows = Math.max(rows, (chip.y() - chipTop()) / (CHIP_H + CHIP_GAP) + 1);
        }
        return rows;
    }

    private int chipTop() {
        return marketY() + MARKET_HEAD - 1;
    }

    /** 当前分类目录中实际出现过的子分类（按枚举顺序，只列有货的） */
    private List<ShopSubtype> subChips() {
        List<ShopSubtype> list = new ArrayList<>();
        for (ShopDataPayload.Entry entry : ClientShopData.entriesOf(category)) {
            ShopSubtype type = subtypeOf(category, entry.subtype());
            if (type != null && !list.contains(type)) {
                list.add(type);
            }
        }
        return list;
    }

    private static Component chipLabel(ShopSubtype type) {
        return Component.translatable(type.getLangKey());
    }

    private void drawChips(GuiGraphics g, int mouseX, int mouseY) {
        ShopSubtype activeType = activeFilter();
        for (Chip chip : chips()) {
            boolean active = chip.type() == activeType;
            boolean hover = hit(mouseX, mouseY, chip.x(), chip.y(), chip.w(), CHIP_H);
            g.fill(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + CHIP_H,
                    active ? C_SEL_BG : (hover ? C_SLOT_HOVER : C_SLOT));
            frame(g, chip.x(), chip.y(), chip.w(), CHIP_H, active ? C_SEL : (hover ? C_BORDER_STRONG : C_BORDER));
            Component label = chipLabel(chip.type());
            drawTextV(g, label, chip.x() + (chip.w() - tw(label)) / 2, chip.y(), CHIP_H,
                    active ? C_GOLD : C_TEXT_SUB);
        }
    }

    /** 筛选条按钮矩形（绘制与命中判定共用，避免漂移） */
    private record Chip(ShopSubtype type, int x, int y, int w) {
    }

    /** 当前分类的可见商品（枪械栏 / 装备栏按子分类筛选） */
    private List<ShopDataPayload.Entry> visibleItems() {
        List<ShopDataPayload.Entry> all = ClientShopData.entriesOf(category);
        ShopSubtype filter = hasSubtypes(category) ? activeFilter() : null;
        if (filter == null) {
            return all;
        }
        List<ShopDataPayload.Entry> list = new ArrayList<>();
        for (ShopDataPayload.Entry entry : all) {
            if (entry.subtype() == filter.ordinal()) {
                list.add(entry);
            }
        }
        return list;
    }

    private int stoViewX() {
        return stoX() + 3;
    }

    private int stoViewY() {
        return lowerY() + 17;
    }

    private int stoViewW() {
        return STO_W - 6;
    }

    private int stoViewH() {
        return LOWER_H - 20;
    }

    // 右侧玩家栏纵向锚点（绘制与点击共用，避免漂移）
    private int colX() {
        return rightX() + 4;
    }

    private int equipGridY() {
        return bodyY() + 23;
    }

    private int accGridY() {
        return equipGridY() + 2 * SLOT + SLOT_GAP + 14;
    }

    private int mainGridY() {
        return accGridY() + SLOT + 16;
    }

    private int hotbarGridY() {
        return mainGridY() + 3 * SLOT + 2 * SLOT_GAP + 14;
    }

    /** 按当前 GUI 尺寸计算缩放与居中位置（1/8 步进，兼顾清晰度） */
    private void updateLayout() {
        float k = Math.min((width * SCALE_FILL) / WIN_W, (height * SCALE_FILL) / WIN_H);
        k = Mth.clamp(k, SCALE_MIN, SCALE_MAX);
        uiScale = Math.round(k * 8.0F) / 8.0F;
        originX = (width - WIN_W * uiScale) / 2.0F;
        originY = (height - WIN_H * uiScale) / 2.0F;
    }

    /** 屏幕坐标 → 窗口设计坐标 */
    private double toLocalX(double screenX) {
        return (screenX - originX) / uiScale;
    }

    private double toLocalY(double screenY) {
        return (screenY - originY) / uiScale;
    }

    /** 裁剪区（把设计坐标转换为屏幕像素；scissor 不随 pose 缩放，必须手动换算） */
    private void scissorOn(GuiGraphics g, int lx1, int ly1, int lx2, int ly2) {
        int sx1 = (int) Math.floor(originX + lx1 * uiScale);
        int sy1 = (int) Math.floor(originY + ly1 * uiScale);
        int sx2 = (int) Math.ceil(originX + lx2 * uiScale);
        int sy2 = (int) Math.ceil(originY + ly2 * uiScale);
        g.enableScissor(Math.max(0, sx1), Math.max(0, sy1), Math.min(width, sx2), Math.min(height, sy2));
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

    private void box(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, C_PANEL);
        frame(g, x, y, w, h, C_BORDER);
    }

    private static String fmtMoney(int value) {
        return (value < 0 ? "-$" : "$") + String.format(Locale.ROOT, "%,d", Math.abs(value));
    }

    /** 截断到设计宽度：文本按 1× 绘制，允许的字体宽度 = 设计宽度 × 缩放 */
    private String trunc(String value, int maxWidth) {
        return font.plainSubstrByWidth(value, Math.max(1, Math.round(maxWidth * uiScale)));
    }

    /** 文本的设计宽度（文本按 1× 绘制 → 换算回设计坐标） */
    private int tw(Component value) {
        return Math.round(font.width(value) / uiScale);
    }

    private int tw(String value) {
        return Math.round(font.width(value) / uiScale);
    }

    /**
     * 绘制 1× 文本：位置随窗口布局缩放（设计坐标），**字号不随窗口放大**。
     * 在已缩放的 pose 内做一次 1/uiScale 反向缩放，即可保持字体原始像素大小。
     */
    private void drawText(GuiGraphics g, Component value, int x, int y, int color) {
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

    private void drawText(GuiGraphics g, String value, int x, int y, int color) {
        drawText(g, Component.literal(value), x, y, color);
    }

    /** 在高度 h（设计单位）的盒内垂直居中绘制 1× 文本（字体实际高度按 1× 计算） */
    private void drawTextV(GuiGraphics g, Component value, int x, int y, int h, int color) {
        drawText(g, value, x, y + Math.round((h - font.lineHeight / uiScale) / 2.0F), color);
    }

    private void drawTextV(GuiGraphics g, String value, int x, int y, int h, int color) {
        drawTextV(g, Component.literal(value), x, y, h, color);
    }

    private static String itemName(ResourceLocation item) {
        if (item == null) {
            return "";
        }
        ItemStack stack = ClientShopData.stack(item);
        return stack.isEmpty() ? item.getPath() : stack.getHoverName().getString();
    }

    private static String reason(String langKey) {
        return Component.translatable(langKey).getString();
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        updateLayout();
        int lx = (int) toLocalX(mouseX);
        int ly = (int) toLocalY(mouseY);
        hoveredEntry = null;
        hoveredStack = null;
        hoveredRefund = 0;
        hoveredPlayerStack = ItemStack.EMPTY;

        g.fill(0, 0, width, height, C_DIM);
        if (!ClientShopData.hasCatalog()) {
            g.drawCenteredString(font, Component.translatable("msb.shop.loading"), width / 2, height / 2, C_TEXT_SUB);
            return;
        }
        // 自适应放大：整窗按 uiScale 绘制（块内使用设计坐标 0..WIN_W / 0..WIN_H）
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0.0F);
        g.pose().scale(uiScale, uiScale, 1.0F);
        x0 = 0;
        y0 = 0;

        g.fill(0, 0, WIN_W, WIN_H, C_WIN_BG);
        frame(g, 0, 0, WIN_W, WIN_H, C_BORDER_STRONG);

        drawHeader(g);
        drawCategories(g, lx, ly);
        drawMarket(g, lx, ly);
        drawStorage(g, lx, ly);
        drawPlayerColumn(g, lx, ly);
        drawPanel(g, lx, ly);
        drawFooter(g);
        drawToasts(g);
        g.pose().popPose();
        // tooltip 画在屏幕空间（不受缩放影响，坐标用原始鼠标位置）
        drawTooltip(g, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics g) {
        drawTextV(g, Component.translatable("msb.shop.title"), x0 + 8, y0, HEADER_H, C_GOLD);
        // 荣誉商店页签显示荣誉点（其他页面只显示现金；荣誉点不在 HUD 暴露）
        boolean honor = category == ShopCategory.HONOR;
        Component right = honor
                ? Component.translatable("msb.shop.honor", ClientShopData.honorPoints())
                : Component.literal(fmtMoney(ClientMatchState.getWalletTotal()));
        drawTextV(g, right, x0 + WIN_W - 8 - tw(right), y0, HEADER_H, C_GOLD);
    }

    private void drawCategories(GuiGraphics g, int mouseX, int mouseY) {
        box(g, leftX(), bodyY(), LEFT_W, BODY_H);
        int cy = bodyY() + 2;
        for (ShopCategory value : ShopCategory.values()) {
            boolean active = value == category;
            boolean hover =  hit(mouseX, mouseY, leftX(), cy, LEFT_W - 4, SLOT);
            if (active) {
                g.fill(leftX(), cy, leftX() + LEFT_W - 4, cy + SLOT, C_SEL_BG);
                g.fill(leftX(), cy, leftX() + 2, cy + SLOT, C_SEL);
            } else if (hover) {
                g.fill(leftX(), cy, leftX() + LEFT_W - 4, cy + SLOT, C_HOVER);
            }
            drawTextV(g, Component.translatable(value.getLangKey()), leftX() + 5, cy, SLOT,
                    active ? C_GOLD : (hover ? C_TEXT : C_TEXT_SUB));
            cy += SLOT + SLOT_GAP;
        }
    }

    private void drawMarket(GuiGraphics g, int mouseX, int mouseY) {
        box(g, midX(), marketY(), MID_W, MARKET_H);
        drawChips(g, mouseX, mouseY);
        List<ShopDataPayload.Entry> items = visibleItems();

        if (items.isEmpty()) {
            Component empty = Component.translatable("msb.shop.empty");
            drawText(g, empty, marketViewX() + (marketViewW() - tw(empty)) / 2,
                    marketViewY() + marketViewH() / 2 - 8, C_TEXT_DIM);
            return;
        }
        int rows = (items.size() + CELL_COLS - 1) / CELL_COLS;
        int contentH = Math.max(0, rows * (CELL + CELL_GAP) - CELL_GAP);
        marketScroll = Mth.clamp(marketScroll, 0, Math.max(0, contentH - marketViewH()));

        int gridX = marketViewX() + (marketViewW() - GRID_W) / 2;
        scissorOn(g, marketViewX(), marketViewY(), marketViewX() + marketViewW(), marketViewY() + marketViewH());
        for (int i = 0; i < items.size(); i++) {
            int cx = gridX + (i % CELL_COLS) * (CELL + CELL_GAP);
            int cy = marketViewY() + (i / CELL_COLS) * (CELL + CELL_GAP) - marketScroll;
            if (cy + CELL < marketViewY() || cy > marketViewY() + marketViewH()) {
                continue;
            }
            ShopDataPayload.Entry entry = items.get(i);
            boolean selected = sel.kind == Kind.CATALOG && entry.item().equals(sel.item);
            boolean hover =  hit(mouseX, mouseY, cx, cy, CELL, CELL);
            g.fill(cx, cy, cx + CELL, cy + CELL, hover ? C_SLOT_HOVER : C_SLOT);
            frame(g, cx, cy, CELL, CELL, selected ? C_SEL : (hover ? C_BORDER_STRONG : C_BORDER));
            g.renderItem(ClientShopData.stack(entry.item()), cx + (CELL - 16) / 2, cy + 2);
            String price = category == ShopCategory.HONOR ? String.valueOf(entry.price()) : fmtMoney(entry.price());
            drawText(g, price, cx + CELL / 2 - tw(price) / 2, cy + 19,
                    category == ShopCategory.HONOR ? C_HONOR : C_GOLD);
            if (hover) {
                hoveredEntry = entry;
            }
        }
        g.disableScissor();
        drawScrollbar(g, marketViewX() + marketViewW() - 2, marketViewY(), marketViewH(), contentH, marketScroll);
    }

    private void drawStorage(GuiGraphics g, int mouseX, int mouseY) {
        box(g, stoX(), lowerY(), STO_W, LOWER_H);
        List<ShopStoragePayload.Stack> stacks = ClientShopData.storage();
        drawTextV(g, Component.translatable("msb.shop.storage", stacks.size(), ClientShopData.storageSlots()),
                stoX() + 5, lowerY() + 2, 14, C_TEXT_DIM);

        int capacity = ClientShopData.storageSlots();
        int rows = (capacity + STO_COLS - 1) / STO_COLS;
        int contentH = Math.max(0, rows * (SLOT + SLOT_GAP) - SLOT_GAP);
        storageScroll = Mth.clamp(storageScroll, 0, Math.max(0, contentH - stoViewH()));

        int gridX = stoViewX() + (stoViewW() - STO_GRID_W) / 2;
        scissorOn(g, stoViewX(), stoViewY(), stoViewX() + stoViewW(), stoViewY() + stoViewH());
        for (int i = 0; i < capacity; i++) {
            int cx = gridX + (i % STO_COLS) * (SLOT + SLOT_GAP);
            int cy = stoViewY() + (i / STO_COLS) * (SLOT + SLOT_GAP) - storageScroll;
            if (cy + SLOT < stoViewY() || cy > stoViewY() + stoViewH()) {
                continue;
            }
            ShopStoragePayload.Stack stack = i < stacks.size() ? stacks.get(i) : null;
            boolean selected = stack != null && sel.kind == Kind.STORAGE && sel.slot == i;
            boolean hover =  stack != null && hit(mouseX, mouseY, cx, cy, SLOT, SLOT);
            g.fill(cx, cy, cx + SLOT, cy + SLOT, hover ? C_SLOT_HOVER : C_SLOT);
            if (stack != null) {
                // 用带数量的展示栈渲染，否则 renderItemDecorations 拿不到 count（堆叠数不显示）
                ItemStack display = ClientShopData.stack(stack.item()).copyWithCount(stack.count());
                g.renderItem(display, cx, cy);
                g.renderItemDecorations(font, display, cx, cy);
            }
            boolean refund = stack != null && stack.refund() > 0;
            boolean flash = refund && ClientShopData.isFlashing(stack.item()) && (System.currentTimeMillis() / 200) % 2 == 0;
            int color = selected ? C_SEL
                    : refund ? (flash ? 0xFFFFFFFF : C_GREEN)
                            : (hover ? C_BORDER_STRONG : C_BORDER);
            frame(g, cx, cy, SLOT, SLOT, color);
            if (hover) {
                hoveredStack = stack;
                hoveredRefund = stack.refund();
            }
        }
        g.disableScissor();
        drawScrollbar(g, stoViewX() + stoViewW() - 2, stoViewY(), stoViewH(), contentH, storageScroll);
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int scroll) {
        if (contentH <= viewH) {
            return;
        }
        g.fill(x, y, x + 2, y + viewH, 0x30000000);
        int thumbH = Math.max(6, viewH * viewH / contentH);
        int thumbY = y + (viewH - thumbH) * scroll / Math.max(1, contentH - viewH);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, 0x60FFFFFF);
    }

    private void drawPlayerColumn(GuiGraphics g, int mouseX, int mouseY) {
        Player player = Minecraft.getInstance().player;
        box(g, rightX(), bodyY(), RIGHT_W, BODY_H);
        int x = colX();

        drawText(g, Component.translatable("msb.shop.group.equip"), x, bodyY() + 3, C_TEXT_DIM);
        drawText(g, Component.translatable("msb.shop.equip.labels"), x, bodyY() + 13, C_TEXT_DIM);
        for (int i = 0; i < 5; i++) {
            int cx = x + (i % 3) * (SLOT + SLOT_GAP);
            int cy = equipGridY() + (i / 3) * (SLOT + SLOT_GAP);
            ShopTradePayload.Zone zone = i < 4 ? ShopTradePayload.Zone.ARMOR : ShopTradePayload.Zone.OFFHAND;
            int slot = i < 4 ? i : 0;
            drawSlot(g, playerStack(zone, slot), cx, cy, mouseX, mouseY,
                    sel.kind == Kind.PLAYER && sel.zone == zone && sel.slot == slot);
        }

        drawText(g, Component.translatable("msb.shop.group.acc"), x, accGridY() - 10, C_TEXT_DIM);
        for (int i = 0; i < 3; i++) {
            int cx = x + i * (SLOT + SLOT_GAP);
            g.fill(cx, accGridY(), cx + SLOT, accGridY() + SLOT, 0x6014171D);
            frame(g, cx, accGridY(), SLOT, SLOT, C_BORDER);
        }
        

        ItemStack[] main = playerSlots(ShopTradePayload.Zone.MAIN, 27);
        drawText(g, Component.translatable("msb.shop.group.main"), x, mainGridY() - 10, C_TEXT_DIM);
        for (int i = 0; i < 27; i++) {
            int cx = x + (i % 9) * (SLOT + SLOT_GAP);
            int cy = mainGridY() + (i / 9) * (SLOT + SLOT_GAP);
            drawSlot(g, main[i], cx, cy, mouseX, mouseY,
                    sel.kind == Kind.PLAYER && sel.zone == ShopTradePayload.Zone.MAIN && sel.slot == i);
        }

        ItemStack[] hotbar = playerSlots(ShopTradePayload.Zone.HOTBAR, 9);
        drawText(g, Component.translatable("msb.shop.group.hotbar"), x, hotbarGridY() - 10, C_TEXT_DIM);
        for (int i = 0; i < 9; i++) {
            int cx = x + i * (SLOT + SLOT_GAP);
            drawSlot(g, hotbar[i], cx, hotbarGridY(), mouseX, mouseY,
                    sel.kind == Kind.PLAYER && sel.zone == ShopTradePayload.Zone.HOTBAR && sel.slot == i);
        }
    }

    private void drawSlot(GuiGraphics g, ItemStack stack, int cx, int cy, int mouseX, int mouseY, boolean selected) {
        boolean hover =  !stack.isEmpty() && hit(mouseX, mouseY, cx, cy, SLOT, SLOT);
        g.fill(cx, cy, cx + SLOT, cy + SLOT, hover ? C_SLOT_HOVER : C_SLOT);
        if (!stack.isEmpty()) {
            g.renderItem(stack, cx, cy);
            g.renderItemDecorations(font, stack, cx, cy);
        }
        frame(g, cx, cy, SLOT, SLOT, selected ? C_SEL : (hover ? C_BORDER_STRONG : C_BORDER));
        if (hover) {
            hoveredPlayerStack = stack;
        }
    }

    private static ItemStack playerStack(ShopTradePayload.Zone zone, int slot) {
        ItemStack[] slots = playerSlots(zone, zone == ShopTradePayload.Zone.ARMOR ? 4 : 1);
        return slot >= 0 && slot < slots.length ? slots[slot] : ItemStack.EMPTY;
    }

    /** 玩家栏槽位快照（护甲展示顺序：头盔/胸甲/护腿/靴子；副手；物品栏 27；快捷栏 9） */
    private static ItemStack[] playerSlots(ShopTradePayload.Zone zone, int size) {
        ItemStack[] out = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            out[i] = ItemStack.EMPTY;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || zone == null) {
            return out;
        }
        var inv = player.getInventory();
        switch (zone) {
            case ARMOR -> {
                int[] order = {3, 2, 1, 0};
                for (int i = 0; i < size && i < order.length; i++) {
                    out[i] = inv.armor.get(order[i]);
                }
            }
            case OFFHAND -> out[0] = inv.offhand.get(0);
            case MAIN -> {
                for (int i = 0; i < size; i++) {
                    out[i] = inv.getItem(i + 9);
                }
            }
            case HOTBAR -> {
                for (int i = 0; i < size; i++) {
                    out[i] = inv.getItem(i);
                }
            }
            default -> {
            }
        }
        return out;
    }

    // ===== 面板 =====

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY) {
        box(g, midX(), lowerY(), PANEL_W, LOWER_H);
        int x = midX() + 3;
        if (sel.kind == Kind.NONE) {
            return;
        }
        if (sel.kind == Kind.CATALOG) {
            drawBuyPanel(g, x, mouseX, mouseY);
        } else {
            drawTransferPanel(g, x, mouseX, mouseY);
        }
    }

    private void drawBuyPanel(GuiGraphics g, int x, int mouseX, int mouseY) {
        ShopDataPayload.Entry entry = ClientShopData.entry(sel.item);
        if (entry == null) {
            clearSelection();
            return;
        }
        int max = maxBuy(entry);
        qty = Mth.clamp(qty, 1, Math.max(1, max));
        boolean honor = entry.category() == ShopCategory.HONOR.ordinal();
        int y = lowerY() + 3;
        g.renderItem(ClientShopData.stack(entry.item()), x, y);
        drawText(g, trunc(itemName(entry.item()), PANEL_W - 26), x + 20, y + 4, C_TEXT);
        Component unitPrice = honor ? Component.translatable("msb.shop.honor", entry.price())
                : Component.literal(fmtMoney(entry.price()));
        drawText(g, Component.translatable("msb.shop.price_line", unitPrice), x, y + 18, C_TEXT_SUB);

        int qtyY = y + 28;
        drawStepButtons(g, x, qtyY, mouseX, mouseY);
        drawTextV(g, String.valueOf(qty), x + 38, qtyY, 14, C_TEXT);
        Component totalPrice = honor ? Component.translatable("msb.shop.honor", entry.price() * qty)
                : Component.literal(fmtMoney(entry.price() * qty));
        drawText(g, Component.translatable("msb.shop.total", totalPrice), x, qtyY + 17, C_TEXT_SUB);

        int btnY = lowerY() + 58;
        boolean enabled = max >= 1;
        drawButton(g, x, btnY, PANEL_W - 6, BUTTON_H, Component.translatable("msb.shop.buy"), enabled, mouseX, mouseY);
                if (!enabled) {
            drawText(g, trunc(buyReason(entry), PANEL_W - 8), x, btnY + 29, C_RED);
        }
    }

    private void drawTransferPanel(GuiGraphics g, int x, int mouseX, int mouseY) {
        boolean fromStorage = sel.kind == Kind.STORAGE;
        ShopStoragePayload.Stack storage = fromStorage ? storageStack(sel.slot) : null;
        ItemStack live = fromStorage ? ItemStack.EMPTY : playerStack(sel.zone, sel.slot);
        ResourceLocation item = fromStorage ? (storage == null ? null : storage.item()) : itemIdOf(live);
        if (item == null) {
            clearSelection();
            return;
        }
        int held = fromStorage ? storage.count() : live.getCount();
        int refund = fromStorage ? storage.refund() : 0;
        ShopDataPayload.Entry entry = ClientShopData.entry(item);
        qty = Mth.clamp(qty, 1, Math.max(1, held));

        int y = lowerY() + 3;
        g.renderItem(ClientShopData.stack(item), x, y);
        drawText(g, trunc(itemName(item), PANEL_W - 26), x + 20, y + 4, C_TEXT);
        drawText(g, Component.translatable("msb.shop.held.line", held, refund), x, y + 18,
                refund > 0 ? C_GREEN : C_TEXT_SUB);

        int qtyY = y + 28;
        drawStepButtons(g, x, qtyY, mouseX, mouseY);
        drawTextV(g, String.valueOf(qty), x + 38, qtyY, 14, C_TEXT);
        int refundPart = Math.min(qty, refund);
        boolean sellHidden = entry == null || !sellable(entry);
        int earn = sellHidden ? 0
                : refundPart * ClientShopData.refundUnit(entry) + (qty - refundPart) * ClientShopData.sellUnit(entry);
        if (sellHidden) {
            drawText(g, Component.translatable("msb.shop.unsellable"), x, qtyY + 17, C_TEXT_DIM);
        } else {
            drawText(g, Component.translatable("msb.shop.income", fmtMoney(earn)), x, qtyY + 17, C_GREEN);
        }

        int btnY = lowerY() + 58;
        int halfW = (PANEL_W - 8) / 2;
        if (fromStorage) {
            String takeReason = takeReason(item, qty);
            drawButton(g, x, btnY, halfW, BUTTON_H, Component.translatable("msb.shop.take"), takeReason.isEmpty(), mouseX, mouseY);
            drawButton(g, x + halfW + 2, btnY, PANEL_W - 8 - halfW, BUTTON_H, Component.translatable("msb.shop.sell"),
                    entry != null && sellable(entry), mouseX, mouseY);
                        if (!takeReason.isEmpty()) {
                drawText(g, trunc(takeReason, PANEL_W - 8), x, btnY + 29, C_RED);
            }
        } else {
            boolean canStore = ClientShopData.storageRoom(item) >= qty;
            drawButton(g, x, btnY, halfW, BUTTON_H, Component.translatable("msb.shop.store"), canStore, mouseX, mouseY);
            drawButton(g, x + halfW + 2, btnY, PANEL_W - 8 - halfW, BUTTON_H, Component.translatable("msb.shop.sell"),
                    entry != null && sellable(entry), mouseX, mouseY);
                        if (!canStore) {
                drawText(g, trunc(reason("msb.shop.error.storage_full"), PANEL_W - 8), x, btnY + 29, C_RED);
            }
        }
    }

    private void drawStepButtons(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawMiniButton(g, x, y, 14, 14, "-", mouseX, mouseY);
        drawMiniButton(g, x + 55, y, 14, 14, "+", mouseX, mouseY);
        drawMiniButton(g, x + 71, y, 20, 14, "MAX", mouseX, mouseY);
    }

    private void drawMiniButton(GuiGraphics g, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        boolean hover =  hit(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? C_SLOT_HOVER : C_SLOT);
        frame(g, x, y, w, h, hover ? C_BORDER_STRONG : C_BORDER);
        drawTextV(g, label, x + w / 2 - tw(label) / 2, y, h, C_TEXT_SUB);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled &&  hit(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, enabled ? (hover ? 0xFFFFC55C : C_GOLD) : 0x60404030);
        frame(g, x, y, w, h, enabled ? 0xFF000000 : C_BORDER);
        drawTextV(g, label, x + w / 2 - tw(label) / 2, y, h, enabled ? C_BTN_TEXT : C_TEXT_DIM);
    }

    private void drawFooter(GuiGraphics g) {
        int fy = y0 + WIN_H - FOOTER_H;
        g.fill(x0, fy, x0 + WIN_W, y0 + WIN_H, 0x40000000);
        
        double weight = ClientShopData.playerWeight(Minecraft.getInstance().player);
        double limit = ClientShopData.weightLimit();
        double ratio = limit <= 0 ? 0 : weight / limit;
        int color = ratio >= 1.0 ? C_RED : ratio >= ClientShopData.weightWarnRatio() ? C_GOLD : C_TEXT_SUB;
        Component text = Component.literal(String.format(Locale.ROOT, "%.1f / %.1f kg", weight, limit));
        int tx = x0 + WIN_W - 8 - tw(text);
        drawTextV(g, text, tx, fy, FOOTER_H, color);
        int barX = tx - 66;
        g.fill(barX, fy + 7, barX + 60, fy + 13, C_SLOT);
        g.fill(barX, fy + 7, barX + (int) Math.min(60, 60 * ratio), fy + 13, color);
    }

    // ===== toast / tooltip =====

    private void drawToasts(GuiGraphics g) {
        List<ClientShopData.Toast> toasts = ClientShopData.activeToasts();
        int y = y0 + WIN_H - FOOTER_H - 6;
        for (int i = toasts.size() - 1; i >= 0 && i >= toasts.size() - 3; i--) {
            Component text = toastText(toasts.get(i).payload());
            int w = Math.min(232, tw(text) + 14);
            int tx = x0 + WIN_W - 8 - w;
            y -= 16;
            g.fill(tx, y, tx + w, y + 14, 0xE0101319);
            frame(g, tx, y, w, 14, C_BORDER);
            boolean ok = toasts.get(i).payload().ok();
            g.fill(tx, y, tx + 2, y + 14, ok ? C_GREEN : C_RED);
            drawText(g, trunc(text.getString(), w - 10), tx + 6, y + 3, ok ? C_TEXT : C_RED);
        }
    }

    private Component toastText(ShopResultPayload payload) {
        Component name = Component.literal(itemName(payload.item()));
        if (!payload.ok()) {
            return Component.translatable("msb.shop.result.failed", Component.translatable(payload.code().getLangKey()));
        }
        return switch (payload.action()) {
            case BUY -> {
                ShopDataPayload.Entry bought = payload.item() == null ? null : ClientShopData.entry(payload.item());
                if (bought != null && bought.category() == ShopCategory.HONOR.ordinal()) {
                    yield Component.translatable("msb.shop.result.bought_honor", name, payload.count(),
                            Math.abs(payload.moneyDelta()));
                }
                yield Component.translatable("msb.shop.result.bought", name, payload.count(), fmtMoney(payload.moneyDelta()));
            }
            case SELL -> payload.refunded() > 0
                    ? Component.translatable("msb.shop.result.sold_refund", name, payload.count(),
                            fmtMoney(payload.moneyDelta()), payload.refunded())
                    : Component.translatable("msb.shop.result.sold", name, payload.count(), fmtMoney(payload.moneyDelta()));
            case TAKE -> Component.translatable("msb.shop.result.taken", name, payload.count());
            case STORE -> Component.translatable("msb.shop.result.stored", name, payload.count());
            case SWAP_HOTBAR -> Component.translatable("msb.shop.result.swapped", name, payload.count());
            case EQUIP -> Component.translatable("msb.shop.result.equipped", name);
        };
    }

    private void drawTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredEntry != null) {
            boolean honor = hoveredEntry.category() == ShopCategory.HONOR.ordinal();
            MutableComponent text = Component.empty().append(ClientShopData.stack(hoveredEntry.item()).getHoverName());
            text.append(Component.literal("\n").append(Component.translatable("msb.shop.price_line",
                    honor ? Component.translatable("msb.shop.honor", hoveredEntry.price())
                            : Component.literal(fmtMoney(hoveredEntry.price())))));
            text.append(Component.literal("\n").append(Component.translatable("msb.shop.tip.weight",
                    String.format(Locale.ROOT, "%.2f", hoveredEntry.weight()))));
            if (honor) {
                text.append(Component.literal("\n").append(Component.translatable("msb.shop.unsellable")));
            } else {
                text.append(Component.literal("\n").append(Component.translatable("msb.shop.tip.sell",
                        fmtMoney(ClientShopData.sellUnit(hoveredEntry)))));
            }
            g.renderTooltip(font, text, mouseX, mouseY);
            return;
        }
        if (hoveredStack != null) {
            MutableComponent text = Component.empty().append(ClientShopData.stack(hoveredStack.item()).getHoverName());
            ShopDataPayload.Entry entry = ClientShopData.entry(hoveredStack.item());
            text.append(Component.literal("\n").append(Component.translatable("msb.shop.held_count", hoveredStack.count())));
            if (entry == null || !sellable(entry)) {
                text.append(Component.literal("\n").append(Component.translatable("msb.shop.unsellable")));
            } else {
                text.append(Component.literal("\n").append(Component.translatable("msb.shop.tip.sell",
                        fmtMoney(ClientShopData.sellUnit(entry)))));
                if (hoveredRefund > 0) {
                    text.append(Component.literal("\n").append(Component.translatable("msb.shop.refund_tip", hoveredRefund)));
                }
            }
            g.renderTooltip(font, text, mouseX, mouseY);
            return;
        }
        if (!hoveredPlayerStack.isEmpty()) {
            g.renderTooltip(font, hoveredPlayerStack, mouseX, mouseY);
        }
    }

    // ===== 交互 =====

    /** 左键：单击选中、双击执行（商品格 = 购买 / 储存格 = 取出 / 玩家栏 = 存入）；右键：卖回；Ctrl+左键：整组取出/存入 */
    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        if (button != 0 && button != 1) {
            return true;
        }
        double mx = toLocalX(screenX);
        double my = toLocalY(screenY);
        boolean ctrl = hasControlDown();

        // 分类栏（左键）
        if (button == 0) {
            int cy = bodyY() + 2;
            for (ShopCategory value : ShopCategory.values()) {
                if (hit(mx, my, leftX(), cy, LEFT_W - 4, SLOT)) {
                    category = value;
                    subFilter = null;
                    marketScroll = 0;
                    clearSelection();
                    return true;
                }
                cy += SLOT + SLOT_GAP;
            }
            // 子分类筛选条（枪械栏 / 装备栏）
            for (Chip chip : chips()) {
                if (hit(mx, my, chip.x(), chip.y(), chip.w(), CHIP_H)) {
                    subFilter = chip.type();
                    marketScroll = 0;
                    return true;
                }
            }
        }

        // 商品格：单击选中、双击购买（Ctrl 双击只买 1 件）
        List<ShopDataPayload.Entry> items = visibleItems();
        int gridX = marketViewX() + (marketViewW() - GRID_W) / 2;
        for (int i = 0; i < items.size(); i++) {
            int cx = gridX + (i % CELL_COLS) * (CELL + CELL_GAP);
            int cyp = marketViewY() + (i / CELL_COLS) * (CELL + CELL_GAP) - marketScroll;
            if (!hit(mx, my, cx, cyp, CELL, CELL)) {
                continue;
            }
            if (button == 0) {
                ShopDataPayload.Entry entry = items.get(i);
                boolean dbl = isDoubleClick("catalog:" + entry.item());
                int wanted = qty;
                selectCatalog(entry);
                if (dbl) {
                    doBuy(ctrl ? 1 : wanted);
                }
            }
            return true;
        }

        // 储存格
        List<ShopStoragePayload.Stack> stacks = ClientShopData.storage();
        int stoGridX = stoViewX() + (stoViewW() - STO_GRID_W) / 2;
        for (int i = 0; i < ClientShopData.storageSlots(); i++) {
            int cx = stoGridX + (i % STO_COLS) * (SLOT + SLOT_GAP);
            int cyp = stoViewY() + (i / STO_COLS) * (SLOT + SLOT_GAP) - storageScroll;
            if (!hit(mx, my, cx, cyp, SLOT, SLOT)) {
                continue;
            }
            ShopStoragePayload.Stack stack = i < stacks.size() ? stacks.get(i) : null;
            if (stack == null) {
                continue;
            }
            if (button == 1) {                                  // 右键：整堆卖回（荣誉商店物品不可卖回）
                ShopDataPayload.Entry shopEntry = ClientShopData.entry(stack.item());
                if (shopEntry != null && sellable(shopEntry)) {
                    send(ShopTradePayload.Action.SELL, ShopTradePayload.Zone.STORAGE, i, stack.item(), stack.count());
                } else {
                    ClientShopData.localFailure(ShopCode.UNSELLABLE, stack.item());
                }
            } else if (ctrl) {                                  // Ctrl+左键：整组取出（该物品全部堆叠）
                selectStorage(i, stack);
                takeAll(stack.item());
            } else {                                            // 左键：单击选中、双击装备（可装备）或取出（自动入包）
                selectStorage(i, stack);
                if (isDoubleClick("storage:" + i)) {
                    if (equippable(ClientShopData.stack(stack.item()))) {
                        send(ShopTradePayload.Action.EQUIP, ShopTradePayload.Zone.STORAGE, i, stack.item(), 1);
                    } else {
                        send(ShopTradePayload.Action.TAKE, ShopTradePayload.Zone.STORAGE, i, stack.item(), stack.count());
                    }
                }
            }
            return true;
        }

        // 玩家栏：装备栏（头盔/胸甲/护腿/靴子/副手）
        for (int i = 0; i < 5; i++) {
            int cx = colX() + (i % 3) * (SLOT + SLOT_GAP);
            int cyp = equipGridY() + (i / 3) * (SLOT + SLOT_GAP);
            if (hit(mx, my, cx, cyp, SLOT, SLOT)) {
                playerZoneClick(button, ctrl,
                        i < 4 ? ShopTradePayload.Zone.ARMOR : ShopTradePayload.Zone.OFFHAND, i < 4 ? i : 0);
                return true;
            }
        }
        // 物品栏（目标格 = i + 9）
        for (int i = 0; i < 27; i++) {
            int cx = colX() + (i % 9) * (SLOT + SLOT_GAP);
            int cyp = mainGridY() + (i / 9) * (SLOT + SLOT_GAP);
            if (hit(mx, my, cx, cyp, SLOT, SLOT)) {
                playerZoneClick(button, ctrl, ShopTradePayload.Zone.MAIN, i);
                return true;
            }
        }
        // 快捷栏
        for (int i = 0; i < 9; i++) {
            int cx = colX() + i * (SLOT + SLOT_GAP);
            if (hit(mx, my, cx, hotbarGridY(), SLOT, SLOT)) {
                playerZoneClick(button, ctrl, ShopTradePayload.Zone.HOTBAR, i);
                return true;
            }
        }

        panelClick(mx, my);
        return true;
    }

    /** 玩家栏格子点击：左键单击选中、双击存入储存格（Ctrl = 该物品全部堆叠）、右键卖回商店 */
    private void playerZoneClick(int button, boolean ctrl, ShopTradePayload.Zone zone, int slot) {
        ItemStack stack = playerStack(zone, slot);
        if (stack.isEmpty()) {
            clearSelection();
            return;
        }
        if (button == 1) {
            ResourceLocation id = itemIdOf(stack);
            ShopDataPayload.Entry shopEntry = id == null ? null : ClientShopData.entry(id);
            if (shopEntry != null && sellable(shopEntry)) {
                send(ShopTradePayload.Action.SELL, zone, slot, id, stack.getCount());
            } else {
                ClientShopData.localFailure(ShopCode.UNSELLABLE, id);
            }
            return;
        }
        selectPlayer(zone, slot);
        if (ctrl) {
            storeAll(itemIdOf(stack));
        } else if (isDoubleClick("player:" + zone + ":" + slot)) {
            // 可装备物品（护甲 / 饰品）：双击自动装备到对应槽位；否则存入储存格
            if (equippable(stack)) {
                send(ShopTradePayload.Action.EQUIP, zone, slot, itemIdOf(stack), 1);
            } else {
                send(ShopTradePayload.Action.STORE, zone, slot, itemIdOf(stack), stack.getCount());
            }
        }
    }

    /** 该物品是否可装备（原版护甲 → 护甲槽；饰品如降落伞 → Curios 槽） */
    private static boolean equippable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof ArmorItem) {
            return true;
        }
        Player player = Minecraft.getInstance().player;
        return player != null && !CuriosApi.getItemStackSlots(stack, player).isEmpty();
    }

    /** Ctrl+左键：取出储存格中该物品的全部堆叠（逐堆请求，服务端各自校验） */
    private void takeAll(ResourceLocation item) {
        for (int i = 0; i < ClientShopData.storage().size(); i++) {
            ShopStoragePayload.Stack stack = storageStack(i);
            if (stack != null && stack.item().equals(item)) {
                send(ShopTradePayload.Action.TAKE, ShopTradePayload.Zone.STORAGE, i, item, stack.count());
            }
        }
    }

    /** Ctrl+左键：把背包/快捷栏中该物品的全部堆叠存入储存格 */
    private void storeAll(ResourceLocation item) {
        storeZone(item, ShopTradePayload.Zone.MAIN, 27);
        storeZone(item, ShopTradePayload.Zone.HOTBAR, 9);
    }

    private void storeZone(ResourceLocation item, ShopTradePayload.Zone zone, int size) {
        ItemStack[] slots = playerSlots(zone, size);
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].isEmpty() && item.equals(itemIdOf(slots[i]))) {
                send(ShopTradePayload.Action.STORE, zone, i, item, slots[i].getCount());
            }
        }
    }

    /** 双击判定（同一格 280ms 内两次左键；命中后消耗，避免三击连锁） */
    private boolean isDoubleClick(String key) {
        long now = System.currentTimeMillis();
        boolean dbl = key.equals(lastClickKey) && now - lastClickAt <= 280L;
        lastClickKey = dbl ? null : key;
        lastClickAt = now;
        return dbl;
    }

    /** 面板：数量步进 + 按钮（按钮与鼠标手势双入口，均直接执行） */
    private void panelClick(double mx, double my) {
        if (sel.kind == Kind.NONE) {
            return;
        }
        int x = midX() + 3;
        int qtyY = lowerY() + 31;
        if (hit(mx, my, x, qtyY, 14, 14)) {
            qty = Math.max(1, qty - 1);
            return;
        }
        if (hit(mx, my, x + 55, qtyY, 14, 14)) {
            qty++;
            clampQty();
            return;
        }
        if (hit(mx, my, x + 71, qtyY, 20, 14)) {
            qty = 999;
            clampQty();
            return;
        }
        int btnY = lowerY() + 58;
        int halfW = (PANEL_W - 8) / 2;
        ShopDataPayload.Entry entry = ClientShopData.entry(sel.item);
        if (sel.kind == Kind.CATALOG) {
            if (hit(mx, my, x, btnY, PANEL_W - 6, BUTTON_H)) {
                doBuy(qty);
            }
            return;
        }
        boolean fromStorage = sel.kind == Kind.STORAGE;
        if (hit(mx, my, x, btnY, halfW, BUTTON_H)) {              // 取回 / 存入
            if (fromStorage) {
                if (takeReason(sel.item, qty).isEmpty()) {
                    send(ShopTradePayload.Action.TAKE, ShopTradePayload.Zone.STORAGE, sel.slot, sel.item, qty);
                }
            } else if (ClientShopData.storageRoom(sel.item) >= qty) {
                send(ShopTradePayload.Action.STORE, sel.zone, sel.slot, sel.item, qty);
            }
            return;
        }
        if (entry != null && sellable(entry) && hit(mx, my, x + halfW + 2, btnY, PANEL_W - 8 - halfW, BUTTON_H)) {
            send(ShopTradePayload.Action.SELL, sel.zone, sel.slot, sel.item, qty);
        }
    }

    /** 购买（双击 / 面板按钮共用）：客户端预检不过则本地提示，不发请求（服务端仍兜底） */
    private void doBuy(int count) {
        if (sel.kind != Kind.CATALOG || sel.item == null) {
            return;
        }
        ShopDataPayload.Entry entry = ClientShopData.entry(sel.item);
        if (entry == null) {
            return;
        }
        int max = maxBuy(entry);
        if (max < 1) {
            ShopCode code = ClientShopData.storageRoom(entry.item()) < 1
                    ? ShopCode.STORAGE_FULL
                    : (entry.category() == ShopCategory.HONOR.ordinal() ? ShopCode.NO_HONOR : ShopCode.NO_BALANCE);
            ClientShopData.localFailure(code, sel.item);
            return;
        }
        int amount = Mth.clamp(count, 1, Math.min(999, max));
        send(ShopTradePayload.Action.BUY, ShopTradePayload.Zone.STORAGE, 0, sel.item, amount);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double deltaX, double deltaY) {
        double mx = toLocalX(screenX);
        double my = toLocalY(screenY);
        if (hit(mx, my, marketViewX(), marketViewY(), marketViewW(), marketViewH())) {
            marketScroll -= (int) (deltaY * 14);
            return true;
        }
        if (hit(mx, my, stoViewX(), stoViewY(), stoViewW(), stoViewH())) {
            storageScroll -= (int) (deltaY * 14);
            return true;
        }
        return super.mouseScrolled(screenX, screenY, deltaX, deltaY);
    }

    

    /**
     * 大键盘 1~9：把左键选中的槽位与对应快捷栏格对调（玩家栏四区 / 储存格均可，商品格不适用）。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int hotbar = keyCode - GLFW.GLFW_KEY_1;
        if (hotbar >= 0 && hotbar <= 8
                && (sel.kind == Kind.PLAYER || sel.kind == Kind.STORAGE) && sel.item != null) {
            send(ShopTradePayload.Action.SWAP_HOTBAR, sel.zone, sel.slot, sel.item, hotbar);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ===== 选择与本地校验（权威在服务端，仅供禁用态提示） =====

    private void selectCatalog(ShopDataPayload.Entry entry) {
        sel.kind = Kind.CATALOG;
        sel.item = entry.item();
        sel.slot = -1;
        sel.zone = null;
        qty = 1;
    }

    /** 选中储存格堆叠（储存格 = Zone.STORAGE；SELL/TAKE 载荷都要带合法 zone） */
    private void selectStorage(int index, ShopStoragePayload.Stack stack) {
        sel.kind = Kind.STORAGE;
        sel.item = stack.item();
        sel.slot = index;
        sel.zone = ShopTradePayload.Zone.STORAGE;
        qty = 1;
    }

    private void selectPlayer(ShopTradePayload.Zone zone, int slot) {
        ItemStack stack = playerStack(zone, slot);
        if (stack.isEmpty()) {
            clearSelection();
            return;
        }
        sel.kind = Kind.PLAYER;
        sel.item = itemIdOf(stack);
        sel.zone = zone;
        sel.slot = slot;
        qty = 1;
    }

    private void clearSelection() {
        sel.kind = Kind.NONE;
        sel.item = null;
        sel.zone = null;
        sel.slot = -1;
        qty = 1;
    }

    private void clampQty() {
        int max = 1;
        if (sel.kind == Kind.CATALOG) {
            max = Math.max(1, maxBuy(ClientShopData.entry(sel.item)));
        } else if (sel.kind == Kind.STORAGE) {
            ShopStoragePayload.Stack stack = storageStack(sel.slot);
            max = stack == null ? 1 : Math.max(1, stack.count());
        } else if (sel.kind == Kind.PLAYER) {
            max = Math.max(1, playerStack(sel.zone, sel.slot).getCount());
        }
        qty = Mth.clamp(qty, 1, Math.min(999, max));
    }

    private void send(ShopTradePayload.Action action, ShopTradePayload.Zone zone, int slot,
            ResourceLocation item, int count) {
        if (item == null) {
            return;
        }
        // 防御：zone 缺失一律按储存格处理（避免编码期 NPE 断开连接）
        ShopTradePayload.Zone safeZone = zone == null ? ShopTradePayload.Zone.STORAGE : zone;
        PacketDistributor.sendToServer(new ShopTradePayload(action, safeZone, slot, item, count));
    }

    private ShopStoragePayload.Stack storageStack(int index) {
        List<ShopStoragePayload.Stack> stacks = ClientShopData.storage();
        return index >= 0 && index < stacks.size() ? stacks.get(index) : null;
    }

    private static ResourceLocation itemIdOf(ItemStack stack) {
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /** 与服务端一致：阵营商店 + 弹药商店允许欠款购买（不受余额限制） */
    private static boolean creditAllowed(ShopDataPayload.Entry entry) {
        return entry.category() == ShopCategory.FACTION.ordinal()
                || entry.category() == ShopCategory.AMMO.ordinal();
    }

    /** 与服务端一致：荣誉商店条目以荣誉点计价，禁止卖回换现金 */
    private static boolean sellable(ShopDataPayload.Entry entry) {
        return entry.category() != ShopCategory.HONOR.ordinal();
    }

    private int maxBuy(ShopDataPayload.Entry entry) {
        if (entry == null) {
            return 0;
        }
        int byRoom = ClientShopData.storageRoom(entry.item());
        int byAfford;
        if (entry.category() == ShopCategory.HONOR.ordinal()) {
            byAfford = ClientShopData.honorPoints() / Math.max(1, entry.price());
        } else if (creditAllowed(entry)) {
            byAfford = 999;
        } else {
            byAfford = ClientMatchState.getWalletTotal() / Math.max(1, entry.price());
        }
        return Math.max(0, Math.min(999, Math.min(byAfford, byRoom)));
    }

    private String buyReason(ShopDataPayload.Entry entry) {
        if (ClientShopData.storageRoom(entry.item()) < 1) {
            return reason("msb.shop.error.storage_full");
        }
        if (entry.category() == ShopCategory.HONOR.ordinal()) {
            return reason("msb.shop.error.no_honor");
        }
        return reason("msb.shop.error.no_balance");
    }

    private String takeReason(ResourceLocation item, int count) {
        ShopDataPayload.Entry entry = ClientShopData.entry(item);
        Player player = Minecraft.getInstance().player;
        if (entry != null && ClientShopData.playerWeight(player) + entry.weight() * count > ClientShopData.weightLimit()) {
            return reason("msb.shop.error.over_weight");
        }
        if (ClientShopData.playerRoom(player, item) < count) {
            return reason("msb.shop.error.bag_full");
        }
        return "";
    }
}