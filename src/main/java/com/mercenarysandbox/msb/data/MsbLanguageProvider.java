package com.mercenarysandbox.msb.data;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 语言文件（en_us）生成：阵营名、HUD 文案、聊天播报。
 */
public final class MsbLanguageProvider extends LanguageProvider {
    public MsbLanguageProvider(PackOutput output) {
        super(output, MercenarySandboxShooter.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // M0 注册项（原手写 en_us.json 已迁移至此，由 datagen 统一管理）
        this.add("itemGroup.msb", "Mercenary Sandbox Shooter");
        this.add("block.msb.test_block", "MSB Test Block");
        this.add("item.msb.test_item", "MSB Test Item");
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
    }
}
