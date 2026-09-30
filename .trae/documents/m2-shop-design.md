# M2 配装商店（军火商店）技术设计

## Context（背景）

M2「经济与配装」目标（docs/03 §3.3）：建立「赚钱 → 买装 → 战斗 → 死亡 → 再买」经济闭环。当前已有设施：

- **钱包**：`PlayerWallet`（spent 本对局花销 / total 总资产）+ `WalletAttachments`（DataAttachment 持久）+ `WalletPayload`（S2C 仅本人，HUD 已显示）；
- **收入**：击杀奖励 / 友伤罚款已走钱包（`MsbServerEvents`）；
- **网络**：`MsbNetwork` PayloadRegistrar（playToServer / playToClient），C2S 参考 `TeleportRequestPayload`（`context.enqueueWork` + `context.player()`）；
- **UI 惯例**：全部 `GuiGraphics` / `Screen` 自绘（`TacticalMapScreen` / `MsbHudOverlay`）；键位切换参考 M 键战术地图（`MsbKeyMappings` + `MsbClientEvents`）；
- **DATAGEN**：语言资源唯一来源 `MsbLanguageProvider`（禁止手写 lang JSON）。

**借鉴 MyShopPanel（用户旧项目，已分析）**：界面骨架（动态尺寸居中面板 / 行列表 + 右侧栏 / 分页 / 选中高亮）、弹窗回调模式、服务端全量校验 + S2C 刷新。**不搬**：玩家市场、动态定价、冗余仓库、自定义 UI 贴图。

**红线**：商店商品只引用 SBW 公开注册名（`superbwarfare:*`），不复制 SBW 代码/资产；UI 零第三方库；语言 key 走 datagen。

---

## 1. 系统概览（组件与数据流）

```
[datapack JSON] data/msb/shop/<category>.json
      │ ServerResourceManager 重载（AddReloadListenerEvent）
      ▼
ShopCatalog（服务端权威价格/负重表，只读缓存）
      │ join / 重载后：ShopDataPayload（S2C 全量目录，仅含展示字段）
      ▼
ClientShopData（客户端缓存）──► ShopScreen（自绘：分类/列表/详情/弹窗/toast）

玩家操作 ── ShopTradePayload（C2S：action + itemId + count）──► ShopManager（服务端校验+执行）
      ├─ 钱包：WalletAttachments 读写 + WalletPayload 回推（复用既有通道）
      ├─ 物品：背包增删（放不下→掉落脚下）
      ├─ 负重：WeightService 重算 + 属性修饰
      └─ 回执：ShopResultPayload（S2C：成功/失败码 + 金额变化）
```

| 组件 | 侧 | 职责 |
|---|---|---|
| `shop/ShopCatalog` | 服务端 | 加载 datapack JSON、按物品 ID / 分类索引、重载后重发 |
| `shop/ShopEntry` | 双端 | 条目 record：itemId / category / price / sell / weight |
| `shop/ShopManager` | 服务端 | 交易校验与执行（唯一改钱包/背包入口） |
| `shop/WeightService` | 服务端 | 负重计算、分级修饰（移速/跳跃） |
| `network/ShopDataPayload` | S2C | 目录全量同步（join + reload，几 KB 一次性） |
| `network/ShopTradePayload` | C2S | 交易请求（不携带价格，服务端查表） |
| `network/ShopResultPayload` | S2C | 交易回执（成功/失败码 + 金额 + 负重） |
| `client/ClientShopData` | 客户端 | 目录缓存（供 ShopScreen / HUD 负重显示读取） |
| `client/ShopScreen` | 客户端 | 自绘购买窗口（本设计 §5，原型 1:1 对应） |

---

## 2. 商品目录（数据层，服务端权威）

**位置**：`src/main/resources/data/msb/shop/<category>.json`，一个分类一个文件（文件即分类，支持 `data/<ns>/shop/` 追加）。**不手写 lang JSON 的约束只针对语言资源；商店目录是游戏数据，直接手写**（与 SBW `data/msb/sbw/mob_guns/ai_combatant.json` 同性质）。

