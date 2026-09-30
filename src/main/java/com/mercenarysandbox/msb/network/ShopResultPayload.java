package com.mercenarysandbox.msb.network;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.client.ClientShopData;
import com.mercenarysandbox.msb.shop.ShopCode;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C 交易/转移回执：动作 + 结果码 + 物品 + 件数 + 金额变化 + 其中无损卖回件数。
 * 客户端本地化后以 toast 呈现（docs §3/§4）。
 */
public record ShopResultPayload(ShopTradePayload.Action action, ShopCode code, ResourceLocation item,
        int count, int moneyDelta, int refunded) implements CustomPacketPayload {

    public static final Type<ShopResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "shop_result"));

    public static final StreamCodec<FriendlyByteBuf, ShopResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.action().ordinal());
                buf.writeVarInt(p.code().ordinal());
                ResourceLocation.STREAM_CODEC.encode(buf, p.item());
                buf.writeVarInt(p.count());
                buf.writeVarInt(p.moneyDelta());
                buf.writeVarInt(p.refunded());
            },
            buf -> new ShopResultPayload(
                    ShopTradePayload.Action.values()[Math.min(buf.readVarInt(), ShopTradePayload.Action.values().length - 1)],
                    ShopCode.byOrdinal(buf.readVarInt()),
                    ResourceLocation.STREAM_CODEC.decode(buf),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public boolean ok() {
        return code == ShopCode.OK;
    }

    public static void handle(ShopResultPayload payload, IPayloadContext context) {
        ClientShopData.accept(payload);
    }
}