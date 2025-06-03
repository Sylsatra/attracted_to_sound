package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfigData;
import com.example.soundattract.ai.AdaptiveScanScheduler;
import com.example.soundattract.ai.MobGroupManager;
import net.minecraft.server.world.ServerWorld;

public class DynamicScanCooldownManager {
    private static volatile double lastTps = 20.0;

    // Start currentScanCooldownTicks at whatever the user configured (or 20 if CONFIG is null).
    public static int currentScanCooldownTicks = (SoundAttractMod.CONFIG != null
        ? SoundAttractMod.CONFIG.scanCooldownTicks
        : 20);

    private static final int DEFAULT_MAX_COOLDOWN = 60;
    private static final int MOBS_100_MAX_COOLDOWN = 100;
    private static final int MOBS_200_MAX_COOLDOWN = 150;
    private static final int MOBS_400_MAX_COOLDOWN = 200;
    private static final int MOBS_800_MAX_COOLDOWN = 300;

    private static final int MOBS_100_THRESHOLD = 100;
    private static final int MOBS_200_THRESHOLD = 200;
    private static final int MOBS_400_THRESHOLD = 400;
    private static final int MOBS_800_THRESHOLD = 800;

    // We no longer use MIN_COOLDOWN for clamping downward; instead we clamp at the configured value.
    private static double getLowTps() {
        return SoundAttractMod.CONFIG.minTpsForScanCooldown;
    }
    private static double getHighTps() {
        return SoundAttractMod.CONFIG.maxTpsForScanCooldown;
    }

    private static long lastCheckTime = System.currentTimeMillis();
    private static long lastTickCount = 0;

    // Base scheduler (unchanged)
    private static final int[] DEFAULT_TIER_SHIFTS = {0, 1, 2, 3};
    public static AdaptiveScanScheduler scheduler =
        new AdaptiveScanScheduler(currentScanCooldownTicks, DEFAULT_TIER_SHIFTS);

    public static void tickScheduler(ServerWorld level, long currentTick) {
        scheduler.tick(currentTick, task -> {
            MobGroupManager.updateCellGroup(task.cellKey, level);
        });
    }

    /**
     * Call this every server tick with the total tick count and current mob count.
     * If TPS is below getLowTps() or mobCount > 100, we slowly increase our cooldown.
     * If TPS is above getHighTps() and we're above the originally‐configured value, we decrease—
     * but we never go below the user’s configured scanCooldownTicks.
     */
    public static void update(long totalTickCount, int mobCount) {
        long now = System.currentTimeMillis();
        long ticksElapsed = totalTickCount - lastTickCount;
        long timeElapsed = now - lastCheckTime;

        // Decide which “maximum” cooldown to use based on how many mobs are loaded
        int maxCooldown;
        if (mobCount > MOBS_800_THRESHOLD) {
            maxCooldown = MOBS_800_MAX_COOLDOWN;
        } else if (mobCount > MOBS_400_THRESHOLD) {
            maxCooldown = MOBS_400_MAX_COOLDOWN;
        } else if (mobCount > MOBS_200_THRESHOLD) {
            maxCooldown = MOBS_200_MAX_COOLDOWN;
        } else if (mobCount > MOBS_100_THRESHOLD) {
            maxCooldown = MOBS_100_MAX_COOLDOWN;
        } else {
            maxCooldown = DEFAULT_MAX_COOLDOWN;
        }

        if (ticksElapsed > 0 && timeElapsed > 0) {
            // Recompute averaged TPS
            double tps = (ticksElapsed * 1000.0) / timeElapsed * 20.0;
            lastTps = 0.8 * lastTps + 0.2 * tps;

            double lowTps = getLowTps();
            double highTps = getHighTps();

            // Pull the user’s original scanCooldownTicks out of the config each tick
            int configuredCooldown = (SoundAttractMod.CONFIG != null
                ? SoundAttractMod.CONFIG.scanCooldownTicks
                : currentScanCooldownTicks);

            if (tps < lowTps || mobCount > MOBS_100_THRESHOLD) {
                // If TPS is low or mobCount is very high, we slowly ramp UP toward maxCooldown
                currentScanCooldownTicks = Math.min(maxCooldown, currentScanCooldownTicks + 2);
            } else if (tps > highTps && currentScanCooldownTicks > configuredCooldown) {
                // If TPS is high, we ramp DOWN— but only as far as the original configured value
                currentScanCooldownTicks = Math.max(configuredCooldown, currentScanCooldownTicks - 1);
            }
        }

        lastCheckTime = now;
        lastTickCount = totalTickCount;

        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[CooldownManager] cooldown={}, mobCount={}, tps={}",
                currentScanCooldownTicks, mobCount, lastTps
            );
        }
    }

    /**
     * Every mob calls this before running its “find‐sound” logic. If it returns true, that mob may
     * look for a new sound this tick; otherwise it skips.
     */
    public static boolean shouldScanThisTick(long mobId, long totalTickCount) {
        return ((mobId + totalTickCount) % currentScanCooldownTicks) == 0;
    }

    /**
     * Group‐assignment interval, unchanged from before.
     */
    public static int getGroupAssignmentInterval() {
        int base = currentScanCooldownTicks;
        double lowTps = getLowTps();
        if (lastTps < lowTps) {
            return Math.max(1, base * 20);
        } else {
            return Math.max(1, base * 3);
        }
    }
}
