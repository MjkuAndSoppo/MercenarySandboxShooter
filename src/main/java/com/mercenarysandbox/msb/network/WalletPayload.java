package com.mercenarysandbox.msb.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.client.ClientMatchState;

/**
 * S2C 钱包同步载荷（个人数据，仅发玩家本人）：本条命赚到的钱、本对局花销与总资产。
 * 资产是玩家私有数据，不进全局 MatchState 广播（防泄露/防作弊）。
 */
public record WalletPayload(int earned, int spent, int total) implements CustomPacketPayload {

    public static final Type<WalletPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "wallet"));

    public static final StreamCodec<FriendlyByteBuf, WalletPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WalletPayload::earned,
            ByteBufCodecs.VAR_INT, WalletPayload::spent,
            ByteBufCodecs.VAR_INT, WalletPayload::total,
            WalletPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：缓存本人钱包（HUD 财产显示只读此结果，服务端权威） */
    public static void handle(WalletPayload payload, IPayloadContext context) {
        ClientMatchState.setWallet(payload.earned(), payload.spent(), payload.total());
    }
}