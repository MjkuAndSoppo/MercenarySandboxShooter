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
    }
}
