/* ============================================================
   api.js — 服务端 API 桩（形态即未来真实协议，见 m2-shop-design.md §3/§4）
   所有校验在「服务端」执行；此桩复刻同一套校验规则，客户端预检只为 UI 禁用态。
   TODO: 替换为真实网络调用 ——
     openShop        → S2C ShopDataPayload（join / 目录重载时推送）
     buy / sell      → C2S ShopTradePayload{action, itemId, count} → S2C ShopResultPayload
     take / store    → C2S ShopTradePayload{action: TAKE / STORE, ...}（同通道，服务端校验）
   绿框规则（refund 资格）：仅「购买后未取出」的件数可无损卖回（100%）；
     取出到玩家栏即失去资格；玩家物品存入储存格不获得资格。
   ============================================================ */
const ShopApi = (() => {
  const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  /** 失败码（与服务端 ShopResultPayload.code 对齐，客户端本地化为语言键） */
  const CODE = {
    OK: "ok",
    NO_BALANCE: "no_balance",
    STORAGE_FULL: "storage_full",
    BAG_FULL: "bag_full",
    OVER_WEIGHT: "over_weight",
    NOT_ENOUGH: "not_enough",
    UNSELLABLE: "unsellable",
    UNKNOWN_ITEM: "unknown_item",
    BAD_COUNT: "bad_count"
  };

  const findEntry = (catalog, itemId) => catalog.find((e) => e.item === itemId) || null;
  const sellUnit = (entry, ratio) => (entry.sell != null ? entry.sell : Math.round(entry.price * ratio));

  /** 储存格剩余可容纳件数（同物品可堆叠 + 空格 ×64） */
  function storageRoom(storage, itemId) {
    let room = 0;
    for (const s of storage) {
      if (s && s.item === itemId && s.count < 64) room += 64 - s.count;
    }
    for (const s of storage) if (!s) room += 64;
    return room;
  }

  /** 放入储存格（refund = 新增的无损卖回件数）；返回未放下的数量 */
  function addToStorage(storage, itemId, count, refund) {
    let rest = count;
    let refundLeft = refund;
    for (const s of storage) {
      if (rest <= 0) break;
      if (s && s.item === itemId && s.count < 64) {
        const put = Math.min(64 - s.count, rest);
        s.count += put;
        const credit = Math.min(put, refundLeft);
        s.refund += credit;
        refundLeft -= credit;
        rest -= put;
      }
    }
    for (let i = 0; i < storage.length && rest > 0; i++) {
      if (!storage[i]) {
        const put = Math.min(64, rest);
        const credit = Math.min(put, refundLeft);
        storage[i] = { item: itemId, count: put, refund: credit };
        refundLeft -= credit;
        rest -= put;
      }
    }
    return rest;
  }

  /** 玩家背包（物品栏 + 快捷栏）剩余可容纳件数 */
  function playerRoom(player, itemId) {
    let room = 0;
    for (const g of ["main", "hotbar"]) {
      for (const s of player[g]) {
        if (s && s.item === itemId && s.count < 64) room += 64 - s.count;
      }
    }
    for (const g of ["main", "hotbar"]) {
      for (const s of player[g]) if (!s) room += 64;
    }
    return room;
  }

  /** 放入玩家背包（物品栏优先，其次快捷栏）；返回未放下的数量 */
  function addToPlayer(player, itemId, count) {
    let rest = count;
    for (const g of ["main", "hotbar"]) {
      for (const s of player[g]) {
        if (rest <= 0) break;
        if (s && s.item === itemId && s.count < 64) {
          const put = Math.min(64 - s.count, rest);
          s.count += put;
          rest -= put;
        }
      }
    }
    for (const g of ["main", "hotbar"]) {
      for (let i = 0; i < player[g].length && rest > 0; i++) {
        if (!player[g][i]) {
          const put = Math.min(64, rest);
          player[g][i] = { item: itemId, count: put };
          rest -= put;
        }
      }
    }
    return rest;
  }

  return {
    CODE,
    storageRoom,
    playerRoom,
    sellUnit,

    /** 打开商店：模拟 S2C 目录下发（join 时已推送，此处仅演示载入态） */
    async openShop() {
      await delay(600);
      return { ok: true, catalog: MOCK.catalog, sellRatio: MOCK.sellRatio, refundRate: MOCK.refundRate, storageSize: MOCK.storageSize };
    },

    /**
     * 购买（服务端权威）：条目存在 → 数量 → 余额 → 储存格空间；通过后扣款、存入储存格（带绿框资格）
     * ctx: { balance, catalog, storage, storageSize }
     */
    async buy(itemId, count, ctx) {
      await delay(420);
      const entry = findEntry(ctx.catalog, itemId);
      if (!entry) return { ok: false, code: CODE.UNKNOWN_ITEM };
      if (!Number.isInteger(count) || count < 1) return { ok: false, code: CODE.BAD_COUNT };
      const cost = entry.price * count;
      if (ctx.balance < cost) return { ok: false, code: CODE.NO_BALANCE, cost };
      if (storageRoom(ctx.storage, itemId) < count) return { ok: false, code: CODE.STORAGE_FULL, cost };
      addToStorage(ctx.storage, itemId, count, count);
      return { ok: true, code: CODE.OK, cost, moneyDelta: -cost, refunded: count };
    },

    /**
     * 出售（储存格）：优先无损卖回已购未取出的件数（100%），其余按 sellRatio
     * ctx: { balance, catalog, sellRatio, refundRate, storage }
     */
    async sellFromStorage(idx, count, ctx) {
      await delay(420);
      const stack = ctx.storage[idx];
      if (!stack) return { ok: false, code: CODE.NOT_ENOUGH };
      const entry = findEntry(ctx.catalog, stack.item);
      if (!entry) return { ok: false, code: CODE.UNSELLABLE };
      if (!Number.isInteger(count) || count < 1) return { ok: false, code: CODE.BAD_COUNT };
      if (stack.count < count) return { ok: false, code: CODE.NOT_ENOUGH };
      const unit = sellUnit(entry, ctx.sellRatio);
      const refunded = Math.min(count, stack.refund);
      const earn = refunded * Math.round(entry.price * ctx.refundRate) + (count - refunded) * unit;
      stack.count -= count;
      stack.refund = Math.min(stack.refund, stack.count);
      if (stack.count <= 0) ctx.storage[idx] = null;
      return { ok: true, code: CODE.OK, earn, moneyDelta: earn, refunded, normal: count - refunded };
    },

    /**
     * 出售（玩家背包任意槽位）：一律按 sellRatio（绿框只在储存格）
     * ctx: { balance, catalog, sellRatio, player }
     */
    async sellFromPlayer(group, idx, count, ctx) {
      await delay(420);
      const stack = ctx.player[group] ? ctx.player[group][idx] : null;
      if (!stack) return { ok: false, code: CODE.NOT_ENOUGH };
      const entry = findEntry(ctx.catalog, stack.item);
      if (!entry) return { ok: false, code: CODE.UNSELLABLE };
      if (!Number.isInteger(count) || count < 1) return { ok: false, code: CODE.BAD_COUNT };
      if (stack.count < count) return { ok: false, code: CODE.NOT_ENOUGH };
      const earn = count * sellUnit(entry, ctx.sellRatio);
      stack.count -= count;
      if (stack.count <= 0) ctx.player[group][idx] = null;
      return { ok: true, code: CODE.OK, earn, moneyDelta: earn, refunded: 0, normal: count };
    },

    /**
     * 取回（储存格 → 玩家背包）：取出即失去绿框资格；受负重上限约束
     * ctx: { storage, player, catalog, weight, weightLimit }
     */
    async takeFromStorage(idx, count, ctx) {
      await delay(320);
      const stack = ctx.storage[idx];
      if (!stack) return { ok: false, code: CODE.NOT_ENOUGH };
      if (!Number.isInteger(count) || count < 1 || stack.count < count) return { ok: false, code: CODE.BAD_COUNT };
      const entry = findEntry(ctx.catalog, stack.item);
      if (entry && ctx.weight + entry.weight * count > ctx.weightLimit) return { ok: false, code: CODE.OVER_WEIGHT };
      if (playerRoom(ctx.player, stack.item) < count) return { ok: false, code: CODE.BAG_FULL };
      stack.count -= count;
      stack.refund = Math.min(stack.refund, stack.count);
      if (stack.count <= 0) ctx.storage[idx] = null;
      addToPlayer(ctx.player, stack.item, count);
      return { ok: true, code: CODE.OK, moved: count, itemId: stack.item };
    },

    /**
     * 存入（玩家背包 → 储存格）：不获得绿框资格
     * ctx: { storage, player }
     */
    async storeFromPlayer(group, idx, count, ctx) {
      await delay(320);
      const stack = ctx.player[group] ? ctx.player[group][idx] : null;
      if (!stack) return { ok: false, code: CODE.NOT_ENOUGH };
      if (!Number.isInteger(count) || count < 1 || stack.count < count) return { ok: false, code: CODE.BAD_COUNT };
      if (storageRoom(ctx.storage, stack.item) < count) return { ok: false, code: CODE.STORAGE_FULL };
      stack.count -= count;
      if (stack.count <= 0) ctx.player[group][idx] = null;
      addToStorage(ctx.storage, stack.item, count, 0);
      return { ok: true, code: CODE.OK, moved: count, itemId: stack.item };
    },

    /** 供 UI 展示：某物品的普通收购价（60%） */
    sellPrice(itemId, catalog, ratio) {
      const entry = findEntry(catalog, itemId);
      return entry ? sellUnit(entry, ratio) : null;
    }
  };
})();