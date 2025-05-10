package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfigData;

public class DynamicScanCooldownManager {
    private static volatile double lastTps = 20.0; 

    public static int currentScanCooldownTicks = com.example.soundattract.SoundAttractMod.CONFIG != null ? com.example.soundattract.SoundAttractMod.CONFIG.scanCooldownTicks : 20;
    private static final int MIN_COOLDOWN = 10;
    private static final int DEFAULT_MAX_COOLDOWN = 60;
    private static final int MOBS_100_MAX_COOLDOWN = 100;
    private static final int MOBS_200_MAX_COOLDOWN = 150;
    private static final int MOBS_400_MAX_COOLDOWN = 200;
    private static final int MOBS_800_MAX_COOLDOWN = 300;
    private static final int MOBS_100_THRESHOLD = 100;
    private static final int MOBS_200_THRESHOLD = 200;
    private static final int MOBS_400_THRESHOLD = 400;
    private static final int MOBS_800_THRESHOLD = 800;

    private static double getLowTps() {
        return com.example.soundattract.SoundAttractMod.CONFIG.minTpsForScanCooldown;
    }
    private static double getHighTps() {
        return com.example.soundattract.SoundAttractMod.CONFIG.maxTpsForScanCooldown;
    }

    private static long lastCheckTime = System.currentTimeMillis();
    private static long lastTickCount = 0;

    public static void update(long totalTickCount, int mobCount) {

        long now = System.currentTimeMillis();
        long ticksElapsed = totalTickCount - lastTickCount;
        long timeElapsed = now - lastCheckTime;
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
            double tps = (ticksElapsed * 1000.0) / timeElapsed * 20.0;
            lastTps = 0.8 * lastTps + 0.2 * tps;
            double lowTps = getLowTps();
            double highTps = getHighTps();
            if (tps < lowTps || mobCount > MOBS_100_THRESHOLD) {
                currentScanCooldownTicks = Math.min(maxCooldown, currentScanCooldownTicks + 2);
            } else if (tps > highTps && currentScanCooldownTicks > MIN_COOLDOWN) {
                currentScanCooldownTicks = Math.max(MIN_COOLDOWN, currentScanCooldownTicks - 1);
            }
        }
        lastCheckTime = now;
        lastTickCount = totalTickCount;
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[CooldownManager] cooldown={}, mobCount={}, tps={}", currentScanCooldownTicks, mobCount, lastTps);
        }
    }

    public static boolean shouldScanThisTick(long mobId, long totalTickCount) {
        return ((mobId + totalTickCount) % currentScanCooldownTicks) == 0;
    }

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
