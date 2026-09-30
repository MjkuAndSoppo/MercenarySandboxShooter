package com.mercenarysandbox.msb.match;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mojang.serialization.Codec;
import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 本命击杀数 DataAttachment（挂在 ServerPlayer 上，随玩家数据持久化）。
 * 「击杀x n」显示当前这条命累计击杀数量，玩家死亡时清零（docs/02 击杀提示）。
 */
public final class KillStreakAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MercenarySandboxShooter.MODID);

    /** 本命击杀数（int，死亡清零） */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> STREAK =
            ATTACHMENT_TYPES.register("kill_streak",
                    () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private KillStreakAttachments() {
    }
}