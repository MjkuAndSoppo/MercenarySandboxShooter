package com.mercenarysandbox.msb.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 网络载荷注册（MOD 总线）：play 阶段载荷。
 * 除战术地图创造传送与商店交易/转移外均为 S2C —— 服务端权威，客户端不做逻辑判定。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class MsbNetwork {
    /** 协议版本：加商店载荷后升为 2（客户端/服务端需同版本） */
    private static final String PROTOCOL_VERSION = "2";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(MatchStatePayload.TYPE, MatchStatePayload.STREAM_CODEC, MatchStatePayload::handle);
        registrar.playToClient(SyncFactionPayload.TYPE, SyncFactionPayload.STREAM_CODEC, SyncFactionPayload::handle);
        registrar.playToClient(UnitPositionsPayload.TYPE, UnitPositionsPayload.STREAM_CODEC, UnitPositionsPayload::handle);
        registrar.playToClient(WalletPayload.TYPE, WalletPayload.STREAM_CODEC, WalletPayload::handle);
        registrar.playToClient(KillFeedPayload.TYPE, KillFeedPayload.STREAM_CODEC, KillFeedPayload::handle);
        registrar.playToServer(TeleportRequestPayload.TYPE, TeleportRequestPayload.STREAM_CODEC, TeleportRequestPayload::handle);
        // 配装商店（docs/02 §3.4）：目录 / 储存格 / 回执（S2C）+ 交易请求（C2S）
        registrar.playToClient(ShopDataPayload.TYPE, ShopDataPayload.STREAM_CODEC, ShopDataPayload::handle);
        registrar.playToClient(ShopStoragePayload.TYPE, ShopStoragePayload.STREAM_CODEC, ShopStoragePayload::handle);
        registrar.playToClient(ShopResultPayload.TYPE, ShopResultPayload.STREAM_CODEC, ShopResultPayload::handle);
        registrar.playToServer(ShopTradePayload.TYPE, ShopTradePayload.STREAM_CODEC, ShopTradePayload::handle);
    }

    private MsbNetwork() {
    }
}