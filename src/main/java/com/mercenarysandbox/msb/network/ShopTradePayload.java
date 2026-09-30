package com.mercenarysandbox.msb.network;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.shop.ShopManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S 交易/转移请求（docs §3）：动作 + 来源区 + 槽位 + 目标槽位 + 物品 + 件数。
 * <b>不携带价格</b>——服务端以自持目录查价（防改包），并校验槽位内物品与 itemId 一致。
 *
 * @param slot       来源槽位（BUY 忽略）
 * @param targetSlot TAKE 的目标背包格（0-8 快捷栏 / 9-35 物品栏；-1 = 自动入包）
 */
public record ShopTradePayload(Action action, Zone zone, int slot, int targetSlot, ResourceLocation item, int count)
        implements CustomPacketPayload {

    /** 目标槽位缺省（自动入包） */
    public static final int TARGET_AUTO = -1;

    /** 便捷构造：不带精确落点（服务端自动入包） */
    public ShopTradePayload(Action action, Zone zone, int slot, ResourceLocation item, int count) {
        this(action, zone, slot, TARGET_AUTO, item, count);
    }

    /** BUY 忽略 zone/slot；SELL/TAKE 的 zone 指向来源 */
    public enum Action {
        BUY, SELL, TAKE, STORE
    }

    /** 来源/目标区：储存格 + 玩家栏四区 */
    public enum Zone {
        STORAGE, ARMOR, OFFHAND, MAIN, HOTBAR
    }

    public static final Type<ShopTradePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "shop_trade"));

    public static final StreamCodec<FriendlyByteBuf, ShopTradePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.action().ordinal());
                buf.writeVarInt(p.zone().ordinal());
                buf.writeVarInt(p.slot());
                buf.writeVarInt(p.targetSlot());
                ResourceLocation.STREAM_CODEC.encode(buf, p.item());
                buf.writeVarInt(p.count());
            },
            buf -> new ShopTradePayload(
                    byteToEnum(Action.values(), buf.readVarInt(), Action.BUY),
                    byteToEnum(Zone.values(), buf.readVarInt(), Zone.STORAGE),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    ResourceLocation.STREAM_CODEC.decode(buf),
                    buf.readVarInt()));

    private static <T> T byteToEnum(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端处理（服务端权威：全部校验与结算在 {@link ShopManager}） */
    public static void handle(ShopTradePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ShopManager.handle(player, payload);
            }
        });
    }
}