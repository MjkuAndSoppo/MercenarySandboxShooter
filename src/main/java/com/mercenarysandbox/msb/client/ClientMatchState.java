package com.mercenarysandbox.msb.client;

import com.mercenarysandbox.msb.network.MatchStatePayload;

/**
 * 客户端对局状态缓存：由 S2C 载荷更新，HUD 只读呈现。
 * 数据源头在服务端，本类不做任何逻辑判定（防作弊：客户端不自行推导归属）。
 */
public final class ClientMatchState {
    private static MatchStatePayload matchState;
    private static int ownFactionId = -1;

    private ClientMatchState() {
    }

    public static void accept(MatchStatePayload payload) {
        matchState = payload;
    }

    public static void setOwnFaction(int factionId) {
        ownFactionId = factionId;
    }

    public static MatchStatePayload getMatchState() {
        return matchState;
    }

    public static int getOwnFactionId() {
        return ownFactionId;
    }
}
