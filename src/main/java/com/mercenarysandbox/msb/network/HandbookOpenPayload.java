package com.mercenarysandbox.msb.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.client.HandbookScreen;

/**
 * S2C 打开雇佣兵手册载荷（右键手册时服务端下发）：客户端仅负责打开界面，不携带数据。
 */
public record HandbookOpenPayload() implements CustomPacketPayload {

    public static final Type<HandbookOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "handbook_open"));

    public static final StreamCodec<FriendlyByteBuf, HandbookOpenPayload> STREAM_CODEC =
            StreamCodec.unit(new HandbookOpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：打开手册界面（已开局则显示教程占位页） */
    public static void handle(HandbookOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new HandbookScreen()));
    }
}