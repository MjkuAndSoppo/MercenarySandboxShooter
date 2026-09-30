/* ============================================================
   app.js — 购买窗口交互与渲染（v2 储存格改版）
   对应实现：ShopScreen（GuiGraphics 自绘，见 .trae/documents/m2-shop-design.md §5）
   布局：分类栏 | 中间栏（商品滚动格 6 列 + 购买/出售面板 + 储存格 5 列可滚动）| 玩家背包映射
   绿框规则：购买入库（储存格）未取出的件数可无损卖回（100%）；
             取出到玩家栏即失去绿框；玩家物品存入储存格不获得绿框。
   ============================================================ */
(() => {
  "use strict";

  // ---------- 小工具 ----------
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const fmtMoney = (n) => (n < 0 ? "-$" + Math.abs(n).toLocaleString("en-US") : "$" + n.toLocaleString("en-US"));
  const fmtKg = (v) => `${Math.round(v * 100) / 100} kg`;

  // Lucide 图标（内联 SVG，stroke 1.5，currentColor；禁止 emoji）
  const ICONS = {
    minus: '<path d="M5 12h14"/>',
    plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
    cart: '<circle cx="8" cy="21" r="1"/><circle cx="19" cy="21" r="1"/><path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12"/>',
    banknote: '<rect width="20" height="12" x="2" y="6" rx="2"/><circle cx="12" cy="12" r="2"/><path d="M6 12h.01M18 12h.01"/>',
    download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>',
    upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>',
    check: '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><path d="m9 11 3 3L22 4"/>',
    warn: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 20h16a2 2 0 0 0 1.73-2Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
    info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>'
  };
  const svg = (paths, size = 14) => `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;

  const ERROR_TEXT = {
    no_balance: "余额不足",
    storage_full: "储存格已满",
    bag_full: "背包已满",
    over_weight: "负重超限",
    not_enough: "数量不足",
    unsellable: "该物品不可收购",
    unknown_item: "商品不存在",
    bad_count: "数量非法"
  };

  // ---------- 状态 ----------
  const S = {
    category: "primary",
    sel: null,                 // {type:'catalog',item} | {type:'storage',idx} | {type:'player',group,idx}
    qty: 1,
    dialog: null,              // {mode:'buy'} | {mode:'sell', sel}
    loading: true,
    closed: false,
    annotate: false,
    demo: "normal",
    balance: 0,
    catalog: [],
    sellRatio: 0.6,
    refundRate: 1.0,
    storageSize: 60,
    storage: [],
    player: { equip: [], acc: [], main: [], hotbar: [] },
    flash: null                // {idx} 新购高亮
  };
  const GROUPS = ["equip", "acc", "main", "hotbar"];

  // ---------- 数据助手 ----------
  const entryOf = (itemId) => S.catalog.find((e) => e.item === itemId) || null;
  const listItems = () => S.catalog.filter((e) => e.category === S.category);
  const sellUnitOf = (entry) => (entry.sell != null ? entry.sell : Math.round(entry.price * S.sellRatio));
  const weightLimit = () => MOCK.player.weightLimit;

  function itemView(itemId) {
    const e = entryOf(itemId);
    if (e) return Object.assign({ sellable: true }, e);
    const m = MOCK.misc[itemId];
    if (m) return { item: itemId, name: m.name, icon: m.icon, tint: m.tint, category: null, price: null, weight: 0, desc: "", sellable: false };
    return { item: itemId, name: itemId, icon: "ingot", tint: null, category: null, price: null, weight: 0, desc: "", sellable: false };
  }

  /** 负重 = 玩家栏（装备/饰品/物品/快捷）内目录物品之和；储存格不计重 */
  function totalWeight() {
    let w = MOCK.player.baseWeight;
    for (const g of GROUPS) {
      for (const s of S.player[g]) {
        if (!s) continue;
        const e = entryOf(s.item);
        if (e) w += e.weight * s.count;
      }
    }
    return w;
  }

  function maxBuyQty(e) {
    const byMoney = Math.floor(S.balance / e.price);
    const byRoom = ShopApi.storageRoom(S.storage, e.item);
    return Math.max(0, Math.min(999, byMoney, byRoom));
  }

  // ---------- 像素图标 ----------
  function makeIcon(iconName, tintKey, cls) {
    const cv = document.createElement("canvas");
    cv.width = 16; cv.height = 16; cv.className = cls || "px";
    const art = ICON_ART[iconName];
    if (!art) return cv;
    const ctx = cv.getContext("2d");
    const tint = tintKey ? ICON_TINTS[iconName + ":" + tintKey] : null;
    const palette = Object.assign({}, art.palette, tint || {});
    art.rows.forEach((row, y) => {
      for (let x = 0; x < Math.min(16, row.length); x++) {
        const ch = row[x];
        if (ch === "." || !palette[ch]) continue;
        ctx.fillStyle = palette[ch];
        ctx.fillRect(x, y, 1, 1);
      }
    });
    return cv;
  }

  // ---------- 选中态 ----------
  const selKey = (sel) => {
    if (!sel) return "";
    if (sel.type === "catalog") return "catalog:" + sel.item;
    if (sel.type === "storage") return "storage:" + sel.idx;
    return `player:${sel.group}:${sel.idx}`;
  };

  function select(sel) {
    S.sel = sel;
    S.qty = 1;
    updateSelClasses();
    renderPanel();
  }

  function updateSelClasses() {
    const key = selKey(S.sel);
    $$("[data-key]").forEach((el) => el.classList.toggle("selected", el.dataset.key === key));
  }

  function validateSel() {
    if (!S.sel) return;
    if (S.sel.type === "catalog" && !entryOf(S.sel.item)) S.sel = null;
    if (S.sel.type === "storage" && !S.storage[S.sel.idx]) S.sel = null;
    if (S.sel.type === "player" && !(S.player[S.sel.group] && S.player[S.sel.group][S.sel.idx])) S.sel = null;
  }

  // ---------- 渲染 ----------
  function render() {
    document.body.classList.toggle("closed", S.closed);
    document.body.classList.toggle("annotate", S.annotate);
    $("#toggleWinBtn").textContent = S.closed ? "打开窗口 (B)" : "关闭窗口 (B)";
    $("#balanceText").textContent = fmtMoney(S.balance);

    // 重建前记录滚动位置（滚动格 / 储存格）
    const catScroll = $("#catView") ? $("#catView").scrollTop : 0;
    const stoScroll = $("#stoView") ? $("#stoView").scrollTop : 0;

    validateSel();
    renderCats();
    renderCatalog();
    renderStorage();
    renderPlayer();
    renderPanel();
    renderFooter();

    if ($("#catView")) $("#catView").scrollTop = catScroll;
    if ($("#stoView")) $("#stoView").scrollTop = stoScroll;
  }

  function renderCats() {
    const col = $("#catCol");
    col.innerHTML = "";
    if (S.loading) { col.innerHTML = '<div class="p-placeholder">载入中…</div>'; return; }
    MOCK.categoryOrder.forEach((cat) => {
      const count = S.catalog.filter((e) => e.category === cat).length;
      const btn = document.createElement("button");
      btn.className = "cat" + (S.category === cat ? " active" : "");
      btn.innerHTML = `<span>${MOCK.categoryNames[cat]}</span><span class="cnt">${count}</span>`;
      btn.addEventListener("click", () => { S.category = cat; S.sel = null; S.qty = 1; render(); });
      col.appendChild(btn);
    });
  }

  function renderCatalog() {
    const grid = $("#catGrid");
    const head = $("#marketHead");
    grid.innerHTML = "";
    if (S.loading) {
      head.textContent = "载入目录";
      for (let i = 0; i < 12; i++) {
        const sk = document.createElement("div");
        sk.className = "cell sk";
        grid.appendChild(sk);
      }
      return;
    }
    const items = listItems();
    head.textContent = `${MOCK.categoryNames[S.category]} · ${items.length} 件`;
    items.forEach((e) => {
      const cell = document.createElement("div");
      cell.className = "cell";
      cell.dataset.key = "catalog:" + e.item;
      if (S.sel && S.sel.type === "catalog" && S.sel.item === e.item) cell.classList.add("selected");
      cell.appendChild(makeIcon(e.icon, e.tint));
      const price = document.createElement("div");
      price.className = "price";
      price.textContent = fmtMoney(e.price);
      cell.appendChild(price);
      cell.addEventListener("click", () => select({ type: "catalog", item: e.item }));
      cell.addEventListener("dblclick", () => { select({ type: "catalog", item: e.item }); openDialog("buy"); });
      attachTooltip(cell, e, null);
      grid.appendChild(cell);
    });
  }

  function renderStorage() {
    const grid = $("#stoGrid");
    grid.innerHTML = "";
    const used = S.storage.filter(Boolean).length;
    $("#stoCount").textContent = `${used}/${S.storageSize}`;
    S.storage.forEach((stack, idx) => {
      const div = document.createElement("div");
      div.className = "slot";
      div.dataset.key = "storage:" + idx;
      if (stack) {
        const info = itemView(stack.item);
        div.appendChild(makeIcon(info.icon, info.tint));
        if (stack.count > 1) {
          const c = document.createElement("span");
          c.className = "cnt";
          c.textContent = stack.count;
          div.appendChild(c);
        }
        if (stack.refund > 0) div.classList.add("refund");
        if (S.flash && S.flash.idx === idx) div.classList.add("flash");
        if (S.sel && S.sel.type === "storage" && S.sel.idx === idx) div.classList.add("selected");
        div.addEventListener("click", () => select({ type: "storage", idx }));
        attachTooltip(div, info, stack.count, stack.refund);
      }
      grid.appendChild(div);
    });
  }

  function renderPlayer() {
    const groups = [
      { key: "equip", gridId: "equipGrid", labels: MOCK.equipSlots },
      { key: "acc", gridId: "accGrid", labels: null, p1: true },
      { key: "main", gridId: "mainGrid", labels: null },
      { key: "hotbar", gridId: "hotbarGrid", labels: null }
    ];
    for (const def of groups) {
      const grid = $("#" + def.gridId);
      grid.innerHTML = "";
      S.player[def.key].forEach((stack, idx) => {
        const div = document.createElement("div");
        div.className = "slot";
        div.dataset.key = `player:${def.key}:${idx}`;
        if (def.p1) {
          // 饰品栏：P1（Curios 接入后开放）—— 占位展示，不可交互
          div.classList.add("p1");
          grid.appendChild(div);
          return;
        }
        if (stack) {
          const info = itemView(stack.item);
          div.appendChild(makeIcon(info.icon, info.tint));
          if (stack.count > 1) {
            const c = document.createElement("span");
            c.className = "cnt";
            c.textContent = stack.count;
            div.appendChild(c);
          }
          div.addEventListener("click", () => select({ type: "player", group: def.key, idx }));
          attachTooltip(div, info, stack.count);
        } else if (def.labels) {
          const lbl = document.createElement("span");
          lbl.className = "slot-lbl";
          lbl.textContent = def.labels[idx] || "";
          div.appendChild(lbl);
        }
        if (S.sel && S.sel.type === "player" && S.sel.group === def.key && S.sel.idx === idx) div.classList.add("selected");
        grid.appendChild(div);
      });
    }
    $("#mainCount").textContent = `${S.player.main.filter(Boolean).length}/27`;
  }

  function renderFooter() {
    const w = totalWeight();
    const ratio = w / weightLimit();
    const wrap = $("#ftWeight");
    wrap.classList.toggle("warn", ratio >= MOCK.player.weightWarnRatio && ratio < 1);
    wrap.classList.toggle("over", ratio >= 1);
    $("#weightText").textContent = `${w.toFixed(1)} / ${weightLimit().toFixed(1)} kg`;
    $("#weightBar").style.width = Math.min(100, ratio * 100) + "%";
  }

  // ---------- 面板（中间栏左下半） ----------
  function renderPanel() {
    const col = $("#panelCol");
    col.innerHTML = "";
    if (S.loading) { col.innerHTML = '<div class="p-placeholder">载入目录中…</div>'; return; }
    if (!S.sel) { col.innerHTML = '<div class="p-placeholder">点击商品 / 储存格<br>或背包物品</div>'; return; }
    if (S.sel.type === "catalog") buildBuyPanel(col);
    else if (S.sel.type === "storage") buildStoragePanel(col);
    else buildPlayerPanel(col);
  }

  function buildQtyRow(getMax) {
    const row = document.createElement("div");
    row.className = "q-row";
    row.innerHTML = `<button class="step" data-step="-1">${svg(ICONS.minus, 12)}</button>
      <span class="qty-val">${S.qty}</span>
      <button class="step" data-step="1">${svg(ICONS.plus, 12)}</button>
      <button class="step-max">MAX</button>`;
    const set = (v) => {
      S.qty = Math.max(1, Math.min(v | 0, Math.max(1, getMax()), 999));
      renderPanel();
    };
    $('[data-step="-1"]', row).addEventListener("click", () => set(S.qty - 1));
    $('[data-step="1"]', row).addEventListener("click", () => set(S.qty + 1));
    $(".step-max", row).addEventListener("click", () => set(getMax()));
    return row;
  }

  function buildHead(icon, tint, name, subHtml) {
    const head = document.createElement("div");
    head.className = "p-head";
    head.appendChild(makeIcon(icon, tint));
    head.insertAdjacentHTML("beforeend", `<div><div class="p-name">${name}</div><div class="p-sub">${subHtml}</div></div>`);
    return head;
  }

  function buildBuyPanel(col) {
    const e = entryOf(S.sel.item);
    if (!e) { S.sel = null; renderPanel(); return; }
    const max = maxBuyQty(e);
    S.qty = Math.max(1, Math.min(S.qty, Math.max(1, max), 999));

    col.appendChild(buildHead(e.icon, e.tint, e.name, `${MOCK.categoryNames[e.category]} · 单价 ${fmtMoney(e.price)}`));
    col.appendChild(buildQtyRow(() => maxBuyQty(e)));
    col.insertAdjacentHTML("beforeend",
      `<div class="p-line"><span>合计（${S.qty} 件）</span><b class="gold">${fmtMoney(e.price * S.qty)}</b></div>
       <div class="p-line"><span>负重</span><b class="dim">${fmtKg(e.weight)} / 件 · 存入储存格不计重</b></div>`);

    const btn = document.createElement("button");
    btn.className = "btn-primary";
    btn.disabled = max < 1;
    btn.innerHTML = `${svg(ICONS.cart, 13)} 购买`;
    btn.addEventListener("click", () => openDialog("buy"));
    const wrap = document.createElement("div");
    wrap.className = "p-btns";
    wrap.appendChild(btn);
    col.appendChild(wrap);

    const note = document.createElement("div");
    note.className = "p-note";
    note.textContent = "购买后存入储存格：绿框 = 可无损卖回（100%），取出后失效";
    col.appendChild(note);

    const reason = document.createElement("div");
    reason.className = "p-reason";
    if (max < 1) {
      reason.textContent = S.balance < e.price
        ? `余额不足（还差 ${fmtMoney(e.price - S.balance)}）`
        : "储存格已满，请先整理";
    } else {
      reason.textContent = "";
    }
    col.appendChild(reason);
  }

  function buildStoragePanel(col) {
    const stack = S.storage[S.sel.idx];
    if (!stack) { S.sel = null; renderPanel(); return; }
    const info = itemView(stack.item);
    const entry = entryOf(stack.item);
    const max = Math.max(1, stack.count);
    S.qty = Math.max(1, Math.min(S.qty, max, 999));

    const refundPart = entry ? Math.min(S.qty, stack.refund) : 0;
    const unit = entry ? sellUnitOf(entry) : 0;
    const full = entry ? Math.round(entry.price * S.refundRate) : 0;
    const earn = refundPart * full + (S.qty - refundPart) * unit;

    const badge = stack.refund > 0 ? `<span class="badge">无损 ×${stack.refund}</span>` : "";
    col.appendChild(buildHead(info.icon, info.tint,
      info.name + badge,
      `持有 ${stack.count} 件${entry ? ` · 六折 ${fmtMoney(unit)}` : " · 不可收购"}`));
    col.appendChild(buildQtyRow(() => max));
    col.insertAdjacentHTML("beforeend",
      `<div class="p-line"><span>收入（无损 ${refundPart} / 六折 ${S.qty - refundPart}）</span><b class="earn">+${fmtMoney(earn)}</b></div>`);

    const takeReason = takeBlockReason(stack, S.qty);
    const take = document.createElement("button");
    take.className = "btn-ghost";
    take.disabled = !!takeReason;
    take.innerHTML = `${svg(ICONS.download, 13)} 取回背包`;
    take.addEventListener("click", doTake);
    const sell = document.createElement("button");
    sell.className = "btn-primary";
    sell.disabled = !entry;
    sell.innerHTML = `${svg(ICONS.banknote, 13)} 卖出`;
    sell.addEventListener("click", () => openDialog("sell"));
    const wrap = document.createElement("div");
    wrap.className = "p-btns";
    wrap.append(take, sell);
    col.appendChild(wrap);

    const note = document.createElement("div");
    note.className = "p-note";
    note.textContent = entry
      ? "卖出优先无损卖回；取回背包后绿框立即失效"
      : "该物品不在收购目录内，仅可取回背包";
    col.appendChild(note);

    const reason = document.createElement("div");
    reason.className = "p-reason";
    reason.textContent = !entry ? "不可收购" : takeReason;
    col.appendChild(reason);
  }

  function buildPlayerPanel(col) {
    const stack = S.player[S.sel.group][S.sel.idx];
    if (!stack) { S.sel = null; renderPanel(); return; }
    const info = itemView(stack.item);
    const entry = entryOf(stack.item);
    const max = Math.max(1, stack.count);
    S.qty = Math.max(1, Math.min(S.qty, max, 999));

    const unit = entry ? sellUnitOf(entry) : 0;
    const earn = S.qty * unit;

    col.appendChild(buildHead(info.icon, info.tint, info.name,
      `持有 ${stack.count} 件${entry ? ` · 六折 ${fmtMoney(unit)}` : " · 不可收购"}`));
    col.appendChild(buildQtyRow(() => max));
    col.insertAdjacentHTML("beforeend",
      `<div class="p-line"><span>收入（六折 ${S.qty} 件）</span><b class="earn">+${fmtMoney(earn)}</b></div>`);

    const storeReason = ShopApi.storageRoom(S.storage, stack.item) < S.qty ? "储存格已满" : "";
    const store = document.createElement("button");
    store.className = "btn-ghost";
    store.disabled = !!storeReason;
    store.innerHTML = `${svg(ICONS.upload, 13)} 存入储存格`;
    store.addEventListener("click", doStore);
    const sell = document.createElement("button");
    sell.className = "btn-primary";
    sell.disabled = !entry;
    sell.innerHTML = `${svg(ICONS.banknote, 13)} 卖出`;
    sell.addEventListener("click", () => openDialog("sell"));
    const wrap = document.createElement("div");
    wrap.className = "p-btns";
    wrap.append(store, sell);
    col.appendChild(wrap);

    const note = document.createElement("div");
    note.className = "p-note";
    note.textContent = "存入储存格不获得绿框；卖出按六折（60%）收购价";
    col.appendChild(note);

    const reason = document.createElement("div");
    reason.className = "p-reason";
    reason.textContent = !entry ? "不可收购" : storeReason;
    col.appendChild(reason);
  }

  /** 取回（储存格 → 玩家栏）的预检原因；返回空串表示可用 */
  function takeBlockReason(stack, qty) {
    const e = entryOf(stack.item);
    if (e && totalWeight() + e.weight * qty > weightLimit()) return `负重超限（上限 ${weightLimit()} kg）`;
    if (ShopApi.playerRoom(S.player, stack.item) < qty) return "背包已满";
    return "";
  }

  // ---------- 弹窗 ----------
  function openDialog(mode) {
    if (mode === "buy") {
      if (!(S.sel && S.sel.type === "catalog")) return;
      S.dialog = { mode: "buy" };
    } else {
      if (!S.sel || S.sel.type === "catalog") return;
      S.dialog = { mode: "sell", sel: Object.assign({}, S.sel) };
    }
    renderDialog();
    $("#dialogLayer").classList.add("open");
  }

  function closeDialog() {
    S.dialog = null;
    const layer = $("#dialogLayer");
    layer.classList.remove("open");
    layer.innerHTML = "";
  }

  function renderDialog() {
    const layer = $("#dialogLayer");
    if (!S.dialog) { layer.innerHTML = ""; return; }
    let iconInfo;
    let body;

    if (S.dialog.mode === "buy") {
      const e = entryOf(S.sel.item);
      const count = S.qty;
      const cost = e.price * count;
      iconInfo = e;
      body = `
        <div class="dlg-title">确认购买</div>
        <div class="dlg-item"><span class="dlg-icon" data-icon="dlg"></span>
          <span><span class="dlg-name">${e.name}</span><span class="dlg-sub" style="display:block">单价 ${fmtMoney(e.price)} × ${count}</span></span>
        </div>
        <div class="dlg-rows">
          <div class="line"><span>合计</span><b class="gold">${fmtMoney(-cost)}</b></div>
          <div class="line"><span>余额</span><b>${fmtMoney(S.balance)} → ${fmtMoney(S.balance - cost)}</b></div>
          <div class="line"><span>去向</span><b>存入储存格（负重不变）</b></div>
          <div class="line"><span>绿框</span><b>${count} 件可无损卖回</b></div>
        </div>
        <div class="dlg-btns">
          <button class="btn-ghost" data-act="cancel">取消</button>
          <button class="btn-primary" data-act="confirm">确认购买</button>
        </div>`;
    } else {
      const sel = S.dialog.sel;
      const stack = sel.type === "storage" ? S.storage[sel.idx] : S.player[sel.group][sel.idx];
      const info = itemView(stack.item);
      const entry = entryOf(stack.item);
      const count = S.qty;
      const unit = entry ? sellUnitOf(entry) : 0;
      let earn = 0;
      let extra = "";
      let weightLine = `负重 ${fmtKg(totalWeight())}（不变）`;
      if (sel.type === "storage") {
        const refundPart = entry ? Math.min(count, stack.refund) : 0;
        const full = entry ? Math.round(entry.price * S.refundRate) : 0;
        earn = refundPart * full + (count - refundPart) * unit;
        extra = refundPart > 0 ? `无损 ${refundPart} 件 · 六折 ${count - refundPart} 件` : `六折 ${count} 件`;
      } else {
        earn = count * unit;
        extra = `六折 ${count} 件`;
        if (info.weight) weightLine = `负重 ${fmtKg(totalWeight())} → ${fmtKg(totalWeight() - info.weight * count)}`;
      }
      iconInfo = info;
      body = `
        <div class="dlg-title">确认出售</div>
        <div class="dlg-item"><span class="dlg-icon" data-icon="dlg"></span>
          <span><span class="dlg-name">${info.name}</span><span class="dlg-sub" style="display:block">${extra}</span></span>
        </div>
        <div class="dlg-rows">
          <div class="line"><span>收入</span><b class="earn">+${fmtMoney(earn)}</b></div>
          <div class="line"><span>余额</span><b>${fmtMoney(S.balance)} → ${fmtMoney(S.balance + earn)}</b></div>
          <div class="line"><span>负重</span><b>${weightLine}</b></div>
        </div>
        <div class="dlg-btns">
          <button class="btn-ghost" data-act="cancel">取消</button>
          <button class="btn-primary" data-act="confirm">确认出售</button>
        </div>`;
    }

    layer.innerHTML = `<div class="dialog-mask"></div><div class="dialog">${body}</div>`;
    $('[data-icon="dlg"]', layer).appendChild(makeIcon(iconInfo.icon, iconInfo.tint));
    $('[data-act="cancel"]', layer).addEventListener("click", closeDialog);
    $('[data-act="confirm"]', layer).addEventListener("click", confirmDialog);
  }

  async function confirmDialog() {
    if (!S.dialog) return;
    const mode = S.dialog.mode;
    const dialogSel = S.dialog.sel;
    const count = S.qty;
    const btn = $('#dialogLayer [data-act="confirm"]');
    btn.disabled = true;
    btn.textContent = "处理中…";

    if (mode === "buy") {
      const e = entryOf(S.sel && S.sel.item);
      if (!e) { closeDialog(); render(); return; }
      const res = await ShopApi.buy(e.item, count, {
        balance: S.balance, catalog: S.catalog, storage: S.storage, storageSize: S.storageSize
      });
      closeDialog();
      if (res.ok) {
        S.balance += res.moneyDelta;
        toast("success", `已购买 ${e.name} ×${count} · ${fmtMoney(res.moneyDelta)}（已存入储存格）`);
        flashStorage(e.item);
      } else {
        toast("danger", `购买失败：${ERROR_TEXT[res.code] || res.code}`);
      }
    } else if (dialogSel.type === "storage") {
      const stack = S.storage[dialogSel.idx];
      if (!stack) { closeDialog(); render(); return; }
      const info = itemView(stack.item);
      const res = await ShopApi.sellFromStorage(dialogSel.idx, count, {
        balance: S.balance, catalog: S.catalog, sellRatio: S.sellRatio, refundRate: S.refundRate, storage: S.storage
      });
      closeDialog();
      if (res.ok) {
        S.balance += res.moneyDelta;
        const suffix = res.refunded > 0 ? `（无损 ${res.refunded} 件）` : "";
        toast("success", `已出售 ${info.name} ×${count} · +${fmtMoney(res.earn)}${suffix}`);
      } else {
        toast("danger", `出售失败：${ERROR_TEXT[res.code] || res.code}`);
      }
    } else {
      const { group, idx } = dialogSel;
      const stack = S.player[group] && S.player[group][idx];
      if (!stack) { closeDialog(); render(); return; }
      const info = itemView(stack.item);
      const res = await ShopApi.sellFromPlayer(group, idx, count, {
        balance: S.balance, catalog: S.catalog, sellRatio: S.sellRatio, player: S.player
      });
      closeDialog();
      if (res.ok) {
        S.balance += res.moneyDelta;
        toast("success", `已出售 ${info.name} ×${count} · +${fmtMoney(res.earn)}`);
      } else {
        toast("danger", `出售失败：${ERROR_TEXT[res.code] || res.code}`);
      }
    }
    validateSel();
    render();
  }

  // ---------- 存取动作 ----------
  async function doTake() {
    if (!(S.sel && S.sel.type === "storage")) return;
    const idx = S.sel.idx;
    const stack = S.storage[idx];
    if (!stack) return;
    const info = itemView(stack.item);
    const count = S.qty;
    const res = await ShopApi.takeFromStorage(idx, count, {
      storage: S.storage, player: S.player, catalog: S.catalog, weight: totalWeight(), weightLimit: weightLimit()
    });
    if (res.ok) toast("success", `已取回 ${info.name} ×${count}（绿框已失效）`);
    else toast("danger", `取回失败：${ERROR_TEXT[res.code] || res.code}`);
    validateSel();
    render();
  }

  async function doStore() {
    if (!(S.sel && S.sel.type === "player")) return;
    const { group, idx } = S.sel;
    const stack = S.player[group][idx];
    if (!stack) return;
    const info = itemView(stack.item);
    const count = S.qty;
    const res = await ShopApi.storeFromPlayer(group, idx, count, { storage: S.storage, player: S.player });
    if (res.ok) toast("success", `已存入 ${info.name} ×${count}`);
    else toast("danger", `存入失败：${ERROR_TEXT[res.code] || res.code}`);
    validateSel();
    render();
  }

  /** 购买成功后高亮对应储存格（绿框脉冲） */
  function flashStorage(itemId) {
    const idx = S.storage.findIndex((s) => s && s.item === itemId && s.refund > 0);
    if (idx < 0) return;
    S.flash = { idx };
    setTimeout(() => { S.flash = null; render(); }, 1600);
  }

  // ---------- Toast / Tooltip ----------
  function toast(type, text) {
    const wrap = $("#toastWrap");
    const el = document.createElement("div");
    el.className = "toast " + type;
    const icon = type === "success" ? ICONS.check : type === "danger" ? ICONS.warn : ICONS.info;
    el.innerHTML = svg(icon, 14) + `<span>${text}</span>`;
    wrap.appendChild(el);
    while (wrap.children.length > 3) wrap.removeChild(wrap.firstChild);
    setTimeout(() => el.remove(), 2600);
  }

  let ttTimer = null;
  function attachTooltip(node, info, count, refund) {
    node.addEventListener("mouseenter", () => {
      clearTimeout(ttTimer);
      ttTimer = setTimeout(() => showTooltip(info, count, refund), 160);
    });
    node.addEventListener("mouseleave", hideTooltip);
  }

  function showTooltip(info, count, refund) {
    const tt = $("#tooltip");
    const lines = [`<div class="tt-name">${info.name}</div>`];
    if (info.category) lines.push(`<div class="tt-line dim">${MOCK.categoryNames[info.category]}</div>`);
    if (count != null && count > 1) lines.push(`<div class="tt-line dim">数量 ${count}</div>`);
    if (info.sellable === false) {
      lines.push('<div class="tt-line danger">不可收购</div>');
    } else if (info.price != null) {
      lines.push(`<div class="tt-line gold">单价 ${fmtMoney(info.price)}</div>`);
      lines.push(`<div class="tt-line dim">负重 ${fmtKg(info.weight)} · 六折 ${fmtMoney(sellUnitOf(info))}</div>`);
      if (refund > 0) lines.push(`<div class="tt-line ok">绿框：${refund} 件可无损卖回（100%）</div>`);
      else lines.push(`<div class="tt-line dim">无绿框（按六折卖回）</div>`);
    }
    tt.innerHTML = lines.join("");
    tt.style.display = "block";
  }

  function hideTooltip() {
    clearTimeout(ttTimer);
    $("#tooltip").style.display = "none";
  }

  // ---------- 演示模式 ----------
  const clonePlayer = () => ({
    equip: MOCK.inventory.equip.map((s) => (s ? Object.assign({}, s) : null)),
    acc: MOCK.inventory.acc.map((s) => (s ? Object.assign({}, s) : null)),
    main: MOCK.inventory.main.map((s) => (s ? Object.assign({}, s) : null)),
    hotbar: MOCK.inventory.hotbar.map((s) => (s ? Object.assign({}, s) : null))
  });
  const cloneStorage = () => MOCK.storage.map((s) => (s ? Object.assign({}, s) : null));

  function applyDemo(mode, opts = {}) {
    S.demo = mode;
    S.player = clonePlayer();
    S.storage = cloneStorage();
    S.balance = mode === "poor" ? MOCK.balances.poor : MOCK.balances.normal;
    if (mode === "emptyBag") {
      for (const g of GROUPS) S.player[g] = S.player[g].map(() => null);
      S.storage = new Array(MOCK.storageSize).fill(null);
    }
    S.sel = null;
    S.qty = 1;
    S.flash = null;
    closeDialog();
    $$(".proto-bar [data-demo]").forEach((b) => b.classList.toggle("active", b.dataset.demo === mode));
    if (!opts.silent) {
      const label = { normal: "默认", poor: "余额不足（$420）", emptyBag: "清空库存" }[mode] || mode;
      toast("info", `演示模式：${label}`);
    }
  }

  // ---------- 事件接线 ----------
  function wire() {
    $$(".proto-bar [data-demo]").forEach((b) => b.addEventListener("click", () => { applyDemo(b.dataset.demo); render(); }));
    $("#annotateBtn").addEventListener("click", (e) => {
      S.annotate = !S.annotate;
      e.currentTarget.classList.toggle("active", S.annotate);
      render();
    });
    $("#toggleWinBtn").addEventListener("click", () => { S.closed = !S.closed; if (S.closed) closeDialog(); render(); });
    $("#reopenBtn").addEventListener("click", () => { S.closed = false; render(); });

    document.addEventListener("mousemove", (e) => {
      const tt = $("#tooltip");
      if (tt.style.display !== "block") return;
      const pad = 14;
      const rect = tt.getBoundingClientRect();
      let x = e.clientX + pad;
      let y = e.clientY + pad;
      if (x + rect.width > window.innerWidth - 8) x = e.clientX - rect.width - pad;
      if (y + rect.height > window.innerHeight - 8) y = e.clientY - rect.height - pad;
      tt.style.left = x + "px";
      tt.style.top = y + "px";
    });

    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        if (S.dialog) closeDialog();
        else { S.closed = true; render(); }
      } else if (e.key === "b" || e.key === "B") {
        S.closed = !S.closed;
        if (S.closed) closeDialog();
        render();
      } else if (e.key === "Enter" && S.dialog) {
        confirmDialog();
      }
    });
  }

  // ---------- 启动 ----------
  async function init() {
    wire();
    applyDemo("normal", { silent: true });
    render();                                   // 载入骨架
    const res = await ShopApi.openShop();       // 模拟 S2C ShopDataPayload
    S.catalog = res.catalog;
    S.sellRatio = res.sellRatio;
    S.refundRate = res.refundRate;
    S.storageSize = res.storageSize;
    S.loading = false;
    S.sel = { type: "catalog", item: (S.catalog.find((e) => e.item === "superbwarfare:hk_416") || S.catalog[0]).item };
    render();
  }

  init();
})();