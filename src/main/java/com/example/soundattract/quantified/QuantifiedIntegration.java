package com.example.soundattract.quantified;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.bridge.QuantifiedOptionalBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.util.concurrent.atomic.AtomicBoolean;

public final class QuantifiedIntegration {
    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    private QuantifiedIntegration() {
    }

    public static void bootstrap() {
        if (!INIT.compareAndSet(false, true)) return;
        if (!FabricLoader.getInstance().isModLoaded("quantified")) return;
        try {
            if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) return;
        } catch (Throwable ignored) {
        }

        try {
            if (!QuantifiedOptionalBridge.register(Soundattract.MOD_ID)) {
                return;
            }
            
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.debug("[Quantified] Successfully registered with Quantified API 1.1.0");
            }
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.debug("[Quantified] Registration failed: {}", t.getMessage());
            }
        }
    }
}
