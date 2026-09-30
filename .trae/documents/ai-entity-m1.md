# AI 实体化加入 M1

> **状态：✅ 已落地（2026-09-30）**。方案全部实现并经 `gradlew build` + 沙箱外 runClient 实测验证——AI 推进/不消失/复活/进圈四项通过（细节见 docs/02 §3.12 排障记录）。本文档保留为设计留档，实现差异：
> - `lazyTick` 签名增加 `tickCounter` 参数（诊断门控，避免 gameTime 相位错位）
> - 追加 `respawnQueue` 基地复活（±6 格，10s CD）、无基地不生成/复活、chunk 强制加载、`purgeStaleUnits` 清存档残留
> - 临时诊断探针仍在代码中，待用户确认后清理
> - **裂手修复（2026-09-30）**：`getTextureLocation` 原用 `DefaultPlayerSkin.getDefaultTexture()`（随本地玩家皮肤类型返回 slim/宽臂纹理，宽臂模型配 slim 时手臂 UV 错位「裂手」）→ 改为固定 `ResourceLocation.withDefaultNamespace("textures/entity/steve.png")`（等价原方案的 `PlayerRenderer.TEXTURE`）

## Context（背景）

M1 阵营与计分目前 AI 是「抽象模拟单位」（[AiUnit.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/ai/AiUnit.java)：纯服务端数据，无实体无渲染），实体化一直标注「延后」。用户要求把 **AI 实体化提前纳入 M1**：AI 变为真实 Minecraft 实体，可被渲染、可被攻击、以实体真实位置参与占区计分，真人顶替/退出补位管理实体生命周期。

用户已确认三项决策：
1. **外观**：原版 Steve 皮肤（PlayerModel + 原版 steve.png，零新增资产）
2. **范围**：仅实体化 + 朝圈推进 + 占区计分 + 顶替补位；攻击交战与 SBW 武器装配延后
3. **结构**：删除纯数据 `AiUnit`，AiManager 直接管理实体列表

## 实现方案

### 1. 新建实体类 `entity/AiCombatantEntity.java`

`extends PathfinderMob`：
- 字段：`Faction faction`（普通字段 + NBT save/load，实体无需跨存档持久化，避免 attachment 复杂度）
- `setFaction/getFaction`；`getScoreboardName()` 用 UUID（供计分板 Team 加入）
- 名字：spawn 时 `setCustomName`（"AI-L-1" 等，沿用现命名）
- `lazyTick(BlockPos zoneCenter, int radius)`：每 4 tick 一次；圈外时 `getNavigation().moveTo(圆心, 0.8)` 朝圈推进，圈内停止 —— 延续 docs/02 §3.12 惰性 tick 约束（低频决策）
- spawn 时由 AiManager 负责 `addPlayerToTeam`（计分板 Team，阵营染色 + SBW 敌我判定可用的 Entity.getTeam()），discard 前 `removePlayerFromTeam`

### 2. 主类注册实体 `MercenarySandboxShooter.java`

新增 `Entities` DeferredRegister：
```java
public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
public static final DeferredHolder<EntityType<?>, EntityType<AiCombatantEntity>> AI_COMBATANT =
        ENTITIES.register("ai_combatant", () ->
                EntityType.Builder.of(AiCombatantEntity::new, MobCategory.MISC)
                        .sized(0.6F, 1.8F).build("ai_combatant"));
```

### 3. 客户端渲染器 `client/AiCombatantRenderer.java`

- `extends MobRenderer<AiCombatantEntity, PlayerModel<AiCombatantEntity>>`
- 构造：`super(ctx, new PlayerModel<>(ctx.bakeLayer(PlayerRenderer.PLAYER_MODEL_LAYER_LOCATION)), 0.5F)`（原版层已注册，直接 bake）
- `getTexture` 返回 `PlayerRenderer.TEXTURE`（原版 steve.png）
- 注册：`EntityRenderersEvent.RegisterRenderers`（MsbClientEvents 或新建事件类）`registerEntityRenderer(AI_COMBATANT.get(), AiCombatantRenderer::new)`

### 4. 改造 `ai/AiManager.java`

- `Map<Faction, List<AiUnit>>` → `Map<Faction, List<AiCombatantEntity>>`
- `spawnUnit(faction)`：`server.overworld()` 生成实体 → `setPos`（圈周围随机散布，沿用现 spread 逻辑）→ `setFaction` → `setCustomName` → 加入 Team → `level.addFreshEntity`
- `onRealPlayerJoined`：移除列表末尾实体的 `discard()` + 保留「AI 被玩家顶替」播报
- `onRealPlayerLeft` / `reconcile`：补位/裁减；**每 tick 先清理 `removeIf(e -> e.isRemoved())`**（实体可能被玩家击杀，防悬空引用）
- `tick(zone)`：每 4 tick 调 `entity.lazyTick(center, radius)`
- `countInZone(zone)`：改用 `entity.blockPosition()` 判定（返回 Map 不变，MatchManager 调用点不改）
- `allUnits()`：返回 `List<AiCombatantEntity>`（战术地图广播从此遍历取 faction/pos）

### 5. `match/MatchManager.java` 微调

`buildUnitPositionsPayload()` 遍历 `AiManager.allUnits()` 处改用实体的 `getFaction()/blockPosition()`。`countInZone` 调用不变。

### 6. 删除

- 删除 `ai/AiUnit.java`
- datagen（MsbLanguageProvider）增加 `entity.msb.ai_combatant` 语言键

## 需要修改的文件清单

- 新建：`src/main/java/com/mercenarysandbox/msb/entity/AiCombatantEntity.java`
- 新建：`src/main/java/com/mercenarysandbox/msb/client/AiCombatantRenderer.java`
- 修改：`MercenarySandboxShooter.java`（Entities 注册）
- 修改：`ai/AiManager.java`（实体化管理）
- 修改：`match/MatchManager.java`（广播取实体数据）
- 修改：`MsbClientEvents.java` 或新建渲染注册事件（RegisterRenderers）
- 修改：`datagen/MsbLanguageProvider.java`（实体名）
- 删除：`ai/AiUnit.java`
- 文档：docs/02 §3.12、docs/03 §3.2 M1、project_memory

## 验证

1. `gradlew build`（IPv4 前缀 `$env:JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true';`）
2. 沙箱外 `runClient` 实测：AI 实体以 Steve 形象生成并渲染、缓慢朝控制区推进、进圈参与占区计分、真人加入顶替时对应实体消失、击杀 AI 后无残留引用（tick 清理生效）、战术地图仍正常显示 AI 单位
3. 服务端逻辑（AiManager）无客户端依赖，`runServer` 可验证纯服务端行为

## 风险与注意

- 实体无攻击 goal（M1 不交战），仅移动；玩家可攻击击杀 → `isRemoved` 清理兜底
- Team 加入用 `getScoreboardName()`（UUID 字符串），`addPlayerToTeam` 后实体名随 Team 染色；discard 前记得 `removePlayerFromTeam` 防计分板残留
- 不新增自定义网络协议：战术地图广播仍走现 UnitPositions 载荷，客户端零改动
