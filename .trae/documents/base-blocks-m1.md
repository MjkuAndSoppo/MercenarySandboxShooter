# 3 基地方块（阵营基地方块安全区）加入 M1

## Context（背景）

M1 目前阵营/计分/控制区/HUD/战术地图/AI 已完成，但**基地方块（安全区）仍是纯文档设计、代码零实现**（docs/02 §5.1、PRD §5.1）。用户要求 M1 补齐**三阵营基地方块**：真实方块 + 数据记录、安全区中心**跟随基地方块**位置（方块放哪安全区在哪）、范围内**同阵营玩家与载具（SBW 提供）无敌**、载具缓慢恢复耐久、驶出恢复可伤。

现状锚点：
- 方块注册：`MercenarySandboxShooter.java` 仅 `TEST_BLOCK` 占位，datagen 骨架已跑通
- 伤害事件：[MsbServerEvents.java#L61-L65](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/MsbServerEvents.java#L61-L65) 已有 `LivingDamageEvent.Pre` 仅记录交战 tick
- 平面距离判定：[ControlZone.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/match/ControlZone.java#L46-L50) `containsXZ` 可复用
- 阵营权威：`FactionManager.getPlayerFaction(player)` + Scoreboard Team
- MatchState 载荷：`MatchStatePayload` / `MatchManager` 每 2s 广播（坐标随 MatchState 同步）

## 实现方案

### 1. 方块类 `block/BaseBlock.java` + 注册

普通 `Block` 子类（本阶段无需实体 NBT；三个注册实例共享同一类）：
```java
public class BaseBlock extends Block {
    public BaseBlock(Properties props) { super(props); }
    // 本阶段无特殊渲染/交互，仅作为可见标记 + 服务端坐标来源
}
```
主类注册三个实例（阵营归属由注册名区分，不写入方块本身）：
`base_block_lonestar` / `base_block_valkyra` / `base_block_manticore`（Block + BlockItem + CreativeTab 触点，沿用现有 `TEST_BLOCK` 的注册写法）。

### 2. 基地方块管理器 `match/MatchManager.java` 扩展

- `Map<Faction, BlockPos> basePositions`；`Map<Faction, ControlZone> baseZones`（半径 `BASE_RADIUS`，复用 `ControlZone`）
- `init()`：以控制区圆心为中心，三阵营各一个，**等夹角 120° 均布、距圆心 ≈ 控制区半径 × 2.5**（`BASE_DISTANCE_MULTIPLIER`）；用 `overworld.getHeight(MOTION_BLOCKING, x, z)` 定地表 Y 后**放置真实方块** `level.setBlock`；安全区中心 = 方块坐标（**跟随方块**，方块移动/被破坏则安全区随之变化——M1 不做破坏保护）
- tick（并入现有 `MatchManager.tick`）：每 4 tick（与 AI 惰性同频，`BASE_REGEN_INTERVAL`）对**已加载**的 SBW 载具做安全区恢复：判定在区内且属本方 → `heal`（见 §4 载具识别与边界）

### 3. 安全区无敌 `MsbServerEvents.onLivingDamage` 扩展

在现有 `LivingDamageEvent.Pre` 中追加（`entity` 为受击方）：
```java
// 玩家：阵营取 FactionManager；载具：取骑乘玩家阵营
Faction f = factionOf(entity);                       // 玩家→玩家阵营；载具→firstPassenger 玩家阵营
if (f != NONE && inBaseZone(f, entity.blockPosition())) {
    event.setCanceled(true);                          // 本方安全区内免疫全部伤害（敌方攻击同样被取消）
    return;
}
```
- `factionOf`：`entity instanceof ServerPlayer` → `getPlayerFaction`；SBW 载具 → `entity.getFirstPassenger()` 为玩家时取其阵营，无玩家乘客视为非本方（不无敌）
- `inBaseZone`：该阵营 `baseZones.get(f).containsXZ(pos.getX(), pos.getZ())`
- 敌方行为不额外限制：仅「本方在区内无敌」（对方造成伤害被 cancel，等同 PRD「敌方无法造成伤害」）

### 4. 载具识别与恢复（SBW 提供，不 import 具体类）

- **识别**：`BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("superbwarfare")` → 视为 SBW 载具（纯注册名判定，不依赖 SBW 类编译、遵守「仅引用 SBW 公开注册名」红线）
- **无敌**：走 §3 `LivingDamageEvent.Pre` 通用 `setCanceled`（事件驱动，无需扫实体）
- **恢复**：`MatchManager.tick` 每 4 tick 遍历 `overworld().getEntities` 中上述载具（已加载范围，M1 规模小开销可接受；与「只比坐标不扫实体」的控制区方案不同，恢复必须接触实体）、区内且属本方 → `LivingEntity.heal`（改容器同在）——若实测 SBW 载具**非** `LivingEntity`（无 heal 接口），则仅保留无敌、恢复延后到载具接入里程碑，代码留 TODO

### 5. MatchState 载荷同步 + 地图标记（小项）

- `MatchStatePayload` 增加 `basePositions`（三个 `int[]{x,y,z}` 压缩或键值组），`MatchManager` 广播时填充，客户端 `ClientMatchState` 解析
- 战术地图 [TacticalMapScreen.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/client/TacticalMapScreen.java) 在控制区圆外叠加三座基地标记（阵营色小方点 + 字母，复用现 IFF 配色常量），敌情规则不变（基地为静态点位，可全量下发——不与「仅交战」冲突）

### 6. Config 新增

- `BASE_RADIUS`（默认 16，范围 5~100）
- `BASE_DISTANCE_MULTIPLIER`（默认 2.5，范围 1~6）
- `BASE_REGEN_INTERVAL_SECONDS`（默认 2s；载具回血每次 heal 1 点，封顶 80/秒档位可调）

### 7. datagen

- `blockstates/models/loot tables` + lang（`block.msb.base_block_*`，三语言键）——沿用现有 provider，`runData` 后再 `build`

## 需要修改/新建的文件

- 新建 `src/main/java/com/mercenarysandbox/msb/block/BaseBlock.java`
- 修改 `MercenarySandboxShooter.java`（BLOCKS 注册三分量 + BlockItem + CreativeTab）
- 修改 `match/MatchManager.java`（basePositions/baseZones/init 放置方块/tick 恢复/载荷填充）
- 修改 `network/MatchStatePayload.java` + 客户端 `ClientMatchState`（basePositions 同步）
- 修改 `MsbServerEvents.java`（onLivingDamage 安全区 cancel）
- 修改 `client/TacticalMapScreen.java`（基地标记渲染）
- 修改 `Config.java`（BASE_RADIUS 等三项）
- 修改 datagen provider（方块模型/掉落/语言）
- 文档：docs/02 §5.1 标注已实现、docs/03 §3.2 M1 功能清单、project_memory

## 验证

1. `runData` 生成新方块资产 → `gradlew build`（IPv4 前缀）
2. 沙箱外 `runClient` 实测：
   - 进世界后三座基地方块自动生成，绕控制区均布可见
   - 本方站进自己基地安全区：被敌人打不掉血（含近战/枪械/环境？至少敌对玩家）
   - 敌方进入本方基地无法造成伤害；本方离开 16 格外恢复可伤
   - 战术地图可见三座基地标记
3. 载具：dev 环境 SBW 载具若已可用，验证区内无敌+缓慢回血、驶出可伤；不可用时确认无敌逻辑不报错（该部分依赖 SBW 运行时）

## 风险与注意

- SBW 载具是否 `LivingEntity` 未知（无法编译期确认）→ 恢复逻辑收敛在 `heal` + TODO 边界
- 恢复需接触实体（非纯坐标），与「免扫实体」约束局部偏离——仅作用于已加载载具，规模可控，文档注明
- 「跟随基地方块」= 安全区中心取方块实时坐标而非 instala 快照；M1 不做方块破坏保护（破坏后安全区随方块移除，易造成基地易毁——若需保护后续加 `BlockBehaviour` 抗破坏）
- BlockItem/CreativeTab 触点沿用 `TEST_BLOCK` 模式，避免重复造轮子