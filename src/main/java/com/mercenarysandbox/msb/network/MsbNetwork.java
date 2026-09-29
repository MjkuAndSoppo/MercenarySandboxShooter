package com.mercenarysandbox.msb.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 网络载荷注册（MOD 总线）：play 阶段载荷。
 * 除战术地图创造模式传送外均为 S2C —— 服务端权威，客户端不做逻辑判定。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class MsbNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(MatchStatePayload.TYPE, MatchStatePayload.STREAM_CODEC, MatchStatePayload::handle);
        registrar.playToClient(SyncFactionPayload.TYPE, SyncFactionPayload.STREAM_CODEC, SyncFactionPayload::handle);
        registrar.playToClient(UnitPositionsPayload.TYPE, UnitPositionsPayload.STREAM_CODEC, UnitPositionsPayload::handle);
        registrar.playToServer(TeleportRequestPayload.TYPE, TeleportRequestPayload.STREAM_CODEC, TeleportRequestPayload::handle);
    }

    private MsbNetwork() {
    }
}
