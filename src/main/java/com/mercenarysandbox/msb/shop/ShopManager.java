package com.mercenarysandbox.msb.shop;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.economy.PlayerWallet;
import com.mercenarysandbox.msb.economy.WalletAttachments;
import com.mercenarysandbox.msb.network.ShopResultPayload;
import com.mercenarysandbox.msb.network.ShopStoragePayload;
import com.mercenarysandbox.msb.network.ShopTradePayload;
import com.mercenarysandbox.msb.network.WalletPayload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 服务端交易逻辑（docs §4，唯一改钱包/储存格/背包入口）。
 *
 * <p>BUY：条目/数量/余额/储存格空间 → 扣款并把商品存入储存格（计入绿框无损额度）；
 * SELL：储存格堆叠按「无损 100% + 其余六折」混算，玩家栏槽位一律六折；
 * TAKE：储存格 → 玩家栏（校验负重上限与背包空间，取出即失去绿框）；
 * STORE：玩家栏 → 储存格（不获得绿框，受容量约束）。
 */
public final class ShopManager {

    /** 单笔件数上限（堆叠 64 × 跨堆叠） */
    private static final int MAX_COUNT = 2304;

    /** 玩家入库顺序：物品栏 9..35 优先，其次快捷栏 0..8 */
    private static final int[] PLAYER_SLOT_ORDER = buildSlotOrder();

    /** 装备区展示顺序（头盔/胸甲/护腿/靴子）→ PlayerInventory.armor 下标（靴→盔） */
    private static final int[] ARMOR_ORDER = {3, 2, 1, 0};

    private ShopManager() {
    }

    private static int[] buildSlotOrder() {
        int[] order = new int[36];
        int i = 0;
        for (int slot = 9; slot < 36; slot++) {
            order[i++] = slot;
        }
        for (int slot = 0; slot < 9; slot++) {
            order[i++] = slot;
        }
        return order;
    }

    public static void handle(ServerPlayer player, ShopTradePayload payload) {
        if (ShopCatalog.INSTANCE.isEmpty()) {
            result(player, payload.action(), ShopCode.NO_CATALOG, payload.item(), 0, 0, 0);
            return;
        }
        switch (payload.action()) {
            case BUY -> buy(player, payload);
            case SELL -> sell(player, payload);
            case TAKE -> take(player, payload);
            case STORE -> store(player, payload);
        }
    }

    /** 该条目是否允许欠款购买（阵营商店 + 弹药商店） */
    public static boolean creditAllowed(ShopEntry entry) {
        return entry.category() == ShopCategory.FACTION || entry.category() == ShopCategory.AMMO;
    }

    // ===== 买入（商品 → 储存格，带绿框无损额度） =====

    private static void buy(ServerPlayer player, ShopTradePayload p) {
        ShopEntry entry = ShopCatalog.INSTANCE.find(p.item());
        if (entry == null) {
            result(player, p.action(), ShopCode.UNKNOWN_ITEM, p.item(), 0, 0, 0);
            return;
        }
        int count = p.count();
        if (count < 1 || count > MAX_COUNT) {
            result(player, p.action(), ShopCode.BAD_COUNT, p.item(), count, 0, 0);
            return;
        }
        long cost = (long) entry.price() * count;
        if (cost > Integer.MAX_VALUE) {
            result(player, p.action(), ShopCode.BAD_COUNT, p.item(), count, 0, 0);
            return;
        }
        PlayerWallet wallet = player.getData(WalletAttachments.WALLET);
        // 阵营商店 / 弹药商店允许欠款购买（余额可为负，后续收入自动抵扣），其余分类仍需足额
        if (!creditAllowed(entry) && wallet.total() < cost) {
            result(player, p.action(), ShopCode.NO_BALANCE, p.item(), count, 0, 0);
            return;
        }
        ShopStorage storage = storage(player);
        if (storageRoom(storage, entry.item()) < count) {
            result(player, p.action(), ShopCode.STORAGE_FULL, p.item(), count, 0, 0);
            return;
        }
        // 执行：入储存格（购买件数全额计入无损额度）→ 扣款（花销 +cost，总资产 -cost）
        addToStorage(storage, entry.item(), count, count);
        setWallet(player, wallet.spent() + (int) cost, wallet.total() - (int) cost);
        result(player, p.action(), ShopCode.OK, entry.item(), count, -(int) cost, count);
        syncStorage(player);
    }