**分类枚举**（固定 6 类，语言键 `msb.shop.category.*`）：`primary` 主武器 / `secondary` 副武器 / `ammo` 弹药 / `armor` 护甲 / `throwable` 投掷物 / `utility` 工具。

**条目格式**：

```json
{
  "entries": [
    { "item": "superbwarfare:hk_416", "price": 1450, "sell": 870, "weight": 3.5 },
    { "item": "superbwarfare:rifle_ammo", "price": 8, "weight": 0.06 }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `item` | ✔ | SBW（或任意）物品注册名；`BuiltInRegistries.ITEM` 不存在 → 跳过并 log warn |
| `price` | ✔ | 买入价（整数 $，≥1；负数/0 → 跳过并 log warn） |
| `sell` | ✘ | 卖出价，缺省 = `price × Config.shopSellRatio`（默认 0.6，四舍五入） |
| `weight` | ✔ | 单件负重（kg，float，≥0） |

**加载**：`ShopCatalog implements SimpleJsonResourceReloadListener`（目录参数 `"shop"`），挂 `AddReloadListenerEvent`；`apply()` 内按文件路径取分类名，未知分类跳过。**重复 item 只保留首个**（log warn）。加载完成后若服务器在线则向全量玩家重发 `ShopDataPayload`。若收到交易请求时目录为空 → 拒绝（返回 `no_catalog`）。

**物品名**：不在目录中存显示名——客户端用 `ItemStack#getHoverName()` 渲染（SBW 自带翻译，自动随语言）；描述文案（可选）用 `msb.shop.desc.<item_path>` 语言键，缺失则不显示。

**初始目录**：见文末附表（35 项，价格/负重为初版平衡值，M5 集中调）。

---

## 3. 网络协议（MsbNetwork 新增 3 个载荷，PROTOCOL_VERSION 升至 "2"）

| 载荷 | 方向 | 字段 | 时机 |
|---|---|---|---|
| `ShopDataPayload` | S2C | `entries: List<{itemId, category, price, sell, weight}>` | 玩家 join 后 + 目录重载后（含 `/reload`）；客户端缓存，打开窗口零请求 |
| `ShopTradePayload` | C2S | `action: BUY/SELL/TAKE/STORE` · `zone: STORAGE/EQUIP/ACC/MAIN/HOTBAR`（BUY 忽略）· `slot: int` · `itemId` · `count: int`（服务端钳 1~2304） | 购买/出售确认、取回、存入时发送 |
| `ShopStoragePayload` | S2C | `stacks: List<{itemId, count, refund}>`（有序堆叠列表，下标即储存格格位） | 玩家加入时 + 每次交易/转移后（个人数据单发本人） |
| `ShopResultPayload` | S2C | `action` · `code: enum`（see §4）· `itemId` · `count` · `moneyDelta: int`（+收入/-支出）· `refunded: int`（其中无损卖回件数） | 每笔交易处理完即回执（客户端本地化为 toast） |

- **无「打开请求」包**：窗口是纯客户端 Screen，目录在 join 时已缓存；未收到数据时显示「载入中」（`ShopDataPayload` 到达前）。目录缺失（旧服务端）→ 窗口显示空态并提示。
- 商品最多 999 件/笔（步进器上限），服务端钳 1~2304（堆叠 64 × 跨堆叠），`count ≤ 0` 直接拒绝；**服务端校验 `zone+slot` 内物品与 `itemId` 一致**（防槽位错位/改包）。
- 价格**永不信任客户端**：`ShopTradePayload` 不含价格；服务端以自持目录计算 `cost = price × count`（long 中间值防溢出，超 `Integer.MAX_VALUE` 拒绝）。

---

## 4. 服务端交易逻辑（`ShopManager`，唯一改钱包/储存格/背包入口）

**核心模型：储存格与「绿框」（refund 资格，2026-10-01 改版）**
- **储存格 = 个人军械库**（per-player，服务端按 UUID 持久化，见 §10）：**购买的商品先入储存格**；储存格内物品**不计负重**（不随身携带）；
- 每个堆叠记录 `{itemId, count, refund}`：`refund` = 该堆叠中仍可**无损卖回（100% 原价）**的件数；
- 规则：**买入 `refund += count`**；**取回到玩家栏 → `refund = min(refund, count - 取出数)`（取出即失去资格）**；**玩家物品存入 → 不增加 refund（新堆叠 refund = 0）**；
- 卖出按件混算：`earn = min(count, refund) × price + (count - min) × sellPrice`（`sellPrice = price × shopSellRatio`，默认 60%）；
- 卖出以**所选堆叠**为单位（≤64 件/笔），不跨堆叠。

