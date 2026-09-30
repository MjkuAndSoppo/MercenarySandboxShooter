package com.mercenarysandbox.msb.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 击杀提示 Overlay（GuiGraphics 自绘，docs/02 击杀提示）。
 * 正常不显示；击杀任意单位后于「屏幕一半下半的一半」处居中显示：
 * <pre>
 * 歼灭 Zombie +12$
 *  ——————————
 * 击杀x 3|经验 +50
 * </pre>
 * 所有数字带滚动动画：老虎机式逐位滚动，低位先启动、先到目标即锁定，高位延迟启动
 * （如目标 123 → 000→001→012→123；目标 256 → 000→001→012→123→234→245→256）。
 * money（金钱变动 ±xx$）与 streak（本命击杀数）/xp（经验 ±xxx）各自独立滚动。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MsbKillFeedOverlay {
    /** 滚动步进频率：每 5 tick（100ms）跳一位数字 */
    private static final long TICK_MS = 100L;
    /** 击杀提示总显示时长（ms），超过后自动消失 */
    private static final long SHOW_MS = 4800L;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ClientKillFeed.KillEntry show = ClientKillFeed.get();
        if (show == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - show.startedMs();
        if (elapsed > SHOW_MS) {
            ClientKillFeed.clear();
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // 屏幕一半下半的一半处（约 3/4 高度）为三行整体中心
        int cx = screenWidth / 2;
        int cy = (int) (screenHeight * 0.75D);

        String money = signed(show.money(), elapsed, "$");
        String streak = String.valueOf(roll(show.streak(), elapsed));
        String xp = signed(show.xp(), elapsed, " EXP");

        Component name = show.isPlayer()
                ? Component.literal(show.targetKey())
                : Component.translatable(show.targetKey());
        Component line1 = Component.translatable("msb.kill_feed.annihilate", name, money);
        Component line2 = Component.literal("——————————");
        Component line3 = Component.translatable("msb.kill_feed.info", streak, xp);

        g.drawCenteredString(font, line1, cx, cy - font.lineHeight - 2, 0xFFFFFF);
        g.drawCenteredString(font, line2, cx, cy, 0xFFFFFF);
        g.drawCenteredString(font, line3, cx, cy + font.lineHeight + 2, 0xFFFFFF);
    }

    /** 带符号与后缀的滚动数字（如 +12$ / +50 EXP / -5$）：数值部分滚动，符号固定 */
    private static String signed(int value, long elapsed, String suffix) {
        String sign = value < 0 ? "-" : "+";
        return sign + roll(Math.abs(value), elapsed) + suffix;
    }

    /**
     * 老虎机式逐位滚动：
     * <ul>
     *   <li>目标按十进制拆位（个位 i=0、十位 i=1、…），补零显示到目标位数；</li>
     *   <li>第 i 位从第 i+1 步开始逐格递增（低位先启动），某位达到目标位值后锁定不再变；</li>
     *   <li>总步数 = max(i + 位值_i)，每 5 tick（100ms）推进一步，到目标后停。</li>
     * </ul>
     * 例：目标 123 → 000, 001, 012, 123；目标 256 → 000, 001, 012, 123, 234, 245, 256。
     */
    private static String roll(int target, long elapsed) {
        if (target == 0) {
            return "0";
        }
        int len = String.valueOf(target).length();
        int[] digits = new int[len];
        int rest = target;
        int maxStep = 0;
        for (int i = 0; i < len; i++) {
            digits[i] = rest % 10;
            rest /= 10;
            maxStep = Math.max(maxStep, i + digits[i]);
        }
        int k = (int) Math.min(maxStep, elapsed / TICK_MS);
        StringBuilder sb = new StringBuilder(len);
        for (int i = len - 1; i >= 0; i--) {
            int val = Math.min(digits[i], Math.max(0, k - i));
            sb.append((char) ('0' + val));
        }
        return sb.toString();
    }

    private MsbKillFeedOverlay() {
    }
}