    // ===== 卖出（储存格优先无损，玩家栏一律六折） =====

    private static void sell(ServerPlayer player, ShopTradePayload p) {
        int count = p.count();
        if (count < 1 || count > MAX_COUNT) {
            result(player, p.action(), ShopCode.BAD_COUNT, p.item(), count, 0, 0);
            return;
        }
        ShopEntry entry = ShopCatalog.INSTANCE.find(p.item());
        if (entry == null) {
            result(player, p.action(), ShopCode.UNSELLABLE, p.item(), count, 0, 0);
            return;
        }
        PlayerWallet wallet = player.getData(WalletAttachments.WALLET);
        if (p.zone() == ShopTradePayload.Zone.STORAGE) {
            ShopStorage storage = storage(player);
            ShopStorage.Stack stack = storage.get(p.slot());
            if (stack == null || !stack.item().equals(p.item()) || stack.count() < count) {
                result(player, p.action(), ShopCode.NOT_ENOUGH, p.item(), count, 0, 0);
                return;
            }
            int unit = entry.sellPrice(Config.SHOP_SELL_RATIO.get());
            int refundUnit = (int) Math.round(entry.price() * Config.SHOP_REFUND_RATE.get());
            int refunded = Math.min(count, stack.refund());
            long earn = (long) refunded * refundUnit + (long) (count - refunded) * unit;
            if (earn > Integer.MAX_VALUE) {
                result(player, p.action(), ShopCode.BAD_COUNT, p.item(), count, 0, 0);
                return;
            }
            stack.setCount(stack.count() - count);
            if (stack.count() <= 0) {
                storage.remove(p.slot());
            }
            setWallet(player, wallet.spent(), wallet.total() + (int) earn);
            result(player, p.action(), ShopCode.OK, p.item(), count, (int) earn, refunded);
            syncStorage(player);
            return;
        }
        // 玩家栏（护甲/副手/物品栏/快捷栏）：一律六折，绿框只在储存格有效
        ItemStack stack = playerStack(player, p.zone(), p.slot());
        if (stack.isEmpty() || !itemId(stack).equals(p.item()) || stack.getCount() < count) {
            result(player, p.action(), ShopCode.NOT_ENOUGH, p.item(), count, 0, 0);
            return;
        }
        int earn = count * entry.sellPrice(Config.SHOP_SELL_RATIO.get());
        stack.shrink(count);
        player.getInventory().setChanged();
        setWallet(player, wallet.spent(), wallet.total() + earn);
        WeightService.apply(player);
        result(player, p.action(), ShopCode.OK, p.item(), count, earn, 0);
    }

    // ===== 取回（储存格 → 玩家栏；失去绿框；受负重上限约束） =====

    private static void take(ServerPlayer player, ShopTradePayload p) {
        int count = p.count();
        if (count < 1 || count > MAX_COUNT) {
            result(player, p.action(), ShopCode.BAD_COUNT, p.item(), count, 0, 0);
            return;
        }
        ShopStorage storage = storage(player);
        ShopStorage.Stack stack = storage.get(p.slot());
        if (stack == null || !stack.item().equals(p.item()) || stack.count() < count) {
            result(player, p.action(), ShopCode.NOT_ENOUGH, p.item(), count, 0, 0);
            return;
        }
        ResourceLocation item = stack.item();
        ShopEntry entry = ShopCatalog.INSTANCE.find(item);
        if (entry != null
                && WeightService.total(player) + entry.weight() * count > Config.LOADOUT_WEIGHT_LIMIT_KG.get()) {
            result(player, p.action(), ShopCode.OVER_WEIGHT, item, count, 0, 0);
            return;
        }
        if (playerRoom(player, item) < count) {
            result(player, p.action(), ShopCode.BAG_FULL, item, count, 0, 0);
            return;
        }
        ItemStack template = templateOf(item);
        if (template.isEmpty()) {
            result(player, p.action(), ShopCode.UNKNOWN_ITEM, item, count, 0, 0);
            return;
        }
        stack.setCount(stack.count() - count);   // 取出即压缩无损额度（refund = min(refund, count)）
        if (stack.count() <= 0) {
            storage.remove(p.slot());
        }
        addToPlayer(player, template, count, p.targetSlot());
        WeightService.apply(player);
        result(player, p.action(), ShopCode.OK, item, count, 0, 0);
        syncStorage(player);
    }