**买入（BUY）校验顺序**（任一失败 → 回执失败码，不动账）：

| # | 校验 | 失败码 |
|---|---|---|
| 1 | 目录已加载且存在该 `itemId` 条目 | `unknown_item` / `no_catalog` |
| 2 | `1 ≤ count ≤ 2304`；`cost = price × count` 不溢出 | `bad_count` |
| 3 | `wallet.total() ≥ cost` | `no_balance` |
| 4 | 储存格可容纳 count 件（同物品堆叠余量 + 空格 ×64） | `storage_full` |

执行：`total -= cost`、`spent += cost` → 入储存格（`refund += count`）→ 发 `WalletPayload` + `ShopResultPayload`。**购买不计负重（入储存格），重量无变化。**

**卖出（SELL）**：来源 = 储存格堆叠（`zone=STORAGE`）或玩家栏槽位（其余 zone）
- 目录存在该条目（否则 `unsellable`）；`count ≤ 所选堆叠数量`（否则 `not_enough`）；
- 储存格按上表混算；玩家栏一律 `sellPrice`（绿框只在储存格内有效）；
- 执行：扣堆叠 → `total += earn`（`spent` 不变）→ 重算负重（储存格卖出重量不变）→ 回执 + `WalletPayload`。

**取回（TAKE，储存格 → 玩家栏）**：

| # | 校验 | 失败码 |
|---|---|---|
| 1 | 槽位堆叠存在且 `count ≤ 堆叠数量` | `not_enough` / `bad_count` |
| 2 | `WeightService.total(player) + weight × count ≤ Config.loadoutWeightLimitKg`（**取回才计入负重**） | `over_weight` |
| 3 | 玩家物品栏 + 快捷栏可容纳 count 件 | `bag_full` |

执行：储存格 `count -= n`、`refund = min(refund, count)` → 玩家侧入包（物品栏优先，其次快捷栏）。

**存入（STORE，玩家栏 → 储存格）**：槽位存在且数量足够（`not_enough`）→ 储存格有空间（`storage_full`）→ 移动，**不获得绿框**。

**钱包公式**（与 M1 既有模式一致）：`setData(WALLET, new PlayerWallet(spent, total))` + `PacketDistributor.sendToPlayer(player, new WalletPayload(...))`。

---

## 5. 客户端 UI（`ShopScreen`，自绘 `Screen`）

**开关**：新增键位 `key.msb.shop`（默认 **B**），`MsbKeyMappings` 注册 + `MsbClientEvents` 切换：`screen == null` 或已是 `ShopScreen` 时切换（与 M 键战术地图同模式，两屏互斥不叠加）；`isPauseScreen() = false`（多人不暂停，沿用地图惯例）。`ECS`：有弹窗先关弹窗，无弹窗关窗口。

**窗口尺寸与四区布局（GUI 单位；原型像素 = GUI × 2；2026-10-01 改版）**：

| 区域 | GUI 单位 | 原型像素 | 内容 |
|---|---|---|---|
| 整窗 | 470 × 260 | 940 × 520 | 居中，`fill` 深色半透明 + 1px 描边 |
| 顶栏 | 470 × 22 | 940 × 44 | 左：标题「军火商店 ARMORY」；右：余额（金色） |
| 分类栏 | 72 × 212 | 144 × 424 | 6 分类纵向列表；选中 = 左缘金色条 + 金色文字 |
| 商品滚动格 | 212 × 112 | 424 × 224 | **6 列滚动格**（格 28×28：物品图标 16 + 价格小字）；左键选中、双击快速购买、滚轮滚动 |
| 购买/出售面板 | 97 × 96 | 196 × 192 | 中栏**左下**（随选中对象切换）：商品 → 数量步进 + 合计 + [购买]；储存格堆叠 → 收入 + [取回背包][卖出]；玩家物品 → 收入 + [存入储存格][卖出]；底部禁用原因 |
| 储存格 | 101 × 96 | 202 × 192 | 中栏**右下**：**5 列滚动格**（共 `shopStorageSlots` = 60 格）；**绿框 = 可无损卖回**（新购脉冲高亮），数量角标 |
| 玩家背包 | 168 × 212 | 336 × 424 | 右栏映射 **MC 原生槽位**：装备栏（头盔/胸甲/护腿/靴子/副手，3×2；槽位名以副标题标注）、饰品栏（3 格占位，P1 接入 Curios 后开放）、物品栏（27，9×3）、快捷栏（9，9×1） |
| 底栏 | 470 × 20 | 940 × 40 | 左：操作提示；右：`负重 20.2 / 24.0 kg` + 细条（≥60% 琥珀、≥100% 红） |

