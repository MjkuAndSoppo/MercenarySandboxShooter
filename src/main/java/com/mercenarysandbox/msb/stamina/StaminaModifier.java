package com.mercenarysandbox.msb.stamina;

import net.minecraft.server.level.ServerPlayer;

/**
 * 耐力能力接口（预留，docs/02 §3.15）：外部系统（装备/增益/减益）可动态改写耐力上限与每秒回复量，
 * 经 {@link StaminaManager#registerModifier} 注册后按注册顺序折叠求值，无需改动核心循环。
 *
 * <p>两个方法均接收当前累积值 {@code base}（首个修改器收到的是配置默认值），返回改写后的值。</p>
 */
public interface StaminaModifier {

    /** 改写耐力上限（base = 配置默认上限；返回最终上限） */
    default double maxStamina(ServerPlayer player, double base) {
        return base;
    }

    /** 改写每秒回复量（base = 配置推导的每秒回复量；返回最终每秒回复量） */
    default double regenPerSecond(ServerPlayer player, double base) {
        return base;
    }
}
