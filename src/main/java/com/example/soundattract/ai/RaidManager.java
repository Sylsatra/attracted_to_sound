package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized manager for RAID orchestration per dimension.
 * A RAID is started by an edge mob detecting a player and returning to its leader.
 * - Ticking: countdown until the raid begins (followers rally to leader).
 * - Advancing: leader and followers move toward the recorded target position.
 */
public class RaidManager {


    private static final int DEFAULT_RAID_COUNTDOWN_TICKS = 200;

    private static class PerWorldData {
        final Map<Mob, Raid> leaderToRaid = Collections.synchronizedMap(new WeakHashMap<>());
        long lastCleanupTime = -1L;
    }

    private static final Map<ResourceLocation, PerWorldData> DATA = new ConcurrentHashMap<>();

    private static PerWorldData getData(ServerLevel level) {
        return DATA.computeIfAbsent(level.dimension().location(), k -> new PerWorldData());
    }

    public static void scheduleRaid(Mob leader, BlockPos targetPos, long now) {
        if (!(leader.level() instanceof ServerLevel level)) return;
        PerWorldData d = getData(level);
        Raid raid = new Raid(leader, targetPos, now);
        d.leaderToRaid.put(leader, raid);
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            int countdown = SoundAttractConfig.COMMON.raidCountdownTicks.get();
            SoundAttractMod.LOGGER.info("[RaidManager] Scheduled RAID for leader {} at {} (countdown {} ticks)",
                    leader.getName().getString(), targetPos, countdown);
        }
    }

    public static boolean isRaidTicking(Mob leader) {
        Raid r = getRaid(leader);
        return r != null && !r.advancing && r.ticksRemaining > 0;
    }

    public static boolean isRaidAdvancing(Mob leader) {
        Raid r = getRaid(leader);
        return r != null && r.advancing;
    }

    public static BlockPos getRaidTarget(Mob leader) {
        Raid r = getRaid(leader);
        return r != null ? r.target : null;
    }

    public static int getTicksRemaining(Mob leader) {
        Raid r = getRaid(leader);
        return r != null ? r.ticksRemaining : -1;
    }

    public static void clearRaid(Mob leader) {
        if (!(leader.level() instanceof ServerLevel level)) return;
        PerWorldData d = getData(level);
        d.leaderToRaid.remove(leader);
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[RaidManager] Cleared RAID for leader {}", leader.getName().getString());
        }
    }

    private static Raid getRaid(Mob leader) {
        if (!(leader.level() instanceof ServerLevel level)) return null;
        PerWorldData d = getData(level);
        Raid r = d.leaderToRaid.get(leader);
        if (r == null) return null;
        if (!r.isValid()) {
            d.leaderToRaid.remove(leader);
            return null;
        }
        return r;
    }

    public static void tick(ServerLevel level) {
        PerWorldData d = getData(level);
        long now = level.getGameTime();

        if (d.lastCleanupTime == -1L || now - d.lastCleanupTime > 200) {
            d.leaderToRaid.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isRemoved() || !e.getKey().isAlive());
            d.lastCleanupTime = now;
        }
        synchronized (d.leaderToRaid) {
            for (Iterator<Map.Entry<Mob, Raid>> it = d.leaderToRaid.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Mob, Raid> entry = it.next();
                Mob leader = entry.getKey();
                Raid raid = entry.getValue();
                if (leader == null || leader.isRemoved() || !leader.isAlive() || raid == null) {
                    it.remove();
                    continue;
                }
                if (!raid.advancing) {
                    if (raid.ticksRemaining > 0) {
                        raid.ticksRemaining--;
                        if (raid.ticksRemaining % 20 == 0 && SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[RaidManager] RAID ticking for leader {}: {} ticks remaining",
                                    leader.getName().getString(), raid.ticksRemaining);
                        }
                    }
                    if (raid.ticksRemaining <= 0) {
                        raid.ticksRemaining = 0;
                        raid.advancing = true;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[RaidManager] RAID READY: leader {} advancing to {}",
                                    leader.getName().getString(), raid.target);
                        }
                    }
                }
            }
        }
    }

    private static class Raid {
        final WeakReference<Mob> leaderRef;
        final BlockPos target;
        int ticksRemaining;
        boolean advancing;
        final long scheduledAt;

        Raid(Mob leader, BlockPos target, long now) {
            this.leaderRef = new WeakReference<>(leader);
            this.target = target;

            int configured = SoundAttractConfig.COMMON != null && SoundAttractConfig.COMMON.raidCountdownTicks != null
                    ? SoundAttractConfig.COMMON.raidCountdownTicks.get()
                    : DEFAULT_RAID_COUNTDOWN_TICKS;
            this.ticksRemaining = configured;
            this.advancing = false;
            this.scheduledAt = now;
        }

        boolean isValid() {
            Mob leader = leaderRef.get();
            return leader != null && leader.isAlive() && !leader.isRemoved();
        }
    }
}
