package com.example.soundattract.ai;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Box;
import net.minecraft.server.world.ServerWorld;

import java.util.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.example.soundattract.SoundAttractMod;
import java.lang.ref.WeakReference;
import net.minecraft.registry.Registries;

import net.minecraft.world.World;
import com.example.soundattract.util.ThreadingChecks;

public class MobGroupManager {
    private static List<Long> cellsToProcess = new ArrayList<>();
    private static int nextCellIndex = 0;
    public static int CELLS_PER_TICK = 20;
    public static int MOBS_PER_CELL_PER_TICK = 10;
    public static int MIN_CELLS_PER_TICK = 5;
    public static int MAX_CELLS_PER_TICK = 40;
    public static int MIN_MOBS_PER_CELL_PER_TICK = 3;
    public static int MAX_MOBS_PER_CELL_PER_TICK = 30;

    private static final Map<Long, java.util.Deque<MobEntity>> cellEdgeMobs = new java.util.HashMap<>();
    private static final double EDGE_MARGIN = 2.0;
    private static final int EDGE_MOBS_PER_TICK = 4;
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


    static final class MemberInfo {
        final UUID uuid; final double x; final double z;
        MemberInfo(UUID uuid, double x, double z) { this.uuid = uuid; this.x = x; this.z = z; }
    }
    static final class LeaderGroupSnapshot {
        final UUID leaderUuid; final double leaderX; final double leaderZ; final java.util.List<MemberInfo> members;
        LeaderGroupSnapshot(UUID leaderUuid, double leaderX, double leaderZ, java.util.List<MemberInfo> members) {
            this.leaderUuid = leaderUuid; this.leaderX = leaderX; this.leaderZ = leaderZ; this.members = members;
        }
    }
    static final class GroupEdgeSnapshot {
        final java.util.List<LeaderGroupSnapshot> groups;
        GroupEdgeSnapshot(java.util.List<LeaderGroupSnapshot> groups) { this.groups = groups; }
    }
    static final class GroupEdgeComputeOutput {
        final java.util.Map<UUID, java.util.Set<UUID>> leaderToEdgeUuids;
        GroupEdgeComputeOutput(java.util.Map<UUID, java.util.Set<UUID>> leaderToEdgeUuids) { this.leaderToEdgeUuids = leaderToEdgeUuids; }
        @Override public String toString() { return "GroupEdgeComputeOutput{" + leaderToEdgeUuids.size() + " groups}"; }
    }

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
    private static boolean isAttractedType(MobEntity mob) {
        String id = Registries.ENTITY_TYPE.getId(mob.getType()).toString();
        return SoundAttractMod.CONFIG.attractedEntities.contains(id);
    }

