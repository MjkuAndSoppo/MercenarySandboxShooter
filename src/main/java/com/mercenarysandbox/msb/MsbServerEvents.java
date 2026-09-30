package com.mercenarysandbox.msb;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
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
import com.mercenarysandbox.msb.entity.AiCombatantEntity;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.match.KillStreakAttachments;
import com.mercenarysandbox.msb.match.MatchManager;
import com.mercenarysandbox.msb.network.KillFeedPayload;
import com.mercenarysandbox.msb.network.ShopStoragePayload;
import com.mercenarysandbox.msb.network.WalletPayload;
import com.mercenarysandbox.msb.shop.ShopManager;
import com.mercenarysandbox.msb.shop.WeightService;

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
        PacketDistributor.sendToPlayer(player, new WalletPayload(wallet.earned(), wallet.spent(), wallet.total()));
        // 下发本人储存格（商店窗口显示；个人数据单发本人）
        PacketDistributor.sendToPlayer(player, ShopStoragePayload.from(ShopManager.storage(player)));
        WeightService.apply(player);
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

    /** 玩家死亡重生：在本方阵营基地上方复活（未放置基地则维持原版出生点；跨维度一并传送） */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Faction faction = FactionManager.getPlayerFaction(player);
        if (faction == Faction.NONE) {
            return;
        }
        BlockPos base = MatchManager.get(player.server).getBasePos(faction);
        if (base == null) {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        player.teleportTo(overworld, base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        // 重生会重置属性，重新应用负重修饰（docs/02 §3.4 负重系统）
        WeightService.apply(player);
    }

    /** 玩家受到伤害：记录交战时间（战术地图敌情判定依据 docs/02 §3.9）+ 非致命友伤结算 */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof ServerPlayer player) {
            MatchManager.get(player.server).recordDamageTick(player);
        }
        handleFriendlyFire(event, victim);
        // AI 被攻击：立刻锁定攻击者并通知半径 8 格内同阵营 AI 队友（巡逻/推进时被打即反击）
        if (victim instanceof AiCombatantEntity ai) {
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity living) {
                ai.retaliate(living);
            }
        }
    }

    /**
     * 非致命友伤（docs/02 友伤规则）：真人玩家击中同阵营玩家/AI 时——
     * 伤害砍半、攻击者按伤害量扣款、推送一行「友伤 &lt;名称&gt; -xx$」提示。
     * 致命一击不在此结算（由死亡事件按友军击杀规则罚款），避免双重扣款。
     */
    private static void handleFriendlyFire(LivingDamageEvent.Pre event, LivingEntity victim) {
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer shooter) || shooter == victim) {
            return;
        }
        Faction shooterFaction = FactionManager.getPlayerFaction(shooter);
        if (shooterFaction == Faction.NONE) {
            return;
        }
        Faction victimFaction = victimFactionOf(victim);
        if (victimFaction == Faction.NONE || victimFaction != shooterFaction) {
            return;
        }
        float damage = event.getNewDamage();
        // 致命一击交给死亡结算（友军击杀规则），此处只处理非致命
        if (damage <= 0.0F || victim.getHealth() - damage <= 0.0F) {
            return;
        }
        // 非致命友伤：伤害砍半（每次命中都砍半）
        event.setNewDamage(damage * 0.5F);
        // 扣款节流：连发/霰弹一次「命中」会产生多个伤害事件（每颗弹丸一次），
        // 同一队友在窗口内只结算一次罚款，否则一梭子瞬间把资产扣光
        if (!shouldChargeFriendlyFire(shooter, victim, shooter.serverLevel().getGameTime())) {
            return;
        }
        // 攻击者按伤害量扣款（至少 1，且不超过单次上限），并复用友伤提示通道（单行显示）
        int penalty = Math.max(1, Math.round(damage * Config.FRIENDLY_DAMAGE_PENALTY_PER_HP.get()));
        penalty = Math.min(penalty, Config.FRIENDLY_DAMAGE_PENALTY_CAP.get());
        PlayerWallet wallet = shooter.getData(WalletAttachments.WALLET);
        // 允许为负：欠款购买后不能靠罚款把欠款抹平（否则等于凭空销账）
        int newTotal = wallet.total() - penalty;
        shooter.setData(WalletAttachments.WALLET, wallet.withFinance(wallet.spent(), newTotal));
        PacketDistributor.sendToPlayer(shooter,
                new WalletPayload(wallet.earned(), wallet.spent(), newTotal));
        PacketDistributor.sendToPlayer(shooter, new KillFeedPayload(displayNameOf(victim), true, -penalty, 0,
                shooter.getData(KillStreakAttachments.STREAK), true));
    }

    /** 友伤扣款节流窗口（tick）：同一攻击者→同一受害者在窗口内只结算一次罚款 */
    private static final int FRIENDLY_FIRE_THROTTLE_TICKS = 10;
    /** 节流表：键 = 攻击者UUID|受害者UUID，值 = 上次结算的 gameTime */
    private static final Map<String, Long> FRIENDLY_FIRE_LAST = new HashMap<>();

    /** 返回 true 表示本次命中应当结算罚款（窗口外首次命中）；窗口内命中只砍半伤害、不再扣款 */
    private static boolean shouldChargeFriendlyFire(ServerPlayer shooter, LivingEntity victim, long gameTime) {
        String key = shooter.getUUID() + "|" + victim.getUUID();
        Long last = FRIENDLY_FIRE_LAST.get(key);
        if (last != null && gameTime - last < FRIENDLY_FIRE_THROTTLE_TICKS) {
            return false;
        }
        FRIENDLY_FIRE_LAST.put(key, gameTime);
        if (FRIENDLY_FIRE_LAST.size() > 512) {
            FRIENDLY_FIRE_LAST.entrySet().removeIf(entry -> gameTime - entry.getValue() >= FRIENDLY_FIRE_THROTTLE_TICKS);
        }
        return true;
    }

    /** 受害者阵营：真人取分配阵营，战斗 AI 取实体阵营 */
    private static Faction victimFactionOf(LivingEntity victim) {
        if (victim instanceof ServerPlayer player) {
            return FactionManager.getPlayerFaction(player);
        }
        if (victim instanceof AiCombatantEntity ai) {
            return ai.getFaction();
        }
        return Faction.NONE;
    }

    /** 单位显示名：玩家用档案名，AI 优先自定义名（S.Fpmc.n） */
    private static String displayNameOf(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            return player.getGameProfile().getName();
        }
        return entity.getCustomName() != null ? entity.getCustomName().getString() : entity.getName().getString();
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
        // 死者为玩家：本命击杀数与本命收入清零（总资产保留，换命后重新统计）
        if (victim instanceof ServerPlayer dead) {
            dead.setData(KillStreakAttachments.STREAK, 0);
            PlayerWallet deadWallet = dead.getData(WalletAttachments.WALLET);
            dead.setData(WalletAttachments.WALLET, deadWallet.resetLife());
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
        // 仅同阵营 AI 友伤时置位：客户端只显示一行「友伤 <名称> -xx$」，不显示下两行
        boolean friendlyFire = false;
        if (targetIsPlayer) {
            ServerPlayer target = (ServerPlayer) victim;
            Faction killerFaction = FactionManager.getPlayerFaction(killer);
            Faction targetFaction = FactionManager.getPlayerFaction(target);
            boolean playerFriendly = killerFaction != Faction.NONE && killerFaction == targetFaction;
            if (playerFriendly) {
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
        } else if (victim instanceof AiCombatantEntity ai) {
            Faction killerFaction = FactionManager.getPlayerFaction(killer);
            Faction aiFaction = ai.getFaction();
            friendlyFire = killerFaction != Faction.NONE && killerFaction == aiFaction;
            if (friendlyFire) {
                // 同阵营 AI 友伤：罚款、无经验（连杀亦不计，见下方 streak 处理）
                money = -Config.KILL_FRIENDLY_PENALTY.get();
                xp = 0;
                targetKey = ai.getCustomName() != null ? ai.getCustomName().getString() : ai.getName().getString();
                isPlayer = true; // AI 名为字面文本，非翻译键
            } else {
                // 击杀敌方 AI：按普通击杀奖励；名称传实体翻译键（客户端本地化）
                money = Config.KILL_MONEY_REWARD.get();
                xp = Config.KILL_XP_REWARD.get();
                targetKey = "entity." + BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getNamespace()
                        + "." + BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getPath();
                isPlayer = false;
            }
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
        // 更新钱包：击杀奖励入账（计入本命收入）/ 友军罚款扣款（不计收入）
        PlayerWallet wallet = killer.getData(WalletAttachments.WALLET);
        int newTotal = wallet.total() + money;   // 允许为负：奖励先抵扣欠款
        int newEarned = money > 0 ? wallet.earned() + money : wallet.earned();
        killer.setData(WalletAttachments.WALLET, new PlayerWallet(wallet.spent(), newTotal, newEarned));
        PacketDistributor.sendToPlayer(killer, new WalletPayload(newEarned, wallet.spent(), newTotal));

        // 本命连杀 +1 并推送击杀提示（同阵营 AI 友伤不计连杀）
        int streak;
        if (friendlyFire) {
            streak = killer.getData(KillStreakAttachments.STREAK);
        } else {
            streak = killer.getData(KillStreakAttachments.STREAK) + 1;
            killer.setData(KillStreakAttachments.STREAK, streak);
        }
        PacketDistributor.sendToPlayer(killer, new KillFeedPayload(targetKey, isPlayer, money, xp, streak, friendlyFire));
    }

    /** 服务端每 tick：控制区结算驱动 + AI 惰性移动 + 战术地图单位广播 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        MatchManager match = MatchManager.get(server);
        match.tick();
        AiManager.get(server).tick(match.getZone());
        // 负重兜底重算：每 20 tick 对在线玩家刷新（成本 41 格 × N 人，可忽略）
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                WeightService.apply(player);
            }
        }
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
