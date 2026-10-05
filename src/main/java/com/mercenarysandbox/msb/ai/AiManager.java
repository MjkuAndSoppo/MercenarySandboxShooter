package com.mercenarysandbox.msb.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.entity.AiCombatantEntity;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.match.ControlZone;
import com.mercenarysandbox.msb.match.MatchManager;
import com.mercenarysandbox.msb.onboarding.FactionSetupData;

/**
 * AI 填充（服务端实体化管理，docs/02 §3.12）。
 * 每阵营 AI 实体数 = 开局选择的 AI 数量目标（FactionSetupData，多真人玩家不占用 AI 槽，不去顶替）；
 * AI 被击杀后进入独立复活队列（每个 AI 单独 CD），CD 到期从所属阵营基地复活，
 * 无基地时不生成；实体可能被玩家击杀，tick 每轮先清理 isRemoved。
 */
public final class AiManager {
    /** 复活 CD（tick）：每个 AI 死亡后单独计时，到期后从阵营基地复活（10s） */
    private static final int RESPAWN_CD_TICKS = 200;

    private static AiManager instance;

    private final MinecraftServer server;
    private final Map<Faction, List<AiCombatantEntity>> units = new EnumMap<>(Faction.class);
    private final Map<Faction, Integer> seq = new EnumMap<>(Faction.class);
    /** 待复活队列：阵营 → 死亡时的服务端 tick（FIFO，队首最早死亡最早到期，各自独立 CD） */
    private final Map<Faction, Deque<Long>> respawnQueue = new EnumMap<>(Faction.class);
    private int tickCounter;

    private AiManager(MinecraftServer server) {
        this.server = server;
        for (Faction f : Faction.values()) {
            if (f != Faction.NONE) {
                units.put(f, new ArrayList<>());
                seq.put(f, 0);
                respawnQueue.put(f, new ArrayDeque<>());
            }
        }
        purgeStaleUnits();
    }

