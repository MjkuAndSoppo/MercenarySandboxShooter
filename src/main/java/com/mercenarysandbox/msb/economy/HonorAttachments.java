package com.mercenarysandbox.msb.economy;

import com.mojang.serialization.Codec;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 荣誉点 DataAttachment（独立货币，仅在荣誉商店页面可见）。
 * 获得方式待定（暂不产生），跨死亡保留（累计成就口径）。
 */
public final class HonorAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MercenarySandboxShooter.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> HONOR =
            ATTACHMENT_TYPES.register("honor_points",
                    () -> AttachmentType.builder(() -> 0)
                            .serialize(Codec.INT)
                            .copyOnDeath()
                            .build());

    private HonorAttachments() {
    }
}