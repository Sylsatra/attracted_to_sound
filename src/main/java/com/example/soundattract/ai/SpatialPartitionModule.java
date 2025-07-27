package com.example.soundattract.ai;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class SpatialPartitionModule {
    public static final int DEFAULT_CELL_SIZE = 64;
    public static int cellSize = DEFAULT_CELL_SIZE;
    public static int cellShift = 6;

    public static final Long2ObjectOpenHashMap<LongOpenHashSet> cellToMobUuids = new Long2ObjectOpenHashMap<>();
    public static final Object2LongOpenHashMap<UUID> mobUuidToCellKey = new Object2LongOpenHashMap<>();

    public static final it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<UUID, WeakReference<MobEntity>> uuidToMobCache = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>();

    public static void addMobToCache(UUID uuid, MobEntity mob) {
        uuidToMobCache.put(uuid, new WeakReference<>(mob));
    }
    public static void removeMobFromCache(UUID uuid) {
        uuidToMobCache.remove(uuid);
    }
    public static MobEntity getMobFromCache(UUID uuid) {
        WeakReference<MobEntity> ref = uuidToMobCache.get(uuid);
        return ref != null ? ref.get() : null;
    }

    private static final int DEAD_RING_SIZE = 2048;
    private static final UUID[] deadUuidRing = new UUID[DEAD_RING_SIZE];
    private static int deadRingHead = 0;
    private static int deadRingTail = 0;

    public static long cellKey(BlockPos pos) {
        long cx = pos.getX() >> cellShift;
        long cz = pos.getZ() >> cellShift;
        return (cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    public static void addMobToCell(UUID uuid, BlockPos pos) {
        long key = cellKey(pos);
        cellToMobUuids.computeIfAbsent(key, k -> new LongOpenHashSet()).add(uuid.getLeastSignificantBits());
        mobUuidToCellKey.put(uuid, key);
    }

    public static void removeMobFromCell(UUID uuid) {
        long key = mobUuidToCellKey.removeLong(uuid);
        if (key == 0L) return;
        LongOpenHashSet set = cellToMobUuids.get(key);
        if (set != null) {
            set.remove(uuid.getLeastSignificantBits());
            if (set.isEmpty()) {
                cellToMobUuids.remove(key);
            }
        }
    }

    public static void enqueueDeadUuid(UUID uuid) {
        deadUuidRing[deadRingHead] = uuid;
        deadRingHead = (deadRingHead + 1) % DEAD_RING_SIZE;
        if (deadRingHead == deadRingTail) {
            deadRingTail = (deadRingTail + 1) % DEAD_RING_SIZE;
        }
    }

    public static void cleanupDeadUuids(int n) {
        for (int i = 0; i < n && deadRingTail != deadRingHead; i++) {
            UUID uuid = deadUuidRing[deadRingTail];
            deadRingTail = (deadRingTail + 1) % DEAD_RING_SIZE;
            removeMobFromCell(uuid);
        }
    }

    public static void setCellSize(int size) {
        if (Integer.bitCount(size) != 1) throw new IllegalArgumentException("Cell size must be a power of two");
        cellSize = size;
        cellShift = Integer.numberOfTrailingZeros(size);
    }
}
