package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;

public class DynamicScanCooldownManager {
    public static int currentScanCooldownTicks = 20;
    private static final int MIN_COOLDOWN = 10;
    private static final int DEFAULT_MAX_COOLDOWN = 60;
    private static final int HIGH_MOBCOUNT_MAX_COOLDOWN = 100;
    private static final int HIGH_MOBCOUNT_THRESHOLD = 100;

    private static double getLowTps() {
        return SoundAttractConfig.minTpsForScanCooldown.get();
    }
    private static double getHighTps() {
        return SoundAttractConfig.maxTpsForScanCooldown.get();
    }

    private static long lastCheckTime = System.currentTimeMillis();
    private static long lastTickCount = 0;

    public static void update(long totalTickCount, int mobCount) {
        long now = System.currentTimeMillis();
        long ticksElapsed = totalTickCount - lastTickCount;
        long timeElapsed = now - lastCheckTime;
        int maxCooldown = mobCount > HIGH_MOBCOUNT_THRESHOLD ? HIGH_MOBCOUNT_MAX_COOLDOWN : DEFAULT_MAX_COOLDOWN;
        if (ticksElapsed > 0 && timeElapsed > 0) {
            double tps = (ticksElapsed * 1000.0) / timeElapsed * 20.0;
            double lowTps = getLowTps();
            double highTps = getHighTps();
            if (tps < lowTps || mobCount > HIGH_MOBCOUNT_THRESHOLD) {
                currentScanCooldownTicks = Math.min(maxCooldown, currentScanCooldownTicks + 2);
            } else if (tps > highTps && currentScanCooldownTicks > MIN_COOLDOWN) {
                currentScanCooldownTicks = Math.max(MIN_COOLDOWN, currentScanCooldownTicks - 1);
            }
        }
        lastCheckTime = now;
        lastTickCount = totalTickCount;
    }

    public static boolean shouldScanThisTick(long mobId, long totalTickCount) {
        return ((mobId + totalTickCount) % currentScanCooldownTicks) == 0;
    }
}
