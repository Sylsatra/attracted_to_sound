package com.example.soundattract.util;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * ThreadingChecks: small helpers to ensure we keep world/entity access on the main server thread.
 */
public final class ThreadingChecks {
    private ThreadingChecks() {}

    public static boolean isServerThread(World world) {
        if (world instanceof ServerWorld sw) {
            return sw.getServer().isOnThread();
        }
        return true;
    }

    public static void warnIfOffServerThread(World world, String where) {
        if (world == null) return;
        if (!isServerThread(world)) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[ThreadingChecks] Off-thread world access at {} (thread: {})", where, Thread.currentThread().getName());
            }
        }
    }
}
