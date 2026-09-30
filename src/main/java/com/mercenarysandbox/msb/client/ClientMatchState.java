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
    /** 本人钱包（HUD 财产显示）：花销 / 总资产，S2C WalletPayload 更新 */
    private static int walletSpent;
    private static int walletTotal;

    private ClientMatchState() {
    }

    public static void setWallet(int spent, int total) {
        walletSpent = spent;
        walletTotal = total;
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
