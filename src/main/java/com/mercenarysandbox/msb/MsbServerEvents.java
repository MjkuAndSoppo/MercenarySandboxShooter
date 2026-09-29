package com.mercenarysandbox.msb;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.mercenarysandbox.msb.ai.AiManager;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.match.MatchManager;

/**
 * 服务端玩法事件接线：阵营分配/恢复、AI 顶替与补位、对局与 AI 惰性 tick。
 * 全部服务端权威（docs/02 §3.1/§3.2/§3.12）。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MsbServerEvents {
    private MsbServerEvents() {
    }

    /** 玩家加入：分配/恢复阵营，真人顶替一个 AI 槽 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FactionManager.ensureTeams(player.getScoreboard());
        FactionManager.assignOnJoin(player);
        AiManager.get(player.server).onRealPlayerJoined(FactionManager.getPlayerFaction(player));
    }

    /** 玩家退出：AI 补位到目标人数 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Faction faction = FactionManager.getPlayerFaction(player);
        if (faction != Faction.NONE) {
            AiManager.get(player.server).onRealPlayerLeft(faction);
        }
    }

    /** 死亡重生：重生会克隆新实体，继承原阵营附体（防重生后掉队） */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()
                || !(event.getOriginal() instanceof ServerPlayer oldPlayer)
                || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }
        FactionManager.copyOnRespawn(newPlayer, oldPlayer);
    }

    /** 玩家受到伤害：记录交战时间（战术地图敌情判定依据 docs/02 §3.9） */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MatchManager.get(player.server).recordDamageTick(player);
        }
    }

    /** 基点安全区（docs/02 §5.1）：本方玩家/本方骑乘的 SBW 载具在己方基地方块半径内免疫全部伤害。
     * 在伤害序列最前的 LivingIncomingDamageEvent 中取消（携带实体所在 Level，兼容不同维度） */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level == null || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        MatchManager match = MatchManager.get(serverLevel.getServer());
        if (entity instanceof ServerPlayer player) {
            Faction f = FactionManager.getPlayerFaction(player);
            if (f != Faction.NONE && match.isInBaseZone(f, player.blockPosition())) {
                event.setCanceled(true);
            }
            return;
        }
        // SBW 载具：取骑乘玩家阵营；区内属本方则无敌（敌方无法造成伤害）
        if (MatchManager.isSbwVehicle(entity)) {
            Entity rider = entity.getFirstPassenger();
            if (rider instanceof ServerPlayer driver) {
                Faction f = FactionManager.getPlayerFaction(driver);
                if (f != Faction.NONE && match.isInBaseZone(f, entity.blockPosition())) {
                    event.setCanceled(true);
                }
            }
        }
    }

    /** 服务端每 tick：控制区结算驱动 + AI 惰性移动 + 战术地图单位广播 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        MatchManager match = MatchManager.get(server);
        match.tick();
        AiManager.get(server).tick(match.getZone());
    }

    /** 玩家放置基地方块 → 注册该阵营基地（docs/02 §3.13；安全区跟随方块） */
    @SubscribeEvent
    public static void onBaseBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Faction f = MatchManager.factionFromBlock(event.getPlacedBlock().getBlock());
        if (f != Faction.NONE) {
            MatchManager.get(level.getServer()).registerBase(f, event.getPos());
        }
    }

    /** 基地方块被拆除 → 移除该阵营基地项，安全区即时失效 */
    @SubscribeEvent
    public static void onBaseBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Faction f = MatchManager.factionFromBlock(event.getState().getBlock());
        if (f != Faction.NONE) {
            MatchManager.get(level.getServer()).removeBase(f, event.getPos());
        }
    }
}
