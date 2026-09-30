package com.mercenarysandbox.msb.network;

import java.util.ArrayList;
import java.util.List;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.client.ClientShopData;
import com.mercenarysandbox.msb.shop.ShopStorage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C 个人储存格同步（玩家加入时 + 每次交易/转移后）。
 * 仅发本人（个人数据，服务端权威）。
 */
public record ShopStoragePayload(List<Stack> stacks) implements CustomPacketPayload {

    /** 传输堆叠（refund = 可无损卖回件数） */
    public record Stack(ResourceLocation item, int count, int refund) {
    }

    public static final Type<ShopStoragePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "shop_storage"));

    public static final StreamCodec<FriendlyByteBuf, ShopStoragePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.stacks().size());
                for (Stack s : p.stacks()) {
                    ResourceLocation.STREAM_CODEC.encode(buf, s.item());
                    buf.writeVarInt(s.count());
                    buf.writeVarInt(s.refund());
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<Stack> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(new Stack(ResourceLocation.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));
                }
                return new ShopStoragePayload(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ShopStoragePayload from(ShopStorage storage) {
        List<Stack> list = new ArrayList<>();
        for (ShopStorage.Stack s : storage.stacks()) {
            list.add(new Stack(s.item(), s.count(), s.refund()));
        }
        return new ShopStoragePayload(list);
    }

    public static void handle(ShopStoragePayload payload, IPayloadContext context) {
        ClientShopData.accept(payload);
    }
}