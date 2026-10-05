package com.mercenarysandbox.msb.shop;

import java.util.ArrayList;
import java.util.List;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.economy.HonorAttachments;
import com.mercenarysandbox.msb.economy.PlayerWallet;
import com.mercenarysandbox.msb.economy.WalletAttachments;
import com.mercenarysandbox.msb.network.ShopDataPayload;
import com.mercenarysandbox.msb.network.ShopResultPayload;
import com.mercenarysandbox.msb.network.ShopStoragePayload;
import com.mercenarysandbox.msb.network.ShopTradePayload;
import com.mercenarysandbox.msb.network.WalletPayload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

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
            case SWAP_HOTBAR -> swapHotbar(player, payload);
            case EQUIP -> equip(player, payload);
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
        ShopStorage storage = storage(player);
        if (storageRoom(storage, entry.item()) < count) {
            result(player, p.action(), ShopCode.STORAGE_FULL, p.item(), count, 0, 0);
            return;
        }
        // 荣誉商店：以荣誉点结算（不扣现金、不产生绿框无损额度）；条目禁止卖回（见 sell）
        if (entry.category() == ShopCategory.HONOR) {
            int honor = player.getData(HonorAttachments.HONOR);
            if (honor < cost) {
                result(player, p.action(), ShopCode.NO_HONOR, p.item(), count, 0, 0);
                return;
            }
            addToStorage(storage, entry.item(), count, 0);
            player.setData(HonorAttachments.HONOR, honor - (int) cost);
            result(player, p.action(), ShopCode.OK, entry.item(), count, -(int) cost, 0);
            syncStorage(player);
            // 复用目录载荷刷新客户端荣誉点（其携带 honorPoints 字段）
            PacketDistributor.sendToPlayer(player, ShopDataPayload.from(ShopCatalog.INSTANCE, player));
            return;
        }
        // 阵营商店 / 弹药商店允许欠款购买（余额可为负，后续收入自动抵扣），其余分类仍需足额
        if (!creditAllowed(entry) && wallet.total() < cost) {
            result(player, p.action(), ShopCode.NO_BALANCE, p.item(), count, 0, 0);
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
        ShopEntry entry = ShopCatalog.INSTANCE.entryFor(p.item());
        if (entry == null) {
            result(player, p.action(), ShopCode.UNSELLABLE, p.item(), count, 0, 0);
            return;
        }
        // 荣誉商店条目以荣誉点计价，禁止卖回换现金（防套现）
        if (entry.category() == ShopCategory.HONOR) {
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
        markPlayerSlot(player, p.zone(), p.slot(), stack);
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
        ShopEntry entry = ShopCatalog.INSTANCE.entryFor(item);
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
        addToPlayer(player, template, count);
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
        markPlayerSlot(player, p.zone(), p.slot(), stack);
        addToStorage(storage, item, count, 0);
        WeightService.apply(player);
        result(player, p.action(), ShopCode.OK, item, count, 0, 0);
        syncStorage(player);
    }

    // ===== 快捷栏对调（大键盘 1~9：把选中槽位与快捷栏对应格互换） =====

    /**
     * 把「选中槽位（zone/slot）」与快捷栏第 {@code count} 格对调（count = 0..8）。
     * 支持玩家栏四区与储存格；目标格可为空（等效搬运）。储存格来源见 {@link #swapStorageHotbar}。
     */
    private static void swapHotbar(ServerPlayer player, ShopTradePayload p) {
        int hotbar = p.count();
        if (hotbar < 0 || hotbar > 8) {
            result(player, p.action(), ShopCode.BAD_COUNT, p.item(), p.count(), 0, 0);
            return;
        }
        Inventory inv = player.getInventory();
        if (p.zone() == ShopTradePayload.Zone.STORAGE) {
            swapStorageHotbar(player, inv, hotbar, p);
            return;
        }
        ItemStack source = playerStack(player, p.zone(), p.slot());
        if (source.isEmpty() || !itemId(source).equals(p.item())) {
            result(player, p.action(), ShopCode.NOT_ENOUGH, p.item(), p.count(), 0, 0);
            return;
        }
        if (p.zone() == ShopTradePayload.Zone.HOTBAR && p.slot() == hotbar) {
            return;   // 同一格：无操作
        }
        ItemStack target = inv.items.get(hotbar).copy();
        setPlayerStack(inv, p.zone(), p.slot(), target);
        inv.items.set(hotbar, source.copy());
        inv.setChanged();
        WeightService.apply(player);
        result(player, p.action(), ShopCode.OK, p.item(), hotbar + 1, 0, 0);
    }

    /**
     * 储存格堆叠 ↔ 快捷栏：被换出的储存格堆叠按正常取出处理（无损额度失效），
     * 快捷栏物品回存储存格（不获得无损额度）；先摘除再回存以腾出格位，保证必定放得下。
     */
    private static void swapStorageHotbar(ServerPlayer player, Inventory inv, int hotbar, ShopTradePayload p) {
        ShopStorage storage = storage(player);
        ShopStorage.Stack stored = storage.get(p.slot());
        if (stored == null || !stored.item().equals(p.item())) {
            result(player, p.action(), ShopCode.NOT_ENOUGH, p.item(), p.count(), 0, 0);
            return;
        }
        ItemStack template = templateOf(stored.item());
        if (template.isEmpty()) {
            result(player, p.action(), ShopCode.UNKNOWN_ITEM, p.item(), p.count(), 0, 0);
            return;
        }
        ResourceLocation storedItem = stored.item();
        int storedCount = stored.count();
        ItemStack hotbarStack = inv.items.get(hotbar).copy();
        storage.remove(p.slot());
        if (!hotbarStack.isEmpty()) {
            addToStorage(storage, itemId(hotbarStack), hotbarStack.getCount(), 0);
        }
        inv.items.set(hotbar, template.copyWithCount(storedCount));
        inv.setChanged();
        WeightService.apply(player);
        result(player, p.action(), ShopCode.OK, storedItem, hotbar + 1, 0, 0);
        syncStorage(player);
    }

    // ===== 双击装备（护甲 → 原版护甲槽；饰品 → Curios 槽；替换下来的物品移交储存格） =====

    /**
     * 双击装备：来源可为储存格堆叠或玩家栏槽位（取 1 件）。
     * 目标解析：原版护甲（头盔/胸甲等）→ 对应护甲槽；饰品（如降落伞）→ Curios 对应槽。
     * 被替换下来的原槽位物品优先移交储存格（满则入包，再满则掉落）。
     */
    private static void equip(ServerPlayer player, ShopTradePayload p) {
        boolean fromStorage = p.zone() == ShopTradePayload.Zone.STORAGE;
        ShopStorage storage = storage(player);
        ShopStorage.Stack storedStack = null;
        ItemStack source;
        if (fromStorage) {
            storedStack = storage.get(p.slot());
            if (storedStack == null || !storedStack.item().equals(p.item())) {
                result(player, p.action(), ShopCode.NOT_ENOUGH, p.item(), 1, 0, 0);
                return;
            }
            source = templateOf(storedStack.item());
        } else {
            ItemStack live = playerStack(player, p.zone(), p.slot());
            if (live.isEmpty() || !itemId(live).equals(p.item())) {
                result(player, p.action(), ShopCode.NOT_ENOUGH, p.item(), 1, 0, 0);
                return;
            }
            source = live.copyWithCount(1);
        }
        if (source.isEmpty()) {
            result(player, p.action(), ShopCode.UNKNOWN_ITEM, p.item(), 1, 0, 0);
            return;
        }
        // 已在饰品槽的饰品双击不再「再装备」：避免自我替换导致物品复制
        if (p.zone() == ShopTradePayload.Zone.CURIOS) {
            result(player, p.action(), ShopCode.OK, p.item(), 1, 0, 0);
            return;
        }
        EquipmentSlot armorSlot = armorSlotOf(source);
        String curioSlot = armorSlot == null ? curioSlotOf(source, player) : null;
        if (armorSlot == null && curioSlot == null) {
            result(player, p.action(), ShopCode.NOT_EQUIPPABLE, p.item(), 1, 0, 0);
            return;
        }
        // 已在该槽位自装备：无操作
        if (armorSlot != null && p.zone() == ShopTradePayload.Zone.ARMOR && slotArmorIndex(p.slot()) == armorSlot.getIndex()) {
            result(player, p.action(), ShopCode.OK, p.item(), 1, 0, 0);
            return;
        }
        ItemStack replaced;
        if (armorSlot != null) {
            replaced = player.getItemBySlot(armorSlot).copy();
            player.setItemSlot(armorSlot, source.copy());
        } else {
            IDynamicStackHandler stacks = curioStacks(player, curioSlot);
            if (stacks == null) {
                result(player, p.action(), ShopCode.NOT_EQUIPPABLE, p.item(), 1, 0, 0);
                return;
            }
            int idx = 0;
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (stacks.getStackInSlot(i).isEmpty()) {
                    idx = i;
                    break;
                }
            }
            replaced = stacks.getStackInSlot(idx).copy();
            stacks.setStackInSlot(idx, source.copy());
        }
        // 消耗来源 1 件（装备即压缩绿框无损额度）
        if (fromStorage) {
            storedStack.setCount(storedStack.count() - 1);
            if (storedStack.count() <= 0) {
                storage.remove(p.slot());
            }
        } else {
            playerStack(player, p.zone(), p.slot()).shrink(1);
            player.getInventory().setChanged();
        }
        if (!replaced.isEmpty()) {
            giveOrStore(player, replaced);
        }
        WeightService.apply(player);
        result(player, p.action(), ShopCode.OK, p.item(), 1, 0, 0);
        syncStorage(player);
    }

    /** 护甲展示下标（头盔/胸甲/护腿/靴子）→ armor 库存下标 */
    private static int slotArmorIndex(int displaySlot) {
        return displaySlot >= 0 && displaySlot < ARMOR_ORDER.length ? ARMOR_ORDER[displaySlot] : -1;
    }

    /** 原版护甲槽（非护甲返回 null） */
    private static EquipmentSlot armorSlotOf(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : null;
    }

    /** 物品可放入的 Curios 槽标识（无则 null，如背饰槽 "back"） */
    private static String curioSlotOf(ItemStack stack, ServerPlayer player) {
        return CuriosApi.getItemStackSlots(stack, player).keySet().stream().findFirst().orElse(null);
    }

    private static IDynamicStackHandler curioStacks(LivingEntity entity, String slot) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(entity).orElse(null);
        if (handler == null) {
            return null;
        }
        ICurioStacksHandler stacksHandler = handler.getStacksHandler(slot).orElse(null);
        return stacksHandler == null ? null : stacksHandler.getStacks();
    }

    // ===== Curios 饰品槽扁平映射（客户端 ShopScreen 与服务端必须使用同一顺序） =====

    /** 饰品区第 n 格 → 具体 Curios 子槽位（槽标识 + 槽内下标） */
    public record CurioSlot(String id, int index) {
    }

    /** 饰品区槽位顺序：按槽标识字典序，再按槽内下标展平（结构稳定，客户端/服务端一致） */
    public static List<CurioSlot> curioSlots(LivingEntity entity) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(entity).orElse(null);
        if (handler == null) {
            return List.of();
        }
        var curios = handler.getCurios();
        if (curios == null || curios.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(curios.keySet());
        ids.sort(String::compareTo);
        List<CurioSlot> out = new ArrayList<>();
        for (String id : ids) {
            ICurioStacksHandler stacksHandler = curios.get(id);
            if (stacksHandler == null) {
                continue;
            }
            int slots = stacksHandler.getStacks().getSlots();
            for (int i = 0; i < slots; i++) {
                out.add(new CurioSlot(id, i));
            }
        }
        return out;
    }

    /** 读取饰品区扁平下标对应槽位的堆叠（活引用，可直接 shrink；越界返回空） */
    public static ItemStack curioStack(LivingEntity entity, int flat) {
        CurioSlot ref = curioSlotAt(entity, flat);
        if (ref == null) {
            return ItemStack.EMPTY;
        }
        IDynamicStackHandler stacks = curioStacks(entity, ref.id());
        if (stacks == null || ref.index() >= stacks.getSlots()) {
            return ItemStack.EMPTY;
        }
        return stacks.getStackInSlot(ref.index());
    }

    /** 写入饰品区扁平下标对应槽位（越界忽略） */
    public static void setCurioStack(LivingEntity entity, int flat, ItemStack stack) {
        CurioSlot ref = curioSlotAt(entity, flat);
        if (ref == null) {
            return;
        }
        IDynamicStackHandler stacks = curioStacks(entity, ref.id());
        if (stacks != null && ref.index() < stacks.getSlots()) {
            stacks.setStackInSlot(ref.index(), stack);
        }
    }

    private static CurioSlot curioSlotAt(LivingEntity entity, int flat) {
        List<CurioSlot> slots = curioSlots(entity);
        return flat >= 0 && flat < slots.size() ? slots.get(flat) : null;
    }

    /** 替换下来的物品移交储存格（满则入包，再满则掉落） */
    private static void giveOrStore(ServerPlayer player, ItemStack stack) {
        ResourceLocation id = itemId(stack);
        ShopStorage storage = storage(player);
        if (storageRoom(storage, id) >= stack.getCount()) {
            addToStorage(storage, id, stack.getCount(), 0);
        } else if (playerRoom(player, id) >= stack.getCount()) {
            addToPlayer(player, stack.copy(), stack.getCount());
        } else {
            player.drop(stack.copy(), false);
        }
    }

    /** 写入玩家栏指定区/槽位（护甲按展示顺序映射到 armor 下标） */
    private static void setPlayerStack(Inventory inv, ShopTradePayload.Zone zone, int slot, ItemStack value) {
        switch (zone) {
            case ARMOR -> {
                if (slot >= 0 && slot < ARMOR_ORDER.length) {
                    inv.armor.set(ARMOR_ORDER[slot], value);
                }
            }
            case OFFHAND -> {
                if (slot == 0) {
                    inv.offhand.set(0, value);
                }
            }
            case MAIN -> {
                if (slot >= 0 && slot < 27) {
                    inv.items.set(slot + 9, value);
                }
            }
            case HOTBAR -> {
                if (slot >= 0 && slot < 9) {
                    inv.items.set(slot, value);
                }
            }
            case CURIOS -> setCurioStack(inv.player, slot, value);
            case STORAGE -> {
            }
        }
    }

    /** 玩家栏槽位变更后通知对应容器（Curios 不走原版 Inventory，需显式回写以触发同步） */
    private static void markPlayerSlot(ServerPlayer player, ShopTradePayload.Zone zone, int slot, ItemStack stack) {
        if (zone == ShopTradePayload.Zone.CURIOS) {
            setCurioStack(player, slot, stack);
        } else {
            player.getInventory().setChanged();
        }
    }

    // ===== 容器与钱包 =====

    public static ShopStorage storage(ServerPlayer player) {
        return player.getData(ShopStorageAttachments.STORAGE);
    }

    /** 储存格可容纳件数：同物品堆叠余量 + （容量 - 已有堆叠数）× 该物品单格上限 */
    public static int storageRoom(ShopStorage storage, ResourceLocation item, int maxSlots) {
        int room = 0;
        for (ShopStorage.Stack s : storage.stacks()) {
            if (s.item().equals(item)) {
                room += s.room();
            }
        }
        room += Math.max(0, maxSlots - storage.stacks().size()) * ShopStorage.maxStackOf(item);
        return room;
    }

    private static int storageRoom(ShopStorage storage, ResourceLocation item) {
        return storageRoom(storage, item, Config.SHOP_STORAGE_SLOTS.get());
    }

    /** 入库：先合并同物品堆叠，再开新堆叠（每格上限取物品自身最大堆叠，枪械等不可堆叠物品独占一格） */
    private static void addToStorage(ShopStorage storage, ResourceLocation item, int count, int refund) {
        int cap = ShopStorage.maxStackOf(item);
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
            int put = Math.min(cap, remaining);
            int credit = Math.min(put, refund);
            storage.stacks().add(new ShopStorage.Stack(item, put, credit));
            refund -= credit;
            remaining -= put;
        }
    }

    /** 玩家物品栏（27）+ 快捷栏（9）可容纳件数（空格按该物品单格上限计，枪械等一格仅 1 把） */
    private static int playerRoom(ServerPlayer player, ResourceLocation item) {
        Inventory inv = player.getInventory();
        int cap = ShopStorage.maxStackOf(item);
        int room = 0;
        for (int idx : PLAYER_SLOT_ORDER) {
            ItemStack stack = inv.items.get(idx);
            if (stack.isEmpty()) {
                room += cap;
            } else if (itemId(stack).equals(item)) {
                room += Math.max(0, Math.min(stack.getMaxStackSize(), ShopStorage.MAX_STACK) - stack.getCount());
            }
        }
        return room;
    }

    /** 入包：按「物品栏优先、其次快捷栏」自动合并与占位（调用前已校验 {@link #playerRoom}） */
    private static void addToPlayer(ServerPlayer player, ItemStack template, int count) {
        Inventory inv = player.getInventory();
        int cap = Math.min(template.getMaxStackSize(), ShopStorage.MAX_STACK);
        int remaining = count;
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
            ItemStack copy = template.copyWithCount(Math.min(cap, remaining));
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
            case CURIOS -> curioStack(player, slot);
            case STORAGE -> ItemStack.EMPTY;
        };
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /**
     * 单件估值（卖价口径，六折档）：目录未收录物品按默认条目（默认价 × 六折）估值。
     * 用于死亡时把被杀阵营玩家的装备折算成赏金（docs/02 击杀结算）。
     */
    public static int itemValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ShopEntry entry = ShopCatalog.INSTANCE.entryFor(itemId(stack));
        return Math.max(0, entry.sellPrice(Config.SHOP_SELL_RATIO.get()));
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