package com.mercenarysandbox.msb.shop;

/**
 * 装备子分类（{@link ShopCategory#EQUIPMENT} 栏目内的筛选维度；datapack 字段 {@code subtype}）。
 * 语言键 {@code msb.shop.equip.type.<id>}。筛选条只展示当前目录里实际存在的子分类。
 */
public enum EquipType implements ShopSubtype {
    /** 护甲（头盔 / 胸甲等） */
    ARMOR("armor"),
    /** 道具（刀 / 医疗包 / 拆弹器 / 维修工具 / 降落伞等） */
    ITEM("item");

    private final String id;

    EquipType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLangKey() {
        return "msb.shop.equip.type." + id;
    }

    /** 按 datapack 名解析；未知返回 null（调用方告警并跳过该条目） */
    public static EquipType byId(String id) {
        for (EquipType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** 按枚举序还原（传输用）；越界返回 null（未分类） */
    public static EquipType byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
    }
}