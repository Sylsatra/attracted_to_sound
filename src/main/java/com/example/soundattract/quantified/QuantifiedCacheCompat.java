package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.admany.quantified.api.QuantifiedAPI;
import org.admany.quantified.api.interfaces.ModCacheManager;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class QuantifiedCacheCompat {
    private static final AtomicBoolean INIT_ATTEMPTED = new AtomicBoolean(false);
    private static volatile ModCacheManager cacheManager;
    private static volatile long lastAppliedLimitMb = Long.MIN_VALUE;
    private static volatile long lastCleanupTick = Long.MIN_VALUE;

    private QuantifiedCacheCompat() {
    }

    public static boolean isUsable() {
        if (!ModList.get().isLoaded("quantified")) return false;
        try {
            if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) return false;
            if (!SoundAttractConfig.COMMON.enableQuantifiedCacheIntegration.get()) return false;
        } catch (Throwable ignored) {
        }
        if (!ensureInit()) return false;

        configureCacheManager();
        if (isMemoryPressureHigh()) {
            return false;
        }
        return true;
    }

    public static <T> T getCached(String cacheName, String key, Supplier<T> loader, long ttlTicks, long maxSize) {
        return getCachedInternal(cacheName, key, loader, ttlTicks, maxSize, false);
    }

    public static <T> T getCachedDisk(String cacheName, String key, Supplier<T> loader, long ttlTicks, long maxSize) {
        return getCachedInternal(cacheName, key, loader, ttlTicks, maxSize, true);
    }

    private static <T> T getCachedInternal(String cacheName, String key, Supplier<T> loader, long ttlTicks, long maxSize, boolean useDisk) {
        if (loader == null) return null;
        if (!isUsable()) {
            return loader.get();
        }

        long clampedTtlTicks = Math.max(1L, ttlTicks);
        Duration ttl = Duration.ofMillis(clampedTtlTicks * estimateMillisPerTick());
        long clampedMax = Math.max(0L, maxSize);

        try {
            return QuantifiedAPI.getCached(cacheName, key, loader, ttl, clampedMax, useDisk);
        } catch (Throwable t) {
            return loader.get();
        }
    }

    private static boolean ensureInit() {
        if (cacheManager != null) return true;
        if (!INIT_ATTEMPTED.compareAndSet(false, true)) {
            return cacheManager != null;
        }
        try {
            QuantifiedAPI.register(SoundAttractMod.MOD_ID);
            cacheManager = QuantifiedAPI.getCacheManager();
            return cacheManager != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void configureCacheManager() {
        ModCacheManager manager = cacheManager;
        if (manager == null) return;

        long limitMb = 0L;
        try {
            limitMb = SoundAttractConfig.COMMON.quantifiedCacheMemoryLimitMB.get();
        } catch (Throwable ignored) {
        }
        limitMb = Math.max(0L, limitMb);

        if (limitMb != lastAppliedLimitMb) {
            try {
                manager.setMemoryLimitMB(limitMb);
                lastAppliedLimitMb = limitMb;
            } catch (Throwable ignored) {
            }
        }

        boolean triggerCleanup = true;
        try {
            triggerCleanup = SoundAttractConfig.COMMON.triggerQuantifiedCacheCleanupOnMemoryPressure.get();
        } catch (Throwable ignored) {
        }
        if (!triggerCleanup) return;

        try {
            if (!manager.isMemoryPressureHigh()) return;
            long tick = System.currentTimeMillis() / 50L;
            if ((tick - lastCleanupTick) < 20L) {
                return;
            }
            lastCleanupTick = tick;
            manager.triggerMemoryPressureCleanup();
        } catch (Throwable ignored) {
        }
    }

    private static boolean isMemoryPressureHigh() {
        boolean bypass = true;
        try {
            bypass = SoundAttractConfig.COMMON.disableQuantifiedCacheOnMemoryPressure.get();
        } catch (Throwable ignored) {
        }
        if (!bypass) return false;

        ModCacheManager manager = cacheManager;
        if (manager == null) return false;
        try {
            return manager.isMemoryPressureHigh();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long estimateMillisPerTick() {
        long fallback = 50L;
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return fallback;
            float avg = server.getAverageTickTime();
            if (Float.isNaN(avg) || avg <= 0.0f) return fallback;
            long ms = (long) avg;
            if (ms < 1L) return fallback;
            return Math.max(20L, Math.min(250L, ms));
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
