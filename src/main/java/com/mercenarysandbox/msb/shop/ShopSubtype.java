package com.mercenarysandbox.msb.shop;

/**
 * 分类内子分类（枪械栏用 {@link GunType}、装备栏用 {@link EquipType}）。
 * datapack 字段 {@code subtype}；网络传输按 {@link #ordinal()} 枚举序，客户端按分类还原。
 */
public interface ShopSubtype {

    String getId();

    String getLangKey();

    /** 枚举序（网络传输 / 客户端还原；由各枚举的 {@link Enum#ordinal()} 提供） */
    int ordinal();
}