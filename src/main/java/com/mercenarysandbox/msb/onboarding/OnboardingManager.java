package com.mercenarysandbox.msb.onboarding;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.economy.PlayerWallet;
import com.mercenarysandbox.msb.economy.WalletAttachments;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.match.MatchManager;
import com.mercenarysandbox.msb.network.WalletPayload;

/**
 * 开局流程服务端逻辑（docs 开局流程）：
 * 无阵营玩家发手册；手册确认后设置阵营/初始资金/雇佣兵档案/阵营 AI 目标并传送至基地。
 */
public final class OnboardingManager {
    /** 初始 AI 数量下限 */
    public static final int AI_COUNT_MIN = 5;
    /** 初始 AI 数量上限 */
    public static final int AI_COUNT_MAX = 15;

    private OnboardingManager() {
    }

    /** 确保玩家背包内有雇佣兵手册（无则补发；放不下则掉在脚下） */
    public static void ensureHandbook(ServerPlayer player) {
        if (player.getInventory().contains(stack -> stack.is(MercenarySandboxShooter.MERCENARY_HANDBOOK.get()))) {
            return;
        }
        ItemStack stack = new ItemStack(MercenarySandboxShooter.MERCENARY_HANDBOOK.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * 应用阵营选择（服务端权威）：设阵营 → 发初始资金 → 落库档案 → 设本阵营 AI 目标 → 传送。
     * 已开局玩家忽略重复选择；非法阵营忽略；AI 数量钳制到 [5,15]。
     */
    public static void applyChoice(ServerPlayer player, int factionId, int tierId, int aiCount) {
        Faction faction = Faction.byId(factionId);
        if (faction == Faction.NONE) {
            return;
        }
        if (FactionManager.getPlayerFaction(player) != Faction.NONE) {
            return; // 已开局，忽略重复选择
        }
        FundingTier tier = FundingTier.byId(tierId);
        int ai = Mth.clamp(aiCount, AI_COUNT_MIN, AI_COUNT_MAX);

        // ① 阵营
        FactionManager.setPlayerFaction(player, faction);

        // ② 初始资金（总资产 = 档位资金；本命收入/花销清零）
        player.setData(WalletAttachments.WALLET, new PlayerWallet(0, tier.money(), 0));
        PlayerWallet wallet = player.getData(WalletAttachments.WALLET);
        PacketDistributor.sendToPlayer(player, new WalletPayload(wallet.earned(), wallet.spent(), wallet.total()));

        // ③ 雇佣兵档案（资金档位 + 击杀倍率 + 初始 AI 数量）
        player.setData(MercenaryProfileAttachments.PROFILE,
                new MercenaryProfile(tier.getId(), (int) Math.round(tier.killMultiplier() * 100.0D), ai));

        // ④ 本阵营开局 AI 目标（首个选择者写入）；开局仅由 OP /MSBS game start 触发（M3 改为 Tab 投票开局）
        FactionSetupData setup = FactionSetupData.get(player.server);
        setup.setTargetIfAbsent(faction, ai);

        // ⑤ 传送：有基地 → 基地上方 1 格；无基地 → 提示「无基地配置」并跳过
        BlockPos base = MatchManager.get(player.server).getBasePos(faction);
        if (base != null) {
            ServerLevel overworld = player.server.overworld();
            player.teleportTo(overworld, base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
        } else {
            player.sendSystemMessage(Component.translatable("msb.onboarding.no_base"));
        }

        player.sendSystemMessage(Component.translatable("msb.onboarding.joined",
                Component.translatable(faction.getDisplayKey()), tier.money(), ai));
    }
}