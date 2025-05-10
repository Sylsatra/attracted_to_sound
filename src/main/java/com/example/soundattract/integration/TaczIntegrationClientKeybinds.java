package com.example.soundattract.integration;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class TaczIntegrationClientKeybinds {
    public static KeyBinding shootKeyBinding;
    public static KeyBinding reloadKeyBinding;

    public static void register() {
        String keyCategory = "key.soundattract.tacz_category";
        shootKeyBinding = new KeyBinding(
            "key.soundattract.shoot",
            InputUtil.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_1,
            keyCategory
        );
        reloadKeyBinding = new KeyBinding(
            "key.soundattract.reload",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            keyCategory
        );
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(shootKeyBinding);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(reloadKeyBinding);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (shootKeyBinding.wasPressed()) {
                TaczIntegrationClientEvents.sendGunshotIfTaczGun(client);
            }
            if (reloadKeyBinding.wasPressed()) {
                TaczIntegrationClientEvents.sendReloadIfTaczGun(client);
            }
        });
    }
}
