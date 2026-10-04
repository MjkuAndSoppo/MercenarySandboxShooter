package com.mercenarysandbox.msb.onboarding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家雇佣兵档案（服务端权威，随附体持久化）：开局选择的资金档位、击杀收益倍率与初始 AI 数量。
 *
 * @param tierId             资金档位 id（对应 {@link FundingTier#getId()}）
 * @param killMultiplierX100 击杀收益倍率 ×100 的整数表示（避免浮点序列化误差）
 * @param aiCount            开局选择的初始 AI 数量
 */
public record MercenaryProfile(int tierId, int killMultiplierX100, int aiCount) {
    /** 默认档案：中档资金、×1.0 倍率、未选 AI 数（管理员/旧玩家兜底） */
    public static final MercenaryProfile DEFAULT = new MercenaryProfile(FundingTier.TIER_MID.getId(), 100, 0);

    public static final Codec<MercenaryProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("tier", FundingTier.TIER_MID.getId()).forGetter(MercenaryProfile::tierId),
            Codec.INT.optionalFieldOf("kill_mult_x100", 100).forGetter(MercenaryProfile::killMultiplierX100),
            Codec.INT.optionalFieldOf("ai_count", 0).forGetter(MercenaryProfile::aiCount))
            .apply(instance, MercenaryProfile::new));

    /** 击杀收益倍率（缺省 1.0） */
    public double killMultiplier() {
        return killMultiplierX100 / 100.0D;
    }
}