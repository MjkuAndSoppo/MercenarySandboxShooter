package com.mercenarysandbox.msb.faction;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 阵营 DataAttachment 注册。
 * 挂在 ServerPlayer 上，序列化后随玩家数据保存 —— 支撑「跨重连保持阵营」。
 */
public final class FactionAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MercenarySandboxShooter.MODID);

    /** 玩家阵营（默认 NONE = 未分配） */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Faction>> FACTION =
            ATTACHMENT_TYPES.register("faction",
                    () -> AttachmentType.builder(() -> Faction.NONE).serialize(Faction.CODEC).build());

    private FactionAttachments() {
    }
}
