# Design Contract · MercenarySandboxShooter — Mod UI 原型

## Style Tier & Aesthetic Direction
- style: tech-dark（军事战术 HUD）
- aesthetic: industrial-utilitarian / military-tactical — 深色底、锐利直角、等宽数字、扫描线噪声质感，克制的高信息密度
- tone keywords: tactical / restrained / high information density

## Design Tokens
- color.bg: #0a0d12      color.surface: #12161e      color.surface-2: #1a202b
- color.border: #232b38   color.border-strong: #303c4e
- color.text: #e8edf4     color.text-sub: #8b98ab     color.text-dim: #5a667a
- color.primary: #f2b13c（战术琥珀，全局主强调）   primary-hover: #ffc55c
- color.success: #4ade80   color.warning: #f2b13c   color.danger: #ff5a4d
- 阵营色（唯一多色来源，禁止其余随机色）:
  - lonestar #4d9eff（蓝）  valkyra #ff5a4d（红）  manticore #4ade80（绿）
- font.display: "Microsoft YaHei UI", "PingFang SC", sans-serif（标题，600~800 字重）
- font.body: "Microsoft YaHei", "PingFang SC", "Segoe UI", sans-serif
- font.mono: "Consolas", "SFMono-Regular", monospace（数字/计时/金额一律 mono）
- font.scale: 12 / 13 / 14 / 16 / 20 / 24 / 32
- radius: sm4 / md6 / lg8   shadow: sm 0 1px 2px rgba(0,0,0,.4); md 0 6px 20px rgba(0,0,0,.45)
- spacing.unit: 4px（4/8/12/16/24/32/48）
- layout: max-width 1280px；左侧固定导航 232px；主内容 margin-left 232px
- icon.lib: Lucide（内联 SVG，stroke 1.5，size 16/18/20，currentColor；禁止 emoji 作图标）
- motion: 页面切屏 staggered reveal（animation-delay 步进 60ms）+ hover/active 过渡 0.18s ease
- bg-texture: 深色网格底纹 + 顶部径向渐变辉光 + 噪点（纯 CSS，不引图）

## Component Spec
- button: primary(琥珀)/ghost(描边)/danger 三态；hover 提亮 8%；active 内缩 1px；disabled 40% 不透明度
- card: bg surface、1px border、radius md、hover 时 border-strong + translateY(-1px)
- tag: 12px mono，圆角 sm4，按阵营/状态着色
- input/select: bg surface-2、border、focus 琥珀描边
- progress: 条状填充（琥珀/绿/红按语义），背景 surface-2
- modal: 居中、bg surface、border、shadow md、0.18s 缩放进入
- toast: 右上角滑入，success/danger 两种，3.2s 自动消失
- sidebar nav: 固定左侧 232px；item = 图标 + 标签，active 左缘 3px 琥珀条 + 高亮

## App Shell + Canonical Nav
- shell: 单页应用（index.html 单文件 + hash 路由）。固定左侧 nav + 顶部 topbar（logo/现金/阵营/分数迷你条）+ 主内容槽。导航由 nav-active 逻辑依据 hash 设置 active，结构单文件天然防漂移。
- nav items（冻结，顺序不变）: 概览 overview | 阵营 faction | 配装 loadout | 战场 battlefield | 建造 build
- active rule: hash 路由 `#/<key>` 与 nav `data-nav` 匹配 → 加 .active；默认 #/overview

## Page List（5 屏）
1. overview 项目概览 | 定位卡、WARDOGS→MC 映射、P0 范围 | → 其余各屏
2. faction 阵营选择 | 三阵营属性卡、选择→持久化、分配状态
3. loadout 配装商店 | 现金、分类页签、负重、购买/部署 | → battlefield
4. battlefield 战场 HUD | 30s 计分、控制区/热区、塔楼码、钻井台、计分板、击杀流
5. build 建造系统 | FOB 工事目录、资源、建造队列、等级升级

## Mock Schema
- faction: { id, name, color, motto, role, perk, strength, defense, players }
- item: { id, name, cat(weapon/armor/consumable), price, weight, dmg/armor/desc, tier, icon }
- fort: { id, name, cost, hp, tier, desc, icon, unlock }
- state: localStorage 保存 { faction, cash, owned: string[] }
