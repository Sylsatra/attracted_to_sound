package com.example.soundattract.worker;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.quantified.QuantifiedWorkScheduler;

import net.minecraftforge.fml.ModList;

public final class WorkSchedulerManager {
    private static volatile SoundAttractWorkScheduler INSTANCE;

    private WorkSchedulerManager() {}

    public static SoundAttractWorkScheduler get() {
        SoundAttractWorkScheduler local = INSTANCE;
        if (local != null) return local;
        synchronized (WorkSchedulerManager.class) {
            if (INSTANCE != null) return INSTANCE;
            INSTANCE = build();
            return INSTANCE;
        }
    }

    public static void refresh() {
        synchronized (WorkSchedulerManager.class) {
            INSTANCE = build();
        }
    }

    private static SoundAttractWorkScheduler build() {
        boolean quantifiedLoaded = ModList.get().isLoaded("quantified");
        if (quantifiedLoaded) {
            try {
                if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) {
                    SoundAttractMod.LOGGER.info("[WorkSchedulerManager] Quantified integration forced on for performance.");
                }
            } catch (Throwable ignored) {}
            try {
                return new QuantifiedWorkScheduler();
            } catch (Throwable ignored) {}
        }

        return new LocalWorkScheduler();
    }
}
