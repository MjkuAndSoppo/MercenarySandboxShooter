# 开局流程 · 雇佣兵手册 · 阵营增益 · Tab 信息栏

> 目标：新玩家进世界（无阵营）→ 自动获得「雇佣兵手册」→ 右键打开手册 UI 选阵营/资金/AI 数量 → 传送到所选阵营基地 → 开始游戏。
> 本轮同时落地阵营增益数据结构（仅展示）、去除 AI 顶替与上限、预留 Tab 信息栏页面。

---

## 1. 摘要（Summary）

- 新增物品 `msb:mercenary_handbook`（雇佣兵手册），无阵营玩家登录时自动发放，右键打开自绘 UI。
- 新增 `HandbookScreen`：三张阵营卡（展示四项增益）＋资金三档（含击杀收益倍率）＋AI 数量 5~15 步进＋基地状态＋确认按钮。
- 新增 C2S `FactionSelectPayload`：服务端校验后设置阵营、发放初始资金、落库击杀倍率、设置本阵营初始 AI 目标并传送至基地（无基地则提示「无基地配置」并跳过传送）。
- 阵营增益四项（人力/火力/补给/上限）**仅做数据结构与展示，暂不接实际效果**。
- **彻底去除 AI 顶替**：真人加入不再顶替 AI；AI 数量由玩家开局选择决定（不再 = 目标数 − 真人数），无硬上限（后续招募接口预留）。
- 新增 Tab 键 `InfoScreen`（占位骨架，内容预留）。

### 已确认决策（来自用户答复）
1. 基地与传送：无基地时**显示「无基地配置」**，跳过传送（不自动放置基地）。
2. 阵营增益：**先只做展示与数据结构**。
3. AI 数量与经济：**两者独立**（资金档位只决定启动资金与击杀倍率；AI 数量只决定开局 AI 数）。
4. 手册领取：**进世界自动发放，右键打开**；手册**保留**，后续用于显示模组游玩教程。
5. **AI 机制彻底去除顶替**。
6. UI 以 HTML 原型先行审核（已通过 dynamic-ui 内联渲染提交，见对话内两张 mockup）。

---

## 2. 现状分析（Current State Analysis）

