package com.mercenarysandbox.msb.onboarding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * 开局资金档位：初始资金越高，后续击杀收益倍率越低（docs 开局流程）。
 * 仅决定启动资金与击杀金钱倍率；与 AI 数量相互独立。
 */
public enum FundingTier {
    /** 低门槛：3000$，击杀收益 ×1.5 */
    TIER_LOW(0, 3000, 1.5D),
    /** 中档：8000$，击杀收益 ×1.0 */
    TIER_MID(1, 8000, 1.0D),
    /** 高门槛：20000$，击杀收益 ×0.8 */
    TIER_HIGH(2, 20000, 0.8D);

    private final int id;
    private final int money;
    private final double killMultiplier;

    FundingTier(int id, int money, double killMultiplier) {
        this.id = id;
        this.money = money;
        this.killMultiplier = killMultiplier;
    }

    public int getId() {
        return id;
    }

    /** 初始资金（$） */
    public int money() {
        return money;
    }

    /** 击杀收益倍率 */
    public double killMultiplier() {
        return killMultiplier;
    }

    /** 按网络 id 取档位；未知 id 回退中档（防改包/旧数据） */
    public static FundingTier byId(int id) {
        for (FundingTier t : values()) {
            if (t.id == id) {
                return t;
            }
        }
        return TIER_MID;
    }

    /** 序列化用 Codec（按名；未知值报错，由调用方兜底） */
    public static final Codec<FundingTier> CODEC = Codec.STRING.flatXmap(
            name -> {
                for (FundingTier t : values()) {
                    if (t.name().equalsIgnoreCase(name)) {
                        return DataResult.success(t);
                    }
                }
                return DataResult.error(() -> "未知资金档位: " + name);
            },
            t -> DataResult.success(t.name()));
}