/* ============================================================
   mock.js — 单一数据源（对应技术设计 §2 商品目录 / §5 mock schema）
   物品均为 SBW 0.8.9.1-final 已核对的注册名；价格/负重为初版平衡值。
   新版商店模型：
     · 商品目录 → 6 列滚动格
     · 购买 → 存入「储存格」（带 refund 资格 = 亮绿框，可无损卖回 100%）
     · 取出（进玩家栏）→ 该部分失去绿框；玩家物品存入储存格 → 不带绿框（只能 60% 卖回）
     · 右侧 = 玩家背包映射：装备栏 / 饰品栏 / 物品栏 / 快捷栏
   ============================================================ */
const MOCK = {
  /** 收购价系数（无绿框时按此比例；对应 Config.shopSellRatio） */
  sellRatio: 0.6,
  /** 无损卖回比例（绿框 = 刚买未取出的部分，对应 Config.shopRefundRate） */
  refundRate: 1.0,
  /** 储存格容量（5 列 × 12 行；实现时可按 Config 调整） */
  storageSize: 60,
  /** 玩家基础负重（已装备物品按目录负重另行计入） */
  player: { baseWeight: 0, weightLimit: 24.0, weightWarnRatio: 0.6 },
  /** 演示用余额（normal / poor = 余额不足演示） */
  balances: { normal: 4850, poor: 420 },

  /** 商品目录（分类：primary/secondary/ammo/armor/throwable/utility；icon+tint 为示意图标） */
  catalog: [
    { item: "superbwarfare:mp_5",              name: "MP5冲锋枪",          category: "primary",   price: 850,  weight: 2.6,  icon: "rifle",    tint: "black",  desc: "德制紧凑冲锋枪，射速快、后坐力低，室内交战的可靠选择。" },
    { item: "superbwarfare:m_870",             name: "M870霰弹枪",         category: "primary",   price: 900,  weight: 3.2,  icon: "rifle",    tint: "tan",    desc: "泵动式霰弹枪，近距一枪制敌，清房利器。" },
    { item: "superbwarfare:sks",               name: "SKS射手步枪",        category: "primary",   price: 800,  weight: 3.8,  icon: "rifle",    tint: "tan",    desc: "半自动射手步枪，中距离点射性价比极高。" },
    { item: "superbwarfare:ak_47",             name: "AK-47突击步枪",      category: "primary",   price: 1100, weight: 3.6,  icon: "rifle",    tint: "olive",  desc: "老而弥坚的 7.62 口径，结构简单、可靠性极高。" },
    { item: "superbwarfare:m_4",               name: "M4A1卡宾枪",         category: "primary",   price: 1250, weight: 3.2,  icon: "rifle",    tint: "tan",    desc: "模块化卡宾枪，操控性与精准度的平衡之作。" },
    { item: "superbwarfare:ak_12",             name: "AK-12突击步枪",      category: "primary",   price: 1300, weight: 3.4,  icon: "rifle",    tint: "olive",  desc: "现代化改进型，导轨齐全、人机工效出色。" },
    { item: "superbwarfare:hk_416",            name: "Hk-416突击步枪",     category: "primary",   price: 1450, weight: 3.5,  icon: "rifle",    tint: "black",  desc: "精度与可靠性的标杆，全能突击步枪。" },
    { item: "superbwarfare:mk_14",             name: "MK-14EBR射手步枪",   category: "primary",   price: 1600, weight: 4.0,  icon: "rifle",    tint: "olive",  desc: "战斗步枪改装型，中远距离火力压制。" },
    { item: "superbwarfare:svd",               name: "SVD狙击步枪",        category: "primary",   price: 1900, weight: 4.5,  icon: "rifle",    tint: "olive",  desc: "经典半自动狙击步枪，战场压制效果显著。" },
    { item: "superbwarfare:m_60",              name: "M60通用机枪",        category: "primary",   price: 2000, weight: 6.5,  icon: "rifle",    tint: "black",  desc: "7.62 通用机枪，阵地防御的持续火力核心。" },
    { item: "superbwarfare:rpg",               name: "RPG-7火箭筒",        category: "primary",   price: 2400, weight: 6.0,  icon: "launcher", tint: null,     desc: "肩射反装甲武器，对载具与工事效果显著。" },

    { item: "superbwarfare:m_1911",            name: "M1911手枪",          category: "secondary", price: 280,  weight: 0.9,  icon: "pistol",   tint: "black",  desc: "百年名枪，.45 口径停止作用强。" },
    { item: "superbwarfare:glock_17",          name: "格洛克17手枪",       category: "secondary", price: 300,  weight: 1.0,  icon: "pistol",   tint: "black",  desc: "聚合物手枪，弹匣容量大、故障率低。" },
    { item: "superbwarfare:mp_443",            name: "MP-443手枪",         category: "secondary", price: 320,  weight: 0.95, icon: "pistol",   tint: "silver", desc: "俄制军用制式手枪，结构简单耐用。" },
    { item: "superbwarfare:glock_18",          name: "格洛克18手枪",       category: "secondary", price: 520,  weight: 1.1,  icon: "pistol",   tint: "black",  desc: "可全自动射击的格洛克，近距自卫利器。" },

    { item: "superbwarfare:handgun_ammo",      name: "手枪弹药",           category: "ammo",      price: 6,    weight: 0.05, icon: "cartridge", tint: null,    desc: "散装手枪弹，按发购买。" },
    { item: "superbwarfare:handgun_ammo_box",  name: "盒装手枪弹药",       category: "ammo",      price: 70,   weight: 0.5,  icon: "ammobox",  tint: null,     desc: "整盒装手枪弹药，批量补给更划算。" },
    { item: "superbwarfare:rifle_ammo",        name: "步枪弹药",           category: "ammo",      price: 8,    weight: 0.06, icon: "cartridge", tint: null,    desc: "散装步枪弹，按发购买。" },
    { item: "superbwarfare:rifle_ammo_box",    name: "盒装步枪弹药",       category: "ammo",      price: 95,   weight: 0.6,  icon: "ammobox",  tint: null,     desc: "整盒装步枪弹药，战场续航的基础。" },
    { item: "superbwarfare:shotgun_ammo_box",  name: "盒装霰弹枪弹药",     category: "ammo",      price: 85,   weight: 0.6,  icon: "ammobox",  tint: null,     desc: "整盒装 12 号霰弹，近战补给。" },
    { item: "superbwarfare:sniper_ammo",       name: "狙击枪弹药",         category: "ammo",      price: 15,   weight: 0.08, icon: "cartridge", tint: null,    desc: "大口径狙击弹，威力与价格成正比。" },

    { item: "superbwarfare:ge_helmet_m_35",    name: "德国M35头盔",        category: "armor",     price: 350,  weight: 1.2,  icon: "helmet",   tint: "black",  desc: "经典钢盔，覆盖面积大、防护扎实。" },
    { item: "superbwarfare:us_helmet_pasgt",   name: "美制PASGT头盔",      category: "armor",     price: 420,  weight: 1.4,  icon: "helmet",   tint: "olive",  desc: "凯夫拉头盔，防弹性能可靠。" },
    { item: "superbwarfare:ru_helmet_6b47",    name: "俄罗斯6B47头盔",     category: "armor",     price: 480,  weight: 1.3,  icon: "helmet",   tint: "tan",    desc: "现代军用头盔，轻量化设计。" },
    { item: "superbwarfare:armor_plate",       name: "防弹插板",           category: "armor",     price: 300,  weight: 1.8,  icon: "plate",    tint: null,     desc: "可替换陶瓷插板，显著提升胸部防护。" },
    { item: "superbwarfare:us_chest_iotv",     name: "美制IOTV防弹胸甲",   category: "armor",     price: 850,  weight: 5.5,  icon: "vest",     tint: "black",  desc: "模块化防弹背心，可挂载各类弹匣包。" },
    { item: "superbwarfare:ru_chest_6b43",     name: "俄罗斯6B43防弹胸甲", category: "armor",     price: 920,  weight: 6.0,  icon: "vest",     tint: "olive",  desc: "重型防弹胸甲，防护等级高、机动代价大。" },

    { item: "superbwarfare:m18_smoke_grenade", name: "M18烟雾弹",          category: "throwable", price: 120,  weight: 0.5,  icon: "smoke",    tint: null,     desc: "遮蔽视野、掩护推进的战术烟雾。" },
    { item: "superbwarfare:hand_grenade",      name: "M67手榴弹",          category: "throwable", price: 150,  weight: 0.45, icon: "grenade",  tint: "olive",  desc: "标准破片手榴弹，室内清除首选。" },
    { item: "superbwarfare:rgo_grenade",       name: "RGO手榴弹",          category: "throwable", price: 180,  weight: 0.5,  icon: "grenade",  tint: "tan",    desc: "攻防两用型手榴弹，破片威力更大。" },
    { item: "superbwarfare:c4_bomb",           name: "C4炸药",             category: "throwable", price: 450,  weight: 1.2,  icon: "c4",       tint: null,     desc: "可远程引爆的塑胶炸药，破门与摧毁工事。" },

    { item: "superbwarfare:knife",             name: "军刀",               category: "utility",   price: 100,  weight: 0.5,  icon: "knife",    tint: null,     desc: "近战武器，也是野外求生的万能工具。" },
    { item: "superbwarfare:medical_kit",       name: "医疗包",             category: "utility",   price: 260,  weight: 1.0,  icon: "medkit",   tint: null,     desc: "战地急救包，可快速恢复生命状态。" },
    { item: "superbwarfare:defuser",           name: "拆弹器",             category: "utility",   price: 500,  weight: 1.0,  icon: "defuser",  tint: null,     desc: "用于拆除爆炸物的专业设备。" },
    { item: "superbwarfare:repair_tool",       name: "维修工具",           category: "utility",   price: 600,  weight: 2.0,  icon: "wrench",   tint: null,     desc: "载具与工事的维修工具，攻坚队伍必备。" }
  ],

  /** 非目录物品（不可收购；装备/饰品/杂项用） */
  misc: {
    "minecraft:iron_ingot":                     { name: "铁锭",           icon: "ingot",   tint: "iron" },
    "superbwarfare:steel_ingot":                { name: "钢锭",           icon: "ingot",   tint: null },
    "superbwarfare:tungsten_ingot":             { name: "钨锭",           icon: "ingot",   tint: "tungsten" },
    "superbwarfare:dog_tag":                    { name: "狗牌",           icon: "tag",     tint: null },
    "superbwarfare:thermal_imaging_goggles":    { name: "热成像护目镜",   icon: "goggles", tint: null },
    "superbwarfare:tactical_terminal":          { name: "单兵战术终端",   icon: "defuser", tint: "terminal" }
  },

  /** 装备栏 / 饰品栏槽位标签（右侧玩家背包映射 = MC 原生槽位；饰品栏 P1 接入 Curios 后开放） */
  equipSlots: ["头盔", "胸甲", "护腿", "靴子", "副手"],
  accSlots: ["狗牌", "护目镜", "终端"],

  /** 玩家背包映射（默认） */
  inventory: {
    // 头盔 / 胸甲 / 护腿 / 靴子 / 副手（MC 原生槽位）
    equip: [
      { item: "superbwarfare:us_helmet_pasgt", count: 1 },
      { item: "superbwarfare:us_chest_iotv",   count: 1 },
      null,
      null,
      { item: "superbwarfare:glock_17",        count: 1 }
    ],
    /** 饰品栏 P1（Curios）未开放：占位空槽 */
    acc: [null, null, null],
    main: [
      { item: "superbwarfare:rifle_ammo",       count: 32 },
      { item: "superbwarfare:handgun_ammo_box", count: 2 },
      { item: "superbwarfare:hand_grenade",     count: 3 },
      { item: "superbwarfare:medical_kit",      count: 1 },
      { item: "minecraft:iron_ingot",           count: 32 },
      { item: "superbwarfare:steel_ingot",      count: 8 },
      { item: "superbwarfare:dog_tag",          count: 3 },
      { item: "superbwarfare:tungsten_ingot",   count: 2 },
      { item: "superbwarfare:knife",            count: 1 },
      null, null, null, null, null, null, null, null, null, null, null, null,
      null, null, null, null, null, null
    ],
    hotbar: [
      { item: "superbwarfare:hk_416",  count: 1 },
      { item: "superbwarfare:c4_bomb", count: 1 },
      null, null, null, null, null, null, null
    ]
  },

  /** 储存格（60 格；refund = 该堆叠中仍可无损卖回的件数；预置物品 refund 为 0） */
  storage: [
    { item: "superbwarfare:ak_47",             count: 1,  refund: 0 },
    { item: "superbwarfare:sniper_ammo",       count: 20, refund: 0 },
    { item: "superbwarfare:m18_smoke_grenade", count: 2,  refund: 0 },
    { item: "superbwarfare:ru_helmet_6b47",    count: 1,  refund: 0 },
    { item: "superbwarfare:repair_tool",       count: 1,  refund: 0 },
    { item: "superbwarfare:us_chest_iotv",     count: 1,  refund: 0 },
    { item: "superbwarfare:rgo_grenade",       count: 4,  refund: 0 },
    { item: "superbwarfare:mp_443",            count: 1,  refund: 0 },
    null, null, null, null, null, null, null, null, null, null, null, null,
    null, null, null, null, null, null, null, null, null, null, null, null,
    null, null, null, null, null, null, null, null, null, null, null, null,
    null, null, null, null, null, null, null, null, null, null, null, null,
    null, null, null, null
  ],

  /** 分类显示名（与语言键 msb.shop.category.* 对应） */
  categoryNames: { primary: "主武器", secondary: "副武器", ammo: "弹药", armor: "护甲", throwable: "投掷物", utility: "工具" },
  categoryOrder: ["primary", "secondary", "ammo", "armor", "throwable", "utility"]
};