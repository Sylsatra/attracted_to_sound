package com.example.soundattract.integration;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;

public class TaczIntegration {

    public static void register() {
        if (ModList.get().isLoaded("tacz")) {
            NeoForge.EVENT_BUS.register(TaczIntegrationServerEvents.class);
        }
    }
}