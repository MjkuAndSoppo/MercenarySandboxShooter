package com.mercenarysandbox.msb.shop;

/**
 * 枪械子分类（{@link ShopCategory#GUNS} 栏目内的筛选维度；datapack 字段 {@code subtype}）。
 * 语言键 {@code msb.shop.gun.<id>}。筛选条只展示当前目录里实际存在的子分类。
 */
public enum GunType {
    HANDGUN("handgun"),
    SMG("smg"),
    RIFLE("rifle"),
    SNIPER("sniper"),
    SHOTGUN("shotgun"),
    MG("mg"),
    LAUNCHER("launcher"),
    /** SBW 的 Special 类枪械（泰瑟枪等非致命/工具类） */
    SPECIAL("special");

    private final String id;

    GunType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getLangKey() {
        return "msb.shop.gun." + id;
    }

    /** 按 datapack 名解析；未知返回 null（调用方告警并跳过该条目） */
    public static GunType byId(String id) {
        for (GunType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** 按枚举序还原（传输用）；越界返回 null（未分类） */
    public static GunType byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
    }
}