package com.mercenarysandbox.msb.match;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.ai.AiManager;
import com.mercenarysandbox.msb.ai.AiUnit;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.network.MatchStatePayload;
import com.mercenarysandbox.msb.network.UnitPositionsPayload;

/**
 * 对局管理器（服务端单例，按 MinecraftServer 实例隔离）。
 * 负责：控制区初始化、30s 结算计分、每 2s 广播 MatchState、
 * 战术地图单位广播（docs/02 §3.9：每 4 tick 与 AI 惰性推进同频）。
 */
public final class MatchManager {
    /** 状态广播周期（tick）：每 20 tick（1s）处理一次倒计时与广播 */
    private static final int SECOND_TICKS = 20;
    /** 战术地图单位广播周期（tick）：与 AI 惰性推进（AiManager 每 4 tick）同频 */
    private static final int UNIT_BROADCAST_TICKS = 4;
    /** 交战窗口（tick）：玩家最近 5s 内受到伤害即标为「交战」，对敌可见 */
    private static final int ENGAGED_TICKS = 100;
    /** 地图边界半径倍数与下限：边界 = 控制区半径 × 倍数，最小 128 格 */
    private static final int MAP_RADIUS_MULTIPLIER = 4;
    private static final int MIN_MAP_RADIUS = 128;

    private static MatchManager instance;

    private final MinecraftServer server;
    private ControlZone zone;
    private final Map<Faction, Integer> scores = new EnumMap<>(Faction.class);
    private final Map<UUID, Integer> lastHurtTick = new HashMap<>();
    private int countdownTicks;
    private int tickCounter;
    private int unitTickCounter;

    private MatchManager(MinecraftServer server) {
        this.server = server;
    }

