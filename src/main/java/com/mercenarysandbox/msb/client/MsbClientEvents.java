package com.mercenarysandbox.msb.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ClientTickEvent;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 客户端游戏事件接线（GAME 总线）：M 键打开战术地图（未打开其他界面时）。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MsbClientEvents {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (MsbKeyMappings.TACTICAL_MAP_KEY.consumeClick() && mc.screen == null) {
            mc.setScreen(new TacticalMapScreen());
        }
    }

    private MsbClientEvents() {
    }
}
