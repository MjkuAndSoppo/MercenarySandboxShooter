/* MercenarySandboxShooter · 交互逻辑（hash 路由 + 各屏状态） */
'use strict';

/* ---------- 图标库（内联 Lucide 风格 SVG，无外部依赖） ---------- */
const ICONS = {
  crosshair: '<circle cx="12" cy="12" r="8"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/><circle cx="12" cy="12" r="1" fill="currentColor" stroke="none"/>',
  shield: '<path d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z"/>',
  heart: '<path d="M12 20s-7-4.6-9.3-9A5.2 5.2 0 0 1 12 6.6 5.2 5.2 0 0 1 21.3 11C19 15.4 12 20 12 20z"/>',
  box: '<path d="M21 8v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8"/><path d="M3 8l9-5 9 5"/><path d="M12 12v8"/>',
  bomb: '<circle cx="11" cy="14" r="6"/><path d="M14.5 4.5 17 7"/><path d="M17 7l3-3"/>',
  cloud: '<path d="M6 16a4 4 0 1 1 1-7.9A5 5 0 0 1 17 9a3.5 3.5 0 0 1 0 7z"/>',
  zap: '<path d="M13 2 4 14h7l-1 8 9-12h-7z"/>',
  wall: '<rect x="4" y="3" width="16" height="18" rx="1"/><path d="M4 9h16M4 15h16M9 3v18M15 3v18"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  flag: '<path d="M5 3v18M5 4h13l-2 4 2 4H5"/>',
  tower: '<path d="M8 3h8M9 3v4a3 3 0 0 1-3 3M15 3v4a3 3 0 0 0 3 3M9 21h6M10 10v5M14 10v5M12 3v18"/>',
  wrench: '<path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.8-3.8a6 6 0 0 1-7.9 7.9l-6.9 6.9a2.1 2.1 0 0 1-3-3l6.9-6.9a6 6 0 0 1 7.9-7.9z"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  coins: '<circle cx="9" cy="9" r="6"/><path d="M15.5 4.2a6 6 0 0 1 0 9.6M4.5 13.8a6 6 0 0 0 0-9.6"/>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  trophy: '<path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0z"/><path d="M7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3"/>'
};
const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">${ICONS[name] || ICONS.grid}</svg>`;

const $ = id => document.getElementById(id);
const money = n => '$' + n.toLocaleString('en-US');

/* ---------- 轻提示 ---------- */
function toast(msg, type) {
  const el = document.createElement('div');
  el.className = 'toast ' + (type || '');
  el.textContent = msg;
  $('toastWrap').appendChild(el);
  setTimeout(() => el.remove(), 3200);
}

/* ---------- 顶栏 ---------- */
function renderTopbar() {
  const f = factionOf(State.faction);
  if (f) {
    $('tbFactionDot').style.background = f.color;
    $('tbFactionName').textContent = f.name + ' · ' + f.en;
    $('tbFactionName').style.color = f.color;
  } else {
    $('tbFactionDot').style.background = 'var(--text-dim)';
    $('tbFactionName').textContent = '未分配阵营';
    $('tbFactionName').style.color = '';
  }
  $('tbCash').textContent = money(State.cash);
  const s = State.match.scores;
  $('tbScore').innerHTML = DB.factions.map(fx =>
    `<span class="seg" style="color:${fx.color}">${fx.en.slice(0,3)}<span class="bar"><i style="width:${pct(s[fx.id])}%; background:${fx.color}"></i></span><b>${s[fx.id]}</b></span>`
  ).join('');
}
const pct = v => Math.min(100, Math.max(0, v));

/* ---------- 路由 ---------- */
const ROUTES = ['overview', 'faction', 'loadout', 'battlefield', 'build'];
let battleTimer = null;

function route() {
  const key = (location.hash.replace('#/', '') || 'overview').split('?')[0];
  const page = ROUTES.includes(key) ? key : 'overview';
  document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
  $('scr-' + page).classList.add('active');
  document.querySelectorAll('.nav-item').forEach(a =>
    a.classList.toggle('active', a.dataset.nav === page));
  if (page === 'battlefield') startBattleSim();
  else stopBattleSim();
  if (page === 'faction') renderFactions();
  if (page === 'loadout') { switchCat('weapon'); renderWeight(); }
  if (page === 'build') renderBuild();
}
window.addEventListener('hashchange', route);

