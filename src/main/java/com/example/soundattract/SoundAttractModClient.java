package com.example.soundattract;

import net.fabricmc.api.ClientModInitializer;
import com.example.soundattract.integration.TaczIntegrationClientLogic;

public class SoundAttractModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) 
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] SoundAttractModClient.onInitializeClient: Starting client-side initialization");
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] TACZ_GUNSHOT_ID (client) = {}", com.example.soundattract.SoundAttractNetwork.TACZ_GUNSHOT_ID);
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] SOUND_MESSAGE_ID (client) = {}", com.example.soundattract.SoundAttractNetwork.SOUND_MESSAGE_ID);
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] TACZ_RELOAD_ID (client) = {}", com.example.soundattract.SoundAttractNetwork.TACZ_RELOAD_ID);
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] TaczGunshotMessage.ID = {}", com.example.soundattract.integration.TaczGunshotMessage.ID);
    com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundAttractModClient] onInitializeClient called");
}
    }
}
