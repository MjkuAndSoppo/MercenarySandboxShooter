package com.mercenarysandbox.msb.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.match.ControlZone;
import com.mercenarysandbox.msb.match.MatchManager;

/**
 * AI 填充与真人顶替（服务端模拟，docs/02 §3.12）。
 * 每阵营 AI 单位数 = 目标战斗单位数 - 真人玩家数；
 * 真人加入 → 顶替一个 AI 槽（播报「AI 被玩家顶替」）；真人退出 → AI 补位。
 */
public final class AiManager {
    private static AiManager instance;

    private final MinecraftServer server;
    private final Map<Faction, List<AiUnit>> units = new EnumMap<>(Faction.class);
    private final Map<Faction, Integer> seq = new EnumMap<>(Faction.class);
    private int tickCounter;

    private AiManager(MinecraftServer server) {
        this.server = server;
        for (Faction f : Faction.values()) {
            if (f != Faction.NONE) {
                units.put(f, new ArrayList<>());
                seq.put(f, 0);
            }
        }
    }

    /** 获取当前服务器的 AI 管理器（服务器实例变化时重建） */
    public static AiManager get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new AiManager(server);
        }
        return instance;
    }

    /** 初始填充：各阵营补齐到目标人数（服务器启动时真人 = 0，即全量 AI） */
    public void reconcileAll() {
        for (Faction f : units.keySet()) {
            reconcile(f);
        }
    }

    /** 真人加入：顶替一个 AI 槽（该阵营尚有 AI 时） */
    public void onRealPlayerJoined(Faction faction) {
        List<AiUnit> list = units.get(faction);
        if (list == null || list.isEmpty()) {
            return;
        }
        AiUnit removed = list.remove(list.size() - 1);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("msb.chat.ai_replaced", removed.getName()), false);
    }

    /** 真人退出：AI 补位到目标人数 */
    public void onRealPlayerLeft(Faction faction) {
        reconcile(faction);
    }

    /** 按目标人数补齐/裁减某阵营 AI（目标 = 配置目标数 - 真人玩家数，下限 0） */
    private void reconcile(Faction faction) {
        int desired = Math.max(0, Config.AI_TARGET_PER_FACTION.get() - FactionManager.countRealPlayers(server, faction));
        List<AiUnit> list = units.get(faction);
        while (list.size() < desired) {
            spawnUnit(faction);
        }
        while (list.size() > desired) {
            list.remove(list.size() - 1);
        }
    }

    /** 生成一个 AI 单位：无阵营出生点时，围绕控制区随机散布（模拟向圈内推进） */
    private void spawnUnit(Faction faction) {
        int n = seq.merge(faction, 1, Integer::sum);
        ControlZone zone = MatchManager.get(server).getZone();
        int spread = Math.max(8, zone.getRadius() / 2);
        int x = zone.getCenterX() + server.overworld().getRandom().nextInt(2 * spread) - spread;
        int z = zone.getCenterZ() + server.overworld().getRandom().nextInt(2 * spread) - spread;
        units.get(faction).add(new AiUnit(UUID.randomUUID(), faction,
                "AI-" + faction.name().charAt(0) + "-" + n, new BlockPos(x, zone.getCenterY(), z)));
    }

    /** 惰性驱动：每 4 tick 让所有 AI 单位推进一次 */
    public void tick(ControlZone zone) {
        if (zone == null) {
            return;
        }
        tickCounter++;
        if (tickCounter % 4 != 0) {
            return;
        }
        BlockPos center = new BlockPos(zone.getCenterX(), zone.getCenterY(), zone.getCenterZ());
        for (List<AiUnit> list : units.values()) {
            for (AiUnit u : list) {
                u.lazyTick(center, zone.getRadius());
            }
        }
    }

    /** 结算用：按阵营统计圈内 AI 单位数 */
    public Map<Faction, Integer> countInZone(ControlZone zone) {
        Map<Faction, Integer> result = new EnumMap<>(Faction.class);
        for (List<AiUnit> list : units.values()) {
            for (AiUnit u : list) {
                if (zone.contains(u.getPos())) {
                    result.merge(u.getFaction(), 1, Integer::sum);
                }
            }
        }
        return result;
    }
}
