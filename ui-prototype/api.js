/* API 桩层：签名与未来真实接口保持一致，延迟模拟网络加载态。
   TODO: 接入 NeoForge 网络后替换为真实请求，保持返回结构不变。
   - joinFaction     C2S: POST /api/faction/join        {faction}     -> {code,data:{faction}}
   - fetchCatalog    GET  /api/loadout/catalog                      -> {code,data:[item]}
   - buyItem         C2S: POST /api/loadout/buy         {itemId}     -> {code,data:{cash, owned}}
   - deployLoadout   C2S: POST /api/loadout/deploy                   -> {code,data:{ok}}
   - fetchMatchState S2C: 周期推送 /api/match/state                  -> {code,data:{scores,zone,hotZone,tick}}
   - placeFort       C2S: POST /api/build/place         {fortId}     -> {code,data:{teamCash, forts}}
   - upgradeFob      C2S: POST /api/build/upgrade                   -> {code,data:{teamCash, fobLevel}}
   - towerInteract   C2S: POST /api/objective/tower     {towerId}    -> {code,data:{digit, sequence}} */

const delay = ms => new Promise(r => setTimeout(r, ms));

const api = {
  async joinFaction(faction) {
    await delay(420);
    return { code: 0, data: { faction } };
  },

  async fetchCatalog() {
    await delay(380);
    return { code: 0, data: DB.items };
  },

  async buyItem(itemId) {
    await delay(620);
    const item = itemOf(itemId);
    if (!item) return { code: 1, msg: '物品不存在' };
    if (State.cash < item.price) return { code: 2, msg: '现金不足' };
    State.cash -= item.price;
    if (!State.loadout.includes(itemId)) State.loadout.push(itemId);
    saveState();
    return { code: 0, data: { cash: State.cash, owned: State.loadout } };
  },

  async deployLoadout() {
    await delay(520);
    return { code: 0, data: { ok: true } };
  },

  async fetchMatchState() {
    await delay(300);
    return { code: 0, data: State.match };
  },

  async placeFort(fortId) {
    await delay(700);
    const fort = fortOf(fortId);
    if (!fort) return { code: 1, msg: '工事不存在' };
    if (State.teamCash < fort.cost) return { code: 2, msg: '队伍金库不足' };
    State.teamCash -= fort.cost;
    State.forts.push(fortId);
    saveState();
    return { code: 0, data: { teamCash: State.teamCash, forts: State.forts } };
  },

  async upgradeFob() {
    await delay(800);
    const cost = State.fobLevel * 4000;
    if (State.teamCash < cost) return { code: 2, msg: '队伍金库不足' };
    State.teamCash -= cost;
    State.fobLevel = Math.min(3, State.fobLevel + 1);
    saveState();
    return { code: 0, data: { teamCash: State.teamCash, fobLevel: State.fobLevel } };
  },

  async towerInteract(towerId) {
    await delay(500);
    const tower = DB.towers.find(t => t.id === towerId);
    if (!tower) return { code: 1, msg: '塔楼不存在' };
    if (State.match.towerDigits[towerId]) return { code: 3, msg: '该塔码已被获取' };
    State.match.towerDigits[towerId] = { digit: tower.code, capturedBy: State.faction || 'lonestar' };
    saveState();
    return { code: 0, data: { digit: tower.code, sequence: collectSequence() } };
  }
};

function collectSequence() {
  return DB.towers.filter(t => State.match.towerDigits[t.id]).map(t => State.match.towerDigits[t.id].digit);
}
