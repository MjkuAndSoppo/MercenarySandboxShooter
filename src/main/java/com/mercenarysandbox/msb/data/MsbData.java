package com.mercenarysandbox.msb.data;

import java.util.List;
import java.util.Set;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.data.loot.LootTableProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * Datagen 入口（MOD 总线）：可生成资源一律走 datagen，禁止手写 JSON（docs/02 §8）。
 * 输出目录 src/generated/resources/，由 runData 任务生成。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class MsbData {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        generator.addProvider(true, new MsbLanguageProvider(packOutput, "en_us"));
        generator.addProvider(true, new MsbLanguageProvider(packOutput, MsbLanguageProvider.ZH_CN));
        generator.addProvider(event.includeClient(),
                new MsbBlockStates(packOutput, MercenarySandboxShooter.MODID, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), new LootTableProvider(packOutput, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(MsbBlockLoot::new, LootContextParamSets.BLOCK)),
                event.getLookupProvider()));
    }

    private MsbData() {
    }
}
