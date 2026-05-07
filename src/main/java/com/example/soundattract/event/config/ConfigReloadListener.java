package com.example.soundattract.event.config;

import com.example.soundattract.Soundattract;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.SoundAttractionEvents;
import com.example.soundattract.worker.WorkSchedulerManager;

public class ConfigReloadListener {
    public static void onConfigLoad() {
        SoundAttractConfig.bakeConfig();
        SoundAttractionEvents.invalidateCachedEntityTypes();
        WorkSchedulerManager.refresh();
    }

    public static void onConfigReload() {
        SoundAttractConfig.bakeConfig();
        SoundAttractionEvents.invalidateCachedEntityTypes();
        WorkSchedulerManager.refresh();
    }
}
