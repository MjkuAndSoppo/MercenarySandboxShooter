package com.mercenarysandbox.msb.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.client.ClientMatchState;

/**
 * S2C 战术地图单位列表载荷（每 4 tick 广播，与 AI 惰性推进同频，docs/02 §3.9）。
 * 内容：地图中心（= 控制区中心/出生点）、地图边界半径、全部可显示单位（含交战标记）。
 * 可见性规则：敌方仅发送「已交战单位 + 全部 AI 模拟单位」，隐蔽敌人不下发（服务端权威，客户端不推断）。
 */
public record UnitPositionsPayload(
        int mapCenterX, int mapCenterZ, int mapRadius,
        List<UnitEntry> units) implements CustomPacketPayload {

    /** 单个可显示单位：阵营 id + XZ 坐标 + 交战标记（仅服务端可判定） */
    public record UnitEntry(int factionId, int x, int z, boolean engaged) {
    }

    public static final Type<UnitPositionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "unit_positions"));

    public static final StreamCodec<FriendlyByteBuf, UnitPositionsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.mapCenterX());
                buf.writeVarInt(p.mapCenterZ());
                buf.writeVarInt(p.mapRadius());
                buf.writeVarInt(p.units().size());
                for (UnitEntry u : p.units()) {
                    buf.writeVarInt(u.factionId());
                    buf.writeVarInt(u.x());
                    buf.writeVarInt(u.z());
                    buf.writeVarInt(u.engaged() ? 1 : 0);
                }
            },
            buf -> {
                int centerX = buf.readVarInt();
                int centerZ = buf.readVarInt();
                int radius = buf.readVarInt();
                int count = buf.readVarInt();
                List<UnitEntry> units = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    units.add(new UnitEntry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() == 1));
                }
                return new UnitPositionsPayload(centerX, centerZ, radius, units);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：缓存到 ClientMatchState 供战术地图渲染 */
    public static void handle(UnitPositionsPayload payload, IPayloadContext context) {
        ClientMatchState.accept(payload);
    }
}
