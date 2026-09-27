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
 * S2C 阵营同步载荷（玩家加入/改派时下发）：携带玩家阵营的网络 id。
 */
public record SyncFactionPayload(int factionId) implements CustomPacketPayload {

    public static final Type<SyncFactionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "sync_faction"));

    public static final StreamCodec<FriendlyByteBuf, SyncFactionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncFactionPayload::factionId, SyncFactionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：缓存自身阵营（IFF/队伍色渲染只读此结果，服务端权威） */
    public static void handle(SyncFactionPayload payload, IPayloadContext context) {
        ClientMatchState.setOwnFaction(payload.factionId());
    }
}
