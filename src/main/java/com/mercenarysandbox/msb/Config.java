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

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