    /** 清理存档遗留的旧 AI 实体（persistenceRequired 跨会话残留，与本次生成重名造成单位混淆），仅保留本实例生成单位 */
    private void purgeStaleUnits() {
        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }
        List<AiCombatantEntity> stale = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof AiCombatantEntity ai) {
                stale.add(ai);
            }
        }
        for (AiCombatantEntity ai : stale) {
            leaveTeam(ai);
            ai.discard();
        }
    }

    /** 获取当前服务器的 AI 管理器（服务器实例变化时重建） */
    public static AiManager get(MinecraftServer server) {
        if (instance == null || instance.server != server) {
            instance = new AiManager(server);
        }
        return instance;
    }

    /**
     * 卸载全部 AI：清空单位列表与复活队列，并兜底清理世界中残留的 AI 实体。
     * 每次进入存档、以及开局前调用 —— 未开局时世界内不应存在任何滞留 AI。
     */
    public void despawnAll() {
        for (List<AiCombatantEntity> list : units.values()) {
            for (AiCombatantEntity e : list) {
                leaveTeam(e);
                e.discard();
            }
            list.clear();
        }
        for (Deque<Long> queue : respawnQueue.values()) {
            queue.clear();
        }
        purgeStaleUnits();
    }

    /** 初始填充：各阵营补齐到目标人数（服务器启动时真人 = 0，即全量 AI） */
    public void reconcileAll() {
        for (Faction f : units.keySet()) {
            reconcile(f);
        }
    }

    /** 基地方块放置后：立即为该阵营补足 AI 实体（无基地时 AI 不生成） */
    public void onBasePlaced(Faction faction) {
        reconcile(faction);
    }

    /** 按目标人数补齐/裁减某阵营 AI（开局门槛未满足或无基地时跳过；目标 = 开局选择的 AI 数量，下限 0） */
    public void reconcile(Faction faction) {
        if (!MatchManager.get(server).isStarted()) {
            return; // 开局门槛未满足：不生成/复活 AI（docs/02 §3.14）
        }
        if (MatchManager.get(server).getBasePos(faction) == null) {
            return;
        }
        int desired = Math.max(0, FactionSetupData.get(server).getTarget(faction));
        List<AiCombatantEntity> list = units.get(faction);
        Deque<Long> queue = respawnQueue.get(faction);
        while (list.size() + queue.size() < desired) {
            spawnUnit(faction);
        }
        while (list.size() + queue.size() > desired) {
            if (queue != null && !queue.isEmpty()) {
                queue.removeLast();
            } else if (!list.isEmpty()) {
                AiCombatantEntity e = list.remove(list.size() - 1);
                leaveTeam(e);
                e.discard();
            } else {
                break;
            }
        }
    }

    /** 生成一个 AI 实体：从所属阵营基地周围生成（无基地不生成，由 reconcile/drain 前置保证） */
    private void spawnUnit(Faction faction) {
        int n = seq.merge(faction, 1, Integer::sum);
        ServerLevel level = server.overworld();
        AiCombatantEntity entity = MercenarySandboxShooter.AI_COMBATANT.get().create(level);
        if (entity == null) {
            return;
        }
        BlockPos pos = respawnPos(faction, level);
        entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        entity.setFaction(faction);
        // 编队种子：阵营内序号 → 各 AI 的推进/巡逻/跳伞目标互不相同（方阵移动）
        entity.setFormationSeed(n);
        entity.equipLoadout();
        entity.setCustomName(Component.literal(faction.getAbbr() + "pmc." + n));
        joinTeam(entity, faction);
        // 强制加载出生 chunk：基地多在模拟距离外，不加载会被卸载（isRemoved）导致单位列表清空、AI 不推进不复活
        level.getChunkSource().updateChunkForced(new ChunkPos(pos), true);
        level.addFreshEntity(entity);
        units.get(faction).add(entity);
    }

    /** 复活/出生点：所属阵营基地周围 6 格（站基地方块顶面） */
    private BlockPos respawnPos(Faction faction, ServerLevel level) {
        BlockPos base = MatchManager.get(server).getBasePos(faction);
        if (base == null) {
            return level.getSharedSpawnPos();
        }
        int ox = level.getRandom().nextInt(13) - 6;
        int oz = level.getRandom().nextInt(13) - 6;
        return new BlockPos(base.getX() + ox, base.getY() + 1, base.getZ() + oz);
    }

    /** 加入阵营计分板队伍（阵营染色 + SBW 敌我判定可用的 Entity.getTeam()） */
    private void joinTeam(AiCombatantEntity entity, Faction faction) {
        Scoreboard scoreboard = server.getScoreboard();
        FactionManager.ensureTeams(scoreboard);
        PlayerTeam team = scoreboard.getPlayerTeam(faction.getTeamName());
        if (team != null) {
            scoreboard.addPlayerToTeam(entity.getScoreboardName(), team);
        }
    }

    /** 移除出阵营计分板队伍（discard 前调用，防计分板残留） */
    private void leaveTeam(AiCombatantEntity entity) {
        server.getScoreboard().removePlayerFromTeam(entity.getScoreboardName());
    }

    /**
     * 惰性驱动：每 tick 清理被击杀实体（登记复活队列）并推进复活 CD；
     * 每 4 tick 让所有 AI 实体朝圈推进一次。
     */
    public void tick(ControlZone zone) {
        if (zone == null) {
            return;
        }
        tickCounter++;
        // 实体可能被玩家击杀（isDeadOrDying 登记复活 → 原版随后移除），清理防悬空引用
        for (Faction f : units.keySet()) {
            List<AiCombatantEntity> list = units.get(f);
            list.removeIf(e -> {
                if (e.isDeadOrDying()) {
                    respawnQueue.get(f).add((long) server.getTickCount());
                    return true;
                }
                return e.isRemoved();
            });
        }
        drainRespawns();
        if (tickCounter % 4 != 0) {
            return;
        }
        BlockPos center = new BlockPos(zone.getCenterX(), zone.getCenterY(), zone.getCenterZ());
        ServerLevel level = server.overworld();
        for (List<AiCombatantEntity> list : units.values()) {
            for (AiCombatantEntity e : list) {
                // 每轮重新钉住单位所在 chunk：基地方块多在模拟距离外，防 chunk 卸载导致实体 isRemoved 被清出单位列表
                level.getChunkSource().updateChunkForced(new ChunkPos(e.blockPosition()), true);
                e.lazyTick(center, zone.getRadius());
            }
        }
    }

    /** 复活队列结算：队首死亡满 CD 即复活（FIFO，每个 AI 单独计时互不影响；无基地则不再生成） */
    private void drainRespawns() {
        for (Faction f : respawnQueue.keySet()) {
            Deque<Long> queue = respawnQueue.get(f);
            while (!queue.isEmpty() && server.getTickCount() - queue.peekFirst() >= RESPAWN_CD_TICKS) {
                queue.removeFirst();
                if (MatchManager.get(server).getBasePos(f) != null) {
                    spawnUnit(f);
                }
            }
        }
    }

    /** 结算用：按阵营统计圈内 AI 实体数 */
    public Map<Faction, Integer> countInZone(ControlZone zone) {
        Map<Faction, Integer> result = new EnumMap<>(Faction.class);
        for (List<AiCombatantEntity> list : units.values()) {
            for (AiCombatantEntity e : list) {
                if (!e.isRemoved() && zone.contains(e.blockPosition())) {
                    result.merge(e.getFaction(), 1, Integer::sum);
                }
            }
        }
        return result;
    }

    /** 全部 AI 实体（跨阵营扁平列表，供战术地图广播使用） */
    public List<AiCombatantEntity> allUnits() {
        List<AiCombatantEntity> all = new ArrayList<>();
        for (List<AiCombatantEntity> list : units.values()) {
            all.addAll(list);
        }
        return all;
    }
}
