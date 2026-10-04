package com.mercenarysandbox.msb.onboarding;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.faction.Faction;

/**
 * 阵营开局设置持久化（SavedData，存于主世界）：三阵营各自的「开局 AI 目标数量」。
 * 由首个选择该阵营的玩家写入（写入后固定，后来者不覆盖）；未设置时回退配置默认值。
 */
public final class FactionSetupData extends SavedData {
    public static final String DATA_NAME = "msb_faction_setup";

    private final Map<Faction, Integer> initialAiTarget = new EnumMap<>(Faction.class);

    public static FactionSetupData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FactionSetupData::new, FactionSetupData::load, null), DATA_NAME);
    }

    private static FactionSetupData load(CompoundTag tag, HolderLookup.Provider provider) {
        FactionSetupData data = new FactionSetupData();
        for (Faction f : Faction.values()) {
            if (f == Faction.NONE) {
                continue;
            }
            if (tag.contains("ai_target_" + f.name())) {
                data.initialAiTarget.put(f, tag.getInt("ai_target_" + f.name()));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        for (Map.Entry<Faction, Integer> e : initialAiTarget.entrySet()) {
            tag.putInt("ai_target_" + e.getKey().name(), e.getValue());
        }
        return tag;
    }

    /** 该阵营开局 AI 目标：未设置时回退配置默认值（保留旧行为兜底） */
    public int getTarget(Faction faction) {
        Integer v = initialAiTarget.get(faction);
        return v != null ? v : Config.AI_TARGET_PER_FACTION.get();
    }

    /** 首个选择者写入生效；已有条目则忽略后来者（阵营开局编制一旦确定即固定） */
    public void setTargetIfAbsent(Faction faction, int target) {
        if (initialAiTarget.containsKey(faction)) {
            return;
        }
        initialAiTarget.put(faction, target);
        setDirty();
    }

    /** 该阵营是否已确认开局 AI 数量 */
    public boolean hasChosen(Faction faction) {
        return initialAiTarget.containsKey(faction);
    }

    /** 三阵营是否均已确认开局 AI 数量（开局门槛，docs/02 §3.14） */
    public boolean allChosen() {
        for (Faction f : Faction.values()) {
            if (f == Faction.NONE) {
                continue;
            }
            if (!initialAiTarget.containsKey(f)) {
                return false;
            }
        }
        return true;
    }
}