package com.mercenarysandbox.msb.onboarding;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 雇佣兵档案 DataAttachment 注册（挂 ServerPlayer，序列化 + copyOnDeath）。
 */
public final class MercenaryProfileAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MercenarySandboxShooter.MODID);

    /** 玩家雇佣兵档案（默认中档 ×1.0） */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MercenaryProfile>> PROFILE =
            ATTACHMENT_TYPES.register("mercenary_profile",
                    () -> AttachmentType.builder(() -> MercenaryProfile.DEFAULT)
                            .serialize(MercenaryProfile.CODEC).copyOnDeath().build());

    private MercenaryProfileAttachments() {
    }
}