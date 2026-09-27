package com.mercenarysandbox.msb.match;

import net.minecraft.core.BlockPos;

/**
 * 控制区（服务端逻辑区）。
 * 以圆心 + 半径做平面距离判定，只依赖玩家坐标 —— 玩家所在区块必加载，
 * 不扫描实体、不强制加载控制区所在区块（docs/02 §3.2 区块未加载问题的解决方案）。
 */
public final class ControlZone {
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int radius;
    private final int radiusSq;

    public ControlZone(int centerX, int centerY, int centerZ, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
        this.radiusSq = radius * radius;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getRadius() {
        return radius;
    }

    /** 平面（XZ）距离判定，忽略 Y（逻辑区域不关注高度差） */
    public boolean contains(BlockPos pos) {
        return containsXZ(pos.getX(), pos.getZ());
    }

    public boolean containsXZ(double x, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= radiusSq;
    }
}