    /** 获取当前服务器的对局管理器（服务器实例变化时重建） */
    public static MatchManager get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new MatchManager(server);
            instance.init();
        }
        return instance;
    }

    /** 初始化：控制区圆心 = 主世界出生点，半径与结算周期来自配置；初始 AI 填充 */
    private void init() {
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        this.zone = new ControlZone(spawn.getX(), spawn.getY(), spawn.getZ(), Config.CONTROL_ZONE_RADIUS.get());
        this.countdownTicks = Config.SETTLE_INTERVAL_SECONDS.get() * SECOND_TICKS;
        for (Faction f : Faction.values()) {
            if (f != Faction.NONE) {
                scores.put(f, 0);
            }
        }
        FactionManager.ensureTeams(server.getScoreboard());
        AiManager.get(server).reconcileAll();
        MercenarySandboxShooter.LOGGER.info("MSB 控制区初始化: 圆心({},{},{}) 半径{} 结算{}s",
                zone.getCenterX(), zone.getCenterY(), zone.getCenterZ(), zone.getRadius(), Config.SETTLE_INTERVAL_SECONDS.get());
    }

    public ControlZone getZone() {
        return zone;
    }

    /** 服务端每 tick 驱动：倒计时 + 结算 + 状态广播 + 战术地图单位广播 */
    public void tick() {
        if (zone == null) {
            return;
        }
        tickCounter++;
        unitTickCounter++;
        if (unitTickCounter % UNIT_BROADCAST_TICKS == 0) {
            broadcastUnits();
        }
        if (tickCounter % SECOND_TICKS == 0) {
            broadcastState();
            countdownTicks--;
            if (countdownTicks <= 0) {
                settle();
                countdownTicks = Config.SETTLE_INTERVAL_SECONDS.get() * SECOND_TICKS;
            }
        }
    }

    /** 记录玩家最近一次受伤 tick（由 LivingDamageEvent.Pre 触发，供交战判定） */
    public void recordDamageTick(ServerPlayer player) {
        lastHurtTick.put(player.getUUID(), server.getTickCount());
    }

    /**
     * 结算瞬间：圈内「战斗单位」（存活真人 + AI 模拟单位）按阵营聚合，每单位 +1 分；
     * 0 人在圈 → 不加分（docs/02 §3.2 空圈策略）。
     */
    private void settle() {
        Map<Faction, Integer> zoneCounts = new EnumMap<>(Faction.class);
        int total = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            Faction f = FactionManager.getPlayerFaction(p);
            if (f == Faction.NONE) {
                continue;
            }
            if (zone.contains(p.blockPosition())) {
                zoneCounts.merge(f, 1, Integer::sum);
                total++;
            }
        }
        // PvPvE：AI 模拟单位按模拟坐标参与计分
        Map<Faction, Integer> aiCounts = AiManager.get(server).countInZone(zone);
        for (Map.Entry<Faction, Integer> e : aiCounts.entrySet()) {
            zoneCounts.merge(e.getKey(), e.getValue(), Integer::sum);
            total += e.getValue();
        }
        if (total == 0) {
            return;
        }
        for (Map.Entry<Faction, Integer> e : zoneCounts.entrySet()) {
            scores.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        server.getPlayerList().broadcastSystemMessage(buildSettleMessage(zoneCounts), false);
        broadcastState();
    }

    /** 结算播报：列出所有获得分数的阵营与增量 */
    private Component buildSettleMessage(Map<Faction, Integer> zoneCounts) {
        MutableComponent msg = Component.translatable("msb.chat.settle");
        for (Faction f : Faction.values()) {
            if (f == Faction.NONE) {
                continue;
            }
            Integer n = zoneCounts.get(f);
            if (n != null && n > 0) {
                msg.append(" ")
                        .append(Component.translatable(f.getDisplayKey()).withStyle(f.getChatColor()))
                        .append(" +")
                        .append(String.valueOf(n));
            }
        }
        return msg;
    }

    /** 向所有客户端广播当前对局状态 */
    private void broadcastState() {
        PacketDistributor.sendToAllPlayers(buildStatePayload());
    }

    public MatchStatePayload buildStatePayload() {
        return new MatchStatePayload(
                zone.getCenterX(), zone.getCenterZ(), zone.getRadius(),
                Math.max(0, countdownTicks / SECOND_TICKS),
                scores.getOrDefault(Faction.LONESTAR, 0),
                scores.getOrDefault(Faction.VALKYRA, 0),
                scores.getOrDefault(Faction.MANTICORE, 0));
    }

    /** 向所有客户端广播战术地图单位列表（无订阅客户端时跳过，省带宽） */
    private void broadcastUnits() {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        PacketDistributor.sendToAllPlayers(buildUnitPositionsPayload());
    }

    /**
     * 组装战术地图载荷：真人（阵营 + 交战标记）+ 全部 AI 模拟单位。
     * 敌情可见性规则（docs/02 §3.9）：敌方仅下发「已交战真人 + 全部 AI」，隐蔽敌人不发送。
     */
    public UnitPositionsPayload buildUnitPositionsPayload() {
        int now = server.getTickCount();
        List<UnitPositionsPayload.UnitEntry> units = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Faction f = FactionManager.getPlayerFaction(p);
            if (f == Faction.NONE) {
                continue;
            }
            boolean engaged = now - lastHurtTick.getOrDefault(p.getUUID(), Integer.MIN_VALUE) < ENGAGED_TICKS;
            units.add(new UnitPositionsPayload.UnitEntry(f.getId(), p.blockPosition().getX(), p.blockPosition().getZ(), engaged));
        }
        for (AiUnit u : AiManager.get(server).allUnits()) {
            units.add(new UnitPositionsPayload.UnitEntry(u.getFaction().getId(), u.getPos().getX(), u.getPos().getZ(), false));
        }
        int mapRadius = Math.max(MIN_MAP_RADIUS, zone.getRadius() * MAP_RADIUS_MULTIPLIER);
        return new UnitPositionsPayload(zone.getCenterX(), zone.getCenterZ(), mapRadius, units);
    }
}
