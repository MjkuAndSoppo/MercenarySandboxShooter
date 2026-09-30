package com.mercenarysandbox.msb.client;

import com.mercenarysandbox.msb.network.KillFeedPayload;

/**
 * 击杀提示客户端状态（docs/02 击杀提示）。
 * 缓存最近一次击杀提示（目标名称、金钱/经验变动、本命连杀数）与接收时刻，
 * MsbKillFeedOverlay 据此在屏幕中下方渲染，数字带滚动动画。
 */
public final class ClientKillFeed {
    /** 击杀提示记录 */
    public record KillEntry(String targetKey, boolean isPlayer, int money, int xp, int streak, long startedMs) {
    }

    private static KillEntry entry;

    private ClientKillFeed() {
    }

    /** 收到服务端击杀提示载荷后缓存（替换旧的；滚动动画从当前时刻重新开始） */
    public static void show(KillFeedPayload payload) {
        entry = new KillEntry(payload.targetKey(), payload.isPlayer(),
                payload.money(), payload.xp(), payload.streak(), System.currentTimeMillis());
    }

    public static KillEntry get() {
        return entry;
    }

    public static void clear() {
        entry = null;
    }
}