package com.mercenarysandbox.msb.match;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.mercenarysandbox.msb.faction.Faction;

/**
 * 基地方块持久化（SavedData，存于主世界）：三阵营各记录一个基地坐标 {x,z,y}。
 * 重量进存档后安全区仍生效（docs/02 §3.13）——服务器启动 MatchManager.init 恢复，
 * registerBase/removeBase 时写回。
 */
public final class BaseData extends SavedData {
    public static final String DATA_NAME = "msb_bases";

    private final Map<Faction, BlockPos> positions = new EnumMap<>(Faction.class);

    public static BaseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BaseData::new, BaseData::load, null), DATA_NAME);
    }

    private static BaseData load(CompoundTag tag, HolderLookup.Provider provider) {
        BaseData data = new BaseData();
        for (Faction f : Faction.values()) {
            if (f == Faction.NONE) {
                continue;
            }
            int[] v = tag.getIntArray("base_" + f.name());
            // 注意：不可能用 v[0]>=0 判断是否存在——基地方块可能放在 x<0（世界西侧），
            // getIntArray 对缺失 key 返回空数组，故仅以长度==3 判存在即可。
            if (v.length == 3) {
                data.positions.put(f, new BlockPos(v[0], v[2], v[1]));
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
            BlockPos p = positions.get(f);
            if (p != null) {
                tag.putIntArray("base_" + f.name(), new int[]{p.getX(), p.getZ(), p.getY()});
            }
        }
        return tag;
    }

    public BlockPos get(Faction faction) {
        return positions.get(faction);
    }

    public void set(Faction faction, BlockPos pos) {
        positions.put(faction, pos);
        setDirty();
    }

    public void clear(Faction faction) {
        positions.remove(faction);
        setDirty();
    }
}