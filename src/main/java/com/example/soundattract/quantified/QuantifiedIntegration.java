package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QuantifiedIntegration {
    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    private QuantifiedIntegration() {
    }

    public static void bootstrap() {
        if (!INIT.compareAndSet(false, true)) return;
        if (!ModList.get().isLoaded("quantified")) return;
        try {
            if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) return;
        } catch (Throwable ignored) {
        }

        try {
            Class<?> api = Class.forName("org.admany.quantified.api.QuantifiedAPI");
            Method register = api.getMethod("register", String.class);
            register.invoke(null, SoundAttractMod.MOD_ID);
            
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("[Quantified] Successfully registered with Quantified API 1.1.0");
            }
        } catch (Throwable t) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("[Quantified] Registration failed: {}", t.getMessage());
            }
        }
    }
}
