package com.mercenarysandbox.msb;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.ai.AiManager;
import com.mercenarysandbox.msb.economy.PlayerWallet;
import com.mercenarysandbox.msb.economy.WalletAttachments;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.match.KillStreakAttachments;
import com.mercenarysandbox.msb.match.MatchManager;
import com.mercenarysandbox.msb.network.KillFeedPayload;
import com.mercenarysandbox.msb.network.WalletPayload;

/**
 * 服务端玩法事件接线：阵营分配/恢复、AI 顶替与补位、对局与 AI 惰性 tick。
 * 全部服务端权威（docs/02 §3.1/§3.2/§3.12）。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MsbServerEvents {
    private MsbServerEvents() {
    }

    /** 生物死亡不掉落经验球（经验改为击杀者直接获得 KILL_XP_REWARD，原版经验球多余） */
    @SubscribeEvent
    public static void onMobExperienceDrop(LivingExperienceDropEvent event) {
        event.setCanceled(true);
    }

    /** 玩家加入：分配/恢复阵营，真人顶替一个 AI 槽 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FactionManager.ensureTeams(player.getScoreboard());
        FactionManager.assignOnJoin(player);
        // 下发本人钱包（HUD 财产显示；服务端权威，玩家私有数据单发本人）
        PlayerWallet wallet = player.getData(WalletAttachments.WALLET);
        PacketDistributor.sendToPlayer(player, new WalletPayload(wallet.spent(), wallet.total()));
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

    /**
     * 击杀结算（docs/02 击杀提示）：击杀任意单位后推送击杀提示给击杀者本人。
     * 计算本命连杀数、击杀奖励金钱/经验；击杀怪物经验直接增加到玩家；
     * 友军击杀犯罪者罚款（按友伤规则扣款）；死者（玩家）本命击杀数清零。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        // 死者为玩家：本命击杀数清零（换命后「击杀x n」重新计数）
        if (victim instanceof ServerPlayer dead) {
            dead.setData(KillStreakAttachments.STREAK, 0);
        }
        // 击杀者判定：仅真人玩家击杀触发结算（玩家互杀/击杀怪物/击杀AI）
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer killer)) {
            return;
        }
        boolean targetIsPlayer = victim instanceof ServerPlayer;
        int money;
        int xp;
        String targetKey;
        boolean isPlayer;
        if (targetIsPlayer) {
            ServerPlayer target = (ServerPlayer) victim;
            Faction killerFaction = FactionManager.getPlayerFaction(killer);
            Faction targetFaction = FactionManager.getPlayerFaction(target);
            boolean friendlyFire = killerFaction != Faction.NONE && killerFaction == targetFaction;
            if (friendlyFire) {
                // 友军击杀（docs/02 友伤规则：攻击者追加罚款；受害者获得补偿）
                money = -Config.KILL_FRIENDLY_PENALTY.get();
                xp = 0;
            } else {
                // 击杀敌方玩家：奖励
                money = Config.KILL_MONEY_REWARD.get();
                xp = Config.KILL_XP_REWARD.get();
            }
            targetKey = target.getGameProfile().getName();
            isPlayer = true;
        } else {
            // 击杀怪物：金钱奖励 + 经验直接增加到玩家；名称传实体翻译键（entity.<ns>.<path>，客户端本地化）
            money = Config.KILL_MONEY_REWARD.get();
            xp = Config.KILL_XP_REWARD.get();
            targetKey = "entity." + BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getNamespace()
                    + "." + BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getPath();
            isPlayer = false;
        }
        // 经验直接增加到击杀者（正数加经验；负数减经验）
        if (xp != 0) {
            killer.giveExperiencePoints(xp);
        }
        // 更新钱包：击杀奖励入账 / 友军罚款扣款
        PlayerWallet wallet = killer.getData(WalletAttachments.WALLET);
        int newTotal = Math.max(0, wallet.total() + money);
        killer.setData(WalletAttachments.WALLET, new PlayerWallet(wallet.spent(), newTotal));
        PacketDistributor.sendToPlayer(killer, new WalletPayload(wallet.spent(), newTotal));

        // 本命连杀 +1 并推送击杀提示
        int streak = killer.getData(KillStreakAttachments.STREAK) + 1;
        killer.setData(KillStreakAttachments.STREAK, streak);
        PacketDistributor.sendToPlayer(killer, new KillFeedPayload(targetKey, isPlayer, money, xp, streak));
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
