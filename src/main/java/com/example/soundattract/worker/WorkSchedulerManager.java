package com.example.soundattract.worker;

import com.example.soundattract.config.SoundAttractConfig;

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
        boolean preferQuantified = false;
        try {
            preferQuantified = SoundAttractConfig.COMMON.enableQuantifiedIntegration.get();
        } catch (Throwable ignored) {}

        if (preferQuantified && ModList.get().isLoaded("quantified")) {
            try {
                return new QuantifiedWorkScheduler();
            } catch (Throwable ignored) {}
        }

        return new LocalWorkScheduler();
    }
}
