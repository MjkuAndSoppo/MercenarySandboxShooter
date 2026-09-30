package com.mercenarysandbox.msb.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家钱包（服务端权威，跨重连保持）。
 *
 * @param spent  本对局已消费金额（购买支出累计）
 * @param total  累计总资产（现金永不丢失，死亡保留）
 * @param earned 本条命赚到的钱（击杀/占区等战斗收益累计，死亡清零）
 */
public record PlayerWallet(int spent, int total, int earned) {
    /** 新玩家默认钱包：无花销、无资产、本命无收入 */
    public static final PlayerWallet DEFAULT = new PlayerWallet(0, 0, 0);

    public static final Codec<PlayerWallet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("spent").forGetter(PlayerWallet::spent),
            Codec.INT.fieldOf("total").forGetter(PlayerWallet::total),
            Codec.INT.optionalFieldOf("earned", 0).forGetter(PlayerWallet::earned))
            .apply(instance, PlayerWallet::new));

    /** 替换花销与总资产，保留本命收入（支出/入账结算用） */
    public PlayerWallet withFinance(int newSpent, int newTotal) {
        return new PlayerWallet(newSpent, newTotal, earned);
    }

    /** 本命收入清零（死亡重生） */
    public PlayerWallet resetLife() {
        return new PlayerWallet(spent, total, 0);
    }
}