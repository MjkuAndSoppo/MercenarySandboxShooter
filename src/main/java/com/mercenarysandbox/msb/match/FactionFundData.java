package com.mercenarysandbox.msb.match;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.mercenarysandbox.msb.faction.Faction;

/**
 * 阵营基金持久化（SavedData，存于主世界）：三阵营各记录一个累计金额。
 * 来源为 AI 击杀结算的赏金（AI 无个人钱包，收益归阵营）；M3 阵营经济/载具采购的资金池。
 */
public final class FactionFundData extends SavedData {
    public static final String DATA_NAME = "msb_funds";

    private final Map<Faction, Long> funds = new EnumMap<>(Faction.class);

    public static FactionFundData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FactionFundData::new, FactionFundData::load, null), DATA_NAME);
    }

    private static FactionFundData load(CompoundTag tag, HolderLookup.Provider provider) {
        FactionFundData data = new FactionFundData();
        for (Faction f : Faction.values()) {
            if (f == Faction.NONE) {
                continue;
            }
            String key = "fund_" + f.name();
            if (tag.contains(key)) {
                data.funds.put(f, tag.getLong(key));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        for (Faction f : Faction.values()) {
            if (f == Faction.NONE) {
                continue;
            }
            Long amount = funds.get(f);
            if (amount != null) {
                tag.putLong("fund_" + f.name(), amount);
            }
        }
        return tag;
    }

    public long get(Faction faction) {
        return funds.getOrDefault(faction, 0L);
    }

    /** 入账（负数即支出；写盘由 setDirty 触发） */
    public void add(Faction faction, long amount) {
        if (faction == Faction.NONE || amount == 0L) {
            return;
        }
        funds.merge(faction, amount, Long::sum);
        setDirty();
    }
}