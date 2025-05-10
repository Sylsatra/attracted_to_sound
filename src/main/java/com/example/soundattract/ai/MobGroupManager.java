package com.example.soundattract.ai;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Box;
import net.minecraft.server.world.ServerWorld;

import java.util.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.example.soundattract.SoundAttractMod;
import java.lang.ref.WeakReference;
import java.util.UUID;
import net.minecraft.world.World;

public class MobGroupManager {
    private static final Map<UUID, MobEntity> uuidToLeader = Collections.synchronizedMap(new HashMap<>());
    private static final List<WeakReference<MobEntity>> leaders = Collections.synchronizedList(new ArrayList<>());
    private static final Map<MobEntity, List<SoundRelay>> mobToRelayedSounds = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MobEntity, Long> mobLastRelayTime = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<UUID> deserterUuids = Collections.synchronizedSet(new HashSet<>());
    private static long lastGroupUpdateTime = -1;
    private static final int RELAY_SOUND_TTL = 40; 
    private static final int RELAY_SOUND_RATE_LIMIT = 20; 
    private static final double STICKY_RADIUS_MARGIN = 2.0; 
    private static long lastCleanupTime = -1;
    private static final Object cleanupLock = new Object();
    private static Map<MobEntity, Set<MobEntity>> lastEdgeMobEntityMap = new HashMap<>(); 

