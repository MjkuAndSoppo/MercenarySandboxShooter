package com.mercenarysandbox.msb;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mercenarysandbox.msb.economy.PlayerWallet;
import com.mercenarysandbox.msb.economy.WalletAttachments;
import com.mercenarysandbox.msb.network.WalletPayload;

/**
 * MSB 管理员指令（OP 权限 2）。
 * <pre>
 * /MSBS AIpmc drop t|f        —— 切换 AI 死亡是否掉落战利品（武器 + 容器），默认 f
 * /MSBS Money get             —— 查看自己的现金（总资产 / 本命赚取 / 已花费）
 * /MSBS Money add &lt;数量&gt;      —— 给自己加钱（仅改总资产，不计入本命赚取）
 * /MSBS Money set &lt;数量&gt;      —— 把自己的总资产设为指定值
 * </pre>
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MsbCommands {
    private MsbCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("MSBS")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("AIpmc")
                        .then(Commands.literal("drop")
                                .then(Commands.literal("t").executes(ctx -> setDropLoot(ctx.getSource(), true)))
                                .then(Commands.literal("f").executes(ctx -> setDropLoot(ctx.getSource(), false)))))
                .then(Commands.literal("Money")
                        .then(Commands.literal("get").executes(ctx -> moneyGet(ctx.getSource())))
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> moneyAdd(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> moneySet(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))));
    }

    /** 切换 AI 战利品掉落开关并写盘（默认 f） */
    private static int setDropLoot(CommandSourceStack source, boolean enabled) {
        Config.AI_DROP_LOOT.set(enabled);
        Config.save();
        source.sendSuccess(() -> Component.translatable("msb.command.ai_drop_loot", String.valueOf(enabled)), true);
        return 1;
    }

    /** 查看现金（仅执行者本人） */
    private static int moneyGet(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerWallet wallet = walletOf(player);
        source.sendSuccess(() -> Component.translatable("msb.command.money_get",
                wallet.total(), wallet.earned(), wallet.spent()), false);
        return wallet.total();
    }

    /** 加钱：只改总资产，不计入「本条命赚到的钱」（那是战斗收益口径） */
    private static int moneyAdd(CommandSourceStack source, int amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerWallet wallet = walletOf(player);
        int newTotal = wallet.total() + amount;
        applyWallet(player, wallet.withFinance(wallet.spent(), newTotal));
        source.sendSuccess(() -> Component.translatable("msb.command.money_add", amount, newTotal), true);
        return newTotal;
    }

    /** 设置总资产 */
    private static int moneySet(CommandSourceStack source, int amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerWallet wallet = walletOf(player);
        applyWallet(player, wallet.withFinance(wallet.spent(), amount));
        source.sendSuccess(() -> Component.translatable("msb.command.money_set", amount), true);
        return amount;
    }

    private static PlayerWallet walletOf(ServerPlayer player) {
        return player.getData(WalletAttachments.WALLET);
    }

    /** 写入钱包并即时同步本人 HUD */
    private static void applyWallet(ServerPlayer player, PlayerWallet wallet) {
        player.setData(WalletAttachments.WALLET, wallet);
        PacketDistributor.sendToPlayer(player,
                new WalletPayload(wallet.earned(), wallet.spent(), wallet.total()));
    }
}