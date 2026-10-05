package com.mercenarysandbox.msb;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// MSB common config (模板自带示例配置，保持最小)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    // ===== M1 对局配置（docs/02 §3.2 / §3.12）=====
    public static final ModConfigSpec.IntValue CONTROL_ZONE_RADIUS = BUILDER
            .comment("Control zone radius in blocks (512-block-scale zone; zone center = world spawn)")
            .defineInRange("controlZoneRadius", 256, 16, 512);

    public static final ModConfigSpec.IntValue SETTLE_INTERVAL_SECONDS = BUILDER
            .comment("Control zone settlement interval in seconds")
            .defineInRange("settleIntervalSeconds", 30, 5, 3600);

    /** 兜底默认：某阵营尚未有玩家通过手册选择开局 AI 数量时使用的默认值（开局选择后以 FactionSetupData 为准） */
    public static final ModConfigSpec.IntValue AI_TARGET_PER_FACTION = BUILDER
            .comment("Fallback AI count per faction when no player has chosen a starting AI count via the handbook")
            .defineInRange("aiTargetPerFaction", 10, 1, 100);

    // ===== 阵营基地方块（docs/02 §5.1）=====
    public static final ModConfigSpec.IntValue BASE_RADIUS = BUILDER
            .comment("Base safe zone radius in blocks (center = base block position)")
            .defineInRange("baseRadius", 16, 5, 100);

    public static final ModConfigSpec.IntValue BASE_REGEN_INTERVAL_SECONDS = BUILDER
            .comment("Friendly vehicle health regen interval in seconds while inside base safe zone")
            .defineInRange("baseRegenIntervalSeconds", 2, 1, 30);

    // ===== 无人机视野强加载 =====
    /** 被操控的 SBW 无人机周围强加载的区块半径（0 = 关闭）；每 1 格 = 16 方块，开销随半径平方增长 */
    public static final ModConfigSpec.IntValue DRONE_CHUNK_RADIUS = BUILDER
            .comment("Force-load chunk radius (in chunks) around a player-controlled SBW drone so its view stays loaded",
                    "0 disables. Cost grows with radius squared (8 = 17x17 = 289 chunks per drone)")
            .defineInRange("droneChunkRadius", 8, 0, 16);

    // ===== 战术地图（docs/02 §3.9）=====
    public static final ModConfigSpec.BooleanValue TACTICAL_MAP_ROTATE_WITH_PLAYER = BUILDER
            .comment("Rotate the tactical map with the player's facing direction. Disable for a fixed north-up map")
            .define("tacticalMapRotateWithPlayer", true);

    // ===== 击杀结算（docs/02 击杀提示）=====
    /** 击杀敌方玩家基础赏金 */
    public static final ModConfigSpec.IntValue KILL_MONEY_REWARD = BUILDER
            .comment("Base money bounty for killing an enemy player")
            .defineInRange("killMoneyReward", 100, 0, 100000);

    /** 击杀怪物（普通生物）基础赏金 */
    public static final ModConfigSpec.IntValue KILL_MONEY_REWARD_MOB = BUILDER
            .comment("Base money bounty for killing a monster / plain mob")
            .defineInRange("killMoneyRewardMob", 100, 0, 100000);

    /** 击杀敌方阵营 AI 基础赏金 */
    public static final ModConfigSpec.IntValue KILL_MONEY_REWARD_AI = BUILDER
            .comment("Base money bounty for killing an enemy faction AI")
            .defineInRange("killMoneyRewardAi", 200, 0, 100000);

    public static final ModConfigSpec.IntValue KILL_XP_REWARD = BUILDER
            .comment("Base experience granted directly to the killer")
            .defineInRange("killXpReward", 15, 0, 10000);

    /** 目标本条命击杀数加成：目标每击杀过一个单位，额外 +100$（赏金随目标连杀增长） */
    public static final ModConfigSpec.IntValue KILL_BOUNTY_PER_KILL = BUILDER
            .comment("Extra money per kill the victim had this life, on top of the base bounty for an enemy player victim")
            .defineInRange("killBountyPerKill", 100, 0, 100000);

    /** 击杀怪物：目标每击杀过一个单位，额外 +50$ */
    public static final ModConfigSpec.IntValue KILL_BOUNTY_PER_KILL_MOB = BUILDER
            .comment("Extra money per kill the victim had this life, on top of the base bounty for a mob victim")
            .defineInRange("killBountyPerKillMob", 50, 0, 100000);

    /** 击杀敌方阵营 AI：目标每击杀过一个单位，额外 +100$ */
    public static final ModConfigSpec.IntValue KILL_BOUNTY_PER_KILL_AI = BUILDER
            .comment("Extra money per kill the victim had this life, on top of the base bounty for an enemy AI victim")
            .defineInRange("killBountyPerKillAi", 100, 0, 100000);

    /** 目标本条命击杀数加成：目标每击杀过一个单位，额外 +5 EXP */
    public static final ModConfigSpec.IntValue KILL_XP_PER_KILL = BUILDER
            .comment("Extra experience per kill the victim had this life")
            .defineInRange("killXpPerKill", 5, 0, 10000);

    public static final ModConfigSpec.IntValue KILL_FRIENDLY_PENALTY = BUILDER
            .comment("Money penalty for killing a teammate (docs/02 friendly fire rules)")
            .defineInRange("killFriendlyPenalty", 100, 1, 10000);

    public static final ModConfigSpec.IntValue FRIENDLY_DAMAGE_PENALTY_PER_HP = BUILDER
            .comment("Money penalty per damage point for hitting a teammate (non-lethal friendly fire)")
            .defineInRange("friendlyDamagePenaltyPerHp", 2, 0, 1000);

    public static final ModConfigSpec.IntValue FRIENDLY_DAMAGE_PENALTY_CAP = BUILDER
            .comment("Maximum money penalty charged for one friendly-fire hit batch (protects against burst weapons)"
                    + " - hits on the same teammate within 0.5s are charged once")
            .defineInRange("friendlyDamagePenaltyCap", 40, 1, 100000);

    // ===== AI 战利品掉落开关（指令 /MSBS AIpmc drop t|f 切换）=====
    public static final ModConfigSpec.BooleanValue AI_DROP_LOOT = BUILDER
            .comment("Whether AI combatants drop their loot (weapon + container) on death. Toggle with /MSBS AIpmc drop t|f")
            .define("aiDropLoot", false);

    // ===== 配装商店与负重（docs/02 §3.4 落地：M2 经济与配装）=====
    public static final ModConfigSpec.DoubleValue SHOP_SELL_RATIO = BUILDER
            .comment("Sell-back ratio for items without green frame (player inventory / stored items)")
            .defineInRange("shopSellRatio", 0.6D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue SHOP_REFUND_RATE = BUILDER
            .comment("Refund rate for green-frame items (bought and not yet taken out of storage)")
            .defineInRange("shopRefundRate", 1.0D, 0.0D, 10.0D);

    public static final ModConfigSpec.IntValue SHOP_STORAGE_SLOTS = BUILDER
            .comment("Personal shop storage capacity in stacks")
            .defineInRange("shopStorageSlots", 60, 6, 600);

    /** 目录未收录物品的默认买入价（$）：使其默认可出售（卖价 = 默认价 × 六折系数） */
    public static final ModConfigSpec.IntValue SHOP_DEFAULT_PRICE = BUILDER
            .comment("Fallback buy price for items not listed in the shop catalog (so they can still be sold back)")
            .defineInRange("shopDefaultPrice", 20, 0, 100000);

    /** 目录未收录物品的默认单件负重（kg） */
    public static final ModConfigSpec.DoubleValue SHOP_DEFAULT_WEIGHT = BUILDER
            .comment("Fallback weight (kg) for items not listed in the shop catalog")
            .defineInRange("shopDefaultWeight", 0.01D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue LOADOUT_WEIGHT_LIMIT_KG = BUILDER
            .comment("Carried weight limit in kg (catalog items in player zones; shop storage excluded)")
            .defineInRange("loadoutWeightLimitKg", 24.0D, 1.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue WEIGHT_SPEED_PENALTY_MAX = BUILDER
            .comment("Movement speed penalty at full weight (0.2 = -20 percent)")
            .defineInRange("weightSpeedPenaltyMax", 0.2D, 0.0D, 0.9D);

    public static final ModConfigSpec.DoubleValue WEIGHT_WARN_RATIO = BUILDER
            .comment("Weight ratio where the slowdown starts (0.6 = 60 percent)")
            .defineInRange("weightWarnRatio", 0.6D, 0.0D, 1.0D);

    // ===== 耐力系统（docs/02 §3.15：饱食度/饱和度魔改）=====
    /** 耐力上限（点）；对应 HUD 条满格 */
    public static final ModConfigSpec.DoubleValue STAMINA_MAX = BUILDER
            .comment("Stamina capacity (points). The vanilla food bar is repurposed as a stamina percentage gauge")
            .defineInRange("staminaMax", 20.0D, 1.0D, 1000.0D);

    /** 冲刺消耗（点 / 每个计费周期） */
    public static final ModConfigSpec.DoubleValue STAMINA_SPRINT_COST = BUILDER
            .comment("Stamina cost per sprint billing cycle")
            .defineInRange("staminaSprintCost", 1.0D, 0.0D, 1000.0D);

    /** 冲刺计费周期（tick）：每满该周期扣一次冲刺消耗，默认 40 = 2s */
    public static final ModConfigSpec.IntValue STAMINA_SPRINT_INTERVAL_TICKS = BUILDER
            .comment("Sprint billing cycle in ticks (40 = 2s per staminaSprintCost)")
            .defineInRange("staminaSprintIntervalTicks", 40, 1, 1200);

    /** 跳跃消耗（点/次，含非冲刺跳） */
    public static final ModConfigSpec.DoubleValue STAMINA_JUMP_COST = BUILDER
            .comment("Stamina cost per jump (including non-sprint jumps)")
            .defineInRange("staminaJumpCost", 1.0D, 0.0D, 1000.0D);

    /** 近战命中消耗（点/次） */
    public static final ModConfigSpec.DoubleValue STAMINA_MELEE_COST = BUILDER
            .comment("Stamina cost per melee hit")
            .defineInRange("staminaMeleeCost", 0.5D, 0.0D, 1000.0D);

    /** 投掷消耗（点/次，仅作用于白名单物品） */
    public static final ModConfigSpec.DoubleValue STAMINA_THROW_COST = BUILDER
            .comment("Stamina cost per thrown item (only items listed in staminaThrowItems)")
            .defineInRange("staminaThrowCost", 1.0D, 0.0D, 1000.0D);

    /** 投掷物白名单（物品注册名）：右键使用这些物品按投掷计费 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STAMINA_THROW_ITEMS = BUILDER
            .comment("Item registry names treated as throws for stamina cost (e.g. minecraft:snowball)")
            .defineListAllowEmpty("staminaThrowItems", List.of(
                    "minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl",
                    "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:experience_bottle"),
                    () -> "", Config::validateItemName);

    /** 回复延迟（tick）：最后一次消耗后多久开始自然回复，默认 60 = 3s */
    public static final ModConfigSpec.IntValue STAMINA_REGEN_DELAY_TICKS = BUILDER
            .comment("Delay in ticks after the last stamina use before natural regeneration starts (60 = 3s)")
            .defineInRange("staminaRegenDelayTicks", 60, 0, 12000);

    /** 自然回复周期（tick）：每满该周期回复一次，默认 40 = 2s */
    public static final ModConfigSpec.IntValue STAMINA_REGEN_INTERVAL_TICKS = BUILDER
            .comment("Natural regeneration interval in ticks (40 = 2s per staminaRegenAmount)")
            .defineInRange("staminaRegenIntervalTicks", 40, 1, 1200);

    /** 自然回复量（点 / 每个回复周期） */
    public static final ModConfigSpec.DoubleValue STAMINA_REGEN_AMOUNT = BUILDER
            .comment("Stamina restored per natural regeneration cycle")
            .defineInRange("staminaRegenAmount", 1.0D, 0.0D, 1000.0D);

    /** 基地安全区内回复倍率（×） */
    public static final ModConfigSpec.DoubleValue STAMINA_BASE_REGEN_MULTIPLIER = BUILDER
            .comment("Regeneration multiplier while inside your faction base safe zone")
            .defineInRange("staminaBaseRegenMultiplier", 3.0D, 0.0D, 100.0D);

    /** 食物逐渐回复时长（秒）：吃下食物后在该时长内摊分回复（营养 / 时长）= 每秒回复量 */
    public static final ModConfigSpec.DoubleValue STAMINA_FOOD_REGEN_SECONDS = BUILDER
            .comment("Seconds over which eaten food restores stamina (nutrition / seconds per second)")
            .defineInRange("staminaFoodRegenSeconds", 5.0D, 0.0D, 600.0D);

    /** 溢出换血比：耐力已满时，每 N 点回复量兑换 1 HP */
    public static final ModConfigSpec.DoubleValue STAMINA_OVERFLOW_PER_HEALTH = BUILDER
            .comment("Stamina overflow needed per 1 HP healed when stamina is full")
            .defineInRange("staminaOverflowPerHealth", 2.0D, 0.1D, 1000.0D);

    /** 力竭解除阈值（点）：耐力从 0 回复到该值才解除力竭（滞回） */
    public static final ModConfigSpec.DoubleValue STAMINA_EXHAUST_RELEASE = BUILDER
            .comment("Stamina required to leave the exhausted state (hysteresis)")
            .defineInRange("staminaExhaustRelease", 3.0D, 0.0D, 1000.0D);

    /** 力竭减速（比例）：移速 -该值，默认 0.15 = -15% */
    public static final ModConfigSpec.DoubleValue STAMINA_EXHAUST_SPEED_PENALTY = BUILDER
            .comment("Movement speed penalty while exhausted (0.15 = -15 percent)")
            .defineInRange("staminaExhaustSpeedPenalty", 0.15D, 0.0D, 0.9D);

    static final ModConfigSpec SPEC = BUILDER.build();

    /** 立即写盘（指令切换运行时开关后调用） */
    public static void save() {
        SPEC.save();
    }

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
