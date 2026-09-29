package com.mercenarysandbox.msb.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * C2S 战术地图传送载荷（docs/02 §3.9）：携带世界目标方块坐标的 X、Z。
 * 仅在服务端校验「创造模式」后执行传送（服务端权威，防作弊）；
 * 一次传送到建造高度上限（Y=320），随即检测玩家脚下方块高度，二次传送到其顶面（y+1）落定；
 * 该列无实体方块（虚空）时，按按 T 前的高度落脚。
 * 无边界/区块加载保护：直接传送。
 */
public record TeleportRequestPayload(int blockX, int blockZ) implements CustomPacketPayload {

    public static final Type<TeleportRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "teleport_request"));

    public static final StreamCodec<FriendlyByteBuf, TeleportRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TeleportRequestPayload::blockX,
            ByteBufCodecs.VAR_INT, TeleportRequestPayload::blockZ,
            TeleportRequestPayload::new);

    /** 传送高度：建造高度上限（主世界最高方块 Y=319，其上 320 为下落起点） */
    private static final double DROP_Y = 320.0D;

    /** 无有效落点（整列虚空）的哨兵值 */
    private static final int NO_GROUND = Integer.MIN_VALUE;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端处理：仅创造模式玩家 —— 一次传送 Y=320，随即检测玩家脚下方块高度，二次传送到其上方落定 */
    public static void handle(TeleportRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.getAbilities().instabuild) {
                return; // 服务端权威：仅创造模式（客户端同样预检，双保险）
            }
            ServerLevel level = player.serverLevel();
            int x = payload.blockX();
            int z = payload.blockZ();
            // 记录按 T 前的高度（该列无实体方块时按此高度落脚）
            double originalY = player.getY();
            // ① 一次传送：固定到 Y=320（最高方块 319 之上，下落起点）
            player.teleportTo(level, x + 0.5D, DROP_Y, z + 0.5D, player.getYRot(), player.getXRot());
            // ② 检测玩家脚下方块高度：自顶向下扫描该列最高可站立方块
            int groundY = findGroundY(level, x, z);
            // ③ 二次传送：存在地面→落定在其顶面（y+1）；无地面→按按 T 前的高度落脚
            player.teleportTo(level, x + 0.5D,
                    groundY == NO_GROUND ? originalY : groundY + 1.0D,
                    z + 0.5D, player.getYRot(), player.getXRot());
        });
    }

    /** 自顶向下扫描该列，返回最高有碰撞盒（可站立）的方块 Y；整列无有效方块返回 NO_GROUND */
    private static int findGroundY(ServerLevel level, int x, int z) {
        int maxY = level.getMaxBuildHeight() - 1;
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        CollisionContext ctx = CollisionContext.empty();
        for (int y = maxY; y > minY; y--) {
            scan.set(x, y, z);
            if (level.getBlockState(scan).getCollisionShape(level, scan, ctx).isEmpty()) {
                continue;
            }
            return y;
        }
        return NO_GROUND;
    }
}