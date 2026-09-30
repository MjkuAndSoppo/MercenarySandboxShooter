package com.mercenarysandbox.msb.network;

import java.util.ArrayList;
import java.util.List;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.client.ClientShopData;
import com.mercenarysandbox.msb.economy.HonorAttachments;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.shop.ShopCatalog;
import com.mercenarysandbox.msb.shop.ShopEntry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C 商品目录同步（玩家加入 / /reload 时下发）：条目 + 展示用系数 + 储存格容量。
 * 客户端缓存到 {@link ClientShopData}，打开窗口零请求（docs §3）。
 */
public record ShopDataPayload(List<Entry> entries, double sellRatio, double refundRate, int storageSlots,
        int honorPoints) implements CustomPacketPayload {

    /** 传输条目（category 为枚举序，客户端按序映射语言键） */
    public record Entry(ResourceLocation item, int category, int price, int sell, double weight) {
    }

    public static final Type<ShopDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "shop_data"));

    public static final StreamCodec<FriendlyByteBuf, ShopDataPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entries().size());
                for (Entry e : p.entries()) {
                    ResourceLocation.STREAM_CODEC.encode(buf, e.item());
                    buf.writeVarInt(e.category());
                    buf.writeVarInt(e.price());
                    buf.writeVarInt(e.sell());
                    buf.writeFloat((float) e.weight());
                }
                buf.writeDouble(p.sellRatio());
                buf.writeDouble(p.refundRate());
                buf.writeVarInt(p.storageSlots());
                buf.writeVarInt(p.honorPoints());
            },
            buf -> {
                int size = buf.readVarInt();
                List<Entry> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(new Entry(
                            ResourceLocation.STREAM_CODEC.decode(buf),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat()));
                }
                return new ShopDataPayload(list, buf.readDouble(), buf.readDouble(),
                        buf.readVarInt(), buf.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端组包：按玩家阵营过滤的目录快照 + 配置系数 + 本人荣誉点 */
    public static ShopDataPayload from(ShopCatalog catalog, ServerPlayer player) {
        Faction viewer = FactionManager.getPlayerFaction(player);
        List<Entry> list = new ArrayList<>();
        for (ShopEntry e : catalog.all()) {
            if (!e.visibleTo(viewer)) {
                continue;                       // 阵营专属装备：仅本阵营可见
            }
            list.add(new Entry(e.item(), e.category().ordinal(), e.price(), e.sell(), e.weight()));
        }
        return new ShopDataPayload(list,
                Config.SHOP_SELL_RATIO.get(), Config.SHOP_REFUND_RATE.get(), Config.SHOP_STORAGE_SLOTS.get(),
                player.getData(HonorAttachments.HONOR));
    }

    public static void handle(ShopDataPayload payload, IPayloadContext context) {
        ClientShopData.accept(payload);
    }
}