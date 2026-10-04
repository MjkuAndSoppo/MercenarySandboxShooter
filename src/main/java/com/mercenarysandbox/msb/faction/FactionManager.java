package com.mercenarysandbox.msb.faction;

import java.util.ArrayList;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.network.SyncFactionPayload;

/**
 * 阵营服务端单例：计分板队伍维护、阵营选择落地、附体读写。
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

    /** 加入即恢复：已有阵营则恢复队伍并同步；无阵营保持 NONE（开局由雇佣兵手册选择阵营） */
    public static void assignOnJoin(ServerPlayer player) {
        Faction current = getPlayerFaction(player);
        if (current == Faction.NONE) {
            // 无阵营：只同步 NONE，等待玩家通过雇佣兵手册选择（开局流程）
            PacketDistributor.sendToPlayer(player, new SyncFactionPayload(Faction.NONE.getId()));
            return;
        }
        setTeam(player, current);
        PacketDistributor.sendToPlayer(player, new SyncFactionPayload(current.getId()));
    }

    /** 显式改派阵营（开局流程：雇佣兵手册 FactionSelect 选择后调用） */
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
}
