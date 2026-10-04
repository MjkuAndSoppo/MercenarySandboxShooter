package com.mercenarysandbox.msb.shop;

/**
 * 商店分类（枚举顺序 = 分类栏显示顺序：阵营商店置顶、荣誉商店垫底；
 * datapack 文件名 = id，语言键 {@code msb.shop.category.<id>}）。
 * 见 .trae/documents/m2-shop-design.md §2。
 */
public enum ShopCategory {
    /** 阵营商店（置顶）：条目带阵营归属，服务端只下发玩家本阵营的装备 */
    FACTION("faction"),
    /** 枪械（主/副武器合并）：条目用 {@link GunType} 再细分 */
    GUNS("guns"),
    AMMO("ammo"),
    /** 装备（护甲 + 工具合并）：条目用 {@link EquipType} 再细分 */
    EQUIPMENT("equipment"),
    THROWABLE("throwable"),
    /** 荣誉商店（垫底）：荣誉点结算，内容与获得方式待定 */
    HONOR("honor");

    private final String id;

    ShopCategory(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getLangKey() {
        return "msb.shop.category." + id;
    }

    /** 按 datapack 文件路径解析分类；未知分类返回 null（调用方跳过并告警） */
    public static ShopCategory byId(String id) {
        for (ShopCategory c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }
}