**窗口自适应放大（2026-10-01 追加）**：整窗按当前 GUI 尺寸**等比缩放绘制**——目标占屏 94%，缩放钳制 0.6~1.75 并取 1/8 步进；小窗口自动缩小（不裁切），大屏适当放大（1920×1080 实测：GUI Scale 2 ≈ **1.75×**、Scale 3 ≈ **1.25×**、Scale 4 ≈ 1.0× 近满屏）。实现：`pose.pushPose/translate/scale` 包裹整窗绘制 + 鼠标坐标逆变换（`(screen-origin)/k`）+ scissor 手动换算；tooltip 在屏幕空间绘制、用原始鼠标坐标。

**文字保持 1× 字号（不随窗口放大）**：槽位/图标/面板随窗口放大，但文本一律按原始字号绘制——`drawText()` 在局部做 1/k 反缩放（盒内文本用 `drawTextV()` 垂直居中）；布局宽度用 `tw()`（字体宽度 ÷ k）、截断用 `trunc()`（设计宽度 × k 换算），保证居中/右对齐/截断仍然正确。物品数量角标由 `renderItemDecorations` 绘制，随图标同倍缩放。

**交互清单**（与 HTML 原型一一对应）：

1. 点击商品格 → 选中（金框 + 面板切换为购买）；**双击 = 直接弹确认购买**；滚轮滚动商品格；
2. 点击储存格 / 玩家栏槽位 → 选中（金框；储存格绿框保留）；面板切换为出售/转移；
3. 数量步进 `[-] [n] [+]` + `MAX`（购买上限 = 余额与储存格余量的较小值；出售/转移上限 = 所选堆叠数量）；
4. `[购买]` / `[卖出]` → **确认弹窗**（数量、合计、余额变化、负重变化；购买额外显示「去向：存入储存格（负重不变）」「绿框 n 件」）；
5. `[取回背包]` / `[存入储存格]` → 即时转移（无弹窗）→ toast 反馈；
6. 确认 → 发 `ShopTradePayload` → 服务端回执 → 成功 toast（含金额 / 无损件数）/ 失败 toast（含原因）；余额、负重、储存格、绿框实时刷新；
7. 禁用态：余额不足 / 储存格已满 / 负重超限（取回）/ 背包已满 / 不可收购 → 按钮变灰 + 下方红字原因（服务端仍兜底校验）。

**绿框视觉**：储存格中 `refund > 0` 的堆叠画**亮绿 2px 内描边**；新购后脉冲发光 3 次；tooltip 显示「绿框：n 件可无损卖回（100%）」；储存格标题栏常驻图例「绿框 = 可无损卖回」。

**渲染规范**：全部 `fill` 矩形 + `drawString` + `renderItem`（真实物品图标 + `renderItemDecorations` 数量角标）+ `renderTooltip`（1.21.1 原生）；色板沿用战术地图深色系（面板 `0xE0101010`、描边 `0x50FFFFFF`、选中/价格金 `0xFFF2B13C`、绿框 `0xFF4ADE80`、危险 `0xFFFF5A4D`）；无贴图、无第三方库；文本全部走语言键。

**状态**：载入中（目录未到）→ 骨架格；空槽 → 装备/饰品槽显示角色标签（头盔/胸甲/…）、其余空槽正常底色；选中失效（被卖光/取空）→ 回落「点击…」占位提示。

