package com.mercenarysandbox.msb.stamina;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家耐力真值（服务端权威，docs/02 §3.15）。
 *
 * @param stamina             当前耐力点（{@code -1} = 未初始化哨兵，首次 tick 填满）
 * @param lastExhaustTick     最近一次消耗的游戏刻（回复延迟计时起点）
 * @param sprintTicks         冲刺计费累加（每达计费周期扣 1 点）
 * @param foodRegenPerTick    食物逐渐回复的每 tick 回复量（吃下时按 营养/时长 摊分）
 * @param foodRegenTicksLeft  食物逐渐回复剩余 tick
 * @param overflowBuffer      溢出换血余数（未满 1 HP 的零头）
 * @param exhausted           力竭标志（滞回：0 点置位、回到解除阈值清除）
 */
public record StaminaData(float stamina, long lastExhaustTick, int sprintTicks,
                          float foodRegenPerTick, int foodRegenTicksLeft,
                          float overflowBuffer, boolean exhausted) {

    /** 新玩家默认：耐力未初始化（首次 tick 填满），其余清零 */
    public static final StaminaData DEFAULT = new StaminaData(-1.0F, 0L, 0, 0.0F, 0, 0.0F, false);

    public static final Codec<StaminaData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("stamina", -1.0F).forGetter(StaminaData::stamina),
            Codec.LONG.optionalFieldOf("lastExhaust", 0L).forGetter(StaminaData::lastExhaustTick),
            Codec.INT.optionalFieldOf("sprintTicks", 0).forGetter(StaminaData::sprintTicks),
            Codec.FLOAT.optionalFieldOf("foodRegenPerTick", 0.0F).forGetter(StaminaData::foodRegenPerTick),
            Codec.INT.optionalFieldOf("foodRegenTicksLeft", 0).forGetter(StaminaData::foodRegenTicksLeft),
            Codec.FLOAT.optionalFieldOf("overflowBuffer", 0.0F).forGetter(StaminaData::overflowBuffer),
            Codec.BOOL.optionalFieldOf("exhausted", false).forGetter(StaminaData::exhausted))
            .apply(instance, StaminaData::new));

    public StaminaData withStamina(float value) {
        return new StaminaData(value, lastExhaustTick, sprintTicks, foodRegenPerTick, foodRegenTicksLeft, overflowBuffer, exhausted);
    }

    public StaminaData withLastExhaustTick(long value) {
        return new StaminaData(stamina, value, sprintTicks, foodRegenPerTick, foodRegenTicksLeft, overflowBuffer, exhausted);
    }

    public StaminaData withSprintTicks(int value) {
        return new StaminaData(stamina, lastExhaustTick, value, foodRegenPerTick, foodRegenTicksLeft, overflowBuffer, exhausted);
    }

    /** 设置食物回复队列：回复速率累加、剩余时长重置为 {@code ticksLeft} */
    public StaminaData withFoodRegen(float perTick, int ticksLeft) {
        return new StaminaData(stamina, lastExhaustTick, sprintTicks, perTick, ticksLeft, overflowBuffer, exhausted);
    }

    public StaminaData withOverflowBuffer(float value) {
        return new StaminaData(stamina, lastExhaustTick, sprintTicks, foodRegenPerTick, foodRegenTicksLeft, value, exhausted);
    }

    public StaminaData withExhausted(boolean value) {
        return new StaminaData(stamina, lastExhaustTick, sprintTicks, foodRegenPerTick, foodRegenTicksLeft, overflowBuffer, value);
    }
}
