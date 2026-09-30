package com.mercenarysandbox.msb.economy;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 钱包 DataAttachment 注册。挂在 ServerPlayer 上，序列化后随玩家数据保存 —— 跨重连保持资产。
 */
public final class WalletAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MercenarySandboxShooter.MODID);

    /** 玩家钱包（M1 起用于 HUD 财产显示；M2 现金系统读写此附件） */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerWallet>> WALLET =
            ATTACHMENT_TYPES.register("wallet",
                    () -> AttachmentType.builder(() -> PlayerWallet.DEFAULT).serialize(PlayerWallet.CODEC).build());

    private WalletAttachments() {
    }
}