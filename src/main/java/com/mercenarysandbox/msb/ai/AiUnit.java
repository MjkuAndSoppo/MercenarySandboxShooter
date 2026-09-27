package com.mercenarysandbox.msb.ai;

import java.util.UUID;

import net.minecraft.core.BlockPos;

import com.mercenarysandbox.msb.faction.Faction;

/**
 * AI 模拟单位（服务端纯数据，无实体、无渲染 —— 实体化延后）。
 * 占据阵营战斗单位槽位，以模拟坐标参与占区计分。
 */
public final class AiUnit {
    private final UUID uuid;
    private final Faction faction;
    private final String name;
    private BlockPos pos;
    private int lazyTick;

    public AiUnit(UUID uuid, Faction faction, String name, BlockPos pos) {
        this.uuid = uuid;
        this.faction = faction;
        this.name = name;
        this.pos = pos;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Faction getFaction() {
        return faction;
    }

    public String getName() {
        return name;
    }

    public BlockPos getPos() {
        return pos;
    }

    /**
     * 惰性 tick：每 4 tick 朝控制区圆心慢速推进一格（带随机抖动），模拟「占区推进」。
     * 满足 docs/02 §3.12 惰性 tick 约束：低频决策，不做逐 tick 计算。
     */
    public void lazyTick(BlockPos zoneCenter, int radius) {
        lazyTick++;
        if (lazyTick % 4 != 0) {
            return;
        }
        int dx = Integer.compare(zoneCenter.getX(), pos.getX());
        int dz = Integer.compare(zoneCenter.getZ(), pos.getZ());
        // 轻微抖动避免所有单位挤在同一格
        int jitterX = (lazyTick % 3) - 1;
        int jitterZ = ((lazyTick / 3) % 3) - 1;
        pos = new BlockPos(pos.getX() + dx + jitterX, pos.getY(), pos.getZ() + dz + jitterZ);
    }
}
