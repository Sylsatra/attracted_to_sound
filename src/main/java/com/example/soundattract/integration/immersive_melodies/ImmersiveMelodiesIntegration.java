package com.example.soundattract.integration.immersive_melodies;

import com.example.soundattract.SoundAttractMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;

public class ImmersiveMelodiesIntegration {
    public static void init() {
        if (ModList.get().isLoaded("immersive_melodies")) {
            SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Initializing integration...");
            MinecraftForge.EVENT_BUS.register(ImmersiveMelodiesEvents.class);
        } else {
            SoundAttractMod.LOGGER.info("[ImmersiveMelodies] Mod not found, skipping integration.");
        }
    }
}
