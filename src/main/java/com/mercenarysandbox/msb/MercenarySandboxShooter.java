package com.mercenarysandbox.msb;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.mercenarysandbox.msb.economy.WalletAttachments;
import com.mercenarysandbox.msb.faction.FactionAttachments;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.mercenarysandbox.msb.block.BaseBlock;
import com.mercenarysandbox.msb.client.AiCombatantRenderer;
import com.mercenarysandbox.msb.entity.AiCombatantEntity;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(MercenarySandboxShooter.MODID)
public class MercenarySandboxShooter {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "msb";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "msb" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "msb" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "msb" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    // Create a Deferred Register to hold EntityTypes which will all be registered under the "msb" namespace
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    // M0 注册链路验证：方块 / 物品 / 创造模式标签（后续里程碑会替换为真实玩法内容）
    public static final DeferredBlock<Block> TEST_BLOCK = BLOCKS.registerSimpleBlock("test_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    public static final DeferredItem<BlockItem> TEST_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("test_block", TEST_BLOCK);

    // ===== 阵营基地方块（docs/02 §5.1）：三阵营各一实例，安全区中心跟随方块位置 =====
    public static final DeferredBlock<BaseBlock> BASE_BLOCK_LONESTAR = BLOCKS.register("base_block_lonestar",
            () -> new BaseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).strength(50.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<BaseBlock> BASE_BLOCK_VALKYRA = BLOCKS.register("base_block_valkyra",
            () -> new BaseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(50.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<BaseBlock> BASE_BLOCK_MANTICORE = BLOCKS.register("base_block_manticore",
            () -> new BaseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(50.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> BASE_BLOCK_LONESTAR_ITEM = ITEMS.registerSimpleBlockItem("base_block_lonestar", BASE_BLOCK_LONESTAR);
    public static final DeferredItem<BlockItem> BASE_BLOCK_VALKYRA_ITEM = ITEMS.registerSimpleBlockItem("base_block_valkyra", BASE_BLOCK_VALKYRA);
    public static final DeferredItem<BlockItem> BASE_BLOCK_MANTICORE_ITEM = ITEMS.registerSimpleBlockItem("base_block_manticore", BASE_BLOCK_MANTICORE);

    public static final DeferredItem<Item> TEST_ITEM = ITEMS.registerSimpleItem("test_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MSB_TAB = CREATIVE_MODE_TABS.register("msb_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.msb"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> TEST_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(TEST_ITEM.get())).build());

    // ===== AI 战斗单位实体（M1 实体化，docs/02 §3.12）：原版 Steve 外观，无攻击 goal =====
    public static final DeferredHolder<EntityType<?>, EntityType<AiCombatantEntity>> AI_COMBATANT = ENTITIES.register("ai_combatant",
            () -> EntityType.Builder.of(AiCombatantEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).build("ai_combatant"));

    public MercenarySandboxShooter(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onAttributeCreation);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITIES.register(modEventBus);
        FactionAttachments.ATTACHMENT_TYPES.register(modEventBus);
        WalletAttachments.ATTACHMENT_TYPES.register(modEventBus);
        com.mercenarysandbox.msb.match.KillStreakAttachments.ATTACHMENT_TYPES.register(modEventBus);
        // 配装商店：个人储存格附体（序列化 + copyOnDeath，跨死亡/重连保持）
        com.mercenarysandbox.msb.shop.ShopStorageAttachments.ATTACHMENT_TYPES.register(modEventBus);
        // 荣誉点（独立货币，荣誉商店用）
        com.mercenarysandbox.msb.economy.HonorAttachments.ATTACHMENT_TYPES.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("MercenarySandboxShooter common setup loaded (msb)");
        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    /** AI 实体属性注册（MOD 总线）：LivingEntity 构造期 getMaxHealth 需要，缺失会 NPE 崩溃 */
    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(AI_COMBATANT.get(), AiCombatantEntity.createAttributes().build());
    }

    // Add the test block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(TEST_BLOCK_ITEM);
            event.accept(BASE_BLOCK_LONESTAR_ITEM);
            event.accept(BASE_BLOCK_VALKYRA_ITEM);
            event.accept(BASE_BLOCK_MANTICORE_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("MercenarySandboxShooter server starting (msb)");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ClientModEvents {
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("MercenarySandboxShooter client setup (msb)");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }

        @SubscribeEvent
        static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(AI_COMBATANT.get(), AiCombatantRenderer::new);
        }
    }
}