---

## 6. 负重系统（M2 同批实现）

- **计算**：`WeightService.total(player)` = Σ（目录内物品 **玩家栏（装备/饰品/物品栏/快捷栏）** 数量 × 条目 weight）。**储存格内物品不计重；目录外物品不计重**（M2 取舍：负重即「随身配装负担」，全物品重量表留 P1）。
- **分级**（`Config.loadoutWeightLimitKg` 默认 24）：
  - `< 60%` 正常；
  - `60% ~ 100%` 移速线性下降，100% 时 `-Config.weightSpeedPenaltyMax`（默认 -20%，`MOVEMENT_SPEED` 负乘法修饰）；
  - `> 100%`：维持 -20% 移速 + 跳跃受限（首选 `Attributes.JUMP_STRENGTH` 负修饰；**实现时先验证 1.21.1 该属性对玩家生效性**，无效则兜底「超重时禁止跳跃」）。**取回不得使负重超过上限**（§4 TAKE 校验 2）；**购买不受重量限制**（先入储存格）。
- **触发重算**：交易 / 取回 / 存入成功后立即；另每 20 tick 对在线玩家兜底重算（成本 36 格 × N 人，可忽略）。
- **显示**：商店购买面板 + 底栏（§5）；HUD 顶栏钱包左侧追加「负重 20.2/24」，≥60% 琥珀、≥100% 红（`MsbHudOverlay` 小改）。
- 修饰符用固定 `ResourceLocation`（`msb:weight_speed`）`addOrUpdateTransientModifier`，避免叠加/残留。

---

## 7. 死亡丢装（接口预留，M2 不新增拦截）

- 规则（PRD §8）：死亡掉落全套配装与背包物品（**原版掉落行为天然满足**）；**现金永不掉落**（钱包为 DataAttachment，天然保留）。
- M2 不做额外 `PlayerDropsEvent` 拦截；在文档与代码注释中预留钩子位置（P1 保险券：死亡时保留目录内配装）。
- 若服务器开启 `keepInventory`，遵循服务器规则（不强制覆盖）。

---

## 8. Config 新增（`Config.java`）

| 键 | 类型/默认 | 说明 |
|---|---|---|
| `shopSellRatio` | double 0.6 | 收购价系数（无绿框时；缺省 sell = price × 系数） |
| `shopRefundRate` | double 1.0 | 绿框无损卖回比例（1.0 = 原价回收） |
| `shopStorageSlots` | int 60 | 个人储存格容量（格数） |
| `loadoutWeightLimitKg` | double 24.0 | 负重上限（kg） |
| `weightSpeedPenaltyMax` | double 0.20 | 满负重移速惩罚（0.2 = -20%） |
| `weightWarnRatio` | double 0.6 | 开始减速/变黄的负重比例 |

（均为 COMMON 配置，非运行时热切换，无需 `Config.save()`。）

---

## 9. 语言 key 清单（`MsbLanguageProvider`，en + zh 双份，datagen）

`key.msb.shop`（"Armory"/"军火商店"）· `msb.shop.title` · `msb.shop.category.primary/secondary/ammo/armor/throwable/utility` · `msb.shop.balance` · `msb.shop.weight`（"%s / %s kg"）· `msb.shop.price` · `msb.shop.sell_price`（六折价）· `msb.shop.qty` · `msb.shop.max` · `msb.shop.total` · `msb.shop.buy` · `msb.shop.sell` · `msb.shop.take`（"取回背包"）· `msb.shop.store`（"存入储存格"）· `msb.shop.storage`（"储存格 %s/%s"）· `msb.shop.refund_legend`（"绿框 = 可无损卖回"）· `msb.shop.refund_tip`（"%s 件可无损卖回（100%）"）· `msb.shop.unsellable` · `msb.shop.loading` · `msb.shop.hint` · `msb.shop.group.equip/acc/main/hotbar` · `msb.shop.slot.head/chest/plate/primary/secondary/melee/charm/goggles/terminal`（装备/饰品槽位标签）· `msb.shop.confirm.buy` / `.sell` · `msb.shop.confirm.to_storage`（"存入储存格（负重不变）"）· `msb.shop.confirm.refund_n`（"绿框 %s 件"）· `msb.shop.confirm.balance_after` · 结果：`msb.shop.result.bought`（"已购买 %s ×%s · -%s（已存入储存格）"）/ `.sold` / `.sold_refund`（"（含无损 %s 件）"）/ `.taken`（"已取回 %s ×%s（绿框已失效）"）/ `.stored` · 错误：`msb.shop.error.no_balance` / `.storage_full` / `.bag_full` / `.over_weight` / `.not_enough` / `.unsellable` / `.unknown_item` / `.bad_count` / `.no_catalog`（服务端返回枚举码，客户端本地化）。

