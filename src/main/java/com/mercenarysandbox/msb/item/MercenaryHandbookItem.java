package com.mercenarysandbox.msb.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.network.HandbookOpenPayload;

/**
 * 雇佣兵手册：无阵营玩家开局自动发放，右键打开阵营选择界面（docs 开局流程）。
 * 界面为客户端自绘，服务端仅下发「打开」指令（服务端权威）；手册不消耗，保留承载后续教程。
 */
public class MercenaryHandbookItem extends Item {

    public MercenaryHandbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new HandbookOpenPayload());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}