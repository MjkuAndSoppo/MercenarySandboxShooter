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
        this.add("block.msb.base_block_lonestar", "Lonestar Base");
        this.add("block.msb.base_block_valkyra", "Valkyra Base");
        this.add("block.msb.base_block_manticore", "Manticore Base");
        // 阵营显示名（计分板队伍 / HUD / Tab）
        this.add("team.msb.lonestar", "LONESTAR");
        this.add("team.msb.valkyra", "VALKYRA");
        this.add("team.msb.manticore", "MANTICORE");
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
    }

    private void addChinese() {
        // M0 注册项
        this.add("itemGroup.msb", "雇佣兵沙盒射击");
        this.add("block.msb.test_block", "MSB 测试方块");
        this.add("item.msb.test_item", "MSB 测试物品");
        // 基地方块（M1）
        this.add("block.msb.base_block_lonestar", "星野孤星基地");
        this.add("block.msb.base_block_valkyra", "瓦尔基里基地");
        this.add("block.msb.base_block_manticore", "曼提柯尔基地");
        // 阵营显示名
        this.add("team.msb.lonestar", "星野孤星");
        this.add("team.msb.valkyra", "瓦尔基里");
        this.add("team.msb.manticore", "曼提柯尔");
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
    }
}