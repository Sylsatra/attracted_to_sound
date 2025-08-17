package com.example.soundattract.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import com.example.soundattract.config.SoundAttractConfig;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import com.example.soundattract.SoundAttractMod;
import java.lang.ref.WeakReference;

public class MobGroupManager {
    private static final Map<UUID, Mob> uuidToLeader = Collections.synchronizedMap(new HashMap<>());
    private static final List<WeakReference<Mob>> leaders = Collections.synchronizedList(new ArrayList<>());
    private static final Map<Mob, List<SoundRelay>> mobToRelayedSounds = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Mob, Long> mobLastRelayTime = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<UUID> deserterUuids = Collections.synchronizedSet(new HashSet<>());
    private static long lastGroupUpdateTime = -1;
    private static final int RELAY_SOUND_TTL = 40; 
    private static final int RELAY_SOUND_RATE_LIMIT = 20; 
    private static final double STICKY_RADIUS_MARGIN = 2.0; 
    private static long lastCleanupTime = -1;
    private static final Object cleanupLock = new Object();
    private static Map<Mob, Set<Mob>> lastEdgeMobMap = new HashMap<>(); 

    public static class SoundRelay {
        public final String soundId; 
        public final double x, y, z, range, weight;
        public final long timestamp;
        public final int hash;
        public SoundRelay(String soundId, double x, double y, double z, double range, double weight, long timestamp) {
            this.soundId = soundId;
            this.x = x; this.y = y; this.z = z; this.range = range; this.weight = weight; this.timestamp = timestamp;
            this.hash = Objects.hash(soundId, (int)x, (int)y, (int)z, (int)range, (int)(weight*100));
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SoundRelay other)) return false;
            return this.hash == other.hash && 
                   Objects.equals(this.soundId, other.soundId) &&
                   Math.abs(this.x - other.x) < 0.1 && Math.abs(this.y - other.y) < 0.1 && Math.abs(this.z - other.z) < 0.1 && 
                   Math.abs(this.range - other.range) < 0.1 && Math.abs(this.weight - other.weight) < 0.01 && 
                   Math.abs(this.timestamp - other.timestamp) < RELAY_SOUND_TTL;
        }
        @Override
        public int hashCode() { return hash; }
    }

    public static boolean isEdgeMob(Mob mob) {
        if (SoundAttractConfig.COMMON.debugLogging.get())
            SoundAttractMod.LOGGER.info("[isEdgeMob] Checking mob {} (pos: {}, {})", mob.getName().getString(), mob.getX(), mob.getZ());
        Mob leader = getLeader(mob);
        if (leader == mob) {
            if (SoundAttractConfig.COMMON.debugLogging.get())
                SoundAttractMod.LOGGER.info("[isEdgeMob] Mob {} is its own leader (not edge)", mob.getName().getString());
            return false;
        }
        Set<Mob> edgeSet = lastEdgeMobMap.get(leader);
        boolean isEdge = edgeSet != null && edgeSet.contains(mob);
        if (SoundAttractConfig.COMMON.debugLogging.get())
            SoundAttractMod.LOGGER.info("[isEdgeMob] Mob {} is {}edge mob for leader {}", mob.getName().getString(), isEdge ? "" : "NOT ", leader.getName().getString()); return isEdge;
    }

    private static void cleanupStaleEntries(ServerLevel level) {
        synchronized (cleanupLock) {
            synchronized (leaders) {
                leaders.removeIf(ref -> {
                    Mob mob = ref.get();
                    return mob == null || mob.isRemoved();
                });
            }
            synchronized (uuidToLeader) {
                uuidToLeader.keySet().removeIf(uuid -> {
                    Mob mob = uuidToLeader.get(uuid);
                    return mob == null || mob.isRemoved();
                });
            }
            synchronized (mobToRelayedSounds) {
                mobToRelayedSounds.keySet().removeIf(mob -> mob == null || mob.isRemoved());
            }
            synchronized (mobLastRelayTime) {
                mobLastRelayTime.keySet().removeIf(mob -> mob == null || mob.isRemoved());
            }
            synchronized (deserterUuids) {
                deserterUuids.removeIf(uuid -> {
                    Mob mob = uuidToLeader.get(uuid);
                    return mob == null || mob.isRemoved();
                });
            }
        }
    }

    public static void updateGroups(ServerLevel level) {
        long time = level.getGameTime();
        if (time - lastGroupUpdateTime < SoundAttractConfig.COMMON.groupUpdateInterval.get()) return;
        lastGroupUpdateTime = time;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[MobGroupManager] Updating groups at time: " + time);
        }
        int scanCooldown = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;

        if (scanCooldown > 0 && (lastCleanupTime == -1 || time - lastCleanupTime > 10L * scanCooldown)) {
            cleanupStaleEntries(level);
            lastCleanupTime = time;
        }

        Set<net.minecraft.world.entity.EntityType<?>> attractedEntityTypes = com.example.soundattract.SoundAttractionEvents.getCachedAttractedEntityTypes();
        int simDistChunks = level.getServer().getPlayerList().getViewDistance();
        int simDistBlocks = simDistChunks * 16;
        Set<Mob> mobsSet = new HashSet<>();
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(simDistBlocks);
            List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, box);
            mobsSet.addAll(nearbyMobs);
        }
        List<Mob> mobs = new ArrayList<>(mobsSet);
        StringBuilder allMobTypesLog = new StringBuilder();
        for (Mob mob : mobs) {
            ResourceLocation id = mob.getType().builtInRegistryHolder().key().location();
            allMobTypesLog.append(String.format("%s at (%.1f, %.1f, %.1f); ", id.toString(), mob.getX(), mob.getY(), mob.getZ()));
        }
        if (SoundAttractConfig.COMMON.debugLogging.get())
            SoundAttractMod.LOGGER.info("[MobGroupManager] All mobs present ({}): {}", mobs.size(), allMobTypesLog.toString());
        List<Mob> attractedMobs = new ArrayList<>();
        for (Mob mob : mobs) {
            boolean byType = attractedEntityTypes.contains(mob.getType());
            boolean hasProfile = SoundAttractConfig.getMatchingProfile(mob) != null;
            if (byType || hasProfile) {
                attractedMobs.add(mob);
            }
        }
        StringBuilder mobPosLog = new StringBuilder();
        for (Mob mob : attractedMobs) {
            mobPosLog.append(String.format("%s at (%.1f, %.1f, %.1f); ", mob.getName().getString(), mob.getX(), mob.getY(), mob.getZ()));
        }
        if (SoundAttractConfig.COMMON.debugLogging.get())
            SoundAttractMod.LOGGER.info("[MobGroupManager] Attracted mobs ({}): {}", attractedMobs.size(), mobPosLog.toString());
        uuidToLeader.clear();
        leaders.clear();
        if (attractedMobs.isEmpty()) return;
        for (Mob m : attractedMobs) {
            if (SoundAttractConfig.getMatchingProfile(m) != null) {
                leaders.add(new WeakReference<>(m));
                uuidToLeader.put(m.getUUID(), m);
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[MobGroupManager] Profiled mob {} forced to leader",
                                               m.getType().builtInRegistryHolder().key().location());            
                }
            }
        }
        Collections.shuffle(attractedMobs, new java.util.Random(level.getGameTime()));
        double groupRadius = SoundAttractConfig.COMMON.leaderGroupRadius.get();
        int maxLeaders = SoundAttractConfig.COMMON.maxLeaders.get();
        int maxGroupSize = SoundAttractConfig.COMMON.maxGroupSize.get();
        double leaderSpacing = groupRadius * SoundAttractConfig.COMMON.leaderSpacingMultiplier.get();
        Set<Mob> assigned = new HashSet<>();
        List<Mob> leaderList = new ArrayList<>();
        synchronized (leaders) {
            leaders.removeIf(ref -> ref.get() == null || ref.get().isRemoved());
            attractedMobs.sort(Comparator.comparingDouble(m -> -m.getHealth())); 

            for (Mob potentialLeader : attractedMobs) {
                if (leaders.size() >= SoundAttractConfig.COMMON.maxLeaders.get()) break;
                if (leaders.stream().anyMatch(ref -> ref.get() == potentialLeader)) continue; 

                boolean tooCloseToExistingLeader = false;
                for (WeakReference<Mob> leaderRef : leaders) {
                    Mob existingLeader = leaderRef.get();
                    if (existingLeader != null && potentialLeader.distanceToSqr(existingLeader) < (SoundAttractConfig.COMMON.leaderGroupRadius.get() * SoundAttractConfig.COMMON.leaderSpacingMultiplier.get()) * (SoundAttractConfig.COMMON.leaderGroupRadius.get() * SoundAttractConfig.COMMON.leaderSpacingMultiplier.get())) {
                        tooCloseToExistingLeader = true;
                        break;
                    }
                }
                if (!tooCloseToExistingLeader) {
                    leaders.add(new WeakReference<>(potentialLeader));
                    uuidToLeader.put(potentialLeader.getUUID(), potentialLeader); 
                    leaderList.add(potentialLeader);
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[MobGroupManager] Promoted {} to LEADER", potentialLeader.getName().getString());
                    }
                }
            }
        }
        Map<Mob, List<Mob>> leaderToGroup = new HashMap<>();
        for (Mob leader : leaderList) {
            List<Mob> group = new ArrayList<>();
            group.add(leader);
            for (Mob mob : attractedMobs) {
                if (mob == leader || assigned.contains(mob)) continue;
                if (mob.distanceTo(leader) <= groupRadius && group.size() < maxGroupSize) {
                    group.add(mob);
                    assigned.add(mob);
                }
            }
            for (Mob mob : group) {
                uuidToLeader.put(mob.getUUID(), leader);
            }
            leaderToGroup.put(leader, group);
        }
        for (Mob mob : attractedMobs) {
            if (!assigned.contains(mob)) {
                deserterUuids.add(mob.getUUID());
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[MobGroupManager] Mob {} marked as DESERTER (not in any group)", mob.getName().getString());
                }
            } else {
                deserterUuids.remove(mob.getUUID());
            }
        }
        lastEdgeMobMap.clear();
        for (Mob leader : leaderList) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                List<Mob> g = leaderToGroup.getOrDefault(leader, Collections.emptyList());
                SoundAttractMod.LOGGER.info(
            "[MobGroupManager] Group for leader {} has {} members (radius {}).",
            leader.getName().getString(), g.size(), groupRadius);
                }
            List<Mob> group = leaderToGroup.get(leader);   
            if (group == null) continue;

            int sectors = SoundAttractConfig.COMMON.numEdgeSectors.get();
            int edgePerSector = 4;
            Map<Integer, List<Mob>> sectorToFarthestList = new HashMap<>();
            double leaderX = leader.getX(), leaderZ = leader.getZ();
            for (Mob m : group) {
                if (m == leader) continue;
                double dx = m.getX() - leaderX, dz = m.getZ() - leaderZ;
                double angle = Math.atan2(dz, dx);
                int sector = (int) Math.floor(((angle + Math.PI) / (2 * Math.PI)) * sectors) % sectors;
                sectorToFarthestList.computeIfAbsent(sector, k -> new ArrayList<>()).add(m);
            }
            Set<Mob> edgeMobs = new HashSet<>();
            for (Map.Entry<Integer, List<Mob>> entry : sectorToFarthestList.entrySet()) {
                int sector = entry.getKey();
                List<Mob> mobsInSector = entry.getValue();
                mobsInSector.sort((a, b) -> Double.compare(b.distanceTo(leader), a.distanceTo(leader)));
                int edgeCount = Math.min(edgePerSector, mobsInSector.size());
                if (edgeCount == 0 && !mobsInSector.isEmpty()) edgeCount = 1;
                for (int i = 0; i < edgeCount; i++) {
                    edgeMobs.add(mobsInSector.get(i));
                }
            }
            if (edgeMobs.isEmpty() && group.size() > 1) {
                Mob farthest = null;
                double maxDist = -1;
                for (Mob m : group) {
                    if (m == leader) continue;
                    double dist = m.distanceTo(leader);
                    if (dist > maxDist) {
                        maxDist = dist;
                        farthest = m;
                    }
                }
                if (farthest != null) edgeMobs.add(farthest);
            }
            lastEdgeMobMap.put(leader, edgeMobs);
            if (SoundAttractConfig.COMMON.debugLogging.get()) { 
                StringBuilder sb = new StringBuilder();
                for (Mob edge : edgeMobs) sb.append(edge.getName().getString()).append(", ");
                SoundAttractMod.LOGGER.info("[MobGroupManager] Edge mobs for leader {}: {}", leader.getName().getString(), sb.toString());
            }
        }
        mobToRelayedSounds.entrySet().removeIf(e -> e.getKey().isRemoved());
        for (List<SoundRelay> relays : mobToRelayedSounds.values()) {
            relays.removeIf(r -> time - r.timestamp > RELAY_SOUND_TTL);
        }
        mobLastRelayTime.entrySet().removeIf(e -> e.getKey().isRemoved());
        List<Mob> allAttractedMobs = new ArrayList<>();
        Set<net.minecraft.world.entity.EntityType<?>> cachedTypes = attractedEntityTypes;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, level.getWorldBorder().getCollisionShape().bounds())) {
            boolean byType = cachedTypes.contains(mob.getType());
            boolean hasProfile = SoundAttractConfig.getMatchingProfile(mob) != null;
            if (byType || hasProfile) {
                allAttractedMobs.add(mob);
            }
        }
        synchronized (deserterUuids) {
            for (Mob mob : allAttractedMobs) {
                Mob leader = uuidToLeader.get(mob.getUUID());
                boolean isGrouped = leader != null && leader != mob;
                if (!isGrouped) {
                    deserterUuids.add(mob.getUUID());
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[MobGroupManager] Mob {} marked as DESERTER", mob.getName().getString());
                    }
                } else {
                    deserterUuids.remove(mob.getUUID());
                }
            }
        }
    }

    public static void relaySoundToLeader(Mob mob, String soundId, double x, double y, double z, double range, double weight, long timestamp) {
        Mob leader = getLeader(mob);
        if (leader == mob) return;
        Long lastRelay = mobLastRelayTime.get(mob);
        if (lastRelay != null && timestamp - lastRelay < RELAY_SOUND_RATE_LIMIT) return;
        mobLastRelayTime.put(mob, timestamp);
        SoundRelay relay = new SoundRelay(soundId, x, y, z, range, weight, timestamp);
        List<SoundRelay> relays = mobToRelayedSounds.computeIfAbsent(leader, k -> new ArrayList<>());
        if (!relays.contains(relay)) {
            relays.add(relay);
        }
    }

    public static List<SoundRelay> consumeRelayedSounds(Mob leader) {
        List<SoundRelay> relays = mobToRelayedSounds.remove(leader);
        if (relays == null) return Collections.emptyList();
        long now = leader.level().getGameTime();
        Set<SoundRelay> deduped = new HashSet<>();
        for (SoundRelay r : relays) {
            if (now - r.timestamp <= RELAY_SOUND_TTL) {
                deduped.add(r);
            }
        }
        return new ArrayList<>(deduped);
    }

    public static Mob getLeader(Mob mob) {
        return uuidToLeader.getOrDefault(mob.getUUID(), mob);
    }

    public static void promoteToDeserter(Mob mob) {
        deserterUuids.add(mob.getUUID());
    }

    public static boolean isDeserter(Mob mob) {
        return deserterUuids.contains(mob.getUUID());
    }
}
