package com.example.soundattract;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.sound.SoundInstance;

public class SoundAttractClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.example.soundattract.integration.TaczIntegrationClientEvents.register();
    }
}
