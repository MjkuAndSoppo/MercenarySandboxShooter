package com.mercenarysandbox.msb.faction;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.network.SyncFactionPayload;

/**
 * 阵营服务端单例：计分板队伍维护、加入即分配、附体读写、真人人数统计。
 * 敌我识别判定（同阵营=友）服务端权威，客户端只收 SyncFaction 结果。
 */
public final class FactionManager {
    private FactionManager() {
    }

    /** 确保三大阵营计分板队伍存在（承载名字染色 / Tab / 友伤开关） */
    public static void ensureTeams(Scoreboard scoreboard) {
        for (Faction f : Faction.values()) {
            if (f == Faction.NONE) {
                continue;
            }
            PlayerTeam team = scoreboard.getPlayerTeam(f.getTeamName());
            if (team == null) {
                team = scoreboard.addPlayerTeam(f.getTeamName());
                team.setDisplayName(Component.translatable(f.getDisplayKey()));
                team.setColor(f.getChatColor());
                // 友伤规则最终口径（docs/02 §3.10）：允许友伤，伤害砍半与按次罚款在 M2 战斗层实现
                team.setAllowFriendlyFire(true);
            }
        }
    }

    /** 读取玩家阵营附体 */
    public static Faction getPlayerFaction(ServerPlayer player) {
        return player.getData(FactionAttachments.FACTION);
    }

    /** 加入即分配：已有阵营则恢复队伍；无阵营则分入真人最少的一方 */
    public static void assignOnJoin(ServerPlayer player) {
        Faction current = getPlayerFaction(player);
        if (current == Faction.NONE) {
            current = leastPopulated(player.server);
            player.setData(FactionAttachments.FACTION, current);
        }
        setTeam(player, current);
        PacketDistributor.sendToPlayer(player, new SyncFactionPayload(current.getId()));
    }

    /** 显式改派阵营（M1 未提供选择 UI，留给后续 FactionSelect 使用） */
    public static void setPlayerFaction(ServerPlayer player, Faction faction) {
        player.setData(FactionAttachments.FACTION, faction);
        setTeam(player, faction);
        PacketDistributor.sendToPlayer(player, new SyncFactionPayload(faction.getId()));
    }

    /** 死亡重生后继承阵营附体（重生会克隆出新实体，附体不会自动复制） */
    public static void copyOnRespawn(ServerPlayer newPlayer, ServerPlayer oldPlayer) {
        Faction faction = getPlayerFaction(oldPlayer);
        newPlayer.setData(FactionAttachments.FACTION, faction);
        setTeam(newPlayer, faction);
    }

    /** 把玩家从所有队伍移除后加入目标队伍（幂等恢复，跨重连安全） */
    private static void setTeam(ServerPlayer player, Faction faction) {
        Scoreboard scoreboard = player.getScoreboard();
        for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
            if (team.getPlayers().contains(player.getScoreboardName())) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
            }
        }
        PlayerTeam target = scoreboard.getPlayerTeam(faction.getTeamName());
        if (target != null) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), target);
        }
    }

    /** 实时统计某阵营在线真人玩家数（供 AI 槽位平衡与加入分配使用） */
    public static int countRealPlayers(MinecraftServer server, Faction faction) {
        int count = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (getPlayerFaction(p) == faction) {
                count++;
            }
        }
        return count;
    }

    /** 选出真人玩家最少的阵营（平局时按服务器 tick 随机取一，避免涌入同一边） */
    public static Faction leastPopulated(MinecraftServer server) {
        List<Faction> candidates = new ArrayList<>();
        int min = Integer.MAX_VALUE;
        for (Faction f : Faction.values()) {
            if (f == Faction.NONE) {
                continue;
            }
            int n = countRealPlayers(server, f);
            if (n < min) {
                min = n;
                candidates.clear();
                candidates.add(f);
            } else if (n == min) {
                candidates.add(f);
            }
        }
        if (candidates.isEmpty()) {
            return Faction.LONESTAR;
        }
        return candidates.get(server.getTickCount() % candidates.size());
    }
}