| 关注点 | 现状 | 位置 |
|---|---|---|
| 阵营枚举 | `Faction` 含 id/chatColor/teamName/displayKey/abbr，无增益字段 | [Faction.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/faction/Faction.java) |
| 加入分配 | `assignOnJoin` 无阵营时 `leastPopulated` **自动分配** | [FactionManager.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/faction/FactionManager.java#L46-L54) |
| 登录流程 | 分配阵营 → 发钱包/储存格/负重 → `AiManager.onRealPlayerJoined`（**顶替**） | [MsbServerEvents.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/MsbServerEvents.java#L62-L77) |
| AI 目标 | `desired = Config.AI_TARGET_PER_FACTION − 真人玩家数`；真人加入顶替、退出补位 | [AiManager.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/ai/AiManager.java#L92-L143) |
| AI 生成前置 | 无基地方块不生成（`getBasePos == null` 直接 return） | AiManager.reconcile L123 |
| 基地坐标（客户端） | `MatchStatePayload.basePos[9]`，-1 = 未放置 | `MatchManager.buildStatePayload` → [ClientMatchState.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/client/ClientMatchState.java#L53-L55) |
| 钱包 | `PlayerWallet(spent,total,earned)` record，`copyOnDeath`，`WalletAttachments.WALLET` | [PlayerWallet.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/economy/PlayerWallet.java) |
| SavedData 范式 | `BaseData`(msb_bases) / `FactionFundData`(msb_funds)，挂主世界 dataStorage | MatchManager.restoreBases L98 |
| 网络 | `MsbNetwork.PROTOCOL_VERSION="3"`，playToClient/playToServer | [MsbNetwork.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/network/MsbNetwork.java#L17-L33) |
| 按键 | `TACTICAL_MAP_KEY(M)` / `SHOP_KEY(B)`，`MsbClientEvents.onClientTick` 切换 Screen | [MsbKeyMappings.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/client/MsbKeyMappings.java)、[MsbClientEvents.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/client/MsbClientEvents.java) |
| 物品注册 | `ITEMS` DeferredRegister；创造标签 `MSB_TAB` 仅列 `TEST_ITEM` | [MercenarySandboxShooter.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/MercenarySandboxShooter.java#L57-L85) |
| 击杀结算 | `settlePlayerKill`/`settleAiKill` → `killBaseMoney`/`killPerKill` 分档 | MsbServerEvents L294/L367 |
| 语言 | datagen `MsbLanguageProvider`（en/zh），`src/generated/resources` 唯一来源 | [MsbLanguageProvider.java](file:///e:/project%20666/MercenarySandboxShooter/src/main/java/com/mercenarysandbox/msb/data/MsbLanguageProvider.java) |

---

## 3. 数据结构设计

### 3.1 阵营增益（展示用）
在 `Faction` 增加四个 `int` 字段与 getter（0~5）：
- LONESTAR 红 S.F：人力 5 / 火力 5 / 补给 3 / 上限 2
- VALKYRA 蓝 KCCO：人力 2 / 火力 4 / 补给 5 / 上限 4
- MANTICORE 绿 I.O.P：人力 4 / 火力 3 / 补给 3 / 上限 5
- NONE：全 0

### 3.2 资金档位 `FundingTier`（新枚举 `com.mercenarysandbox.msb.onboarding`）
```
TIER_LOW(0, 3000, 1.5D) / TIER_MID(1, 8000, 1.0D) / TIER_HIGH(2, 20000, 0.8D)
```
字段：`id / money / killMultiplier`；`byId(int)`；`CODEC`（按名，未知回退 TIER_MID）。

### 3.3 玩家雇佣兵档案 `MercenaryProfile`（新 DataAttachment）
```java
record MercenaryProfile(int tierId, int killMultiplierX100, int aiCount)
```
- 用整数存倍率（×100）避免浮点序列化误差；`killMultiplier() = killMultiplierX100 / 100.0`。
- `CODE`：`RecordCodecBuilder`，全字段 `optionalFieldOf` 兜底（tierId=1、倍率100、aiCount=0）——旧存档兼容。
- `MercenaryProfileAttachments.PROFILE`：`serialize(PROFILE_CODEC).copyOnDeath()`，在 `MercenarySandboxShooter` 构造器注册。
- 无档案时的击杀倍率默认 1.0（管理员/旧玩家）。

### 3.4 阵营开局设置 `FactionSetupData`（新 SavedData）
- key = `msb_faction_setup`，挂主世界 `dataStorage`（范式同 `BaseData`）。
- 内容：`Map<Faction,Integer> initialAiTarget`（未设置 = 不存在条目）。
- `getTarget(faction)`：有条目返回条目值；无条目返回 `Config.AI_TARGET_PER_FACTION.get()`（保留旧行为兜底）。
- `setTarget(faction, n)` + `setDirty()`：**首个选择的玩家写入生效；已有条目则忽略后来者**（阵营开局编制一旦确定即固定，避免多人重复选择互相覆盖）。

---

## 4. 开局流程与协议

### 4.1 登录（`MsbServerEvents.onPlayerLoggedIn`）
1. `FactionManager.ensureTeams` 保留。
2. `FactionManager.assignOnJoin` **改为不自动分配**：仅当已有阵营时恢复计分板队伍；无阵营保持 `NONE`。
3. 若 `getPlayerFaction == NONE`：调 `OnboardingManager.ensureHandbook(player)`（背包内无手册则给一本）＋发一条引导聊天（`msb.handbook.granted`）。
4. 移除 `AiManager.get(...).onRealPlayerJoined(...)` 调用（**去顶替**）。
5. 钱包/储存格/负重下发保持不变。

### 4.2 手册物品 `MercenaryHandbookItem`（新）
- 注册名 `mercenary_handbook`，`ITEMS.register("mercenary_handbook", MercenaryHandbookItem::new)`。
- `use()`：`if (!level.isClientSide && player instanceof ServerPlayer sp)` → `PacketDistributor.sendToPlayer(sp, new HandbookOpenPayload())`；返回 `InteractionResultHolder.sidedSuccess`。
- 模型：`assets/msb/models/item/mercenary_handbook.json`（`parent: item/generated`，`layer0: minecraft:item/book`）——P0 复用原版书贴图，零新增美术资产。
- 加入 `MSB_TAB` 的 `displayItems`。
- 手册**保留**（选阵营后不消耗），后续承载教程页。

### 4.3 打开手册（S2C `HandbookOpenPayload`，空载荷）
- 注册 playToClient；`handle`：`Minecraft.getInstance().setScreen(new HandbookScreen())`。
- 客户端只为打开界面，不携带数据（数据来源见 4.5）。

### 4.4 阵营选择提交（C2S `FactionSelectPayload`）
字段：`int factionId, int tierId, int aiCount`（`aiCount` 客户端已钳制 5~15）。
`handle`（服务端）：
1. 校验 `factionId` 属于 LONESTAR/VALKYRA/MANTICORE；非法则回执/忽略。
2. 若玩家已有非 NONE 阵营 → 忽略（防重复开局）。
3. `FactionManager.setPlayerFaction(player, faction)`（写附体 + 计分板队伍 + 发 SyncFactionPayload）。
4. 资金：`player.setData(WalletAttachments.WALLET, new PlayerWallet(0, tier.money(), 0))`，随后发 `WalletPayload`。
5. 档案：`player.setData(MercenaryProfileAttachments.PROFILE, new MercenaryProfile(tier.id(), (int)Math.round(tier.killMultiplier()*100), aiCount))`。
6. AI 目标：`FactionSetupData.get(server).setTarget(faction, aiCount)`；随后 `AiManager.get(server).reconcile(faction)` 补足（无基地时不生成，逻辑不变）。
7. 传送：`MatchManager.get(server).getBasePos(faction)` 非空 → `teleportTo(overworld, x+0.5, y+1, z+0.5, yRot, xRot)`（复用 `onPlayerRespawn` 的范式）；为空 → 发聊天 `msb.onboarding.no_base`。
8. 发成功聊天 `msb.onboarding.joined`（含阵营名/资金/AI 数）。

### 4.5 手册界面 `HandbookScreen`（client，GuiGraphics 自绘）
- `extends Screen`，无 `AbstractContainerMenu`，沿用 `ShopScreen`/`TacticalMapScreen` 自绘与 `MsbNetwork` 发送方式。
- 布局（与 mockup 一致）：
  - 标题「雇佣兵手册」+ 副标题。
  - 三张阵营卡：阵营色点 + 中文名 + 缩写 + 四项增益条（人力/火力/补给/上限，5 段 pip，读 `Faction` getter）。点击选中（金/品牌色描边）。
  - 资金三档：`3000/8000/20000` 与「击杀收益 ×1.5/×1.0/×0.8」（读 `FundingTier`）。点击选中，默认 `TIER_MID`。
  - AI 数量：`−`/`+` 步进 5~15，默认 10，配进度条。
  - 基地状态：读 `ClientMatchState.getMatchState().basePos[factionIndex]`；为 null 或 -1 → 显示「无基地配置」警示；否则显示基地坐标。
  - 确认按钮：发 `FactionSelectPayload`，随后 `setScreen(null)`。
- 已是非 NONE 阵营时打开手册：显示「教程（预留）」占位页（本页仅标题 + 占位文案）。

### 4.6 重生兜底
`MsbServerEvents.onPlayerRespawn`：若 `getPlayerFaction == NONE` → `OnboardingManager.ensureHandbook(player)`（无阵营玩家不享受不掉落，死亡后补发手册）。

---

## 5. 彻底去除 AI 顶替

| 改动 | 说明 |
|---|---|
| `AiManager.onRealPlayerJoined` | **删除**（连同 `msb.chat.ai_replaced` 播报）。 |
| `AiManager.onRealPlayerLeft` | **删除**；`MsbServerEvents.onPlayerLoggedOut` 中对应调用移除。 |
| `AiManager.reconcile(faction)` | `desired = FactionSetupData.get(server).getTarget(faction)`（**不再减真人玩家数**）。 |
| `FactionManager.assignOnJoin` | 不再调用 `leastPopulated` 自动分配。 |
| `FactionManager.leastPopulated` / `countRealPlayers` | 改后若无其他引用则删除（执行前 grep 确认；`countRealPlayers` 若无引用一并删）。 |
| `Config.AI_TARGET_PER_FACTION` | **保留**，仅作为未设置开局目标时的兜底值（注释同步更新）。 |
| 上限 | 目标值由玩家选择，不再受「真人数」挤压；`reconcile` 不再裁减到目标以下。 |

> 注：AI 仍受「必须已放置基地方块」前置约束（无基地不生成），本轮不改。

---

## 6. 击杀收益倍率

`MsbServerEvents` 击杀结算处（`settlePlayerKill` / `settleAiKill`）：
- 取击杀者 `MercenaryProfile.killMultiplier()`（缺省 1.0）。
- 对 `killBaseMoney(...) + killPerKill(...)` 的**金钱总额**乘倍率并 `Math.round`；**经验不变**。
- 倍率只在玩家结算路径生效；AI 击杀者仍按原规则入阵营基金（不受影响）。

---

## 7. Tab 信息栏（预留）

- `MsbKeyMappings` 新增 `INFO_KEY = new KeyMapping("key.msb.info", KEYSYM, GLFW_KEY_TAB, "key.categories.msb")`，并在 `onRegisterKeyMappings` 注册。
- `MsbClientEvents.onClientTick`：`INFO_KEY.consumeClick()` → 打开/关闭 `InfoScreen`（同 M/B 的 toggle 写法）。
- `InfoScreen extends Screen`（占位骨架）：
  - 顶部四个页签：阵营 / 战绩 / 队伍 / 教程；右侧「预留」标记。
  - 阵营页：展示本人阵营四项增益（读 `ClientMatchState.getOwnFactionId()` + `Faction` getter）；未选阵营显示提示。
  - 战绩/队伍/教程：占位文案（`msb.info.reserved`）。
- **已知取舍**：Tab 同时是原版玩家列表键。打开自绘 Screen 后 `mc.screen != null`，原版列表不会显示；关闭后释放 Tab 即恢复，可接受。

---

## 8. 网络改动

`MsbNetwork`：
- `PROTOCOL_VERSION` `"3"` → `"4"`。
- 新增：
  - `registrar.playToClient(HandbookOpenPayload.TYPE, ..., HandbookOpenPayload::handle)`
  - `registrar.playToServer(FactionSelectPayload.TYPE, ..., FactionSelectPayload::handle)`

---

## 9. 语言键（MsbLanguageProvider，en + zh 同步）

新增：
- `item.msb.mercenary_handbook`
- `msb.handbook.title` / `.subtitle`
- `msb.handbook.perk.manpower` / `.firepower` / `.supply` / `.cap`
- `msb.handbook.funds` / `.funds.money` / `.funds.killmul`
- `msb.handbook.ai_count` / `.ai_count_val`
- `msb.handbook.no_base` / `.base_at`
- `msb.handbook.confirm`
- `msb.handbook.granted`（登录引导）
- `msb.handbook.tutorial_reserved`
- `msb.onboarding.joined` / `msb.onboarding.no_base`
- `key.msb.info` / `msb.info.title` / `msb.info.reserved`
- `msb.info.tab.faction` / `.record` / `.team` / `.tutorial`
- `msb.info.record.kills` / `.deaths` / `.kd` / `.xp`

删除：`msb.chat.ai_replaced`（顶替播报已移除）。

---

## 10. 涉及文件

### 新增
| 文件 | 作用 |
|---|---|
| `com/mercenarysandbox/msb/item/MercenaryHandbookItem.java` | 手册物品，右键发 S2C 开界面 |
| `com/mercenarysandbox/msb/onboarding/FundingTier.java` | 资金三档枚举（钱/倍率） |
| `com/mercenarysandbox/msb/onboarding/MercenaryProfile.java` | 玩家档案 record + Codec |
| `com/mercenarysandbox/msb/onboarding/MercenaryProfileAttachments.java` | 档案附体（copyOnDeath） |
| `com/mercenarysandbox/msb/onboarding/OnboardingManager.java` | 发手册 + 应用选择（服务端） |
| `com/mercenarysandbox/msb/onboarding/FactionSetupData.java` | SavedData：各阵营初始 AI 目标 |
| `com/mercenarysandbox/msb/network/HandbookOpenPayload.java` | S2C 打开手册 |
| `com/mercenarysandbox/msb/network/FactionSelectPayload.java` | C2S 提交选择 |
| `com/mercenarysandbox/msb/client/HandbookScreen.java` | 手册自绘界面 |
| `com/mercenarysandbox/msb/client/InfoScreen.java` | Tab 信息栏占位界面 |
| `src/main/resources/assets/msb/models/item/mercenary_handbook.json` | 物品模型（复用原版书贴图） |

### 修改
| 文件 | 改动 |
|---|---|
| `MercenarySandboxShooter.java` | 注册手册物品；`MSB_TAB` 加入手册；注册 `MercenaryProfileAttachments.ATTACHMENT_TYPES`；`MatchManager.init` 侧初始化 `FactionSetupData`（或首次使用时 `computeIfAbsent`） |
| `Faction.java` | 四项增益字段 + getter |
| `FactionManager.java` | `assignOnJoin` 去自动分配；清理 `leastPopulated`（及无引用的 `countRealPlayers`） |
| `MsbServerEvents.java` | 登录发手册 + 去顶替调用；重生补发手册；`onPlayerLoggedOut` 去补位；击杀结算乘倍率 |
| `AiManager.java` | 删顶替/补位方法；`reconcile` 目标改读 `FactionSetupData` |
| `MsbNetwork.java` | 升版本 4 + 注册 2 个新载荷 |
| `MsbKeyMappings.java` | 新增 Tab 键 |
| `MsbClientEvents.java` | Tab 切换 `InfoScreen` |
| `Config.java` | `AI_TARGET_PER_FACTION` 注释更新（含义改为兜底默认值） |
| `MsbLanguageProvider.java` | §9 语言键增删 |

---

## 11. 假设与决策（Assumptions & Decisions）

1. **首个选择者决定阵营 AI 编制**：同阵营后续加入者的 AI 数量选择不覆盖已有目标（避免互相拉扯）。若需「取较大值/可累加」，后续单独调整。
2. **未设置开局目标时**沿用 `Config.AI_TARGET_PER_FACTION` 兜底，保证旧存档/管理场景行为不突变。
3. **AI 仍然只在基地方块已放置时生成**（本轮不改基地自动放置策略）。
4. 手册使用原版书贴图（`minecraft:item/book`），自定义美术后续替换。
5. 手册选完阵营**不消耗**，保留承载教程页。
6. Tab 覆盖原版玩家列表的显示时机（打开界面期间被抑制），按用户要求保留 Tab。
7. 资金档位只决定启动资金与击杀倍率；AI 数量与经济**独立**（已确认）。
8. 阵营增益四项**只存数据与展示**，不接效果。

---

## 12. 验证（Verification）

1. `gradlew runData` 生成语言资源，`gradlew build` 通过。
2. 新建世界进入：
   - 无阵营玩家登录后背包出现「雇佣兵手册」并收到引导聊天。
   - 右键手册打开 `HandbookScreen`；三张阵营卡增益数值与档位倍率显示正确；AI 步进 5~15 可调。
   - 未放置基地时显示「无基地配置」；选中后确认 → 不传送，收到「无基地配置」提示，阵营/资金/AI 目标已设置。
3. 放置某阵营基地方块后重复选择另一玩家：确认 → 传送至基地上方 1 格；该阵营 AI 生成数量 = 所选值（与真人数无关，无顶替）。
4. 击杀敌人：金钱奖励 = 档位倍率 ×（基础 + 每杀加成），经验不变；切换档位后倍率随之变化。
5. 按 Tab：打开/关闭 `InfoScreen` 占位页，本人阵营增益正确显示。
6. 重进存档：`FactionSetupData` 目标、玩家档案与阵营附体均保持。