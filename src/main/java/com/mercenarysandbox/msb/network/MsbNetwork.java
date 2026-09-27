package com.mercenarysandbox.msb.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 网络载荷注册（MOD 总线）：play 阶段 S2C 载荷。
 * 服务端权威 —— 本 mod 不注册任何 C2S 载荷（M1 无玩家输入型玩法）。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class MsbNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(MatchStatePayload.TYPE, MatchStatePayload.STREAM_CODEC, MatchStatePayload::handle);
        registrar.playToClient(SyncFactionPayload.TYPE, SyncFactionPayload.STREAM_CODEC, SyncFactionPayload::handle);
    }

    private MsbNetwork() {
    }
}
