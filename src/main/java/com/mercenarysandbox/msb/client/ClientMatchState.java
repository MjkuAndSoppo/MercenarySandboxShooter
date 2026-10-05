package com.mercenarysandbox.msb.client;

import com.mercenarysandbox.msb.network.MatchStatePayload;
import com.mercenarysandbox.msb.network.UnitPositionsPayload;

/**
 * 客户端对局状态缓存：由 S2C 载荷更新，HUD/战术地图只读呈现。
 * 数据源头在服务端，本类不做任何逻辑判定（防作弊：客户端不自行推导归属/敌情）。
 */
public final class ClientMatchState {
    private static MatchStatePayload matchState;
    private static UnitPositionsPayload unitPositions;
    private static int ownFactionId = -1;
    /** 本人钱包（HUD 财产显示）：本条命收入 / 花销 / 总资产，S2C WalletPayload 更新 */
    private static int walletEarned;
    private static int walletSpent;
    private static int walletTotal;
    /** 本人耐力百分比（0~1 浮点）：S2C StaminaPayload 更新；<0 表示尚未收到（HUD 回退到 foodLevel/20） */
    private static float staminaPercent = -1.0F;

    private ClientMatchState() {
    }

    /** 本人耐力百分比（自绘耐力条精确读取；负数表示未收到，调用方自行回退） */
    public static float getStaminaPercent() {
        return staminaPercent;
    }

    public static void setStaminaPercent(float percent) {
        staminaPercent = percent;
    }

    public static void setWallet(int earned, int spent, int total) {
        walletEarned = earned;
        walletSpent = spent;
        walletTotal = total;
    }

    /** 本条命赚到的钱（HUD 左侧显示） */
    public static int getWalletEarned() {
        return walletEarned;
    }

    public static int getWalletSpent() {
        return walletSpent;
    }

    public static int getWalletTotal() {
        return walletTotal;
    }

    public static void accept(MatchStatePayload payload) {
        matchState = payload;
    }

    public static void accept(UnitPositionsPayload payload) {
        unitPositions = payload;
    }

    public static void setOwnFaction(int factionId) {
        ownFactionId = factionId;
    }

    public static MatchStatePayload getMatchState() {
        return matchState;
    }

    public static UnitPositionsPayload getUnitPositions() {
        return unitPositions;
    }

    public static int getOwnFactionId() {
        return ownFactionId;
    }
}
