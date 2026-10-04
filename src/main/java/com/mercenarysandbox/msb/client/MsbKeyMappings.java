package com.mercenarysandbox.msb.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 客户端按键注册（MOD 总线）：战术地图 M 键、军火商店 B 键、信息栏 Tab 键。
 */
@EventBusSubscriber(modid = MercenarySandboxShooter.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MsbKeyMappings {
    public static final KeyMapping TACTICAL_MAP_KEY = new KeyMapping(
            "key.msb.tactical_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.msb");

    /** 军火商店开关（B 键，docs/02 §3.4） */
    public static final KeyMapping SHOP_KEY = new KeyMapping(
            "key.msb.shop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.msb");

    /** 信息栏开关（Tab 键；打开自绘界面后压制原版玩家列表，docs 开局流程） */
    public static final KeyMapping INFO_KEY = new KeyMapping(
            "key.msb.info", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, "key.categories.msb");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TACTICAL_MAP_KEY);
        event.register(SHOP_KEY);
        event.register(INFO_KEY);
    }

    private MsbKeyMappings() {
    }
}