---

## 10. 文件清单

**新建**：

- `src/main/java/com/mercenarysandbox/msb/shop/ShopCategory.java` / `ShopEntry.java` / `ShopCode.java` / `ShopCatalog.java` / `ShopManager.java` / `WeightService.java`
- `src/main/java/com/mercenarysandbox/msb/shop/ShopStorage.java`（储存格堆叠 record `{itemId, count, refund}` + Codec）· `ShopStorageAttachments.java`（**per-player DataAttachment** 注册，序列化 + `copyOnDeath`，跨重连/死亡保留）
- `src/main/java/com/mercenarysandbox/msb/network/ShopDataPayload.java` / `ShopStoragePayload.java` / `ShopTradePayload.java` / `ShopResultPayload.java`
- `src/main/java/com/mercenarysandbox/msb/client/ShopScreen.java` / `ClientShopData.java`
- `src/main/resources/data/msb/shop/{primary,secondary,ammo,armor,throwable,utility}.json`（初始目录）

**修改**：

- `MsbNetwork`（3 载荷注册，PROTOCOL_VERSION→"2"）；`MsbServerEvents`（join 后发目录、重载重发、每 20 tick 负重兜底）
- `MercenarySandboxShooter`（注册 `ShopStorageAttachments` 附体）
- `MsbKeyMappings` + `MsbClientEvents`（B 键开关）
- `Config`（§8 六项）；`MsbHudOverlay`（顶栏负重显示）
- `MsbLanguageProvider`（§9 全部 key）→ `runData` 后 `gradlew build`

**原型（本设计的 HTML 展示件）**：`ui-prototype/shop/`（index.html / styles.css / mock.js / icons.js / api.js / app.js），窗口像素 = GUI 单位 × 2，交互与 §5 清单一一对应。

---

## 11. 验收标准

1. 单机闭环：B 开商店 → 买 HK-416（扣款、**存入储存格并亮绿框脉冲、负重不变**）→ **取回背包**（+3.5kg、绿框失效）→ 卖出（六折）→ 另买一件**不动它、直接从储存格无损卖回**（100%）→ 余额/负重/储存格实时正确；
2. 拒绝路径：余额不足 / 储存格已满 / 取回超重 / 背包已满 / 不可收购，均被拒且提示明确；
3. 多人：目录 join 同步；他人看不到你的钱包与储存格（个人数据单人发送）；改包（改价格/负数/超量/槽位错位）被服务端拒绝；
4. `/reload`（或换 datapack）后目录刷新且在线玩家即时收到；
5. 负重回落到 60% 以下时移速恢复（修饰符无残留）；
6. 死亡后：配装掉落、现金保留、基地复活照常（M1 回归）；**储存格内容跨死亡与重连保持**。

---

## 12. 待确认项（实现前需用户拍板）

