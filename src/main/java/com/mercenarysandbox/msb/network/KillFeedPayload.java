package com.mercenarysandbox.msb.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.client.ClientKillFeed;

/**
 * S2C 击杀提示载荷（仅发击杀者本人）：携带目标名称、本次击杀金钱/经验变动与本命连杀数。
 * 目标名称：玩家/AI 名传字面文本（isPlayer=true），怪物传翻译键（实体描述 key，客户端本地化）。
 * friendlyFire=true 表示同阵营 AI 友伤：客户端仅显示一行「友伤 <名称> -xx$」，不显示下两行。
 */
public record KillFeedPayload(String targetKey, boolean isPlayer, int money, int xp, int streak, boolean friendlyFire)
        implements CustomPacketPayload {

    public static final Type<KillFeedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "kill_feed"));

    public static final StreamCodec<FriendlyByteBuf, KillFeedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, KillFeedPayload::targetKey,
            ByteBufCodecs.BOOL, KillFeedPayload::isPlayer,
            ByteBufCodecs.VAR_INT, KillFeedPayload::money,
            ByteBufCodecs.VAR_INT, KillFeedPayload::xp,
            ByteBufCodecs.VAR_INT, KillFeedPayload::streak,
            ByteBufCodecs.BOOL, KillFeedPayload::friendlyFire,
            KillFeedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：缓存本次击杀提示（MsbKillFeedOverlay 渲染，数字滚动动画以接收时刻为起点） */
    public static void handle(KillFeedPayload payload, IPayloadContext context) {
        ClientKillFeed.show(payload);
    }
}