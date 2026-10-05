package com.mercenarysandbox.msb.stamina;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;
import com.mercenarysandbox.msb.match.MatchManager;

/**
 * 耐力系统核心（docs/02 §3.15）：放弃原版饥饿，把 {@link FoodData} 当作「耐力百分比刻度」。
 *
 * <p>真值为 {@link StaminaData}（服务端附体）。每 tick：
 * {@link #preTick} 先推进耐力模拟再压平原版 FoodData（三条原版分支全部失效），
 * {@link #postTick} 把「耐力百分比 × 20」写回 foodLevel，供客户端 HUD 与冲刺门槛读取
 * （因此无需同步上限、无需新增协议）。</p>
 */
public final class StaminaManager {

    /** 能力接口注册表（docs/02 §3.15「能力接口（预留）」） */
    private static final List<StaminaModifier> MODIFIERS = new ArrayList<>();

    private static final ResourceLocation SPEED_PENALTY_ID =
            ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "stamina_speed");
    private static final ResourceLocation JUMP_PENALTY_ID =
            ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "stamina_jump");

    private StaminaManager() {
    }

    /** 注册能力接口（按注册顺序折叠求值） */
    public static void registerModifier(StaminaModifier modifier) {
        MODIFIERS.add(modifier);
    }

    public static StaminaData get(ServerPlayer player) {
        return player.getData(StaminaAttachments.STAMINA);
    }

    private static void set(ServerPlayer player, StaminaData data) {
        player.setData(StaminaAttachments.STAMINA, data);
    }

    /** 耐力上限（配置默认值经能力接口折叠） */
    public static double maxStamina(ServerPlayer player) {
        double value = Config.STAMINA_MAX.get();
        for (StaminaModifier modifier : MODIFIERS) {
            value = modifier.maxStamina(player, value);
        }
        return Math.max(1.0D, value);
    }

    /** 每秒回复量（配置推导值经能力接口折叠） */
    public static double regenPerSecond(ServerPlayer player) {
        double base = Config.STAMINA_REGEN_AMOUNT.get() * 20.0D
                / Math.max(1, Config.STAMINA_REGEN_INTERVAL_TICKS.get());
        double value = base;
        for (StaminaModifier modifier : MODIFIERS) {
            value = modifier.regenPerSecond(player, value);
        }
        return Math.max(0.0D, value);
    }

    /** 耐力百分比（0~1）；未初始化视为满 */
    public static float percent(ServerPlayer player) {
        StaminaData data = get(player);
        if (data.stamina() < 0.0F) {
            return 1.0F;
        }
        double max = maxStamina(player);
        return (float) Math.min(1.0D, Math.max(0.0D, data.stamina() / max));
    }

    /** 消耗耐力（跳跃/近战/投掷/冲刺），并刷新回复延迟计时 */
    public static void exhaust(ServerPlayer player, double amount) {
        if (amount <= 0.0D || player.isCreative() || player.isSpectator()) {
            return;
        }
        StaminaData data = get(player);
        float next = (float) Math.max(0.0D, data.stamina() - amount);
        set(player, data.withStamina(next).withLastExhaustTick(player.level().getGameTime()));
    }

    /** 吃下营养 {@code nutrition} 的食物：登记「N 秒内每秒 +（营养 / N）」的逐渐回复队列（可叠加） */
    public static void addFoodRegen(ServerPlayer player, double nutrition) {
        int ticks = (int) Math.round(Config.STAMINA_FOOD_REGEN_SECONDS.get() * 20.0D);
        if (nutrition <= 0.0D || ticks <= 0) {
            return;
        }
        StaminaData data = get(player);
        float perTick = data.foodRegenPerTick() + (float) (nutrition / ticks);
        set(player, data.withFoodRegen(perTick, ticks));
    }

    /** 每 tick Pre：推进耐力模拟 + 压平原版 FoodData（创造/旁观跳过模拟，仅清消耗度） */
    public static void preTick(ServerPlayer player) {
        FoodData food = player.getFoodData();
        if (player.isCreative() || player.isSpectator()) {
            food.setExhaustion(0.0F);
            return;
        }
        tick(player);
        // 压平：1~17 避开原版「18+ 缓慢回复」与「0 饥饿掉血」两条分支，配合 saturation/exhaustion=0 使三条分支全失效
        int shown = Math.round(percent(player) * 20.0F);
        food.setSaturation(0.0F);
        food.setExhaustion(0.0F);
        food.setFoodLevel(Math.max(1, Math.min(17, shown)));
    }

    /** 每 tick Post：把「耐力百分比 × 20」写回 foodLevel（客户端 HUD 与冲刺门槛依据） */
    public static void postTick(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        int shown = Math.round(percent(player) * 20.0F);
        player.getFoodData().setFoodLevel(Math.max(0, Math.min(20, shown)));
    }

    /** 耐力模拟：冲刺消耗 → 自然/食物回复 → 溢出换血 → 力竭滞回与惩罚 */
    private static void tick(ServerPlayer player) {
        long now = player.level().getGameTime();
        StaminaData data = get(player);
        float max = (float) maxStamina(player);

        // 未初始化哨兵：首次 tick 填满
        if (data.stamina() < 0.0F) {
            data = data.withStamina(max);
        }

        // 1) 冲刺消耗：每计费周期扣 1 点
        int sprintInterval = Config.STAMINA_SPRINT_INTERVAL_TICKS.get();
        if (player.isSprinting()) {
            data = data.withSprintTicks(data.sprintTicks() + 1);
            if (data.sprintTicks() >= sprintInterval) {
                data = data.withSprintTicks(data.sprintTicks() - sprintInterval)
                        .withStamina(Math.max(0.0F, data.stamina() - (float) (double) Config.STAMINA_SPRINT_COST.get()))
                        .withLastExhaustTick(now);
            }
        } else if (data.sprintTicks() != 0) {
            data = data.withSprintTicks(0);
        }

        // 2) 回复量：脱离消耗延迟后（地面 + 未冲刺）自然回复；基地安全区加速；食物队列叠加
        float regen = 0.0F;
        if (now - data.lastExhaustTick() >= Config.STAMINA_REGEN_DELAY_TICKS.get()
                && player.onGround() && !player.isSprinting()) {
            double multiplier = inBaseZone(player) ? Config.STAMINA_BASE_REGEN_MULTIPLIER.get() : 1.0D;
            regen += (float) (regenPerSecond(player) * multiplier / 20.0D);
        }
        if (data.foodRegenTicksLeft() > 0) {
            regen += data.foodRegenPerTick();
            data = data.withFoodRegen(data.foodRegenPerTick(), data.foodRegenTicksLeft() - 1);
        }
        if (regen > 0.0F) {
            data = addStamina(player, data, regen, max);
        }

        // 3) 夹紧
        if (data.stamina() > max) {
            data = data.withStamina(max);
        }
        if (data.stamina() < 0.0F) {
            data = data.withStamina(0.0F);
        }

        // 4) 力竭滞回：0 点置位，回到解除阈值清除；力竭期间服务端压制冲刺 + 施加禁跳/减速
        boolean wasExhausted = data.exhausted();
        boolean nowExhausted = wasExhausted
                ? data.stamina() < (float) (double) Config.STAMINA_EXHAUST_RELEASE.get()
                : data.stamina() <= 0.0F;
        if (nowExhausted != wasExhausted) {
            data = data.withExhausted(nowExhausted);
            applyPenalty(player, nowExhausted);
        }
        if (nowExhausted) {
            player.setSprinting(false);
        }
        set(player, data);
    }

    /** 加耐力；超出上限的部分按「每 N 点换 1 HP」转为回血（满血时溢出作废，不累计储蓄） */
    private static StaminaData addStamina(ServerPlayer player, StaminaData data, float amount, float max) {
        float space = max - data.stamina();
        if (amount <= space) {
            return data.withStamina(data.stamina() + amount);
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            return data.withStamina(max).withOverflowBuffer(0.0F);
        }
        float buffer = data.overflowBuffer() + (amount - space);
        double ratio = Math.max(0.0001D, Config.STAMINA_OVERFLOW_PER_HEALTH.get());
        float healed = 0.0F;
        while (buffer >= ratio && player.getHealth() < player.getMaxHealth()) {
            buffer -= (float) ratio;
            healed += 1.0F;
        }
        if (healed > 0.0F) {
            player.heal(healed);
        }
        return data.withStamina(max).withOverflowBuffer(buffer);
    }

    /** 玩家是否处在己方阵营基地安全区（复用 MatchManager.baseZones，docs/02 §3.13） */
    private static boolean inBaseZone(ServerPlayer player) {
        Faction faction = FactionManager.getPlayerFaction(player);
        if (faction == Faction.NONE) {
            return false;
        }
        return MatchManager.get(player.server).isInBaseZone(faction, player.blockPosition());
    }

    /** 力竭惩罚修饰：独立 id 的 transient modifier，与负重修饰（weight_speed）互不干扰 */
    public static void applyPenalty(ServerPlayer player, boolean exhausted) {
        setModifier(player, Attributes.JUMP_STRENGTH, JUMP_PENALTY_ID, exhausted ? -1.0D : 0.0D);
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_PENALTY_ID,
                exhausted ? -Config.STAMINA_EXHAUST_SPEED_PENALTY.get() : 0.0D);
    }

    /** 定点更新修饰：固定 id + transient modifier，避免叠加/残留 */
    private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