    // ===== 存入（玩家栏 → 储存格；不获得绿框） =====

    private static void store(ServerPlayer player, ShopTradePayload p) {
        int count = p.count();
        if (count < 1 || count > MAX_COUNT) {
            result(player, p.action(), ShopCode.BAD_COUNT, p.item(), count, 0, 0);
            return;
        }
        ItemStack stack = playerStack(player, p.zone(), p.slot());
        if (stack.isEmpty() || !itemId(stack).equals(p.item()) || stack.getCount() < count) {
            result(player, p.action(), ShopCode.NOT_ENOUGH, p.item(), count, 0, 0);
            return;
        }
        ResourceLocation item = itemId(stack);
        ShopStorage storage = storage(player);
        if (storageRoom(storage, item) < count) {
            result(player, p.action(), ShopCode.STORAGE_FULL, item, count, 0, 0);
            return;
        }
        stack.shrink(count);
        player.getInventory().setChanged();
        addToStorage(storage, item, count, 0);
        WeightService.apply(player);
        result(player, p.action(), ShopCode.OK, item, count, 0, 0);
        syncStorage(player);
    }

    // ===== 容器与钱包 =====

    public static ShopStorage storage(ServerPlayer player) {
        return player.getData(ShopStorageAttachments.STORAGE);
    }

    /** 储存格可容纳件数：同物品堆叠余量 + （容量 - 已有堆叠数）× 64 */
    public static int storageRoom(ShopStorage storage, ResourceLocation item, int maxSlots) {
        int room = 0;
        for (ShopStorage.Stack s : storage.stacks()) {
            if (s.item().equals(item)) {
                room += s.room();
            }
        }
        room += Math.max(0, maxSlots - storage.stacks().size()) * ShopStorage.MAX_STACK;
        return room;
    }

    private static int storageRoom(ShopStorage storage, ResourceLocation item) {
        return storageRoom(storage, item, Config.SHOP_STORAGE_SLOTS.get());
    }

    /** 入库：先合并同物品堆叠，再开新堆叠（refund = 本次新增的无损额度件数） */
    private static void addToStorage(ShopStorage storage, ResourceLocation item, int count, int refund) {
        int remaining = count;
        for (ShopStorage.Stack s : storage.stacks()) {
            if (remaining <= 0) {
                break;
            }
            if (!s.item().equals(item)) {
                continue;
            }
            int put = Math.min(s.room(), remaining);
            if (put <= 0) {
                continue;
            }
            if (refund > 0) {
                s.addPurchased(put);
                refund = Math.max(0, refund - put);
            } else {
                s.addStored(put);
            }
            remaining -= put;
        }
        while (remaining > 0 && storage.stacks().size() < Config.SHOP_STORAGE_SLOTS.get()) {
            int put = Math.min(ShopStorage.MAX_STACK, remaining);
            int credit = Math.min(put, refund);
            storage.stacks().add(new ShopStorage.Stack(item, put, credit));
            refund -= credit;
            remaining -= put;
        }
    }

