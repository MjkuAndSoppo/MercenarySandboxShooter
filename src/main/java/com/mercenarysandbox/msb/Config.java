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
            .comment("Control zone radius in blocks (zone center = world spawn)")
            .defineInRange("controlZoneRadius", 30, 5, 500);

    public static final ModConfigSpec.IntValue SETTLE_INTERVAL_SECONDS = BUILDER
            .comment("Control zone settlement interval in seconds")
            .defineInRange("settleIntervalSeconds", 30, 5, 3600);

    public static final ModConfigSpec.IntValue AI_TARGET_PER_FACTION = BUILDER
            .comment("Target combat units per faction (real players + AI). AI count = target - real players")
            .defineInRange("aiTargetPerFaction", 10, 1, 100);

    // ===== 阵营基地方块（docs/02 §5.1）=====
    public static final ModConfigSpec.IntValue BASE_RADIUS = BUILDER
            .comment("Base safe zone radius in blocks (center = base block position)")
            .defineInRange("baseRadius", 16, 5, 100);

    public static final ModConfigSpec.IntValue BASE_REGEN_INTERVAL_SECONDS = BUILDER
            .comment("Friendly vehicle health regen interval in seconds while inside base safe zone")
            .defineInRange("baseRegenIntervalSeconds", 2, 1, 30);

    // ===== 战术地图（docs/02 §3.9）=====
    public static final ModConfigSpec.BooleanValue TACTICAL_MAP_ROTATE_WITH_PLAYER = BUILDER
            .comment("Rotate the tactical map with the player's facing direction. Disable for a fixed north-up map")
            .define("tacticalMapRotateWithPlayer", true);

    // ===== 击杀结算（docs/02 击杀提示）=====
    public static final ModConfigSpec.IntValue KILL_MONEY_REWARD = BUILDER
            .comment("Money reward for killing a monster or enemy player")
            .defineInRange("killMoneyReward", 20, 0, 10000);

    public static final ModConfigSpec.IntValue KILL_XP_REWARD = BUILDER
            .comment("Experience points granted directly to the killer")
            .defineInRange("killXpReward", 10, 0, 1000);

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

    public static final ModConfigSpec.DoubleValue LOADOUT_WEIGHT_LIMIT_KG = BUILDER
            .comment("Carried weight limit in kg (catalog items in player zones; shop storage excluded)")
            .defineInRange("loadoutWeightLimitKg", 24.0D, 1.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue WEIGHT_SPEED_PENALTY_MAX = BUILDER
            .comment("Movement speed penalty at full weight (0.2 = -20 percent)")
            .defineInRange("weightSpeedPenaltyMax", 0.2D, 0.0D, 0.9D);

    public static final ModConfigSpec.DoubleValue WEIGHT_WARN_RATIO = BUILDER
            .comment("Weight ratio where the slowdown starts (0.6 = 60 percent)")
            .defineInRange("weightWarnRatio", 0.6D, 0.0D, 1.0D);

    static final ModConfigSpec SPEC = BUILDER.build();

    /** 立即写盘（指令切换运行时开关后调用） */
    public static void save() {
        SPEC.save();
    }

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
