/* MercenarySandboxShooter · 原型数据层（单文件 mock，页面与 api 从这读） */
const DB = {
  factions: [
    { id: 'lonestar', name: '孤星', en: 'LONESTAR', color: '#4d9eff', motto: '重火力与破袭，把战火烧进敌人腹地。', role: '攻坚 · 火力', perk: '弹药补给 +20%', strength: 4, defense: 2, players: 27 },
    { id: 'valkyra', name: '女武神', en: 'VALKYRA', color: '#ff5a4d', motto: '筑墙、坚守，把任何阵地变成绞肉机。', role: '防御 · 机动', perk: '掩体建造时间 -25%', strength: 2, defense: 4, players: 24 },
    { id: 'manticore', name: '蝎狮', en: 'MANTICORE', color: '#4ade80', motto: '猎手从不打正面，他们只打要害。', role: '侦察 · 经济', perk: '击杀赏金 +15%', strength: 3, defense: 3, players: 22 }
  ],

  items: [
    { id: 'smg9',  name: 'SMG-9 冲锋枪',  cat: 'weapon', price: 2600, weight: 4.0, tier: 'T1', desc: '近战射速流，腰射稳定。', icon: 'crosshair' },
    { id: 'msbm4', name: 'MSB-M4 突击步枪', cat: 'weapon', price: 3800, weight: 6.5, tier: 'T1', desc: '中程全能，新手首选。', icon: 'crosshair' },
    { id: 'shot12', name: 'SHOT-12 霰弹枪', cat: 'weapon', price: 3200, weight: 5.5, tier: 'T1', desc: '近距一枪制敌，弹容小。', icon: 'crosshair' },
    { id: 'scarh', name: 'SCAR-H 战斗步枪', cat: 'weapon', price: 5200, weight: 7.2, tier: 'T2', desc: '高伤精准，后座偏高。', icon: 'crosshair' },
    { id: 'dmr76', name: 'DMR-76 射手步枪', cat: 'weapon', price: 4600, weight: 6.8, tier: 'T2', desc: '中远距离点名，一击 88。', icon: 'crosshair' },
    { id: 'mg88',  name: 'MG-88 轻机枪',  cat: 'weapon', price: 6800, weight: 12.5, tier: 'T3', desc: '火力压制，负重惩罚大。', icon: 'crosshair' },
    { id: 'vest',    name: '战术背心', cat: 'armor', price: 1400, weight: 3.5, tier: 'T1', desc: '+12 护甲', icon: 'shield' },
    { id: 'helmet',  name: '防弹头盔', cat: 'armor', price: 900,  weight: 1.8, tier: 'T1', desc: '头部伤害 -30%', icon: 'shield' },
    { id: 'composite', name: '复合护甲', cat: 'armor', price: 2600, weight: 5.5, tier: 'T2', desc: '+20 护甲', icon: 'shield' },
    { id: 'nvg',     name: '夜视仪',   cat: 'armor', price: 1800, weight: 1.2, tier: 'T2', desc: '夜间视野增强', icon: 'shield' },
    { id: 'heavyset', name: '重型甲板', cat: 'armor', price: 4200, weight: 8.0, tier: 'T3', desc: '+30 护甲，移速 -8%', icon: 'shield' },
    { id: 'bandage',   name: '绷带',   cat: 'consumable', price: 350,  weight: 0.4, tier: 'T1', desc: '回复 6 点生命', icon: 'heart' },
    { id: 'medkit',    name: '医疗包', cat: 'consumable', price: 900,  weight: 1.0, tier: 'T2', desc: '回复 18 点生命', icon: 'heart' },
    { id: 'ammobox',   name: '弹药盒', cat: 'consumable', price: 500,  weight: 1.5, tier: 'T1', desc: '全武器补弹', icon: 'box' },
    { id: 'frag',      name: '破片手雷', cat: 'consumable', price: 700, weight: 0.8, tier: 'T1', desc: '范围伤害 40', icon: 'bomb' },
    { id: 'smoke',     name: '烟雾弹', cat: 'consumable', price: 450,  weight: 0.6, tier: 'T1', desc: '封锁敌方视线', icon: 'cloud' },
    { id: 'energy',    name: '能量饮料', cat: 'consumable', price: 250, weight: 0.2, tier: 'T1', desc: '移速 +10%，持续 30s', icon: 'zap' }
  ],

  forts: [
    { id: 'sandbag',   name: '沙袋墙',  cost: 800,  hp: 240,  tier: 'I',  desc: '基础掩体，可堆叠成胸墙。', icon: 'wall', unlock: 'FOB I' },
    { id: 'wire',      name: '铁丝网',  cost: 500,  hp: 120,  tier: 'I',  desc: '减速并持续伤害敌人。', icon: 'grid', unlock: 'FOB I' },
    { id: 'ammocrate', name: '弹药箱',  cost: 1200, hp: 300,  tier: 'I',  desc: '己方靠近补给弹药。', icon: 'box', unlock: 'FOB I' },
    { id: 'beacon',    name: '复活信标', cost: 3000, hp: 450,  tier: 'II', desc: '重设本队出生点。', icon: 'flag', unlock: 'FOB II' },
    { id: 'bunker',    name: '碉堡',    cost: 4500, hp: 900,  tier: 'II', desc: '重型掩体 + 射击孔。', icon: 'shield', unlock: 'FOB II' },
    { id: 'radar',     name: '雷达塔',  cost: 2800, hp: 400,  tier: 'II', desc: '小地图侦测敌方红点。', icon: 'tower', unlock: 'FOB II' },
    { id: 'drill',     name: '钻井台',  cost: 8000, hp: 1500, tier: 'III', desc: '把控制区拉向 FOB，需燃料。', icon: 'drill', unlock: 'FOB III' }
  ],

  towers: [
    { id: 'T1', label: '塔楼 · 北', code: '3' },
    { id: 'T2', label: '塔楼 · 东', code: '7' },
    { id: 'T3', label: '塔楼 · 西', code: '1' },
    { id: 'T4', label: '塔楼 · 南', code: '5' }
  ],

  match: {
    target: 100,
    tickSeconds: 30,
    zone: { x: 50, z: 50, radius: 30 },
    hotZone: { x: 64, z: 34, radius: 10 },
    scores: { lonestar: 34, valkyra: 41, manticore: 29 },
    towerDigits: {}, // towerId -> { digit, capturedBy }
    drill: { active: false, fuel: 0 }
  },

  killfeed: [
    { killer: 'Kessler', victim: 'ViperX', weapon: 'SCAR-H' },
    { killer: 'Sh4dow',  victim: 'Rook',   weapon: 'SMG-9' },
    { killer: 'Mako',    victim: 'Ghost',  weapon: 'DMR-76' },
    { killer: 'Tanky',   victim: 'Nova',   weapon: 'SHOT-12' },
    { killer: 'Wraith',  victim: 'Blaze',  weapon: 'MSB-M4' }
  ]
};

/* 初始客户端状态（localStorage 持久化） */
function defaultState() {
  return {
    faction: null,
    cash: 10000,
    teamCash: 12000,
    loadout: [],       // 已购物品 id
    forts: [],         // 已建工事 id
    fobLevel: 1,
    match: DB.match
  };
}
let State = loadState();
function loadState() {
  try { return Object.assign(defaultState(), JSON.parse(localStorage.getItem('msb-state'))); }
  catch (e) { return defaultState(); }
}
function saveState() {
  try { localStorage.setItem('msb-state', JSON.stringify(State)); } catch (e) { /* 隐私模式忽略 */ }
}
function factionOf(id) { return DB.factions.find(f => f.id === id) || null; }
function itemOf(id) { return DB.items.find(i => i.id === id) || null; }
function fortOf(id) { return DB.forts.find(f => f.id === id) || null; }