/* ================= 阵营 ================= */
function renderFactions() {
  const g = $('factionGrid');
  g.innerHTML = DB.factions.map(f => `
    <div class="card faction-card rise" data-f="${f.id}" style="${f.id === State.faction ? 'border-color:var(--primary);' : ''}">
      <div class="faction-head">
        <span class="faction-name" style="color:${f.color}">${f.name}</span>
        <span class="faction-en" style="color:${f.color}">${f.en}</span>
      </div>
      <div class="tag" style="border-color:${f.color}55; color:${f.color};">${f.role} · ${f.perk}</div>
      <p class="faction-motto">${f.motto}</p>
      <div class="attr-row"><span class="attr-label">攻击</span><span class="dots">${dots(f.strength)}</span></div>
      <div class="attr-row"><span class="attr-label">防御</span><span class="dots">${dots(f.defense)}</span></div>
      <div class="faction-foot">
        <span class="dim mono">在线 ${f.players}</span>
        <button class="btn btn-ghost btn-sm join-btn" data-f="${f.id}">${State.faction === f.id ? '已加入' : '加入阵营'}</button>
      </div>
    </div>`).join('');
  g.querySelectorAll('.join-btn').forEach(btn => btn.addEventListener('click', async e => {
    e.stopPropagation();
    const id = btn.dataset.f;
    btn.disabled = true; btn.textContent = '加入中…';
    const r = await api.joinFaction(id);
    btn.disabled = false;
    if (r.code !== 0) { toast(r.msg, 'danger'); return; }
    State.faction = id; saveState(); renderTopbar(); renderFactions();
    toast(`已加入「${factionOf(id).name}」阵营`, 'success');
  }));
  g.querySelectorAll('.faction-card').forEach(c => c.addEventListener('click', () => {
    g.querySelectorAll('.faction-card').forEach(x => x.classList.remove('selected'));
    c.classList.add('selected');
  }));
}
function dots(n) {
  let s = '';
  for (let i = 0; i < 4; i++) s += `<i class="${i < n ? 'on' : ''}"></i>`;
  return s;
}

/* ================= 配装 ================= */
let activeCat = 'weapon';
function switchCat(cat) {
  activeCat = cat;
  document.querySelectorAll('#itemTabs .tab').forEach(t => t.classList.toggle('active', t.dataset.cat === cat));
  renderItems();
}
async function renderItems() {
  const r = await api.fetchCatalog();
  const items = r.data.filter(i => i.cat === activeCat);
  const g = $('itemGrid');
  g.innerHTML = items.map(it => {
    const owned = State.loadout.includes(it.id);
    const afford = State.cash >= it.price;
    return `
    <div class="card item-card">
      <div class="item-top">
        <span class="item-name">${it.name}</span>${icon(it.icon)}
      </div>
      <p class="item-desc">${it.desc}</p>
      <div class="item-meta">
        <span class="tag">${it.tier}</span>
        <span class="dim mono">${it.weight.toFixed(1)} kg</span>
        <span class="item-price">${money(it.price)}</span>
      </div>
      <button class="btn btn-sm ${owned ? 'btn-ghost' : 'btn-primary'}" data-id="${it.id}" ${owned ? 'disabled' : ''}>
        ${icon(owned ? 'check' : 'plus')}${owned ? '已购买' : '购买'}
      </button>
    </div>`;
  }).join('');
  g.querySelectorAll('button[data-id]').forEach(btn => btn.addEventListener('click', async () => {
    btn.disabled = true;
    const r = await api.buyItem(btn.dataset.id);
    btn.disabled = false;
    if (r.code !== 0) { toast(r.msg, 'danger'); renderItems(); return; }
    renderTopbar(); renderWeight(); renderItems();
    toast('已购买，装入配装', 'success');
  }));
  renderWeight();
}
function renderWeight() {
  const total = State.loadout.reduce((s, id) => s + (itemOf(id)?.weight || 0), 0);
  const max = 14.0;
  $('weightLbl').textContent = `${total.toFixed(1)} / ${max} kg`;
  const p = Math.min(100, total / max * 100);
  const bar = $('weightBar');
  bar.style.width = p + '%';
  bar.style.background = total > 11 ? 'var(--danger)' : total > 8 ? 'var(--warning)' : 'var(--success)';
}
$('deployBtn').addEventListener('click', async () => {
  if (State.loadout.length === 0) { toast('请先购买至少一件装备', 'danger'); return; }
  $('deployBtn').disabled = true;
  const r = await api.deployLoadout();
  $('deployBtn').disabled = false;
  if (r.code !== 0) { toast(r.msg, 'danger'); return; }
  toast('已部署：携带 ' + State.loadout.length + ' 件装备进入战场', 'success');
  location.hash = '#/battlefield';
});
document.querySelectorAll('#itemTabs .tab').forEach(t => t.addEventListener('click', () => switchCat(t.dataset.cat)));

