package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric RaidManager: schedules group raids after an edge mob detects a player and relays to the leader.
 * Countdown and advancing states are ticked per-world.
 */
public class RaidManager {
    private static class PerWorldData {
        final Map<MobEntity, Raid> leaderToRaid = Collections.synchronizedMap(new WeakHashMap<>());
        long lastCleanupTime = -1L;
    }

    private static final Map<RegistryKey<World>, PerWorldData> DATA = new ConcurrentHashMap<>();

    private static PerWorldData getData(ServerWorld world) {
        return DATA.computeIfAbsent(world.getRegistryKey(), k -> new PerWorldData());
    }

    private static class Raid {
        final WeakReference<MobEntity> leaderRef;
        final BlockPos target;
        int ticksRemaining;
        boolean advancing;
        final long scheduledAt;

        Raid(MobEntity leader, BlockPos target, long nowTicks) {
            this.leaderRef = new WeakReference<>(leader);
            this.target = target;
            int configured = SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.raidCountdownTicks : 200;
            this.ticksRemaining = configured;
            this.advancing = false;
            this.scheduledAt = nowTicks;
        }

        boolean isValid() {
            MobEntity leader = leaderRef.get();
            return leader != null && leader.isAlive() && !leader.isRemoved();
        }
    }

    public static void scheduleRaid(MobEntity leader, BlockPos targetPos, long nowTicks) {
        if (!(leader.getWorld() instanceof ServerWorld world)) return;
        PerWorldData d = getData(world);
        Raid raid = new Raid(leader, targetPos, nowTicks);
        d.leaderToRaid.put(leader, raid);
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[RaidManager] Scheduled RAID for leader {} at {} (countdown {} ticks)",
                    leader.getName().getString(), targetPos, raid.ticksRemaining);
        }
    }

    public static boolean isRaidTicking(MobEntity leader) {
        Raid r = getRaid(leader);
        return r != null && !r.advancing && r.ticksRemaining > 0;
    }

    public static boolean isRaidAdvancing(MobEntity leader) {
        Raid r = getRaid(leader);
        return r != null && r.advancing;
    }

    public static BlockPos getRaidTarget(MobEntity leader) {
        Raid r = getRaid(leader);
        return r != null ? r.target : null;
    }

    public static void clearRaid(MobEntity leader) {
        if (!(leader.getWorld() instanceof ServerWorld world)) return;
        PerWorldData d = getData(world);
        d.leaderToRaid.remove(leader);
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[RaidManager] Cleared RAID for leader {}.", leader.getName().getString());
        }
    }

    private static Raid getRaid(MobEntity leader) {
        if (!(leader.getWorld() instanceof ServerWorld world)) return null;
        PerWorldData d = getData(world);
        return d.leaderToRaid.get(leader);
    }

    public static void tick(ServerWorld world) {
        PerWorldData d = getData(world);
        if (d.leaderToRaid.isEmpty()) return;

        double arrivalDistance = SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.arrivalDistance : 6.0;
        double arrivalDistSq = arrivalDistance * arrivalDistance;

        d.leaderToRaid.entrySet().removeIf(entry -> {
            MobEntity leader = entry.getKey();
            Raid raid = entry.getValue();
            if (leader == null || raid == null || !raid.isValid()) return true;

            if (!raid.advancing) {
                raid.ticksRemaining--;
                if (raid.ticksRemaining <= 0) {
                    raid.advancing = true;
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[RaidManager] RAID READY for leader {}. Advancing to {}", leader.getName().getString(), raid.target);
                    }
                } else if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging && raid.ticksRemaining % 40 == 0) {
                    SoundAttractMod.LOGGER.info("[RaidManager] RAID ticking... leader {} -> {} ({} ticks left)", leader.getName().getString(), raid.target, raid.ticksRemaining);
                }
            } else {

                if (leader.squaredDistanceTo(raid.target.getX() + 0.5, raid.target.getY() + 0.5, raid.target.getZ() + 0.5) < arrivalDistSq) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[RaidManager] RAID completed for leader {} at {}", leader.getName().getString(), raid.target);
                    }
                    return true;
                }
            }
            return false;
        });
    }
}
