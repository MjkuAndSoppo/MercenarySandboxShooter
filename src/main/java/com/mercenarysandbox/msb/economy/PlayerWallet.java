package com.mercenarysandbox.msb.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家钱包（服务端权威，M1 仅为 HUD 财产显示预留；M2 现金系统在此基础上扩展）。
 * 「当前花销」= 本对局已消费金额；「总资产」= 累计持有金额。
 */
public record PlayerWallet(int spent, int total) {
    /** 新玩家默认钱包：无花销、无资产 */
    public static final PlayerWallet DEFAULT = new PlayerWallet(0, 0);

    public static final Codec<PlayerWallet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("spent").forGetter(PlayerWallet::spent),
            Codec.INT.fieldOf("total").forGetter(PlayerWallet::total))
            .apply(instance, PlayerWallet::new));
}