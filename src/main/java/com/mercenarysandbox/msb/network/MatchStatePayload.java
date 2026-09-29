package com.mercenarysandbox.msb.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.client.ClientMatchState;

/**
 * S2C 对局状态载荷（每 2s 广播）：控制区信息 + 三方分数 + 结算倒计时（秒）+
 * 三阵营基地方块坐标（basePositions，顺序 LONESTAR/VALKYRA/MANTICORE，各 {x,z,y}，未放置=-1 表示不可用）。
 */
public record MatchStatePayload(
        int zoneCenterX, int zoneCenterZ, int zoneRadius,
        int countdownSeconds,
        int lonestarScore, int valkyraScore, int manticoreScore,
        int[] basePositions) implements CustomPacketPayload {

    public static final Type<MatchStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "match_state"));

    public static final StreamCodec<FriendlyByteBuf, MatchStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.zoneCenterX());
                buf.writeVarInt(p.zoneCenterZ());
                buf.writeVarInt(p.zoneRadius());
                buf.writeVarInt(p.countdownSeconds());
                buf.writeVarInt(p.lonestarScore());
                buf.writeVarInt(p.valkyraScore());
                buf.writeVarInt(p.manticoreScore());
                buf.writeVarIntArray(p.basePositions());
            },
            buf -> new MatchStatePayload(
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：缓存到 ClientMatchState 供 HUD 自绘（playToClient 载荷，默认主线程执行） */
    public static void handle(MatchStatePayload payload, IPayloadContext context) {
        ClientMatchState.accept(payload);
    }
}
