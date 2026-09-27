package com.mercenarysandbox.msb.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

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
        generator.addProvider(true, new MsbLanguageProvider(packOutput));
    }

    private MsbData() {
    }
}
