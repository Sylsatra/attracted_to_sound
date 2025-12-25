package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.function.Supplier;

public final class QuantifiedCacheCompat {
    private static final boolean IS_QUANTIFIED_LOADED = ModList.get().isLoaded("quantified");

    private static volatile boolean initAttempted;
    private static volatile boolean initialized;

    private static Method register;
    private static Method getCached;
    private static Method getCacheManager;
    private static Method cacheManagerSetLimit;
    private static Method cacheManagerIsPressureHigh;
    private static Method cacheManagerTriggerCleanup;

    private static volatile boolean cacheManagerInitAttempted;
    private static volatile boolean cacheManagerInitialized;
    private static volatile long lastAppliedLimitMb = Long.MIN_VALUE;
    private static volatile long lastCleanupTick = Long.MIN_VALUE;

    private QuantifiedCacheCompat() {
    }

    public static boolean isUsable() {
        if (!IS_QUANTIFIED_LOADED) return false;
        try {
            if (!SoundAttractConfig.COMMON.enableQuantifiedIntegration.get()) return false;
            if (!SoundAttractConfig.COMMON.enableQuantifiedCacheIntegration.get()) return false;
        } catch (Throwable ignored) {
        }
        if (!ensureInit()) return false;

        tryConfigureCacheManager();
        if (shouldBypassDueToMemoryPressure()) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getCached(String cacheName, String key, Supplier<T> loader, long ttlTicks, long maxSize) {
        return getCachedInternal(cacheName, key, loader, ttlTicks, maxSize, false);
    }

    public static <T> T getCachedDisk(String cacheName, String key, Supplier<T> loader, long ttlTicks, long maxSize) {
        return getCachedInternal(cacheName, key, loader, ttlTicks, maxSize, true);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getCachedInternal(String cacheName, String key, Supplier<T> loader, long ttlTicks, long maxSize, boolean useDisk) {
        if (loader == null) return null;
        if (!isUsable()) {
            return loader.get();
        }

        long clampedTtlTicks = Math.max(1L, ttlTicks);
        Duration ttl = Duration.ofMillis(clampedTtlTicks * estimateMillisPerTick());
        long clampedMax = Math.max(0L, maxSize);

        try {
            Object out = getCached.invoke(null, cacheName, key, loader, ttl, clampedMax, useDisk);
            return (T) out;
        } catch (Throwable t) {
            return loader.get();
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
            return Math.max(50L, Math.min(250L, ms));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean shouldBypassDueToMemoryPressure() {
        boolean bypass = true;
        try {
            bypass = SoundAttractConfig.COMMON.disableQuantifiedCacheOnMemoryPressure.get();
        } catch (Throwable ignored) {
        }
        if (!bypass) return false;

        if (!cacheManagerInitialized) return false;
        try {
            Object mgr = getCacheManager.invoke(null);
            if (mgr == null) return false;
            Object res = cacheManagerIsPressureHigh.invoke(mgr);
            return (res instanceof Boolean b) && b.booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void tryConfigureCacheManager() {
        if (!ensureCacheManagerInit()) {
            return;
        }

        long limitMb = 0L;
        try {
            limitMb = SoundAttractConfig.COMMON.quantifiedCacheMemoryLimitMB.get();
        } catch (Throwable ignored) {
        }
        limitMb = Math.max(0L, limitMb);

        try {
            if (limitMb != lastAppliedLimitMb) {
                Object mgr = getCacheManager.invoke(null);
                if (mgr != null) {
                    cacheManagerSetLimit.invoke(mgr, limitMb);
                    lastAppliedLimitMb = limitMb;
                }
            }
        } catch (Throwable ignored) {
        }

        boolean triggerCleanup = true;
        try {
            triggerCleanup = SoundAttractConfig.COMMON.triggerQuantifiedCacheCleanupOnMemoryPressure.get();
        } catch (Throwable ignored) {
        }
        if (!triggerCleanup) return;

        try {
            Object mgr = getCacheManager.invoke(null);
            if (mgr == null) return;
            Object res = cacheManagerIsPressureHigh.invoke(mgr);
            boolean high = (res instanceof Boolean b) && b.booleanValue();
            if (!high) return;

            long tick = System.currentTimeMillis() / 50L;
            if ((tick - lastCleanupTick) < 20L) {
                return;
            }
            lastCleanupTick = tick;
            cacheManagerTriggerCleanup.invoke(mgr);
        } catch (Throwable ignored) {
        }
    }

    private static boolean ensureCacheManagerInit() {
        if (cacheManagerInitialized) return true;
        if (cacheManagerInitAttempted && !cacheManagerInitialized) return false;
        synchronized (QuantifiedCacheCompat.class) {
            if (cacheManagerInitialized) return true;
            if (cacheManagerInitAttempted && !cacheManagerInitialized) return false;
            cacheManagerInitAttempted = true;
            try {
                Class<?> api = Class.forName("org.admany.quantified.api.QuantifiedAPI");
                getCacheManager = api.getMethod("getCacheManager");

                Class<?> mgrClass = Class.forName("org.admany.quantified.api.interfaces.ModCacheManager");
                cacheManagerSetLimit = mgrClass.getMethod("setMemoryLimitMB", long.class);
                cacheManagerIsPressureHigh = mgrClass.getMethod("isMemoryPressureHigh");
                cacheManagerTriggerCleanup = mgrClass.getMethod("triggerMemoryPressureCleanup");

                cacheManagerInitialized = true;
                return true;
            } catch (Throwable t) {
                cacheManagerInitialized = false;
                return false;
            }
        }
    }

    private static boolean ensureInit() {
        if (initialized) return true;
        if (initAttempted && !initialized) return false;
        synchronized (QuantifiedCacheCompat.class) {
            if (initialized) return true;
            if (initAttempted && !initialized) return false;
            initAttempted = true;
            try {
                Class<?> api = Class.forName("org.admany.quantified.api.QuantifiedAPI");
                register = api.getMethod("register", String.class);
                getCached = api.getMethod(
                    "getCached",
                    String.class,
                    String.class,
                    java.util.function.Supplier.class,
                    java.time.Duration.class,
                    long.class,
                    boolean.class
                );
                register.invoke(null, SoundAttractMod.MOD_ID);
                initialized = true;
                return true;
            } catch (Throwable t) {
                initialized = false;
                return false;
            }
        }
    }
}
