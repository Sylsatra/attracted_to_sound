package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.neoforged.fml.ModList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import su.plo.voice.api.server.PlasmoVoiceServer;

/**
 * NeoForge bootstrap to load the Plasmo Voice addon at server start.
 * Parity with Fabric's explicit addon loader call.
 */
public final class PlasmoVoiceBootstrap {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {

        if (!ModList.get().isLoaded("plasmovoice")) {
            return;
        }
        if (!SoundAttractConfig.COMMON.enableVoiceChatIntegration.get()) {
            return;
        }
        try {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[PlasmoVoiceBootstrap] Loading Plasmo Voice addon...");
            }

            PlasmoVoiceServer.getAddonsLoader().load(new PlasmoIntegration());
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[PlasmoVoiceBootstrap] Plasmo Voice addon loaded.");
            }
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.error("[PlasmoVoiceBootstrap] Failed to load Plasmo Voice addon", t);
        }
    }
}
