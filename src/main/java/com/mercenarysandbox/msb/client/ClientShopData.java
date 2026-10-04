package com.mercenarysandbox.msb.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.network.ShopDataPayload;
import com.mercenarysandbox.msb.network.ShopResultPayload;
import com.mercenarysandbox.msb.network.ShopStoragePayload;
import com.mercenarysandbox.msb.network.ShopTradePayload;
import com.mercenarysandbox.msb.shop.ShopCategory;
import com.mercenarysandbox.msb.shop.ShopCode;
import com.mercenarysandbox.msb.shop.ShopStorage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端商店缓存：目录（S2C 下发）+ 本人储存格 + 待显示回执 + 本地负重口径。
 * 只读呈现，不做权威判定（服务端兜底校验）。
 */
public final class ClientShopData {

    /** 回执 toast（按时间滤除） */
    public record Toast(ShopResultPayload payload, long at) {
    }

    private static final long TOAST_MS = 2600L;

    private static ShopDataPayload catalog;
    private static List<ShopStoragePayload.Stack> storage = List.of();
    private static final List<Toast> toasts = new ArrayList<>();
    private static final Map<ResourceLocation, ItemStack> STACK_CACHE = new HashMap<>();

    /** 新购高亮（对应原型绿框脉冲） */
    private static ResourceLocation flashItem;
    private static long flashUntil;

    private ClientShopData() {
    }

    // ===== 载荷入口 =====

    public static void accept(ShopDataPayload payload) {
        catalog = payload;
        STACK_CACHE.clear();
    }

    public static void accept(ShopStoragePayload payload) {
        storage = payload.stacks();
    }

    public static void accept(ShopResultPayload payload) {
        toasts.add(new Toast(payload, System.currentTimeMillis()));
        if (payload.action() == ShopTradePayload.Action.BUY && payload.ok()) {
            flashItem = payload.item();
            flashUntil = System.currentTimeMillis() + 1600L;
        }
    }

    /** 本地预检失败（未发包）：直接以回执形态提示（如双击购买但余额 / 储存格不足） */
    public static void localFailure(ShopCode code, ResourceLocation item) {
        toasts.add(new Toast(new ShopResultPayload(ShopTradePayload.Action.BUY, code, item, 0, 0, 0),
                System.currentTimeMillis()));
    }

    // ===== 读取 =====

    public static ShopDataPayload catalog() {
        return catalog;
    }

    public static boolean hasCatalog() {
        return catalog != null && !catalog.entries().isEmpty();
    }

    /** 荣誉点（仅在荣誉商店页面显示） */
    public static int honorPoints() {
        return catalog == null ? 0 : catalog.honorPoints();
    }

    public static List<ShopStoragePayload.Stack> storage() {
        return storage;
    }

    public static List<Toast> activeToasts() {
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now - t.at() > TOAST_MS);
        return List.copyOf(toasts);
    }

    public static boolean isFlashing(ResourceLocation item) {
        return item.equals(flashItem) && System.currentTimeMillis() < flashUntil;
    }

    /** 某分类的「可见」商品（阵营专属装备仅本阵营可见；用于购买列表与子分类筛选） */
    public static List<ShopDataPayload.Entry> entriesOf(ShopCategory category) {
        List<ShopDataPayload.Entry> list = new ArrayList<>();
        if (catalog != null) {
            for (ShopDataPayload.Entry entry : catalog.entries()) {
                if (entry.category() == category.ordinal() && entry.visible()) {
                    list.add(entry);
                }
            }
        }
        return list;
    }

    /**
     * 查条目用于卖价/负重口径：返回目录中的真实条目（不受可见性限制），
     * 目录未收录时合成默认条目（默认价 / 默认负重），使任何物品都能出售。
     */
    public static ShopDataPayload.Entry entry(ResourceLocation item) {
        if (catalog != null) {
            for (ShopDataPayload.Entry entry : catalog.entries()) {
                if (entry.item().equals(item)) {
                    return entry;
                }
            }
        }
        return new ShopDataPayload.Entry(item, ShopCategory.EQUIPMENT.ordinal(), -1,
                Config.SHOP_DEFAULT_PRICE.get(), -1, Config.SHOP_DEFAULT_WEIGHT.get(), true);
    }

    public static ShopCategory categoryOf(ShopDataPayload.Entry entry) {
        ShopCategory[] values = ShopCategory.values();
        return entry.category() >= 0 && entry.category() < values.length ? values[entry.category()] : ShopCategory.EQUIPMENT;
    }

    /** 展示用 ItemStack（缓存，不修改） */
    public static ItemStack stack(ResourceLocation item) {
        return STACK_CACHE.computeIfAbsent(item, id -> {
            var value = BuiltInRegistries.ITEM.get(id);
            return value == null ? ItemStack.EMPTY : new ItemStack(value);
        });
    }

    // ===== 价格与容量口径 =====

    public static int sellUnit(ShopDataPayload.Entry entry) {
        double ratio = catalog == null ? Config.SHOP_SELL_RATIO.get() : catalog.sellRatio();
        return entry.sell() > 0 ? entry.sell() : (int) Math.round(entry.price() * ratio);
    }

    public static int refundUnit(ShopDataPayload.Entry entry) {
        double rate = catalog == null ? Config.SHOP_REFUND_RATE.get() : catalog.refundRate();
        return (int) Math.round(entry.price() * rate);
    }

    public static int storageSlots() {
        return catalog == null ? Config.SHOP_STORAGE_SLOTS.get() : catalog.storageSlots();
    }

    /** 储存格可容纳件数（与服务端同口径，仅用于禁用态提示） */
    public static int storageRoom(ResourceLocation item) {
        int cap = ShopStorage.maxStackOf(item);
        int room = 0;
        for (ShopStoragePayload.Stack s : storage) {
            if (s.item().equals(item)) {
                room += Math.max(0, cap - s.count());
            }
        }
        room += Math.max(0, storageSlots() - storage.size()) * cap;
        return room;
    }

    /** 玩家物品栏 + 快捷栏可容纳件数（空格按该物品单格上限计） */
    public static int playerRoom(Player player, ResourceLocation item) {
        if (player == null) {
            return 0;
        }
        var inv = player.getInventory();
        int cap = ShopStorage.maxStackOf(item);
        int room = 0;
        for (int idx = 0; idx < 36; idx++) {
            ItemStack stack = inv.getItem(idx);
            if (stack.isEmpty()) {
                room += cap;
            } else if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                room += Math.max(0, Math.min(stack.getMaxStackSize(), ShopStorage.MAX_STACK) - stack.getCount());
            }
        }
        return room;
    }

    /** 本地负重（与服务端同口径；展示用） */
    public static double playerWeight(Player player) {
        if (player == null) {
            return 0.0D;
        }
        var inv = player.getInventory();
        double weight = 0.0D;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            weight += weightOf(inv.getItem(i));
        }
        for (ItemStack stack : inv.armor) {
            weight += weightOf(stack);
        }
        for (ItemStack stack : inv.offhand) {
            weight += weightOf(stack);
        }
        return weight;
    }

    private static double weightOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        // entry() 对目录外物品返回默认条目（默认负重），保证与服务端同口径
        ShopDataPayload.Entry entry = entry(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return entry.weight() * stack.getCount();
    }

    public static double weightLimit() {
        return Config.LOADOUT_WEIGHT_LIMIT_KG.get();
    }

    public static double weightWarnRatio() {
        return Config.WEIGHT_WARN_RATIO.get();
    }
}