package com.mercenarysandbox.msb.shop;

import java.util.Locale;

/**
 * 交易/转移结果码（服务端权威判定，客户端按语言键本地化）。
 * 语言键：{@code msb.shop.error.<id>}（OK 不显示）。
 */
public enum ShopCode {
    OK,
    NO_BALANCE,
    STORAGE_FULL,
    BAG_FULL,
    OVER_WEIGHT,
    NOT_ENOUGH,
    UNSELLABLE,
    UNKNOWN_ITEM,
    BAD_COUNT,
    NO_CATALOG,
    /** 荣誉点不足（荣誉商店专用） */
    NO_HONOR;

    public String getLangKey() {
        return "msb.shop.error." + name().toLowerCase(Locale.ROOT);
    }

    /** 网络解码（越界视为 BAD_COUNT，防改包崩溃） */
    public static ShopCode byOrdinal(int ordinal) {
        ShopCode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BAD_COUNT;
    }
}