package com.mercenarysandbox.msb.data;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 语言文件生成（en_us / zh_cn）：阵营名、HUD 文案、聊天播报、战术地图文案。
 * 由 datagen 统一管理，禁止手写 lang JSON（docs/02 §8）。
 */
public final class MsbLanguageProvider extends LanguageProvider {
    /** 简体中文语言代码 */
    public static final String ZH_CN = "zh_cn";

    private final String langLocale;

    public MsbLanguageProvider(PackOutput output, String locale) {
        super(output, MercenarySandboxShooter.MODID, locale);
        this.langLocale = locale;
    }

    @Override
    protected void addTranslations() {
        if (langLocale.equals(ZH_CN)) {
            addChinese();
        } else {
            addEnglish();
        }
    }

    private void addEnglish() {
        // M0 注册项（原手写 en_us.json 已迁移至此，由 datagen 统一管理）
        this.add("itemGroup.msb", "Mercenary Sandbox Shooter");
        this.add("block.msb.test_block", "MSB Test Block");
        this.add("item.msb.test_item", "MSB Test Item");
        // 基地方块（M1）
        this.add("block.msb.base_block_lonestar", "S.F. Base");
        this.add("block.msb.base_block_valkyra", "KCCO Base");
        this.add("block.msb.base_block_manticore", "I.O.P. Base");
        // AI 实体（M1 实体化）
        this.add("entity.msb.ai_combatant", "AI Combatant");
        // 阵营显示名（计分板队伍 / HUD / Tab）
        this.add("team.msb.lonestar", "SANGVIS FERRI");
        this.add("team.msb.valkyra", "Special Operations Forces Command");
        this.add("team.msb.manticore", "Important Operation Prototype");
        // HUD 计分板
        this.add("msb.hud.zone", "Zone (%s,%s) R=%s");
        this.add("msb.hud.settle", "Settle in %s s");
        // 聊天播报
        this.add("msb.chat.settle", "Zone settled:");
        this.add("msb.chat.ai_replaced", "AI %s replaced by a real player");
        // 按键与战术地图
        this.add("key.categories.msb", "Mercenary Sandbox Shooter");
        this.add("key.msb.tactical_map", "Tactical Map");
        this.add("msb.tactical_map.title", "Tactical Map");
        this.add("msb.tactical_map.waiting", "Waiting for match data...");
        // 战术地图（2026-09-29 增强版）
        this.add("msb.tactical_map.follow", "Follow");
        this.add("msb.tactical_map.free", "Free");
        this.add("msb.tactical_map.unit", "Unit");
        this.add("msb.tactical_map.countdown", "%s s");
        this.add("msb.tactical_map.distance", "Distance: %s blocks");
        this.add("msb.tactical_map.status", "Status: %s");
        this.add("msb.tactical_map.engaged", "Engaged");
        this.add("msb.tactical_map.hidden", "Hidden");
        this.add("msb.tactical_map.cursor", "X %s Z %s  |  %s blocks away");
        this.add("msb.tactical_map.hint", "Wheel/right slider: zoom | Drag: pan | Top-right: lock north | Bottom-right: zone/me");
        this.add("msb.tactical_map.tp.creative_only", "Teleport (T): Creative only");
        this.add("msb.tactical_map.tp.out_of_range", "Teleport failed: target unreachable");
        // 按钮：右上角锁定北向 / 右下角圈居中、我（跟随-自由）
        this.add("msb.tactical_map.lock", "Lock");
        this.add("msb.tactical_map.unlock", "Unlock");
        this.add("msb.tactical_map.btn.zone_center", "Zone");
        this.add("msb.tactical_map.edge.me", "You: %s blocks");
        this.add("msb.tactical_map.edge.zone", "Zone: %s blocks");
        this.add("msb.tactical_map.legend.spawn", "Spawn");
        this.add("msb.tactical_map.legend.zone", "Control Zone");
        this.add("msb.tactical_map.legend.boundary", "Map Boundary");
        this.add("msb.tactical_map.legend.friendly", "Friend (T)");
        this.add("msb.tactical_map.legend.enemy", "Enemy (E · engaged/AI)");
        this.add("msb.tactical_map.legend.neutral", "Unassigned");
        this.add("msb.tactical_map.legend.self", "You");
        this.add("msb.tactical_map.legend.base", "Base");
        // 击杀提示（M2：docs/02 击杀提示）
        this.add("msb.kill_feed.annihilate", "Eliminate %s %s");
        this.add("msb.kill_feed.info", "Kills %s | XP %s");
        this.add("msb.kill_feed.friendly_fire", "Friendly Fire %s %s");
        // 管理员指令
        this.add("msb.command.ai_drop_loot", "AI loot drop set to %s");
        // 现金管理指令
        this.add("msb.command.money_get", "Money %s$ (earned %s$ | spent %s$)");
        this.add("msb.command.money_add", "Added %s$, now %s$");
        this.add("msb.command.money_set", "Money set to %s$");
        // 配装商店（M2，docs/02 §3.4）
        this.add("key.msb.shop", "Armory");
        this.add("msb.shop.title", "Armory");
        this.add("msb.shop.category.faction", "Faction Store");
        this.add("msb.shop.category.primary", "Primary");
        this.add("msb.shop.category.secondary", "Sidearm");
        this.add("msb.shop.category.ammo", "Ammo");
        this.add("msb.shop.category.armor", "Armor");
        this.add("msb.shop.category.throwable", "Throwables");
        this.add("msb.shop.category.utility", "Utility");
        this.add("msb.shop.category.honor", "Honor Store");
        this.add("msb.shop.honor", "Honor %s");
        this.add("msb.shop.empty", "No items");
        this.add("msb.shop.group.equip", "Equipment");
        this.add("msb.shop.equip.labels", "Helmet/Chest/Legs/Boots/Offhand");
        this.add("msb.shop.group.acc", "Accessories");
        this.add("msb.shop.group.main", "Inventory");
        this.add("msb.shop.group.hotbar", "Hotbar");
        this.add("msb.shop.storage", "Storage %s/%s");
        this.add("msb.shop.refund_tip", "Green frame: %s item(s) refund at full price");
        this.add("msb.shop.held_count", "%s item(s)");
        this.add("msb.shop.price_line", "Unit price %s");
        this.add("msb.shop.total", "Total %s");
        this.add("msb.shop.income", "Income %s");
        this.add("msb.shop.held.line", "Held %s - refundable %s");
        this.add("msb.shop.held.unsellable", "Held %s - not purchasable");
        this.add("msb.shop.tip.weight", "Weight %s kg");
        this.add("msb.shop.tip.sell", "Sell-back %s");
        this.add("msb.shop.buy", "Buy");
        this.add("msb.shop.sell", "Sell");
        this.add("msb.shop.take", "Take out");
        this.add("msb.shop.store", "Store");
        this.add("msb.shop.loading", "Waiting for shop catalog...");
        this.add("msb.shop.unsellable", "Not purchasable by the shop");
        this.add("msb.shop.result.failed", "Failed: %s");
        this.add("msb.shop.result.bought", "Bought %s x%s - %s (stored)");
        this.add("msb.shop.result.sold", "Sold %s x%s - +%s");
        this.add("msb.shop.result.sold_refund", "Sold %s x%s - +%s (incl. %s refunded)");
        this.add("msb.shop.result.taken", "Took out %s x%s");
        this.add("msb.shop.result.stored", "Stored %s x%s");
        this.add("msb.shop.error.no_balance", "Not enough money");
        this.add("msb.shop.error.storage_full", "Storage is full");
        this.add("msb.shop.error.bag_full", "Inventory is full");
        this.add("msb.shop.error.over_weight", "Over the weight limit");
        this.add("msb.shop.error.not_enough", "Not enough items");
        this.add("msb.shop.error.unsellable", "This item cannot be sold");
        this.add("msb.shop.error.unknown_item", "Unknown item");
        this.add("msb.shop.error.bad_count", "Invalid amount");
        this.add("msb.shop.error.no_catalog", "Shop catalog unavailable");
        this.add("msb.hud.weight", "Weight %s/%s");
    }

