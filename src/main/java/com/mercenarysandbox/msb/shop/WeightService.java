package com.mercenarysandbox.msb.shop;

import com.mercenarysandbox.msb.Config;
import com.mercenarysandbox.msb.MercenarySandboxShooter;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 负重系统（docs §6）：Σ 玩家栏物品（物品栏/快捷栏/护甲/副手）× 条目负重。
 * 储存格不计重；目录外物品按默认条目负重（{@code Config.SHOP_DEFAULT_WEIGHT}，默认 0.01kg）计重。
 * 超 {@code weightWarnRatio}（默认 60%）起线性减速，
 * 达上限 100% 时移速惩罚到达 {@code weightSpeedPenaltyMax}（默认 -20%），并下调跳跃强度。
 */
public final class WeightService {

    private static final ResourceLocation SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "weight_speed");
    private static final ResourceLocation JUMP_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MercenarySandboxShooter.MODID, "weight_jump");

    private WeightService() {
    }

    /** 当前随身负重（kg） */
    public static double total(ServerPlayer player) {
        Inventory inv = player.getInventory();
        double weight = 0.0D;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            weight += weightOf(inv.getItem(i));
        }
        for (ItemStack stack : inv.armor) {
            weight += weightOf(stack);
        }
        for (ItemStack stack : inv.offhand) {
            weight += weightOf(stack);
        }
        return weight;
    }

    public static double weightOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        // 目录未收录物品按默认条目负重（Config.SHOP_DEFAULT_WEIGHT）计重
        ShopEntry entry = ShopCatalog.INSTANCE.entryFor(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return entry.weight() * stack.getCount();
    }

    /** 负重比例（0 = 空载，1 = 达上限） */
    public static double ratio(ServerPlayer player) {
        double limit = Config.LOADOUT_WEIGHT_LIMIT_KG.get();
        return limit <= 0.0D ? 0.0D : total(player) / limit;
    }

    /** 重算并应用负重修饰（交易/取回/存入后与周期兜底调用） */
    public static void apply(ServerPlayer player) {
        double ratio = ratio(player);
        double warn = Config.WEIGHT_WARN_RATIO.get();
        double maxPenalty = Config.WEIGHT_SPEED_PENALTY_MAX.get();

        // 移速：warn 以下为 0，warn→100% 线性爬升至 -maxPenalty，超重保持 -maxPenalty
        double factor = ratio <= warn ? 0.0D
                : Math.min(1.0D, (ratio - warn) / Math.max(0.0001D, 1.0D - warn));
        setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_ID, -factor * maxPenalty);

        // 跳跃：超重（≥100%）时下调跳跃强度（1.21.1 玩家的 jump_strength 默认 0.42）
        setModifier(player, Attributes.JUMP_STRENGTH, JUMP_MODIFIER_ID, ratio >= 1.0D ? -0.2D : 0.0D);
    }

    /** 定点更新修饰：固定 id + transient modifier，避免叠加/残留（docs §6） */
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