| # | 事项 | 本次改版采用（原型已实现） | 备选 |
|---|---|---|---|
| 1 | 开店方式 | **B 键随时可开**（M2 便于测试与单机体验） | 仅基地安全区内可购 |
| 2 | 储存格归属 / 容量 | **个人储存格**（按 UUID 持久化，跨重连/死亡保留）· **60 格**（Config 可调） | 全局共享军械柜 / 更大容量 |
| 3 | 绿框口径 | **购买后未取出的件数 = 可无损卖回（100%）**；取回即失效；玩家物品存入不获得 | 整单撤销（未取出可整单原价退） |
| 4 | 卖出粒度 | **以所选堆叠为单位（≤64 件/笔，不跨堆叠）** | 同物品跨堆叠合计卖出 |
| 5 | 转移约束 | **取回受负重上限 + 背包空间约束；存入受储存格容量约束；购买不计重** | 取回允许超重（由负重惩罚承担） |
| 6 | 出售范围 | **仅目录内物品可售** | 目录外物品按材料价回收（需全物品价格表，P1） |
| 7 | 死亡掉装 | **原版掉落 + 现金与储存格保留** | M2 即做保险券/指定保留（P1 原计划） |
| 8 | 数值 | 负重上限 24kg、六折 60%、无损 100%、储存格 60 格、满负重 -20% 移速 | 可按测试反馈调 |

---

## 附：初始商品目录（35 项，初版平衡值；已与 SBW 0.8.9.1-final jar 语言文件逐一核对）

| 分类 | 物品（注册名） | 名称 | 价 $ | 负重 kg |
|---|---|---|---|---|
| primary | mp_5 | MP5冲锋枪 | 850 | 2.6 |
| primary | m_870 | M870霰弹枪 | 900 | 3.2 |
| primary | sks | SKS射手步枪 | 800 | 3.8 |
| primary | ak_47 | AK-47突击步枪 | 1100 | 3.6 |
| primary | m_4 | M4A1卡宾枪 | 1250 | 3.2 |
| primary | ak_12 | AK-12突击步枪 | 1300 | 3.4 |
| primary | hk_416 | Hk-416突击步枪 | 1450 | 3.5 |
| primary | mk_14 | MK-14EBR射手步枪 | 1600 | 4.0 |
| primary | svd | SVD狙击步枪 | 1900 | 4.5 |
| primary | m_60 | M60通用机枪 | 2000 | 6.5 |
| primary | rpg | RPG-7火箭筒 | 2400 | 6.0 |
| secondary | m_1911 | M1911手枪 | 280 | 0.9 |
| secondary | glock_17 | 格洛克17手枪 | 300 | 1.0 |
| secondary | mp_443 | MP-443手枪 | 320 | 0.95 |
| secondary | glock_18 | 格洛克18手枪 | 520 | 1.1 |
| ammo | handgun_ammo | 手枪弹药 | 6 | 0.05 |
| ammo | handgun_ammo_box | 盒装手枪弹药 | 70 | 0.5 |
| ammo | rifle_ammo | 步枪弹药 | 8 | 0.06 |
| ammo | rifle_ammo_box | 盒装步枪弹药 | 95 | 0.6 |
| ammo | shotgun_ammo_box | 盒装霰弹枪弹药 | 85 | 0.6 |
| ammo | sniper_ammo | 狙击枪弹药 | 15 | 0.08 |
| armor | ge_helmet_m_35 | 德国M35头盔 | 350 | 1.2 |
| armor | us_helmet_pasgt | 美制PASGT头盔 | 420 | 1.4 |
| armor | ru_helmet_6b47 | 俄罗斯6B47头盔 | 480 | 1.3 |
| armor | armor_plate | 防弹插板 | 300 | 1.8 |
| armor | us_chest_iotv | 美制IOTV防弹胸甲 | 850 | 5.5 |
| armor | ru_chest_6b43 | 俄罗斯6B43防弹胸甲 | 920 | 6.0 |
| throwable | m18_smoke_grenade | M18烟雾弹 | 120 | 0.5 |
| throwable | hand_grenade | M67手榴弹 | 150 | 0.45 |
| throwable | rgo_grenade | RGO手榴弹 | 180 | 0.5 |
| throwable | c4_bomb | C4炸药 | 450 | 1.2 |
| utility | knife | 军刀 | 100 | 0.5 |
| utility | medical_kit | 医疗包 | 260 | 1.0 |
| utility | defuser | 拆弹器 | 500 | 1.0 |
| utility | repair_tool | 维修工具 | 600 | 2.0 |

> 全部为 `superbwarfare:` 注册名（已从 SBW 0.8.9.1-final jar 语言文件核对存在）；弹药散装/盒装的堆叠粒度在实现时按 SBW 实际物品语义最终核对（价格表以「1 件物品」为单位，与原型 mock 一致）。