    private void addChinese() {
        // M0 注册项
        this.add("itemGroup.msb", "雇佣兵沙盒射击");
        this.add("block.msb.test_block", "MSB 测试方块");
        this.add("item.msb.test_item", "MSB 测试物品");
        // 基地方块（M1）
        this.add("block.msb.base_block_lonestar", "铁血工业基地");
        this.add("block.msb.base_block_valkyra", "新苏联特战部基地");
        this.add("block.msb.base_block_manticore", "重要原型基地");
        // AI 实体（M1 实体化）
        this.add("entity.msb.ai_combatant", "AI 作战单位");
        // 阵营显示名
        this.add("team.msb.lonestar", "铁血工业制造");
        this.add("team.msb.valkyra", "新苏联特战部");
        this.add("team.msb.manticore", "重要原型制造");
        // HUD 计分板
        this.add("msb.hud.zone", "控制区 (%s,%s) R=%s");
        this.add("msb.hud.settle", "%s 秒后结算");
        // 聊天播报
        this.add("msb.chat.settle", "控制区结算：");
        this.add("msb.chat.ai_replaced", "AI %s 已被真人玩家顶替");
        // 按键与战术地图
        this.add("key.categories.msb", "雇佣兵沙盒射击");
        this.add("key.msb.tactical_map", "战术地图");
        this.add("msb.tactical_map.title", "战术地图");
        this.add("msb.tactical_map.waiting", "等待对局数据...");
        // 战术地图增强版
        this.add("msb.tactical_map.follow", "跟随");
        this.add("msb.tactical_map.free", "自由");
        this.add("msb.tactical_map.unit", "单位");
        this.add("msb.tactical_map.countdown", "%s 秒");
        this.add("msb.tactical_map.distance", "距离：%s 格");
        this.add("msb.tactical_map.status", "状态：%s");
        this.add("msb.tactical_map.engaged", "交战中");
        this.add("msb.tactical_map.hidden", "隐蔽");
        this.add("msb.tactical_map.cursor", "X %s Z %s  |  距你 %s 格");
        this.add("msb.tactical_map.hint", "滚轮/右侧滑块：缩放 | 拖动：平移 | 右上：锁定北 | 右下：圈/我");
        this.add("msb.tactical_map.tp.creative_only", "传送 (T)：仅创造模式");
        this.add("msb.tactical_map.tp.out_of_range", "传送失败：目标不可达");
        // 按钮
        this.add("msb.tactical_map.lock", "锁定");
        this.add("msb.tactical_map.unlock", "解锁");
        this.add("msb.tactical_map.btn.zone_center", "控制区");
        this.add("msb.tactical_map.edge.me", "你：%s 格");
        this.add("msb.tactical_map.edge.zone", "控制区：%s 格");
        this.add("msb.tactical_map.legend.spawn", "出生点");
        this.add("msb.tactical_map.legend.zone", "控制区");
        this.add("msb.tactical_map.legend.boundary", "地图边界");
        this.add("msb.tactical_map.legend.friendly", "友方 (T)");
        this.add("msb.tactical_map.legend.enemy", "敌方 (E · 交战/AI)");
        this.add("msb.tactical_map.legend.neutral", "未分配");
        this.add("msb.tactical_map.legend.self", "你");
        this.add("msb.tactical_map.legend.base", "基地");
        // 击杀提示（M2）
        this.add("msb.kill_feed.annihilate", "歼灭 %s %s");
        this.add("msb.kill_feed.info", "击杀x %s|经验 %s");
        this.add("msb.kill_feed.friendly_fire", "友伤 %s %s");
        // 管理员指令
        this.add("msb.command.ai_drop_loot", "AI 战利品掉落已设为 %s");
        // 现金管理指令
        this.add("msb.command.money_get", "现金 %s$（本命赚取 %s$ | 已花费 %s$）");
        this.add("msb.command.money_add", "已增加 %s$，当前 %s$");
        this.add("msb.command.money_set", "现金已设为 %s$");
        // 配装商店（M2）
        this.add("key.msb.shop", "军火商店");
        this.add("msb.shop.title", "军火商店");
        this.add("msb.shop.category.faction", "阵营商店");
        this.add("msb.shop.category.primary", "主武器");
        this.add("msb.shop.category.secondary", "副武器");
        this.add("msb.shop.category.ammo", "弹药");
        this.add("msb.shop.category.armor", "护甲");
        this.add("msb.shop.category.throwable", "投掷物");
        this.add("msb.shop.category.utility", "工具");
        this.add("msb.shop.category.honor", "荣誉商店");
        this.add("msb.shop.honor", "荣誉 %s");
        this.add("msb.shop.empty", "暂无商品");
        this.add("msb.shop.group.equip", "装备栏");
        this.add("msb.shop.equip.labels", "头盔/胸甲/护腿/靴子/副手");
        this.add("msb.shop.group.acc", "饰品栏");
        this.add("msb.shop.group.main", "物品栏");
        this.add("msb.shop.group.hotbar", "快捷栏");
        this.add("msb.shop.storage", "储存格 %s/%s");
        this.add("msb.shop.refund_tip", "绿框：%s 件可按原价卖回");
        this.add("msb.shop.held_count", "%s 件");
        this.add("msb.shop.price_line", "单价 %s");
        this.add("msb.shop.total", "合计 %s");
        this.add("msb.shop.income", "收入 %s");
        this.add("msb.shop.held.line", "持有 %s · 可无损 %s");
        this.add("msb.shop.held.unsellable", "持有 %s · 不可收购");
        this.add("msb.shop.tip.weight", "负重 %s kg");
        this.add("msb.shop.tip.sell", "六折卖回 %s");
        this.add("msb.shop.buy", "购买");
        this.add("msb.shop.sell", "卖出");
        this.add("msb.shop.take", "取回背包");
        this.add("msb.shop.store", "存入储存格");
        this.add("msb.shop.loading", "等待商店目录下发...");
        this.add("msb.shop.unsellable", "商店不可收购");
        this.add("msb.shop.result.failed", "失败：%s");
        this.add("msb.shop.result.bought", "已购买 %s ×%s · %s（已存入储存格）");
        this.add("msb.shop.result.sold", "已出售 %s ×%s · +%s");
        this.add("msb.shop.result.sold_refund", "已出售 %s ×%s · +%s（含无损 %s 件）");
        this.add("msb.shop.result.taken", "已取回 %s ×%s（绿框已失效）");
        this.add("msb.shop.result.stored", "已存入 %s ×%s");
        this.add("msb.shop.error.no_balance", "余额不足");
        this.add("msb.shop.error.storage_full", "储存格已满");
        this.add("msb.shop.error.bag_full", "背包已满");
        this.add("msb.shop.error.over_weight", "负重超限");
        this.add("msb.shop.error.not_enough", "数量不足");
        this.add("msb.shop.error.unsellable", "该物品不可收购");
        this.add("msb.shop.error.unknown_item", "商品不存在");
        this.add("msb.shop.error.bad_count", "数量非法");
        this.add("msb.shop.error.no_catalog", "商店目录不可用");
        this.add("msb.hud.weight", "负重 %s/%s");
    }
}