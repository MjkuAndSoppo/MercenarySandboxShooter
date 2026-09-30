package com.mercenarysandbox.msb.shop;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 个人储存格 DataAttachment（挂 ServerPlayer，序列化随玩家数据保存）。
 * {@code copyOnDeath()}：死亡重生自动携带（跨死亡/重连保持，见 docs §4/§11）。
 */
public final class ShopStorageAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MercenarySandboxShooter.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ShopStorage>> STORAGE =
            ATTACHMENT_TYPES.register("shop_storage",
                    () -> AttachmentType.builder(() -> new ShopStorage())
                            .serialize(ShopStorage.CODEC)
                            .copyOnDeath()
                            .build());

    private ShopStorageAttachments() {
    }
}