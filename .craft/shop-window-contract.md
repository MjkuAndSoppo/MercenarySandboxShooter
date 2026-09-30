# Design Contract · MSB 配装商店购买窗口原型（ui-prototype/shop）

> 依据 `m2-shop-design.md` §5 冻结；窗口像素 = GUI 单位 × 2（游戏内 GUI Scale 2 观感）。

## Style Tier & Aesthetic Direction
- style: tech-dark（军事战术 GUI，作为「MC 内嵌窗口」呈现）
- aesthetic: industrial-utilitarian / in-game HUD —— 深色半透明面板、1px 锐利描边、直角、等宽数字、MC 物品图标质感
- tone keywords: tactical / restrained / high information density
- 场景：窗口浮在压暗的「战场」背景上（纯 CSS 氛围），窗口本身严格直角、无渐变

## Design Tokens（沿用项目既有 contract，窗口专属项另列）
- color.window-bg: rgba(10,12,16,.94)   color.window-border: #2a3240   color.window-border-inner: #1a202b
- color.bg: #0a0d12   surface: #12161e   surface-2: #1a202b   border: #232b38   border-strong: #303c4e
- color.text: #e8edf4   text-sub: #8b98ab   text-dim: #5a667a
- color.primary(金/价格/选中): #f2b13c   primary-hover: #ffc55c
- color.success: #4ade80   danger: #ff5a4d
- color.row-hover: rgba(255,255,255,.06)   row-selected: rgba(242,177,60,.16) + 左缘 2px 金条
- MC 物品 tooltip: bg rgba(16,0,16,.94) / 边框上 #5000ff 下 #28007f 左右 #3a00b4
- font.display: "Microsoft YaHei UI","PingFang SC"（标题 600，字距 1px）
- font.body: "Microsoft YaHei","PingFang SC","Segoe UI"
- font.mono: "Consolas","Cascadia Mono",monospace（金额/数量/负重一律 mono）
- font.scale: 11 / 12 / 13 / 14 / 16 / 18
- spacing.unit: 4px；radius: 0（窗口内全部直角，仅原型标注 chip 用 4px）
- motion: 窗口打开 fade+scale(.98→1) 160ms; toast 滑入 180ms；行 hover/选中 120ms; 无其他动效

## Component Spec（2026-10-01 v2 储存格改版）
- window: 940×520 居中；bg window-bg；外框 1px window-border + 外圈 1px #05070a 硬阴影
- header: 高 44；左标题（金）、右余额（mono 金）
- category item: 高 32；默认 text-sub；hover 白字+row-hover；active = 金条+金字
- cell(商品格): 56×56（6 列滚动格）；bg #14171d；hover 描边 border-strong；selected 金框 2px；= 32px 图标 + 价格 10px mono 金
- slot(槽位): 32×32（储存格 5 列滚动 / 玩家背包 9 列）；bg #14171d + 内阴影 1px #0a0d12；hover 描边；空装备/饰品槽显示角色标签；数量角标右下 mono 10
- 绿框(refund): 亮绿 2px 内描边（`refund>0`）；selected+refund = 金 1px + 绿 3px；新购脉冲 3 次（glow）
- stepper: [-] [数量 mono] [+] [MAX]，按钮 24×24 surface-2 描边（上下布局嵌于面板）
- button.primary: 高 30，金底 #f2b13c + 深字 #14161c；disabled = 40% 透明 + 下方红字原因
- 面板(购买/出售): 中栏左下 196×192；随选中切换（购买 / 取回+卖出 / 存入+卖出）+ 图例说明 + 原因行
- dialog: 348×~200 居中于窗口内；bg surface + 1px border-strong + 遮罩 rgba(0,0,0,.55)；[取消][确认]
- toast: 窗口右下角，宽度自适应 ≤340；success 绿左条 / danger 红左条 / info 金左条；2.6s 自动消失
- weight meter: 6px 细条（footer 右），<60% 绿 / ≥60% 琥珀 / ≥100% 红
- loading: 商品格 12 个骨架格（surface-2 呼吸闪烁）
- empty: 面板占位文案（「点击商品 / 储存格 / 背包物品」）
- annotation(仅原型): 窗口外标注 chip / 虚线框，标注 GUI 尺寸，1:1 不进入设计

## Page List（单页 + 窗口内状态；v2）
- shop window（唯一页面）: 四区布局 —— 分类栏 | 中间栏（商品 6 列滚动格 + 购买/出售面板 + 储存格 5 列滚动格）| 玩家背包映射（装备/饰品/物品/快捷）；
  窗口内状态：购买面板 / 储存格面板（取回+卖出）/ 玩家物品面板（存入+卖出）/ 确认弹窗 / toast / 载入骨架 / 空槽；
  原型附加演示控件（窗口外）：默认 / 余额不足 / 清空库存 / 显示标注（GUI 尺寸）/ 关闭-重开

## Mock Schema（mock.js 单一数据源；v2）
- catalog[35]: { item, name, category, price, weight, icon, tint?, desc }
- inventory: { equip[6], acc[3], main[27], hotbar[9] } → null | { item, count }
- storage[60]: null | { item, count, refund }（refund = 该堆叠可无损卖回件数）
- player: { baseWeight: 0, weightLimit: 24.0, weightWarnRatio: 0.6 } · sellRatio 0.6 · refundRate 1.0 · storageSize 60
- demo: { mode: normal|poor|emptyBag, annotate: bool }

## Icon Spec
- UI 图标：Lucide 内联 SVG（coins / weight / x / chevron-left / chevron-right / minus / plus / shopping-cart / banknote / check-circle / alert-triangle），stroke 1.5，currentColor，禁止 emoji
- 物品图标：自绘 16×16 像素画 canvas（image-rendering: pixelated，2×/4× 显示），示意用途 —— 实现时替换为 SBW 物品贴图（页面底部有说明标注）

## 说明
- 不存在跨页导航（单页）；App Shell 不适用；无外部依赖，双击 index.html 离线可开。