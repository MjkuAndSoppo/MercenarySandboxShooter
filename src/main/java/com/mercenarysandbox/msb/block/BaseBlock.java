package com.mercenarysandbox.msb.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 阵营基地方块（docs/02 §5.1 基地方块安全区）。
 * 三个阵营各注册一个实例（阵营归属由注册名区分，不写入方块状态）；
 * 本阶段仅作为可见标记 + 服务端安全区坐标来源：安全区中心 = 方块所在位置（跟随方块）。
 */
public class BaseBlock extends Block {
    public BaseBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}