    /** 玩家物品栏（27）+ 快捷栏（9）可容纳件数 */
    private static int playerRoom(ServerPlayer player, ResourceLocation item) {
        Inventory inv = player.getInventory();
        int room = 0;
        for (int idx : PLAYER_SLOT_ORDER) {
            ItemStack stack = inv.items.get(idx);
            if (stack.isEmpty()) {
                room += ShopStorage.MAX_STACK;
            } else if (itemId(stack).equals(item)) {
                room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
        }
        return room;
    }

    /**
     * 入包：先尝试 {@code preferredSlot}（拖拽放下的目标格，0-8 快捷栏 / 9-35 物品栏），
     * 余量再按「物品栏优先、其次快捷栏」自动合并与占位。
     */
    private static void addToPlayer(ServerPlayer player, ItemStack template, int count, int preferredSlot) {
        Inventory inv = player.getInventory();
        int remaining = count;
        if (preferredSlot >= 0 && preferredSlot < 36) {
            ItemStack target = inv.getItem(preferredSlot);
            if (target.isEmpty()) {
                ItemStack placed = template.copyWithCount(Math.min(ShopStorage.MAX_STACK, remaining));
                inv.setItem(preferredSlot, placed);
                remaining -= placed.getCount();
            } else if (ItemStack.isSameItemSameComponents(target, template)) {
                int room = Math.max(0, Math.min(target.getMaxStackSize(), ShopStorage.MAX_STACK) - target.getCount());
                int put = Math.min(room, remaining);
                target.grow(put);
                remaining -= put;
            }
        }
        for (int idx : PLAYER_SLOT_ORDER) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = inv.items.get(idx);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            int room = Math.max(0, Math.min(stack.getMaxStackSize(), ShopStorage.MAX_STACK) - stack.getCount());
            int put = Math.min(room, remaining);
            if (put > 0) {
                stack.grow(put);
                remaining -= put;
            }
        }
        for (int idx : PLAYER_SLOT_ORDER) {
            if (remaining <= 0) {
                break;
            }
            if (!inv.items.get(idx).isEmpty()) {
                continue;
            }
            ItemStack copy = template.copyWithCount(Math.min(ShopStorage.MAX_STACK, remaining));
            inv.setItem(idx, copy);
            remaining -= copy.getCount();
        }
        inv.setChanged();
    }

    /** 取玩家栏指定区/槽位的堆叠（活引用，可直接 shrink） */
    private static ItemStack playerStack(ServerPlayer player, ShopTradePayload.Zone zone, int slot) {
        Inventory inv = player.getInventory();
        return switch (zone) {
            case ARMOR -> slot >= 0 && slot < ARMOR_ORDER.length ? inv.armor.get(ARMOR_ORDER[slot]) : ItemStack.EMPTY;
            case OFFHAND -> slot == 0 ? inv.offhand.get(0) : ItemStack.EMPTY;
            case MAIN -> slot >= 0 && slot < 27 ? inv.items.get(slot + 9) : ItemStack.EMPTY;
            case HOTBAR -> slot >= 0 && slot < 9 ? inv.items.get(slot) : ItemStack.EMPTY;
            case STORAGE -> ItemStack.EMPTY;
        };
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static ItemStack templateOf(ResourceLocation item) {
        Item value = BuiltInRegistries.ITEM.get(item);
        return value == null ? ItemStack.EMPTY : new ItemStack(value);
    }

    /** 结算钱包：本命收入不受商店买卖影响（只随战斗收益增长与死亡清零） */
    private static void setWallet(ServerPlayer player, int spent, int total) {
        PlayerWallet current = player.getData(WalletAttachments.WALLET);
        player.setData(WalletAttachments.WALLET, current.withFinance(spent, total));
        PacketDistributor.sendToPlayer(player, new WalletPayload(current.earned(), spent, total));
    }

    private static void result(ServerPlayer player, ShopTradePayload.Action action, ShopCode code,
            ResourceLocation item, int count, int moneyDelta, int refunded) {
        PacketDistributor.sendToPlayer(player, new ShopResultPayload(action, code, item, count, moneyDelta, refunded));
    }

    private static void syncStorage(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ShopStoragePayload.from(storage(player)));
    }
}