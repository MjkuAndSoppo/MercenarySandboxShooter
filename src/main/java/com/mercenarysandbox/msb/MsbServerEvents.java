package com.mercenarysandbox.msb;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.mercenarysandbox.msb.ai.AiManager;
import com.mercenarysandbox.msb.economy.PlayerWallet;
import com.mercenarysandbox.msb.economy.WalletAttachments;
import com.mercenarysandbox.msb.entity.AiCombatantEntity;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.match.FactionFundData;
import com.mercenarysandbox.msb.match.KillStreakAttachments;
import com.mercenarysandbox.msb.match.MatchManager;
import com.mercenarysandbox.msb.network.KillFeedPayload;
import com.mercenarysandbox.msb.network.ShopStoragePayload;
import com.mercenarysandbox.msb.network.StaminaPayload;
import com.mercenarysandbox.msb.network.WalletPayload;
import com.mercenarysandbox.msb.onboarding.MercenaryProfileAttachments;
import com.mercenarysandbox.msb.onboarding.OnboardingManager;
import com.mercenarysandbox.msb.shop.ShopManager;
import com.mercenarysandbox.msb.shop.WeightService;
import com.mercenarysandbox.msb.stamina.StaminaManager;

/**
 * 服务端玩法事件接线：阵营分配/恢复、开局手册与阵营选择、对局与 AI 惰性 tick。
 * 全部服务端权威（docs/02 §3.1/§3.2/§3.12）。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MsbServerEvents {
    private MsbServerEvents() {
    }

    /**
     * 未开局时禁止任何 AI 存在：滞留 AI（尤其视距外、随 chunk 后加载从存档恢复的实体）一进入世界即卸载。
     * {@code AiManager.despawnAll} 只能清理已加载 chunk，覆盖不到未加载区域；此事件对 loadedFromDisk 实体同样触发，
     * 因而能兜底清理全图残留（docs/02 §3.12）。
     */
    @SubscribeEvent
    public static void onAiJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AiCombatantEntity)) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level && !MatchManager.get(level.getServer()).isStarted()) {
            event.setCanceled(true);
        }
    }

    /** 生物死亡不掉落经验球（经验改为击杀者直接获得 KILL_XP_REWARD，原版经验球多余） */
    @SubscribeEvent
    public static void onMobExperienceDrop(LivingExperienceDropEvent event) {
        event.setCanceled(true);
    }

    /** 玩家加入：恢复阵营（无阵营保持 NONE）；无阵营玩家发放雇佣兵手册，由手册选择开局 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FactionManager.ensureTeams(player.getScoreboard());
        FactionManager.assignOnJoin(player);
        // 无阵营 → 发手册 + 引导（开局由手册选择阵营/资金/AI 数量）
        if (FactionManager.getPlayerFaction(player) == Faction.NONE) {
            OnboardingManager.ensureHandbook(player);
            player.sendSystemMessage(Component.translatable("msb.handbook.granted"));
        }
        // 下发本人钱包（HUD 财产显示；服务端权威，玩家私有数据单发本人）
        PlayerWallet wallet = player.getData(WalletAttachments.WALLET);
        PacketDistributor.sendToPlayer(player, new WalletPayload(wallet.earned(), wallet.spent(), wallet.total()));
        // 下发本人储存格（商店窗口显示；个人数据单发本人）
        PacketDistributor.sendToPlayer(player, ShopStoragePayload.from(ShopManager.storage(player)));
        WeightService.apply(player);
        // 恢复力竭惩罚（附体随重连保留；属性修饰为 transient，需重挂）
        StaminaManager.applyPenalty(player, StaminaManager.get(player).exhausted());
    }

    /** 死亡重生：重生会克隆新实体，继承原阵营附体（防重生后掉队）+ 恢复「不掉落」保留的装备 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()
                || !(event.getOriginal() instanceof ServerPlayer oldPlayer)
                || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }
        FactionManager.copyOnRespawn(newPlayer, oldPlayer);
        restoreKeptInventory(newPlayer, oldPlayer.getUUID());
    }

    /** 玩家死亡重生：在本方阵营基地上方复活（未放置基地则维持原版出生点；跨维度一并传送） */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Faction faction = FactionManager.getPlayerFaction(player);
        if (faction == Faction.NONE) {
            // 未选阵营：重生后补发雇佣兵手册，保证手册不因死亡丢失
            OnboardingManager.ensureHandbook(player);
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
        // 重生同样重置属性，重新应用力竭惩罚（docs/02 §3.15）
        StaminaManager.applyPenalty(player, StaminaManager.get(player).exhausted());
    }

    // ===== 耐力系统（docs/02 §3.15：饱食度/饱和度魔改）=====

    /** 每 tick Pre：推进耐力模拟并把原版 FoodData 压平（回血/饿死/被动三条分支全部失效） */
    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StaminaManager.preTick(player);
        }
    }

    /** 每 tick Post：把「耐力百分比 × 20」写回 foodLevel，供客户端 HUD 与冲刺门槛读取 */
    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StaminaManager.postTick(player);
            // 耐力百分比另以浮点单独下发（原版食物包只有 1/20 量化、HUD 会阶梯跳变）；2 tick 降频
            if (!player.isCreative() && !player.isSpectator() && player.tickCount % 2 == 0) {
                PacketDistributor.sendToPlayer(player, new StaminaPayload(StaminaManager.percent(player)));
            }
        }
    }

    /** 跳跃消耗（含非冲刺跳；原版跳跃事件不可取消，仅作消耗通知） */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StaminaManager.exhaust(player, Config.STAMINA_JUMP_COST.get());
        }
    }

    /** 近战命中消耗 */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity) {
            StaminaManager.exhaust(player, Config.STAMINA_MELEE_COST.get());
        }
    }

    /** 投掷消耗：右键使用白名单投掷物（默认原版雪球/鸡蛋/末影珍珠/药水/经验瓶） */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (Config.STAMINA_THROW_ITEMS.get().contains(id.toString())) {
            StaminaManager.exhaust(player, Config.STAMINA_THROW_COST.get());
        }
    }

    /** 吃完食物：登记「N 秒内每秒 +（营养 / N）」的逐渐回复队列（docs/02 §3.15 食物回复） */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food != null) {
            StaminaManager.addFoodRegen(player, food.nutrition());
        }
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
     * 击杀结算（docs/02 击杀提示）：赏金 = 基础值 + 目标本条命击杀数 × 每杀加成；
     * 击杀阵营玩家时，其装备（价值 &gt; 0）另按卖价折算成赏金。
     * 真人击杀 → 钱包 + 经验；AI 击杀 → 阵营基金（AI 无个人钱包，为 M3 阵营经济开路）。
     * 死者（玩家）本命击杀数与本命收入清零；阵营玩家死亡不掉落（见 {@link #onLivingDrops}）。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Entity source = event.getSource().getEntity();

        // ===== 死者结算 =====
        // 目标本条命击杀数（先取再清零，作为击杀者的赏金/经验档位）
        int victimKills = 0;
        // 被杀阵营玩家的装备折算赏金（被敌方击杀时装备消耗，价值转为赏金）
        int gearBounty = 0;
        if (victim instanceof ServerPlayer dead) {
            victimKills = dead.getData(KillStreakAttachments.STREAK);
            dead.setData(KillStreakAttachments.STREAK, 0);
            PlayerWallet deadWallet = dead.getData(WalletAttachments.WALLET);
            dead.setData(WalletAttachments.WALLET, deadWallet.resetLife());
            if (FactionManager.getPlayerFaction(dead) != Faction.NONE) {
                if (isKilledByEnemy(dead, source)) {
                    // 被敌方击杀：装备消耗（不掉落），价值折算为赏金给击杀者
                    gearBounty = consumeInventory(dead);
                } else {
                    // 其他死因（怪物/环境/友军）：装备原样保留，重生时恢复
                    KEEP_ON_DEATH.put(dead.getUUID(), snapshotInventory(dead));
                }
            }
        } else if (victim instanceof AiCombatantEntity aiVictim) {
            victimKills = aiVictim.getKillCount();
        }

        // ===== 击杀者结算 =====
        if (source instanceof ServerPlayer killer) {
            settlePlayerKill(killer, victim, victimKills, gearBounty);
        } else if (source instanceof AiCombatantEntity aiKiller) {
            settleAiKill(aiKiller, victim, victimKills, gearBounty);
        }
    }

    /** 真人击杀结算：钱包入账/罚款 + 经验 + 本命连杀 + 击杀提示（docs/02 击杀提示） */
    private static void settlePlayerKill(ServerPlayer killer, LivingEntity victim, int victimKills, int gearBounty) {
        int base = killBaseMoney(victim);
        int baseXp = Config.KILL_XP_REWARD.get();
        int perKill = killPerKill(victim);
        int perKillXp = Config.KILL_XP_PER_KILL.get();

        int money;
        int xp;
        String targetKey;
        boolean isPlayer;
        // 仅同阵营友伤时置位：客户端只显示一行「友伤 <名称> -xx$」，不显示下两行
        boolean friendlyFire = false;
        if (victim instanceof ServerPlayer target) {
            Faction killerFaction = FactionManager.getPlayerFaction(killer);
            Faction targetFaction = FactionManager.getPlayerFaction(target);
            if (killerFaction != Faction.NONE && killerFaction == targetFaction) {
                // 友军击杀（docs/02 友伤规则：攻击者追加罚款）
                money = -Config.KILL_FRIENDLY_PENALTY.get();
                xp = 0;
            } else {
                // 击杀敌方玩家：基础赏金 + 目标连杀加成 + 目标装备折算
                money = base + perKill * victimKills + gearBounty;
                xp = baseXp + perKillXp * victimKills;
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
                // 击杀敌方 AI：基础赏金 + 目标连杀加成；名称传实体翻译键（客户端本地化）
                money = base + perKill * victimKills;
                xp = baseXp + perKillXp * victimKills;
                targetKey = entityKey(victim);
                isPlayer = false;
            }
        } else {
            // 击杀怪物：仅基础赏金 + 基础经验；名称传实体翻译键（entity.<ns>.<path>，客户端本地化）
            money = base;
            xp = baseXp;
            targetKey = entityKey(victim);
            isPlayer = false;
        }
        // 击杀收益倍率：开局选择的资金档位越高，击杀赏金越低（仅缩放正奖励，友军罚款负数不缩放）
        if (money > 0) {
            money = (int) Math.round(money * killer.getData(MercenaryProfileAttachments.PROFILE).killMultiplier());
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

    /** AI 击杀结算：赏金（基础 + 目标连杀加成 + 装备折算）计入自身阵营基金，并记录本条命击杀数 */
    private static void settleAiKill(AiCombatantEntity killer, LivingEntity victim, int victimKills, int gearBounty) {
        Faction killerFaction = killer.getFaction();
        // 未分配阵营 / 同阵营友军击杀：不结算（AI 无钱包，不涉及友伤罚款）
        if (killerFaction == Faction.NONE || victimFactionOf(victim) == killerFaction) {
            return;
        }
        long bounty = (long) killBaseMoney(victim)
                + (long) killPerKill(victim) * victimKills
                + gearBounty;
        killer.registerKill();
        FactionFundData.get(killer.getServer()).add(killerFaction, bounty);
    }

    /** 击杀基础赏金（按受害者类型分档：敌方玩家 100 / 怪物 100 / 敌方阵营 AI 200） */
    private static int killBaseMoney(LivingEntity victim) {
        if (victim instanceof ServerPlayer) {
            return Config.KILL_MONEY_REWARD.get();
        }
        if (victim instanceof AiCombatantEntity) {
            return Config.KILL_MONEY_REWARD_AI.get();
        }
        return Config.KILL_MONEY_REWARD_MOB.get();
    }

    /** 目标本条命每击杀加成（按受害者类型分档：玩家 +100 / 怪物 +50 / 敌方阵营 AI +100） */
    private static int killPerKill(LivingEntity victim) {
        if (victim instanceof ServerPlayer) {
            return Config.KILL_BOUNTY_PER_KILL.get();
        }
        if (victim instanceof AiCombatantEntity) {
            return Config.KILL_BOUNTY_PER_KILL_AI.get();
        }
        return Config.KILL_BOUNTY_PER_KILL_MOB.get();
    }

    // ===== 阵营玩家死亡不掉落（docs/02 击杀结算）=====

    /** 死亡瞬间快照：41 格装备（物品栏 36 + 护甲 4 + 副手 1）+ Curios 饰品槽 */
    private record DeathSnapshot(List<ItemStack> inventory, List<ItemStack> curios) {
    }

    /** 「不掉落」暂存：玩家 UUID → 死亡瞬间装备快照（重生恢复；被敌方击杀消耗则不登记） */
    private static final Map<UUID, DeathSnapshot> KEEP_ON_DEATH = new HashMap<>();

    /**
     * 阵营玩家死亡一律不产生地面掉落：装备要么被折算成赏金（敌方击杀），要么在重生时原样返还。
     * 仅拦玩家自身掉落，AI 战利品不受影响（仍由 /MSBS AIpmc drop 控制）。
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && FactionManager.getPlayerFaction(player) != Faction.NONE) {
            event.setCanceled(true);
        }
    }

    /** 击杀者是否为敌方战斗单位（敌方玩家 / 敌方 AI）：决定被杀玩家装备是否折算成赏金 */
    private static boolean isKilledByEnemy(ServerPlayer victim, Entity source) {
        Faction victimFaction = FactionManager.getPlayerFaction(victim);
        if (victimFaction == Faction.NONE) {
            return false;
        }
        Faction killerFaction;
        if (source instanceof ServerPlayer killer) {
            killerFaction = FactionManager.getPlayerFaction(killer);
        } else if (source instanceof AiCombatantEntity ai) {
            killerFaction = ai.getFaction();
        } else {
            return false;   // 怪物/环境：不算敌方击杀，装备保留
        }
        return killerFaction != Faction.NONE && killerFaction != victimFaction;
    }

    /** 死亡瞬间快照 41 格装备（物品栏 36 + 护甲 4 + 副手 1）+ Curios 饰品，供重生恢复 */
    private static DeathSnapshot snapshotInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        List<ItemStack> slots = new ArrayList<>(inv.getContainerSize());
        for (int i = 0; i < inv.getContainerSize(); i++) {
            slots.add(inv.getItem(i).copy());
        }
        int curioCount = ShopManager.curioSlots(player).size();
        List<ItemStack> curios = new ArrayList<>(curioCount);
        for (int i = 0; i < curioCount; i++) {
            curios.add(ShopManager.curioStack(player, i).copy());
        }
        return new DeathSnapshot(slots, curios);
    }

    /** 消耗全部装备（含 Curios 饰品）并返回卖价总值（价值为 0 的物品不折算，仅清空） */
    private static int consumeInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            total += ShopManager.itemValue(stack) * stack.getCount();
            inv.setItem(i, ItemStack.EMPTY);
        }
        int curioCount = ShopManager.curioSlots(player).size();
        for (int i = 0; i < curioCount; i++) {
            ItemStack stack = ShopManager.curioStack(player, i);
            if (stack.isEmpty()) {
                continue;
            }
            total += ShopManager.itemValue(stack) * stack.getCount();
            ShopManager.setCurioStack(player, i, ItemStack.EMPTY);
        }
        return total;
    }

    /** 重生时恢复「不掉落」保留的装备（含 Curios 饰品）；被敌方击杀已消耗的不在暂存表内 */
    private static void restoreKeptInventory(ServerPlayer player, UUID ownerId) {
        DeathSnapshot kept = KEEP_ON_DEATH.remove(ownerId);
        if (kept == null) {
            return;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < kept.inventory().size() && i < inv.getContainerSize(); i++) {
            inv.setItem(i, kept.inventory().get(i));
        }
        for (int i = 0; i < kept.curios().size(); i++) {
            ShopManager.setCurioStack(player, i, kept.curios().get(i));
        }
    }

    /** 实体显示用语言键（entity.&lt;namespace&gt;.&lt;path&gt;，客户端本地化） */
    private static String entityKey(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return "entity." + id.getNamespace() + "." + id.getPath();
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
        // 冻结子弹回收：每 1s 清理一次（弱加载区块内的 SBW 子弹永不消失并堆积，见 reapFrozenBullets 说明）
        if (server.getTickCount() % PROJECTILE_REAP_INTERVAL_TICKS == 0) {
            reapFrozenBullets(server);
        }
        // 无人机视野强加载：每 tick 刷新（票据有存活时间，需持续续期，与 SBW 载具自身做法一致）
        keepDroneChunksLoaded(server);
    }

    /**
     * 无人机视野强加载：以「已被玩家操控」的 SBW 无人机为中心，对半径 {@link Config#DRONE_CHUNK_RADIUS}
     * 区块内的每个区块持续投递 {@link TicketType#POST_TELEPORT}（level 3 = 实体 tick，与 SBW 载具自身的做法一致），
     * 使无人机飞远后其镜头范围内的区块预先加载/保持加载，避免视角空白与生成滞后。
     * <p>
     * 只作用于被操控的无人机（未被操控的无人机无视角需求，跳过以省开销）；票据每 tick 续期
     * （票据有存活时间，需持续续期），半径 0 时整体关闭。
     */
    private static void keepDroneChunksLoaded(MinecraftServer server) {
        int radius = Config.DRONE_CHUNK_RADIUS.get();
        if (radius <= 0) {
            return;
        }
        // 先收集再投递票据：投递票据可能触发区块加载，避免遍历实体集合时被并发修改
        List<Entity> drones = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (isControlledDrone(entity)) {
                    drones.add(entity);
                }
            }
        }
        for (Entity drone : drones) {
            if (!(drone.level() instanceof ServerLevel level)) {
                continue;
            }
            ChunkPos center = drone.chunkPosition();
            int value = drone.getId();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT,
                            new ChunkPos(center.x + dx, center.z + dz), 3, value);
                }
            }
        }
    }

    /** SBW 无人机实体注册名（按注册名识别，不直接引用 DroneEntity 类） */
    private static final ResourceLocation SBW_DRONE_ID = ResourceLocation.fromNamespaceAndPath("superbwarfare", "drone");

    /**
     * {@code DroneEntity#getController()} 反射句柄（SBW 公开 API）。编译期无法直接引用该类：其继承链包含
     * GeckoLib 的 {@code GeoEntity}，而 GeckoLib 不在本 mod 的编译类路径上（运行时由 SBW 的 JarJar 提供）。
     */
    private static Method droneControllerMethod;
    private static boolean droneControllerResolved;

    /** 是否为「已被玩家操控」的 SBW 无人机（未操控的无人机不产生加载开销） */
    private static boolean isControlledDrone(Entity entity) {
        if (!SBW_DRONE_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            return false;
        }
        if (!droneControllerResolved) {
            droneControllerResolved = true;
            try {
                droneControllerMethod = entity.getClass().getMethod("getController");
            } catch (NoSuchMethodException e) {
                MercenarySandboxShooter.LOGGER.warn("SBW DroneEntity#getController 不可用，无人机视野强加载已禁用", e);
            }
        }
        if (droneControllerMethod == null) {
            return false;
        }
        try {
            return droneControllerMethod.invoke(entity) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** 冻结子弹回收周期（tick）：1s 一次，足够及时且成本可忽略 */
    private static final int PROJECTILE_REAP_INTERVAL_TICKS = 20;

    /**
     * 回收「冻结」的 SBW 子弹（SBW 原生行为兜底）。
     * <p>
     * 原版实体只在 {@code simulation-distance} 内的 chunk 里 tick；{@code view-distance} 加载但不 tick 的
     * 「弱加载区块」中实体被冻结。SBW 原生只为 4 类投射物（制导导弹/中型火箭/炮弹/迫击炮弹）做了强制区块加载，
     * 子弹（{@link ProjectileEntity}）不在此列 —— 飞出刷新范围边界即冻结，其生命周期倒计时（唯一销毁条件）随之停摆，
     * 永不消失并无限堆积；玩家移动使区块重新进入 tick 范围时集中爆发，造成卡顿尖峰（MC-197646 / MC-301664）。
     * <p>
     * 每 {@link #PROJECTILE_REAP_INTERVAL_TICKS} tick 扫描各维度，凡所在位置不再实体 tick（或高于世界高度）的
     * SBW 子弹直接丢弃。只作用于子弹，不影响 SBW 自带强制加载的火箭/炮弹。
     */
    private static void reapFrozenBullets(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> frozen = new ArrayList<>();
            for (Entity entity : level.getEntities().getAll()) {
                if (!(entity instanceof ProjectileEntity)) {
                    continue;   // 只回收 SBW 子弹；火箭/炮弹等由 SBW 自行强制加载，不干预
                }
                BlockPos pos = entity.blockPosition();
                if (!level.isPositionEntityTicking(pos) || pos.getY() >= level.getMaxBuildHeight()) {
                    frozen.add(entity);
                }
            }
            for (Entity entity : frozen) {
                entity.discard();
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
            MatchManager mm = MatchManager.get(level.getServer());
            // 放置约束：基地方块不可放入控制区圆内（防「圈内无敌区」，docs/02 §3.13）
            if (mm.getZone() != null && mm.getZone().contains(event.getPos())) {
                event.setCanceled(true);
                if (event.getEntity() instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("msb.base.in_zone"), true);
                }
                return;
            }
            mm.registerBase(f, event.getPos());
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
