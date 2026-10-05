package com.mercenarysandbox.msb.stamina;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 耐力 DataAttachment 注册（挂 {@code ServerPlayer}，序列化 + copyOnDeath，docs/02 §3.15）。
 */
public final class StaminaAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MercenarySandboxShooter.MODID);

    /** 玩家耐力（唯一真值；原版饱食度/饱和度被压平为空转） */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StaminaData>> STAMINA =
            ATTACHMENT_TYPES.register("stamina",
                    () -> AttachmentType.builder(() -> StaminaData.DEFAULT)
                            .serialize(StaminaData.CODEC).copyOnDeath().build());

    private StaminaAttachments() {
    }
}
