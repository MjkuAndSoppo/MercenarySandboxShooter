package com.mercenarysandbox.msb.shop;

import net.minecraft.resources.ResourceLocation;

import com.mercenarysandbox.msb.faction.Faction;

/**
 * 商品目录条目（服务端权威价格与负重；客户端仅用于展示）。
 *
 * @param item     物品注册名（SBW 等，仅引用公开注册名）
 * @param category 分类
 * @param faction  阵营归属（仅 {@link ShopCategory#FACTION} 用；null = 通用商品）
 * @param subtype  枪械子分类（仅 {@link ShopCategory#GUNS} 用；null = 未分类）
 * @param price    买入价（$，≥0；0 = 免费）
 * @param sell     卖出覆盖价（$；≤0 表示未设置，按 price × Config.shopSellRatio 计算）
 * @param weight   单件负重（kg，≥0）
 */
public record ShopEntry(ResourceLocation item, ShopCategory category, Faction faction, GunType subtype,
        int price, int sell, double weight) {

    /** 是否对该阵营可见（通用商品对所有人可见；阵营专属只看本阵营） */
    public boolean visibleTo(Faction viewer) {
        return faction == null || faction == viewer;
    }

    /** 普通卖价（六折档）：显式 sell 优先，否则 price × 系数四舍五入 */
    public int sellPrice(double ratio) {
        return sell > 0 ? sell : (int) Math.round(price * ratio);
    }
}