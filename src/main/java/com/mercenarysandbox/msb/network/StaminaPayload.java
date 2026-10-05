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
 * S2C 耐力同步载荷（个人数据，仅发玩家本人）：精确耐力百分比（0~1 浮点）。
 *
 * <p>原版生命/食物同步包只能携带整数 {@code foodLevel}（1/20 = 5% 量化），HUD 因此呈阶梯状跳变；
 * 本载荷以浮点携带真实百分比，供自绘耐力条**线性平滑**渲染（docs/02 §3.15）。
 * 客户端在收到前回退到 {@code foodLevel / 20}，故不依赖本包也能工作。</p>
 */
public record StaminaPayload(float percent) implements CustomPacketPayload {

    public static final Type<StaminaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "stamina"));

    public static final StreamCodec<FriendlyByteBuf, StaminaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, StaminaPayload::percent,
            StaminaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：缓存本人耐力百分比（HUD 只读此结果，服务端权威） */
    public static void handle(StaminaPayload payload, IPayloadContext context) {
        ClientMatchState.setStaminaPercent(payload.percent());
    }
}
