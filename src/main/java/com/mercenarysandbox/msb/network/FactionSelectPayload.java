package com.mercenarysandbox.msb.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.onboarding.OnboardingManager;

/**
 * C2S 阵营选择载荷（手册确认时提交）：阵营 id + 资金档位 id + 初始 AI 数量。
 * 服务端权威校验后设置阵营/资金/档案/AI 目标并传送（无基地则提示）。
 */
public record FactionSelectPayload(int factionId, int tierId, int aiCount) implements CustomPacketPayload {

    public static final Type<FactionSelectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "faction_select"));

    public static final StreamCodec<FriendlyByteBuf, FactionSelectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FactionSelectPayload::factionId,
            ByteBufCodecs.VAR_INT, FactionSelectPayload::tierId,
            ByteBufCodecs.VAR_INT, FactionSelectPayload::aiCount,
            FactionSelectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端处理：应用开局选择（防重复开局/越界值在 OnboardingManager 内校验） */
    public static void handle(FactionSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                OnboardingManager.applyChoice(player, payload.factionId(), payload.tierId(), payload.aiCount());
            }
        });
    }
}