    private static boolean isEligibleLeader(MobEntity mob) {
        if (mob == null) return false;
        if (SoundAttractMod.CONFIG == null) return false;

        if (isAttractedType(mob)) return true;
        try {
            return SoundAttractMod.CONFIG.getMatchingProfile(mob) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
    public static boolean isEdgeMobEntity(MobEntity mob) {
        if (mob != null) com.example.soundattract.util.ThreadingChecks.warnIfOffServerThread(mob.getWorld(), "MobGroupManager.isEdgeMobEntity");
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
        ThreadingChecks.warnIfOffServerThread(level, "MobGroupManager.updateGroups");
        double tps = 20.0;
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.serverTpsSupplier != null) {
            tps = com.example.soundattract.SoundAttractMod.CONFIG.serverTpsSupplier.get();
        } else if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.lastKnownTps > 0) {
            tps = com.example.soundattract.SoundAttractMod.CONFIG.lastKnownTps;
        }
        if (com.example.soundattract.SoundAttractMod.CONFIG != null) {
            MIN_CELLS_PER_TICK = com.example.soundattract.SoundAttractMod.CONFIG.minCellsPerTick;
            MAX_CELLS_PER_TICK = com.example.soundattract.SoundAttractMod.CONFIG.maxCellsPerTick;
            MIN_MOBS_PER_CELL_PER_TICK = com.example.soundattract.SoundAttractMod.CONFIG.minMobsPerCellPerTick;
            MAX_MOBS_PER_CELL_PER_TICK = com.example.soundattract.SoundAttractMod.CONFIG.maxMobsPerCellPerTick;
        }
        double clampedTps = Math.max(10.0, Math.min(20.0, tps));
        double tpsFrac = (clampedTps - 10.0) / 10.0; 
        CELLS_PER_TICK = (int)Math.round(MIN_CELLS_PER_TICK + (MAX_CELLS_PER_TICK - MIN_CELLS_PER_TICK) * tpsFrac);
        MOBS_PER_CELL_PER_TICK = (int)Math.round(MIN_MOBS_PER_CELL_PER_TICK + (MAX_MOBS_PER_CELL_PER_TICK - MIN_MOBS_PER_CELL_PER_TICK) * tpsFrac);
        if (MIN_CELLS_PER_TICK == MAX_CELLS_PER_TICK) CELLS_PER_TICK = MIN_CELLS_PER_TICK;
        if (MIN_MOBS_PER_CELL_PER_TICK == MAX_MOBS_PER_CELL_PER_TICK) MOBS_PER_CELL_PER_TICK = MIN_MOBS_PER_CELL_PER_TICK;
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] TPS: {} | CELLS_PER_TICK: {} | MOBS_PER_CELL_PER_TICK: {}", tps, CELLS_PER_TICK, MOBS_PER_CELL_PER_TICK);
        }

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
        Map<MobEntity, net.minecraft.util.Identifier> mobIdMap = new HashMap<>();

        if (cellsToProcess.isEmpty()) {
            cellsToProcess.addAll(com.example.soundattract.ai.SpatialPartitionModule.cellToMobUuids.keySet());
            nextCellIndex = 0;
        }
        int cellsProcessed = 0;
        List<MobEntity> mobs = new ArrayList<>();
        for (; nextCellIndex < cellsToProcess.size() && cellsProcessed < CELLS_PER_TICK; nextCellIndex++, cellsProcessed++) {
            Long cellKey = cellsToProcess.get(nextCellIndex);
            LongOpenHashSet uuidSet = com.example.soundattract.ai.SpatialPartitionModule.cellToMobUuids.get(cellKey);
            if (uuidSet == null) continue;
            int mobsProcessed = 0;
            for (long uuidLsb : uuidSet) {
                if (mobsProcessed >= MOBS_PER_CELL_PER_TICK) break;
                for (UUID uuid : com.example.soundattract.ai.SpatialPartitionModule.uuidToMobCache.keySet()) {
                    if (uuid.getLeastSignificantBits() == uuidLsb) {
                        MobEntity mob = com.example.soundattract.ai.SpatialPartitionModule.getMobFromCache(uuid);
                        if (mob != null && mob.isAlive()) {
                            if (!isAttractedType(mob)) {
                                continue;
                            }
                            net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ENTITY_TYPE.getId(mob.getType());
                            mobIdMap.put(mob, id);
                            if (attracted.contains(id.toString())) {
                                mobs.add(mob);
                            }
                        }
                        mobsProcessed++;
                        break;
                    }
                }
            }
        }
        if (nextCellIndex >= cellsToProcess.size()) {
            cellsToProcess.clear();
            nextCellIndex = 0;
        }
        if (cellsProcessed == 0) return;

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
        for (MobEntity m : attractedMobEntities) {
            if (SoundAttractMod.CONFIG.getMatchingProfile(m) != null) {
                leaders.add(new WeakReference<>(m));
                uuidToLeader.put(m.getUuid(), m);
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[MobGroupManager] Profiled mob {} forced to leader",
                                            Registries.ENTITY_TYPE.getId(m.getType()));
                }
            }
        }
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
        List<MobEntity> assignedLeaders = new ArrayList<>();
        for (Map.Entry<Long, List<MobEntity>> entry : cellToMobs.entrySet()) {
            List<MobEntity> group = entry.getValue();
            if (group.isEmpty()) continue;
            for (MobEntity mob : group) {
                MobEntity currentLeader = uuidToLeader.get(mob.getUuid());
                MobEntity nearestLeader = currentLeader != null && currentLeader.isAlive() ? currentLeader : null;
                double nearestDistSq = Double.MAX_VALUE;
                if (nearestLeader == null) {
                    for (MobEntity leader : assignedLeaders) {
                        double distSq = mob.squaredDistanceTo(leader);
                        if (distSq <= groupRadius * groupRadius && distSq < nearestDistSq) {
                            nearestLeader = leader;
                            nearestDistSq = distSq;
                        }
                    }
                    if (nearestLeader == null) {
                        assignedLeaders.add(mob);
                        leaders.add(new WeakReference<>(mob));
                        leaderCount++;
                        nearestLeader = mob;
                    }
                }
                uuidToLeader.put(mob.getUuid(), nearestLeader);
                leaderToGroup.computeIfAbsent(nearestLeader, k -> new ArrayList<>()).add(mob);
                totalGroupSize++;
            }
        }
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            double avgGroupSize = leaderCount > 0 ? (double) totalGroupSize / leaderCount : 0.0;
            com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] Grouped {} mobs into {} groups (avg group size: {:.2f})", attractedMobEntities.size(), leaderCount, avgGroupSize);
        }



        submitEdgeSelectionAsync(leaderToGroup);


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
            for (Map.Entry<Integer, List<MobEntity>> entry2 : sectorToFarthestList.entrySet()) {
                List<MobEntity> mobsInSector = entry2.getValue();
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
                    if (dist > maxDist) { maxDist = dist; farthest = m; }
                }
                if (farthest != null) edgeMobEntities.add(farthest);
            }
            lastEdgeMobEntityMap.put(leader, edgeMobEntities);
            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                StringBuilder sb = new StringBuilder();
                for (MobEntity edge : edgeMobEntities) sb.append(edge.getName().getString()).append(", ");
                com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] Edge mobs for leader {} (sync): {}", leader.getName().getString(), sb.toString());
            }
        }
        mobToRelayedSounds.entrySet().removeIf(e -> e.getKey().isRemoved());
        for (List<SoundRelay> relays : mobToRelayedSounds.values()) {
            relays.removeIf(r -> time - r.timestamp > RELAY_SOUND_TTL);
        }
        mobLastRelayTime.entrySet().removeIf(e -> e.getKey().isRemoved());
        List<MobEntity> allAttractedMobEntities = new ArrayList<>();
        Box worldBox = new Box(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        for (MobEntity mob : level.getEntitiesByClass(MobEntity.class, worldBox, m -> true)) {
            if (!isAttractedType(mob)) {
                continue;
            }
                allAttractedMobEntities.add(mob);
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
        if (mob != null) ThreadingChecks.warnIfOffServerThread(mob.getWorld(), "MobGroupManager.relaySoundToLeader");
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
        if (leader != null) ThreadingChecks.warnIfOffServerThread(leader.getWorld(), "MobGroupManager.consumeRelayedSounds");
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

    public static void updateCellGroup(long cellKey, ServerWorld level) {
        ThreadingChecks.warnIfOffServerThread(level, "MobGroupManager.updateCellGroup");
        List<String> attracted = com.example.soundattract.SoundAttractMod.CONFIG.attractedEntities.stream().map(Object::toString).toList();
        List<MobEntity> mobsInCell = new ArrayList<>();
        LongOpenHashSet uuidSet = com.example.soundattract.ai.SpatialPartitionModule.cellToMobUuids.get(cellKey);
        if (uuidSet == null || uuidSet.isEmpty()) return;
        for (UUID uuid : com.example.soundattract.ai.SpatialPartitionModule.uuidToMobCache.keySet()) {
            if (uuidSet.contains(uuid.getLeastSignificantBits())) {
                MobEntity mob = com.example.soundattract.ai.SpatialPartitionModule.getMobFromCache(uuid);
                if (mob != null && mob.isAlive()) {
                    if (!isAttractedType(mob)) {
                        continue;
                    }
                    mobsInCell.add(mob);
                }
            }
        }
        if (mobsInCell.isEmpty()) return;
        MobEntity leader = null;
        java.util.Deque<MobEntity> edgeBuffer = cellEdgeMobs.computeIfAbsent(cellKey, k -> new java.util.LinkedList<>());
        edgeBuffer.clear();
        double cellSize = com.example.soundattract.ai.SpatialPartitionModule.cellSize;
        long cellX = cellKey >> 32;
        long cellZ = cellKey & 0xFFFFFFFFL;
        double minX = cellX * cellSize;
        double minZ = cellZ * cellSize;
        double maxX = minX + cellSize;
        double maxZ = minZ + cellSize;

        for (MobEntity candidate : mobsInCell) {
            if (isEligibleLeader(candidate)) {
                leader = candidate;
                break;
            }
        }
        if (leader == null) {
            return;
        }

        for (int i = 0; i < mobsInCell.size(); i++) {
            MobEntity mob = mobsInCell.get(i);
            if (mob == leader) {
                uuidToLeader.put(mob.getUuid(), mob);
                leaders.add(new WeakReference<>(mob));
            } else {
                uuidToLeader.put(mob.getUuid(), leader);
            }
            double x = mob.getX();
            double z = mob.getZ();
            if (x - minX < EDGE_MARGIN || maxX - x < EDGE_MARGIN || z - minZ < EDGE_MARGIN || maxZ - z < EDGE_MARGIN) {
                edgeBuffer.add(mob);
            }
        }
        for (int i = 0; i < EDGE_MOBS_PER_TICK && !edgeBuffer.isEmpty(); i++) {
            MobEntity edgeMob = edgeBuffer.poll();
            if (edgeMob == null || edgeMob.isRemoved() || !edgeMob.isAlive()) continue;
            MobEntity bestLeader = leader;
            double bestDist = edgeMob.squaredDistanceTo(leader);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    long neighborCell = ((cellX + dx) << 32) | ((cellZ + dz) & 0xFFFFFFFFL);
                    java.util.List<MobEntity> neighborMobs = new java.util.ArrayList<>();
                    LongOpenHashSet neighborUuids = com.example.soundattract.ai.SpatialPartitionModule.cellToMobUuids.get(neighborCell);
                    if (neighborUuids != null) {
                        for (UUID uuid : com.example.soundattract.ai.SpatialPartitionModule.uuidToMobCache.keySet()) {
                            if (neighborUuids.contains(uuid.getLeastSignificantBits())) {
                                MobEntity nm = com.example.soundattract.ai.SpatialPartitionModule.getMobFromCache(uuid);
                                if (nm != null && nm.isAlive()) neighborMobs.add(nm);
                            }
                        }
                    }
                    for (MobEntity nm : neighborMobs) {
                        MobEntity nLeader = uuidToLeader.getOrDefault(nm.getUuid(), nm);
                        if (!isEligibleLeader(nLeader)) continue;
                        double d = edgeMob.squaredDistanceTo(nLeader);
                        if (d < bestDist) {
                            bestLeader = nLeader;
                            bestDist = d;
                        }
                    }
                }
            }
            if (bestLeader != leader) {
                uuidToLeader.put(edgeMob.getUuid(), bestLeader);
                if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[EdgeMobOptimizer] Mob {} reassigned to closer leader {} (dist={})", edgeMob.getName().getString(), bestLeader.getName().getString(), Math.sqrt(bestDist));
                }
            }
            if (edgeMob.isAlive()) edgeBuffer.add(edgeMob);
        }
        if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging && leader != null) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[MobGroupManager] updateCellGroup: cellKey={} leader={} groupSize={}", cellKey, leader.getName().getString(), mobsInCell.size());
        }
    }

    public static void scheduleCellsForSound(BlockPos soundPos, double range, ServerWorld level, long currentTick) {
        ThreadingChecks.warnIfOffServerThread(level, "MobGroupManager.scheduleCellsForSound");
        int partitionSize = com.example.soundattract.SoundAttractMod.CONFIG.spatialPartitionSize;
        int gridRadius = (int)Math.ceil(range / partitionSize);
        int baseX = soundPos.getX() / partitionSize;
        int baseZ = soundPos.getZ() / partitionSize;
        for (int layer = 0; layer <= gridRadius; layer++) {
            int tickDelay = layer;
            for (int dx = -layer; dx <= layer; dx++) {
                for (int dz = -layer; dz <= layer; dz++) {
                    if (Math.abs(dx) != layer && Math.abs(dz) != layer) continue;
                    int cellX = baseX + dx;
                    int cellZ = baseZ + dz;
                    long cellKey = (((long)cellX) << 32) | (cellZ & 0xFFFFFFFFL);
                    long scheduledTick = currentTick + tickDelay;
                    com.example.soundattract.DynamicScanCooldownManager.scheduler.scheduleCell(cellKey, 0, scheduledTick);
                }
            }
        }
    }

    public static void scheduleNearbyCellsForPlayers(ServerWorld level, long currentTick) {
        ThreadingChecks.warnIfOffServerThread(level, "MobGroupManager.scheduleNearbyCellsForPlayers");
        int[] tierRadii = new int[] {3, 5, 8, 12};
        for (net.minecraft.server.network.ServerPlayerEntity player : level.getPlayers()) {
            net.minecraft.util.math.BlockPos pos = player.getBlockPos();
            for (int tier = 0; tier < tierRadii.length; tier++) {
                int radius = tierRadii[tier];
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        long cellKey = com.example.soundattract.ai.SpatialPartitionModule.cellKey(
                            pos.add(dx * com.example.soundattract.ai.SpatialPartitionModule.cellSize, 0, dz * com.example.soundattract.ai.SpatialPartitionModule.cellSize)
                        );
                        com.example.soundattract.DynamicScanCooldownManager.scheduler.scheduleCell(cellKey, tier, currentTick);
                    }
                }
            }
        }
    }


    public static void applyGroupResult(com.example.soundattract.util.WorkerScheduler.GroupComputeResult result) {
        if (result == null || result.data == null) return;

        net.minecraft.world.World guardWorld = null;
        if (result.data instanceof GroupEdgeComputeOutput edgeOut) {
            for (java.util.UUID leaderId : edgeOut.leaderToEdgeUuids.keySet()) {
                MobEntity leaderRef = com.example.soundattract.ai.SpatialPartitionModule.getMobFromCache(leaderId);
                if (leaderRef != null) { guardWorld = leaderRef.getWorld(); break; }
            }
        }
        if (guardWorld != null) {
            com.example.soundattract.util.ThreadingChecks.warnIfOffServerThread(guardWorld, "MobGroupManager.applyGroupResult");
        }
        if (result.data instanceof GroupEdgeComputeOutput out) {
            Map<MobEntity, Set<MobEntity>> newMap = new HashMap<>();
            for (Map.Entry<UUID, java.util.Set<UUID>> e : out.leaderToEdgeUuids.entrySet()) {
                MobEntity leader = com.example.soundattract.ai.SpatialPartitionModule.getMobFromCache(e.getKey());
                if (leader == null || leader.isRemoved()) continue;
                Set<MobEntity> edges = new HashSet<>();
                for (UUID edgeUuid : e.getValue()) {
                    MobEntity mob = com.example.soundattract.ai.SpatialPartitionModule.getMobFromCache(edgeUuid);
                    if (mob != null && mob.isAlive()) edges.add(mob);
                }
                if (!edges.isEmpty()) newMap.put(leader, edges);
            }
            if (!newMap.isEmpty()) {
                lastEdgeMobEntityMap.clear();
                lastEdgeMobEntityMap.putAll(newMap);
                if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    com.example.soundattract.SoundAttractMod.LOGGER.debug("[MobGroupManager] Applied async edge selection for {} groups.", newMap.size());
                }
            }
        } else {
            if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                com.example.soundattract.SoundAttractMod.LOGGER.debug("[MobGroupManager] Ignoring unknown group result type: {}", result.data.getClass().getName());
            }
        }
    }


    private static void submitEdgeSelectionAsync(Map<MobEntity, List<MobEntity>> leaderToGroup) {
        if (leaderToGroup == null || leaderToGroup.isEmpty()) return;
        java.util.List<LeaderGroupSnapshot> groupSnaps = new ArrayList<>();
        for (Map.Entry<MobEntity, List<MobEntity>> e : leaderToGroup.entrySet()) {
            MobEntity leader = e.getKey();
            if (leader == null || leader.isRemoved()) continue;
            java.util.List<MemberInfo> members = new ArrayList<>();
            for (MobEntity m : e.getValue()) {
                if (m == null || m.isRemoved()) continue;
                members.add(new MemberInfo(m.getUuid(), m.getX(), m.getZ()));
            }
            groupSnaps.add(new LeaderGroupSnapshot(leader.getUuid(), leader.getX(), leader.getZ(), members));
        }
        GroupEdgeSnapshot snapshot = new GroupEdgeSnapshot(groupSnaps);

        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            com.example.soundattract.SoundAttractMod.LOGGER.debug("[MobGroupManager] Submitting async group edge compute for {} groups", groupSnaps.size());
        }
        com.example.soundattract.util.WorkerScheduler.submitGroupTask(() -> {
            int sectors = com.example.soundattract.SoundAttractMod.CONFIG != null ? com.example.soundattract.SoundAttractMod.CONFIG.numEdgeSectors : 8;
            int edgePerSector = 4;
            java.util.Map<UUID, java.util.Set<UUID>> out = new java.util.HashMap<>();
            for (LeaderGroupSnapshot g : snapshot.groups) {
                if (g == null || g.members == null || g.members.isEmpty()) continue;
                java.util.Map<Integer, java.util.List<MemberInfo>> sectorMap = new java.util.HashMap<>();
                for (MemberInfo mi : g.members) {
                    if (mi.uuid.equals(g.leaderUuid)) continue;
                    double dx = mi.x - g.leaderX;
                    double dz = mi.z - g.leaderZ;
                    double angle = Math.atan2(dz, dx);
                    int sector = (int) Math.floor(((angle + Math.PI) / (2 * Math.PI)) * sectors) % sectors;
                    sectorMap.computeIfAbsent(sector, k -> new java.util.ArrayList<>()).add(mi);
                }
                java.util.Set<UUID> edges = new java.util.HashSet<>();
                for (java.util.List<MemberInfo> list : sectorMap.values()) {
                    list.sort((a, b) -> {
                        double da = (a.x - g.leaderX) * (a.x - g.leaderX) + (a.z - g.leaderZ) * (a.z - g.leaderZ);
                        double db = (b.x - g.leaderX) * (b.x - g.leaderX) + (b.z - g.leaderZ) * (b.z - g.leaderZ);
                        return Double.compare(db, da);
                    });
                    int edgeCount = Math.min(edgePerSector, list.size());
                    if (edgeCount == 0 && !list.isEmpty()) edgeCount = 1;
                    for (int i = 0; i < edgeCount; i++) edges.add(list.get(i).uuid);
                }
                if (edges.isEmpty() && g.members.size() > 1) {

                    MemberInfo farthest = null; double max = -1;
                    for (MemberInfo mi : g.members) {
                        if (mi.uuid.equals(g.leaderUuid)) continue;
                        double d = (mi.x - g.leaderX) * (mi.x - g.leaderX) + (mi.z - g.leaderZ) * (mi.z - g.leaderZ);
                        if (d > max) { max = d; farthest = mi; }
                    }
                    if (farthest != null) edges.add(farthest.uuid);
                }
                if (!edges.isEmpty()) out.put(g.leaderUuid, edges);
            }
            return new com.example.soundattract.util.WorkerScheduler.GroupComputeResult(new GroupEdgeComputeOutput(out));
        });
    }
}
