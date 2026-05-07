package com.example.soundattract;

import com.example.soundattract.camo.CamoClientEvents;
import com.example.soundattract.client.AttractionClientEvents;
import com.example.soundattract.config.SoundAttractConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundAttractClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(Soundattract.MOD_ID + "-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Sound Attract client...");

        com.example.soundattract.camo.CamoClientEvents.register();
        AttractionClientEvents.register();
        com.example.soundattract.event.SoundAttractFabricEvents.registerClient();

        if (ModChecker.isLoaded("voicechat")) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                LOGGER.info("[SoundAttractClient] Registering VoiceChat integration on client setup");
            }
            com.example.soundattract.event.client.SoundAttractClientEvents.registerVoiceChatIntegration();
        } else {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                LOGGER.info("[SoundAttractClient] VoiceChat mod not present; skipping integration");
            }
        }

        LOGGER.info("Sound Attract client initialized successfully!");
    }
}