/* ================= 战场模拟 ================= */
const MAP = 100;
let tickLeft = 30, hotLeft = 45, kfLeft = 0;

function startBattleSim() {
  if (battleTimer) return;
  renderMatch();
  battleTimer = setInterval(() => {
    tickLeft--;
    hotLeft--;
    kfLeft--;
    $('tickTimer').textContent = Math.max(0, tickLeft);
    if (tickLeft <= 0) settleTick();
    if (hotLeft <= 0) moveHotZone();
    if (kfLeft <= 0) pushKillfeed();
    renderTowers();
  }, 1000);
}
function stopBattleSim() {
  if (battleTimer) { clearInterval(battleTimer); battleTimer = null; }
}
function renderMatch() {
  const m = State.match;
  // 控制区圆环
  const zone = m.zone;
  $('zoneRing').style.cssText = `left:${zone.x}%; top:${zone.z}%; width:${zone.radius * 2}%; height:${zone.radius * 2}%; transform:translate(-50%,-50%);`;
  const hot = m.hotZone;
  $('hotRing').style.cssText = `left:${hot.x}%; top:${hot.z}%; width:${hot.radius * 2}%; height:${hot.radius * 2}%; transform:translate(-50%,-50%);`;
  $('hotLabel').textContent = 'HOT ZONE ×2';
  $('hotLabel').style.cssText = `left:${hot.x}%; top:${hot.z - hot.radius - 3}%; transform:translateX(-50%);`;
  renderScore();
  renderTowers();
}
function renderScore() {
  const s = State.match.scores;
  $('scoreRows').innerHTML = DB.factions.map(f => `
    <div class="score-row">
      <span style="color:${f.color}">${f.en.slice(0,3)}</span>
      <div class="progress"><i style="width:${pct(s[f.id])}%; background:${f.color};"></i></div>
      <b style="text-align:right;">${s[f.id]}</b>
    </div>`).join('');
  renderTopbar();
}
function settleTick() {
  const s = State.match.scores;
  const counts = { lonestar: ri(2, 7), valkyra: ri(2, 7), manticore: ri(2, 7) };
  const winner = Object.keys(counts).reduce((a, b) => counts[a] > counts[b] ? a : b);
  const max = counts[winner];
  const tied = Object.values(counts).filter(v => v === max).length > 1;
  tickLeft = 30;
  if (tied) { toast('30s 结算：平局，无人得分'); return; }
  s[winner] = Math.min(100, s[winner] + 1);
  const f = factionOf(winner);
  toast(`30s 结算：${f.name} +1（区内 ${max} 人）`, 'success');
  renderScore();
  if (s[winner] >= 100) {
    stopBattleSim();
    toast(`${f.name} 阵营先到 100 分，本局结束！`, 'success');
  }
}
function moveHotZone() {
  const h = State.match.hotZone;
  h.x = 30 + ri(0, 40);
  h.z = 30 + ri(0, 40);
  hotLeft = 45;
  renderMatch();
  toast('Hot Zone 已迁移：站入者计 2 倍权重', 'warning');
}
function pushKillfeed() {
  kfLeft = 6;
  const k = DB.killfeed[ri(0, DB.killfeed.length - 1)];
  const row = document.createElement('div');
  row.className = 'kf-row';
  row.innerHTML = `<span class="kw">${k.killer}</span><span class="dim">×</span><span class="kv">${k.victim}</span><span class="dim">[${k.weapon}]</span>`;
  const box = $('killfeed');
  box.prepend(row);
  while (box.children.length > 6) box.lastChild.remove();
}
function renderTowers() {
  const seq = collectSequence();
  $('towerGrid').innerHTML = DB.towers.map(t => {
    const got = State.match.towerDigits[t.id];
    return `
    <div class="tower-cell ${got ? 'captured' : ''}" data-t="${t.id}" style="cursor:pointer;">
      <span class="t-name">${t.label}</span>
      <span class="t-code">${got ? got.digit : '?'}</span>
      <button class="btn btn-ghost btn-sm">${got ? '已获取' : '交互终端'}</button>
    </div>`;
  }).join('');
  $('towerGrid').querySelectorAll('.tower-cell[data-t]').forEach(cell => cell.addEventListener('click', async () => {
    const t = cell.dataset.t;
    if (State.match.towerDigits[t]) return;
    const btn = cell.querySelector('button'); btn.disabled = true; btn.textContent = '解密中…';
    const r = await api.towerInteract(t);
    btn.disabled = false;
    if (r.code !== 0) { toast(r.msg, 'danger'); renderTowers(); return; }
    toast(`塔楼码获取：${r.data.digit}`, 'success');
    renderTowers();
    const full = collectSequence();
    if (full.length === 4) toast(`码序完整「${full.join('-')}」：控制区拉向已占领塔楼！`, 'success');
  }));
  const slots = $('seqStrip');
  slots.innerHTML = [0, 1, 2, 3].map(i =>
    `<span class="seq-slot ${seq[i] ? 'filled' : ''}">${seq[i] || '·'}</span>`).join('');
  const m = State.match;
  $('drillState').textContent = m.drill.active ? '运行中' : '未部署（P1 解锁）';
  $('drillFuel').textContent = m.drill.active ? m.drill.fuel + ' / 100' : '--';
}
$('towerInfoBtn').addEventListener('click', () => toast('集齐 4 位码字后，控制区将被拉向己方占领的塔楼；塔楼可被敌方破坏并重建。', 'warning'));

