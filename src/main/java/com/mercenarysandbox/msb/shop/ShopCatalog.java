package com.mercenarysandbox.msb.shop;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.network.ShopDataPayload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 商品目录（服务端权威，docs §2）。
 *
 * <p>数据位置：{@code data/<namespace>/shop/<category>.json}，一个分类一个文件：
 * <pre>
 * { "entries": [ { "item": "superbwarfare:hk_416", "price": 1450, "sell": 870, "weight": 3.5 } ] }
 * </pre>
 * 加载：{@link SimpleJsonResourceReloadListener}（/reload 生效）；
 * 同步：{@link OnDatapackSyncEvent}（玩家加入 / /reload 后）下发 {@link ShopDataPayload}。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ShopCatalog extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    /** 单例（重载监听 + 交易查询共用） */
    public static final ShopCatalog INSTANCE = new ShopCatalog();

    private final Map<ResourceLocation, ShopEntry> byItem = new HashMap<>();
    private final Map<ShopCategory, List<ShopEntry>> byCategory = new EnumMap<>(ShopCategory.class);
    private final List<ShopEntry> all = new ArrayList<>();

    private ShopCatalog() {
        super(GSON, "shop");
    }

    // ===== 查询 =====

    /** 按物品注册名查条目；未收录返回 null */
    public ShopEntry find(ResourceLocation item) {
        return byItem.get(item);
    }

    /**
     * 查询（含默认兜底）：目录未收录的物品按 {@code Config.SHOP_DEFAULT_PRICE / SHOP_DEFAULT_WEIGHT} 合成默认条目。
     * 默认条目仅用于出售/估值/负重口径，<b>不进入</b> {@link #all()} 等列表，不会出现在商店购买页。
     */
    public ShopEntry entryFor(ResourceLocation item) {
        ShopEntry entry = byItem.get(item);
        if (entry != null) {
            return entry;
        }
        return new ShopEntry(item, ShopCategory.EQUIPMENT, null, null,
                Config.SHOP_DEFAULT_PRICE.get(), -1, Config.SHOP_DEFAULT_WEIGHT.get());
    }

    public List<ShopEntry> byCategory(ShopCategory category) {
        return byCategory.getOrDefault(category, List.of());
    }

    public List<ShopEntry> all() {
        return all;
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    // ===== 加载 =====

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        byItem.clear();
        byCategory.clear();
        all.clear();
        for (Map.Entry<ResourceLocation, JsonElement> file : objects.entrySet()) {
            ShopCategory category = ShopCategory.byId(file.getKey().getPath());
            if (category == null) {
                MercenarySandboxShooter.LOGGER.warn("MSB shop: unknown category file '{}', skipped", file.getKey());
                continue;
            }
            try {
                JsonObject root = file.getValue().getAsJsonObject();
                JsonArray entries = root.getAsJsonArray("entries");
                if (entries == null) {
                    MercenarySandboxShooter.LOGGER.warn("MSB shop: '{}' has no 'entries' array", file.getKey());
                    continue;
                }
                for (JsonElement raw : entries) {
                    parseEntry(raw.getAsJsonObject(), category);
                }
            } catch (Exception ex) {
                MercenarySandboxShooter.LOGGER.warn("MSB shop: failed to read '{}': {}", file.getKey(), ex.toString());
            }
        }
        MercenarySandboxShooter.LOGGER.info("MSB shop catalog loaded: {} entries in {} categories",
                all.size(), byCategory.size());
    }

    private void parseEntry(JsonObject json, ShopCategory category) {
        try {
            ResourceLocation item = ResourceLocation.parse(json.get("item").getAsString());
            if (!BuiltInRegistries.ITEM.containsKey(item)) {
                MercenarySandboxShooter.LOGGER.warn("MSB shop: item '{}' not registered, skipped", item);
                return;
            }
            int price = json.get("price").getAsInt();
            if (price < 0) {
                MercenarySandboxShooter.LOGGER.warn("MSB shop: item '{}' has negative price, skipped", item);
                return;
            }
            int sell = json.has("sell") ? json.get("sell").getAsInt() : -1;
            double weight = json.has("weight") ? json.get("weight").getAsDouble() : 0.0D;
            if (weight < 0) {
                weight = 0.0D;
            }
            if (byItem.containsKey(item)) {
                MercenarySandboxShooter.LOGGER.warn("MSB shop: duplicate item '{}', later entry ignored", item);
                return;
            }
            // 阵营归属（仅阵营商店需要）：未写明 = 通用商品
            Faction faction = null;
            if (json.has("faction")) {
                faction = parseFaction(json.get("faction").getAsString());
                if (faction == null) {
                    MercenarySandboxShooter.LOGGER.warn("MSB shop: item '{}' has unknown faction, skipped", item);
                    return;
                }
            }
            // 子分类（枪械栏用 GunType、装备栏用 EquipType；缺失时归入未分类）
            ShopSubtype subtype = null;
            if (json.has("subtype")) {
                String raw = json.get("subtype").getAsString();
                subtype = category == ShopCategory.EQUIPMENT ? EquipType.byId(raw) : GunType.byId(raw);
                if (subtype == null) {
                    MercenarySandboxShooter.LOGGER.warn("MSB shop: item '{}' has unknown subtype, skipped", item);
                    return;
                }
            } else if (category == ShopCategory.GUNS || category == ShopCategory.EQUIPMENT) {
                MercenarySandboxShooter.LOGGER.warn("MSB shop: item '{}' has no subtype (not listed under any chip)", item);
            }
            ShopEntry entry = new ShopEntry(item, category, faction, subtype, price, sell, weight);
            byItem.put(item, entry);
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
            all.add(entry);
        } catch (Exception ex) {
            MercenarySandboxShooter.LOGGER.warn("MSB shop: malformed entry in {}: {}", category.getId(), ex.toString());
        }
    }

    // ===== 事件接线 =====

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    /** 玩家加入 / /reload：按玩家阵营过滤后下发目录（客户端缓存后打开窗口零请求） */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player ->
                PacketDistributor.sendToPlayer(player, ShopDataPayload.from(INSTANCE, player)));
    }

    /** 阵营名（datapack 用小写枚举名）；未识别返回 null */
    private static Faction parseFaction(String name) {
        for (Faction value : Faction.values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}