    public static class SoundRelay {
        public final double x, y, z, range, weight;
        public final long timestamp;
        public final int hash;
        public SoundRelay(double x, double y, double z, double range, double weight, long timestamp) {
            this.x = x; this.y = y; this.z = z; this.range = range; this.weight = weight; this.timestamp = timestamp;
            this.hash = Objects.hash((int)x, (int)y, (int)z, (int)range, (int)(weight*100));
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SoundRelay other)) return false;
            return this.hash == other.hash && Math.abs(this.timestamp - other.timestamp) < RELAY_SOUND_TTL;
        }
        @Override
        public int hashCode() { return hash; }
    }

    public static boolean isEdgeMobEntity(MobEntity mob) {
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging)
            com.example.soundattract.SoundAttractMod.LOGGER.info("[isEdgeMobEntity] Checking mob {} (pos: {}, {})", mob.getName().getString(), mob.getX(), mob.getZ());
        MobEntity leader = getLeader(mob);
        if (leader == mob) {
            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging)
                com.example.soundattract.SoundAttractMod.LOGGER.info("[isEdgeMobEntity] MobEntity {} is its own leader (not edge)", mob.getName().getString());
            return false;
        }
        Set<MobEntity> edgeSet = lastEdgeMobEntityMap.get(leader);
        boolean isEdge = edgeSet != null && edgeSet.contains(mob);
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging)
            com.example.soundattract.SoundAttractMod.LOGGER.info("[isEdgeMobEntity] MobEntity {} edge result: {} (from cache)", mob.getName().getString(), isEdge);
        return isEdge;
    }

    private static void cleanupStaleEntries(ServerWorld level) {
        synchronized (cleanupLock) {
            synchronized (leaders) {
                leaders.removeIf(ref -> {
                    MobEntity mob = ref.get();
                    return mob == null || mob.isRemoved();
                });
            }
            synchronized (uuidToLeader) {
                uuidToLeader.keySet().removeIf(uuid -> {
                    MobEntity mob = uuidToLeader.get(uuid);
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
                    MobEntity mob = uuidToLeader.get(uuid);
                    return mob == null || mob.isRemoved();
                });
            }
        }
    }

    public static void updateGroups(ServerWorld level) {
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging)
            com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] updateGroups called at game time {}", level.getTime());
        long time = level.getTime();
        int scanCooldown = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
        int groupAssignInterval = com.example.soundattract.DynamicScanCooldownManager.getGroupAssignmentInterval();
        if (lastGroupUpdateTime >= 0 && time - lastGroupUpdateTime < groupAssignInterval) return;
        lastGroupUpdateTime = time;

        if (scanCooldown > 0 && (lastCleanupTime == -1 || time - lastCleanupTime > 10L * scanCooldown)) {
            cleanupStaleEntries(level);
            lastCleanupTime = time;
        }

        List<String> attracted = SoundAttractMod.CONFIG.attractedEntities.stream().map(Object::toString).toList();
        int simDistChunks = level.getServer().getPlayerManager().getViewDistance();
        int simDistBlocks = simDistChunks * 16;
        Map<MobEntity, net.minecraft.util.Identifier> mobIdMap = new HashMap<>();
        net.minecraft.world.border.WorldBorder border = level.getWorldBorder();
        net.minecraft.util.math.Box worldBox = new net.minecraft.util.math.Box(
            border.getBoundWest(), 0, border.getBoundNorth(),
            border.getBoundEast(), level.getHeight(), border.getBoundSouth()
        );
        List<MobEntity> mobs = level.getEntitiesByClass(MobEntity.class, worldBox, MobEntity::isAlive);
        mobs.removeIf(m -> {
            net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(m.getType());
            mobIdMap.put(m, id);
            return !attracted.contains(id.toString());
        });
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            StringBuilder allMobEntityTypesLog = new StringBuilder();
            for (MobEntity mob : mobs) {
                net.minecraft.util.Identifier id = mobIdMap.get(mob);
                allMobEntityTypesLog.append(String.format("%s at (%.1f, %.1f, %.1f); ", id.toString(), mob.getX(), mob.getY(), mob.getZ()));
            }
            com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] All mobs present ({}): {}", mobs.size(), allMobEntityTypesLog.toString());
        }
        List<MobEntity> attractedMobEntities = new ArrayList<>();
        for (MobEntity mob : mobs) {
            net.minecraft.util.Identifier id = mobIdMap.get(mob);
            if (attracted.contains(id.toString())) {
                attractedMobEntities.add(mob);
            }
        }
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            StringBuilder mobPosLog = new StringBuilder();
            for (MobEntity mob : attractedMobEntities) {
                mobPosLog.append(String.format("%s at (%.1f, %.1f, %.1f); ", mob.getName().getString(), mob.getX(), mob.getY(), mob.getZ()));
            }
            com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] Attracted mobs ({}): {}", attractedMobEntities.size(), mobPosLog.toString());
        }
        uuidToLeader.clear();
        leaders.clear();
        if (attractedMobEntities.isEmpty()) return;
        double groupRadius = com.example.soundattract.SoundAttractMod.CONFIG.groupDistance;
        int maxGroupSize = com.example.soundattract.SoundAttractMod.CONFIG.maxGroupSize;
        double cellSize = groupRadius;
        Map<Long, List<MobEntity>> cellToMobs = new HashMap<>();
        for (MobEntity mob : attractedMobEntities) {
            long cellKey = SpatialPartitioner.getKey(new BlockPos((int)mob.getX(), 0, (int)mob.getZ()), SoundAttractMod.CONFIG.spatialPartitionSize);
            cellToMobs.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(mob);
        }
        int leaderCount = 0;
        int totalGroupSize = 0;
        Map<MobEntity, List<MobEntity>> leaderToGroup = new HashMap<>();
        for (Map.Entry<Long, List<MobEntity>> entry : cellToMobs.entrySet()) {
            List<MobEntity> group = entry.getValue();
            if (group.isEmpty()) continue;
            MobEntity leader = group.get(0);
            leaders.add(new WeakReference<>(leader));
            leaderCount++;
            List<MobEntity> groupMembers = new ArrayList<>();
            int groupSize = 0;
            for (MobEntity mob : group) {
                if (groupSize < maxGroupSize) {
                    uuidToLeader.put(mob.getUuid(), leader);
                    groupMembers.add(mob);
                    groupSize++;
                }
            }
            leaderToGroup.put(leader, groupMembers);
            totalGroupSize += groupMembers.size();
        }
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            double avgGroupSize = leaderCount > 0 ? (double) totalGroupSize / leaderCount : 0.0;
            com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] Grouped {} mobs into {} groups (avg group size: {:.2f})", attractedMobEntities.size(), leaderCount, avgGroupSize);
        }


        for (MobEntity mob : attractedMobEntities) {
            if (!uuidToLeader.containsKey(mob.getUuid())) {
                deserterUuids.add(mob.getUuid());
                if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] MobEntity {} marked as DESERTER (not in any group)", mob.getName().getString());
                }
            } else {
                deserterUuids.remove(mob.getUuid());
            }
        }
        lastEdgeMobEntityMap.clear();
        for (MobEntity leader : leaderToGroup.keySet()) {
            List<MobEntity> group = leaderToGroup.get(leader);
            if (group == null) continue;
            int sectors = com.example.soundattract.SoundAttractMod.CONFIG.numEdgeSectors;
            int edgePerSector = 4;
            Map<Integer, List<MobEntity>> sectorToFarthestList = new HashMap<>();
            double leaderX = leader.getX(), leaderZ = leader.getZ();
            for (MobEntity m : group) {
                if (m == leader) continue;
                double dx2 = m.getX() - leaderX, dz2 = m.getZ() - leaderZ;
                double angle = Math.atan2(dz2, dx2);
                int sector = (int) Math.floor(((angle + Math.PI) / (2 * Math.PI)) * sectors) % sectors;
                sectorToFarthestList.computeIfAbsent(sector, k -> new ArrayList<>()).add(m);
            }
            Set<MobEntity> edgeMobEntities = new HashSet<>();
            for (Map.Entry<Integer, List<MobEntity>> entry : sectorToFarthestList.entrySet()) {
                int sector = entry.getKey();
                List<MobEntity> mobsInSector = entry.getValue();
                mobsInSector.sort((a, b) -> Double.compare(b.distanceTo(leader), a.distanceTo(leader)));
                int edgeCount = Math.min(edgePerSector, mobsInSector.size());
                if (edgeCount == 0 && !mobsInSector.isEmpty()) edgeCount = 1;
                for (int i = 0; i < edgeCount; i++) {
                    edgeMobEntities.add(mobsInSector.get(i));
                }
            }
            if (edgeMobEntities.isEmpty() && group.size() > 1) {
                MobEntity farthest = null;
                double maxDist = -1;
                for (MobEntity m : group) {
                    if (m == leader) continue;
                    double dist = m.distanceTo(leader);
                    if (dist > maxDist) {
                        maxDist = dist;
                        farthest = m;
                    }
                }
                if (farthest != null) edgeMobEntities.add(farthest);
            }
            lastEdgeMobEntityMap.put(leader, edgeMobEntities);
            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                StringBuilder sb = new StringBuilder();
                for (MobEntity edge : edgeMobEntities) sb.append(edge.getName().getString()).append(", ");
                com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] Edge mobs for leader {}: {}", leader.getName().getString(), sb.toString());
            }
        }
        mobToRelayedSounds.entrySet().removeIf(e -> e.getKey().isRemoved());
        for (List<SoundRelay> relays : mobToRelayedSounds.values()) {
            relays.removeIf(r -> time - r.timestamp > RELAY_SOUND_TTL);
        }
        mobLastRelayTime.entrySet().removeIf(e -> e.getKey().isRemoved());
        List<MobEntity> allAttractedMobEntities = new ArrayList<>();
        for (MobEntity mob : level.getEntitiesByClass(MobEntity.class, worldBox, m -> true)) {
            String mobId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(mob.getType()).toString();
            if (com.example.soundattract.SoundAttractMod.CONFIG.attractedEntities.contains(mobId)) {
                allAttractedMobEntities.add(mob);
            }
        }
        synchronized (deserterUuids) {
            for (MobEntity mob : allAttractedMobEntities) {
                MobEntity leader = uuidToLeader.get(mob.getUuid());
                boolean isGrouped = leader != null && leader != mob;
                if (!isGrouped) {
                    deserterUuids.add(mob.getUuid());
                    if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                        com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] MobEntity {} marked as DESERTER", mob.getName().getString());
                    }
                } else {
                    deserterUuids.remove(mob.getUuid());
                }
            }
        }
    }

    private static long offsetCellKey(long baseKey, int dx, int dz, double cellSize) {
        long cellX = baseKey >> 32;
        long cellZ = baseKey & 0xFFFFFFFFL;
        return ((cellX + dx) << 32) | ((cellZ + dz) & 0xFFFFFFFFL);
    }

    public static void relaySoundToLeader(MobEntity mob, double x, double y, double z, double range, double weight, long timestamp) {
        MobEntity leader = getLeader(mob);
        if (leader == mob) return;
        Long lastRelay = mobLastRelayTime.get(mob);
        if (lastRelay != null && timestamp - lastRelay < RELAY_SOUND_RATE_LIMIT) return;
        mobLastRelayTime.put(mob, timestamp);
        SoundRelay relay = new SoundRelay(x, y, z, range, weight, timestamp);
        List<SoundRelay> relays = mobToRelayedSounds.computeIfAbsent(leader, k -> new ArrayList<>());
        if (!relays.contains(relay)) {
            relays.add(relay);
        }
    }

    public static List<SoundRelay> consumeRelayedSounds(MobEntity leader) {
        List<SoundRelay> relays = mobToRelayedSounds.remove(leader);
        if (relays == null) return Collections.emptyList();
        long now = leader.getWorld().getTime();
        Set<SoundRelay> deduped = new HashSet<>();
        for (SoundRelay r : relays) {
            if (now - r.timestamp <= RELAY_SOUND_TTL) {
                deduped.add(r);
            }
        }
        return new ArrayList<>(deduped);
    }

    public static MobEntity getLeader(MobEntity mob) {
        return uuidToLeader.getOrDefault(mob.getUuid(), mob);
    }

    public static void promoteToDeserter(MobEntity mob) {
        deserterUuids.add(mob.getUuid());
    }

    public static boolean isDeserter(MobEntity mob) {
        return deserterUuids.contains(mob.getUuid());
    }
}