const ri = (a, b) => Math.floor(Math.random() * (b - a + 1)) + a;

/* ================= 建造 ================= */
function renderBuild() {
  const lv = State.fobLevel;
  const maxLv = 3;
  $('fobLevel').textContent = 'Lv ' + ['I', 'II', 'III'][lv - 1];
  $('teamCashLbl').textContent = `队伍金库 ${money(State.teamCash)}`;
  $('upgradeFobBtn').disabled = lv >= maxLv || State.teamCash < lv * 4000;
  $('upgradeFobBtn').textContent = lv >= maxLv ? '已达最高等级' : `升级 FOB（${money(lv * 4000)}）`;
  $('fortGrid').innerHTML = DB.forts.map(f => {
    const unlocked = unlockNum(f.unlock) <= lv;
    const afford = State.teamCash >= f.cost;
    return `
    <div class="card fort-card">
      <div class="fort-icon">${icon(f.icon)}</div>
      <div style="display:flex; align-items:center; gap:8px;">
        <b>${f.name}</b><span class="tag fort-tier">${f.tier}</span>
      </div>
      <p class="item-desc" style="margin:8px 0 10px; color:var(--text-sub); font-size:12px; line-height:1.6;">${f.desc}</p>
      <div class="item-meta" style="display:flex; justify-content:space-between; align-items:center;">
        <span class="item-price">${money(f.cost)}</span>
        <span class="dim mono">耐久 ${f.hp}</span>
      </div>
      <button class="btn btn-sm ${!unlocked ? 'btn-ghost' : 'btn-primary'}" data-id="${f.id}" ${!unlocked ? 'disabled' : ''} style="width:100%; margin-top:12px;">
        ${unlocked ? (afford ? '建造' : '金库不足') : '需 ' + f.unlock}
      </button>
    </div>`;
  }).join('');
  $('fortGrid').querySelectorAll('button[data-id]').forEach(btn => btn.addEventListener('click', async () => {
    btn.disabled = true;
    const r = await api.placeFort(btn.dataset.id);
    btn.disabled = false;
    if (r.code !== 0) { toast(r.msg, 'danger'); renderBuild(); return; }
    toast('工事已放置（演示）', 'success');
    renderBuild();
  }));
  renderBuilt();
}
function renderBuilt() {
  const list = $('builtList');
  if (State.forts.length === 0) { list.innerHTML = '<div class="empty">尚未建造工事。FOB 权限下放置工事将计入队伍防线。</div>'; return; }
  list.innerHTML = State.forts.map((id, i) => {
    const f = fortOf(id);
    return `
    <div class="built-row">
      <span>${icon(f.icon)}</span>
      <b>${f.name}</b>
      <div class="built-hp"><div class="progress"><i style="width:${100 - (i * 7) % 35}%; background:${i % 2 ? 'var(--danger)' : 'var(--success)'};"></i></div></div>
      <span class="dim mono">耐久 ${100 - (i * 7) % 35}%</span>
    </div>`;
  }).join('');
}
function unlockNum(s) { return s.includes('III') ? 3 : s.includes('II') ? 2 : 1; }
$('upgradeFobBtn').addEventListener('click', async () => {
  $('upgradeFobBtn').disabled = true;
  const r = await api.upgradeFob();
  $('upgradeFobBtn').disabled = false;
  if (r.code !== 0) { toast(r.msg, 'danger'); return; }
  toast('FOB 已升级至 Lv ' + ['I', 'II', 'III'][State.fobLevel - 1], 'success');
  renderBuild();
});

/* ---------- 启动 ---------- */
renderTopbar();
route();
