package com.mercenarysandbox.msb.shop;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * 个人储存格（服务端权威，挂在 ServerPlayer 附体上；见 docs §3/§4）。
 *
 * <p>模型：有序堆叠列表（列表下标即 UI「储存格」格位；容量 = Config.shopStorageSlots 个堆叠）。
 * 每个堆叠记录 {@code refund} = 其中仍可「无损卖回（100% 原价）」的件数：
 * 购买后计入；取回玩家栏即失效（{@code refund = min(refund, count)}）；玩家物品存入不获得。
 */
public final class ShopStorage {

    /** 单格最大堆叠（与背包一致） */
    public static final int MAX_STACK = 64;

    public static final Codec<ShopStorage> CODEC =
            Stack.CODEC.listOf().xmap(ShopStorage::new, ShopStorage::stacks);

    /** 储存格堆叠（可变，直接原地修改后随附体保存） */
    public static final class Stack {
        public static final Codec<Stack> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Stack::item),
                Codec.INT.fieldOf("count").forGetter(Stack::count),
                Codec.INT.optionalFieldOf("refund", 0).forGetter(Stack::refund)
        ).apply(inst, Stack::new));

        private final ResourceLocation item;
        private int count;
        private int refund;

        public Stack(ResourceLocation item, int count, int refund) {
            this.item = item;
            this.count = count;
            this.refund = Math.max(0, Math.min(refund, count));
        }

        public ResourceLocation item() {
            return item;
        }

        public int count() {
            return count;
        }

        public int refund() {
            return refund;
        }

        /** 数量变化（取出/卖出）后同步压缩无损额度 */
        public void setCount(int value) {
            this.count = Math.max(0, value);
            this.refund = Math.min(this.refund, this.count);
        }

        /** 购买入库：数量与无损额度同时增加 */
        public void addPurchased(int amount) {
            this.count += amount;
            this.refund = Math.min(this.count, this.refund + amount);
        }

        /** 玩家物品存入：只加数量，不获得无损额度 */
        public void addStored(int amount) {
            this.count += amount;
        }

        public int room() {
            return Math.max(0, MAX_STACK - count);
        }
    }

    private final List<Stack> stacks = new ArrayList<>();

    public ShopStorage() {
    }

    public ShopStorage(List<Stack> initial) {
        this.stacks.addAll(initial);
    }

    public List<Stack> stacks() {
        return stacks;
    }

    public Stack get(int index) {
        return index >= 0 && index < stacks.size() ? stacks.get(index) : null;
    }

    public void remove(int index) {
        if (index >= 0 && index < stacks.size()) {
            stacks.remove(index);
        }
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }
}