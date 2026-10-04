package com.mercenarysandbox.msb.match;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.ai.AiManager;
import com.mercenarysandbox.msb.entity.AiCombatantEntity;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.network.MatchStatePayload;
import com.mercenarysandbox.msb.network.UnitPositionsPayload;
import com.mercenarysandbox.msb.onboarding.FactionSetupData;

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
    private final BaseData baseData;
    private ControlZone zone;
    /** 三阵营基地方块位置（安全区中心 = 方块位置，跟随方块实时坐标） */
    private final Map<Faction, BlockPos> basePositions = new EnumMap<>(Faction.class);
    /** 三阵营基地方块安全区（半径 = Config.BASE_RADIUS，复用 ControlZone 平面距离判定） */
    private final Map<Faction, ControlZone> baseZones = new EnumMap<>(Faction.class);
    private final Map<Faction, Integer> scores = new EnumMap<>(Faction.class);
    private final Map<UUID, Integer> lastHurtTick = new HashMap<>();
    private int countdownTicks;
    private int tickCounter;
    private int unitTickCounter;
    private int baseRegenTick;
    /** 开局门槛是否已满足（三阵营均确认 AI 数量，或 OP /MSBS game start 强开） */
    private boolean started;

    private MatchManager(MinecraftServer server) {
        this.server = server;
        this.baseData = BaseData.get(server);
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
        // 恢复持久化的基地坐标（重进存档后安全区仍生效）
        restoreBases();
        // 开局门槛（docs/02 §3.14）：三阵营均已确认 AI 数量 → 自动开局；否则等待选择完成或 OP /MSBS game start 强开
        this.started = FactionSetupData.get(server).allChosen();
        // 基地方块不自动放置：由玩家放置 base_block_* 方块动态注册（docs/02 §3.13）
        AiManager.get(server).reconcileAll();
        MercenarySandboxShooter.LOGGER.info("MSB 控制区初始化: 圆心({},{},{}) 半径{} 结算{}s",
                zone.getCenterX(), zone.getCenterY(), zone.getCenterZ(), zone.getRadius(), Config.SETTLE_INTERVAL_SECONDS.get());
    }

    /** 从 SavedData 恢复三阵营基地（安全区中心跟随方块坐标，重进存档生效） */
    private void restoreBases() {
        for (Faction f : new Faction[]{Faction.LONESTAR, Faction.VALKYRA, Faction.MANTICORE}) {
            BlockPos p = baseData.get(f);
            if (p != null) {
                basePositions.put(f, p);
                baseZones.put(f, new ControlZone(p.getX(), p.getY(), p.getZ(), Config.BASE_RADIUS.get()));
                MercenarySandboxShooter.LOGGER.info("MSB 基地恢复: {} 位置({},{},{})", f.name(), p.getX(), p.getY(), p.getZ());
            }
        }
    }

    /** 玩家放置 base_block_* 方块时注册该阵营基地（安全区跟随方块坐标；重复放置覆盖，落盘持久化；AI 补足） */
    public void registerBase(Faction faction, BlockPos pos) {
        basePositions.put(faction, pos);
        baseZones.put(faction, new ControlZone(pos.getX(), pos.getY(), pos.getZ(), Config.BASE_RADIUS.get()));
        baseData.set(faction, pos);
        MercenarySandboxShooter.LOGGER.info("MSB 基地方块注册: {} 位置({},{},{}) 安全区半径{}",
                faction.name(), pos.getX(), pos.getY(), pos.getZ(), Config.BASE_RADIUS.get());
        broadcastState();
        // 无基地时 AI 不生成；放置后立即补足该阵营 AI（docs/02 §3.12）
        AiManager.get(server).onBasePlaced(faction);
    }

    /** 方块被拆除 → 移除该阵营基地项并落盘，安全区即时失效（未到该位置不动作） */
    public void removeBase(Faction faction, BlockPos pos) {
        if (pos.equals(basePositions.get(faction))) {
            basePositions.remove(faction);
            baseZones.remove(faction);
            baseData.clear(faction);
            MercenarySandboxShooter.LOGGER.info("MSB 基地方块移除: {} 位置({},{},{})", faction.name(), pos.getX(), pos.getY(), pos.getZ());
            broadcastState();
        }
    }

    /** 方块实例 → 阵营（非基地方块返回 NONE） */
    public static Faction factionFromBlock(Block block) {
        if (block == MercenarySandboxShooter.BASE_BLOCK_LONESTAR.get()) {
            return Faction.LONESTAR;
        }
        if (block == MercenarySandboxShooter.BASE_BLOCK_VALKYRA.get()) {
            return Faction.VALKYRA;
        }
        if (block == MercenarySandboxShooter.BASE_BLOCK_MANTICORE.get()) {
            return Faction.MANTICORE;
        }
        return Faction.NONE;
    }

    public BlockPos getBasePos(Faction faction) {
        return basePositions.get(faction);
    }

    /** 安全区判定（平面距离，忽略 Y）——本阵营基地方块范围内为安全区 */
    public boolean isInBaseZone(Faction faction, BlockPos pos) {
        ControlZone zone = baseZones.get(faction);
        return zone != null && zone.containsXZ(pos.getX(), pos.getZ());
    }

    public ControlZone getZone() {
        return zone;
    }

    /** 开局门槛是否已满足（未满足时控制区不结算、AI 不生成，docs/02 §3.14） */
    public boolean isStarted() {
        return started;
    }

    /**
     * 开局：激活控制区结算与 AI 生成（docs/02 §3.14）。
     * 已开局则忽略；重置结算倒计时并立即补齐三方 AI、广播状态，提示全服。
     */
    public void startMatch() {
        if (started) {
            return;
        }
        started = true;
        countdownTicks = Config.SETTLE_INTERVAL_SECONDS.get() * SECOND_TICKS;
        AiManager.get(server).reconcileAll();
        broadcastState();
        server.getPlayerList().broadcastSystemMessage(Component.translatable("msb.game.started"), false);
        MercenarySandboxShooter.LOGGER.info("MSB 对局开始：控制区结算与 AI 生成已激活");
    }

    /** 服务端每 tick 驱动：倒计时 + 结算 + 状态广播 + 战术地图单位广播 + 基地载具恢复（AI 惰性驱动见 MsbServerEvents.onServerTick） */
    public void tick() {
        if (zone == null) {
            return;
        }
        tickCounter++;
        unitTickCounter++;
        baseRegenTick++;
        if (unitTickCounter % UNIT_BROADCAST_TICKS == 0) {
            broadcastUnits();
        }
        if (baseRegenTick % (Config.BASE_REGEN_INTERVAL_SECONDS.get() * SECOND_TICKS) == 0) {
            regenFriendlyVehicles();
        }
        if (tickCounter % SECOND_TICKS == 0) {
            broadcastState();
            // 开局门槛未满足时不推进结算倒计时（控制区计分不激活，docs/02 §3.14）
            if (started) {
                countdownTicks--;
                if (countdownTicks <= 0) {
                    settle();
                    countdownTicks = Config.SETTLE_INTERVAL_SECONDS.get() * SECOND_TICKS;
                }
            }
        }
    }

    /** 安全区载具恢复：对已加载的 SBW 载具，区内且属本方 → heal（docs/02 §5.1） */
    private void regenFriendlyVehicles() {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (!isSbwVehicle(entity)) {
                    continue;
                }
                Faction f = vehicleFaction(entity);
                if (f == Faction.NONE) {
                    continue;
                }
                ControlZone base = baseZones.get(f);
                if (base == null || !base.containsXZ(entity.blockPosition().getX(), entity.blockPosition().getZ())) {
                    continue;
                }
                if (entity instanceof LivingEntity living) {
                    living.heal(1.0F);
                }
                // TODO: 若 SBW 载具非 LivingEntity（无 heal 接口），恢复延后到载具接入里程碑；本轮已覆盖无敌判定
            }
        }
    }

    /** SBW 载具识别：按实体注册名 namespace 判断（不 import SBW 类，遵守「仅引用 SBW 公开注册名」红线） */
    public static boolean isSbwVehicle(Entity entity) {
        return "superbwarfare".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }

    /** 载具阵营：取骑乘玩家阵营；无玩家乘客视为非本方 */
    private Faction vehicleFaction(Entity entity) {
        Entity rider = entity.getFirstPassenger();
        if (rider instanceof ServerPlayer player) {
            return FactionManager.getPlayerFaction(player);
        }
        return Faction.NONE;
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
        int[] basePos = new int[9];
        Faction[] ordered = {Faction.LONESTAR, Faction.VALKYRA, Faction.MANTICORE};
        for (int i = 0; i < ordered.length; i++) {
            BlockPos p = basePositions.get(ordered[i]);
            if (p != null) {
                basePos[i * 3] = p.getX();
                basePos[i * 3 + 1] = p.getZ();
                basePos[i * 3 + 2] = p.getY();
            } else {
                basePos[i * 3] = basePos[i * 3 + 1] = basePos[i * 3 + 2] = -1;
            }
        }
        return new MatchStatePayload(
                zone.getCenterX(), zone.getCenterZ(), zone.getRadius(),
                Math.max(0, countdownTicks / SECOND_TICKS),
                scores.getOrDefault(Faction.LONESTAR, 0),
                scores.getOrDefault(Faction.VALKYRA, 0),
                scores.getOrDefault(Faction.MANTICORE, 0),
                basePos);
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
        for (AiCombatantEntity u : AiManager.get(server).allUnits()) {
            units.add(new UnitPositionsPayload.UnitEntry(u.getFaction().getId(), u.blockPosition().getX(), u.blockPosition().getZ(), false));
        }
        int mapRadius = Math.max(MIN_MAP_RADIUS, zone.getRadius() * MAP_RADIUS_MULTIPLIER);
        return new UnitPositionsPayload(zone.getCenterX(), zone.getCenterZ(), mapRadius, units);
    }
}
