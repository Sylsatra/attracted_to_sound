package com.example.soundattract;

import com.example.soundattract.config.MobProfile;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.SoundOverride;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SoundTracker {

    public static class SoundRecord {
        public final SoundEvent sound;
        public final String soundId;
        public final BlockPos pos;
        public int ticksRemaining;
        public final String dimensionKey;
        public final double range;
        public final double weight;

        public SoundRecord(SoundEvent sound, String soundId, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this.sound = sound;
            this.soundId = soundId;
            this.pos = pos;
            this.ticksRemaining = lifetime;
            this.dimensionKey = dimensionKey;
            this.range = range;
            this.weight = weight;
        }
    }

    public static class VirtualSoundRecord extends SoundRecord {
        public final UUID sourcePlayer;
        public final String animationClass;

        public VirtualSoundRecord(BlockPos pos, int lifetime, String dimensionKey, double range, double weight, UUID sourcePlayer, String animationClass) {
            super(null, SoundMessage.VOICE_CHAT_SOUND_ID.toString(), pos, lifetime, dimensionKey, range, weight);
            this.sourcePlayer = sourcePlayer;
            this.animationClass = animationClass;
        }
    }

    private static final List<SoundRecord> RECENT_SOUNDS = new ArrayList<>();
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static final Lock readLock = lock.readLock();
    private static final Lock writeLock = lock.writeLock();

    private record GridKey3D(int x, int y, int z) {}
    private static final Map<String, Map<GridKey3D, List<SoundRecord>>> SPATIAL_SOUNDS = new HashMap<>();
    private static final List<SoundRecord> LARGE_RANGE_SOUNDS = new ArrayList<>();


    private static GridKey3D gridKey(BlockPos pos) {
        int x = pos.getX() >> 4; 
        int y = pos.getY() >> 4;
        int z = pos.getZ() >> 4;
        return new GridKey3D(x, y, z);
    }

    private static void addRecordToSpatialCollections(SoundRecord r) {
        double threshold = SoundAttractConfig.COMMON.largeSoundRangeThreshold.get();
        if (r.range > threshold) {
            LARGE_RANGE_SOUNDS.add(r);
        } else {
            SPATIAL_SOUNDS.computeIfAbsent(r.dimensionKey, d -> new HashMap<>())
                          .computeIfAbsent(gridKey(r.pos), k -> new ArrayList<>())
                          .add(r);
        }
    }

    private static void removeRecordFromSpatialCollections(SoundRecord r) {
        double threshold = SoundAttractConfig.COMMON.largeSoundRangeThreshold.get();
        if (r.range > threshold) {
            LARGE_RANGE_SOUNDS.remove(r);
        } else {
            Map<GridKey3D, List<SoundRecord>> dimMap = SPATIAL_SOUNDS.get(r.dimensionKey);
            if (dimMap != null) {
                GridKey3D key = gridKey(r.pos);
                List<SoundRecord> list = dimMap.get(key);
                if (list != null) {
                    list.remove(r);
                    if (list.isEmpty()) {
                        dimMap.remove(key);
                    }
                }
                if (dimMap.isEmpty()) {
                    SPATIAL_SOUNDS.remove(r.dimensionKey);
                }
            }
        }
    }

    public static void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime, String explicitSoundId) {
        String soundIdToUse = (explicitSoundId != null) ? explicitSoundId : (se != null && se.getLocation() != null ? se.getLocation().toString() : "unknown");

        ResourceLocation loc = ResourceLocation.tryParse(soundIdToUse);
        if (loc != null && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("Sound {} not in whitelist, ignoring.", soundIdToUse);
            }
            return;
        }

        writeLock.lock();
        try {
            for (Iterator<SoundRecord> it = RECENT_SOUNDS.iterator(); it.hasNext(); ) {
                SoundRecord existing = it.next();
                if (existing.dimensionKey.equals(dimensionKey) && existing.pos.equals(pos) && Objects.equals(existing.soundId, soundIdToUse)) {
                    if (existing.weight >= weight) {
                        return; 
                    }
                    it.remove();
                    removeRecordFromSpatialCollections(existing);
                    break;
                }
            }

            int cap = SoundAttractConfig.COMMON.maxSoundsTracked.get();
            if (RECENT_SOUNDS.size() >= cap) {
                SoundRecord worstRecord = null;
                int worstRecordIndex = -1;
                double minMetric = Double.MAX_VALUE;

                for (int i = 0; i < RECENT_SOUNDS.size(); i++) {
                    SoundRecord current = RECENT_SOUNDS.get(i);
                    double currentMetric = current.weight + (current.range / 1000.0);
                    if (currentMetric < minMetric) {
                        minMetric = currentMetric;
                        worstRecord = current;
                        worstRecordIndex = i;
                    }
                }

                double newMetric = weight + (range / 1000.0);
                if (worstRecord != null && newMetric > minMetric) {
                    RECENT_SOUNDS.remove(worstRecordIndex);
                    removeRecordFromSpatialCollections(worstRecord);
                } else {
                    return; 
                }
            }
            
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[addSound] Stored {} (Range={}, Weight={})", soundIdToUse, range, weight);
            }
            SoundRecord record = new SoundRecord(se, soundIdToUse, pos, lifetime, dimensionKey, range, weight);
            RECENT_SOUNDS.add(record);
            addRecordToSpatialCollections(record);

        } finally {
            writeLock.unlock();
        }
    }

    public static void addVirtualSound(BlockPos pos, String dimensionKey, double range, double weight, int lifetime, UUID sourcePlayer, String animationClass) {
        writeLock.lock();
        try {
            RECENT_SOUNDS.removeIf(r -> {
                boolean shouldRemove = r instanceof VirtualSoundRecord && r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight < weight;
                if (shouldRemove) {
                    removeRecordFromSpatialCollections(r);
                }
                return shouldRemove;
            });

            boolean strongerOrEqualExists = RECENT_SOUNDS.stream().anyMatch(r -> r instanceof VirtualSoundRecord && r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight >= weight);

            if (!strongerOrEqualExists) {
                VirtualSoundRecord record = new VirtualSoundRecord(pos, lifetime, dimensionKey, range, weight, sourcePlayer, animationClass);
                RECENT_SOUNDS.add(record);
                addRecordToSpatialCollections(record);
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    public static void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        addSound(se, pos, dimensionKey, range, weight, lifetime, null);
    }
    public static void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        addSound(se, pos, dimensionKey, 16.0, 1.0, SoundAttractConfig.COMMON.soundLifetimeTicks.get());
    }

    private static long ticksSinceCleanup = 0;


    private static final ArrayDeque<EvalRequest> EVAL_QUEUE = new ArrayDeque<>();
    private static final int EVAL_QUEUE_MAX = 4096;
    private static final Map<UUID, CachedEval> EVAL_CACHE = new HashMap<>();

    private static double avgEvalMicros = 0.0;
    private static int lastProcessedCount = 0;
    private static int lastQueueSize = 0;

    private static final class EvalRequest {
        final UUID mobId;
        final Level level;
        final BlockPos pos;
        final Vec3 eye;
        final com.example.soundattract.config.MobProfile profile;
        EvalRequest(UUID mobId, Level level, BlockPos pos, Vec3 eye, com.example.soundattract.config.MobProfile profile) {
            this.mobId = mobId;
            this.level = level;
            this.pos = pos;
            this.eye = eye;
            this.profile = profile;
        }
    }
    private static final class CachedEval {
        final SoundRecord record;
        final long tick;
        CachedEval(SoundRecord record, long tick) {
            this.record = record;
            this.tick = tick;
        }
    }


    private static final class CandidateEval {
        final SoundRecord rec;
        final double muffledRange;
        final double muffledWeight;
        final double finalWeight;
        final double distSqr;
        CandidateEval(SoundRecord rec, double muffledRange, double muffledWeight, double finalWeight, double distSqr) {
            this.rec = rec;
            this.muffledRange = muffledRange;
            this.muffledWeight = muffledWeight;
            this.finalWeight = finalWeight;
            this.distSqr = distSqr;
        }
    }

    public static SoundRecord getCachedOrRequestNearest(Mob mob, Level level, BlockPos pos, Vec3 eyePos) {
        if (!SoundAttractConfig.COMMON.enableTaskQueue.get()) {
            return findNearestSound(mob, level, pos, eyePos);
        }
        UUID id = mob.getUUID();
        int freshness = SoundAttractConfig.COMMON.resultFreshnessTicks.get();
        CachedEval cached = EVAL_CACHE.get(id);
        if (cached != null && (currentTickCounter - cached.tick) <= freshness) {
            return cached.record;
        }
        if (EVAL_QUEUE.size() < EVAL_QUEUE_MAX) {
            com.example.soundattract.config.MobProfile profile = SoundAttractConfig.getMatchingProfile(mob);
            EVAL_QUEUE.addLast(new EvalRequest(id, level, pos.immutable(), eyePos, profile));
        }
        return cached != null ? cached.record : null;
    }

    public static void tick() {
        writeLock.lock();
        try {

            currentTickCounter++;
            Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
            while (iter.hasNext()) {
                SoundRecord r = iter.next();
                r.ticksRemaining--;
                if (r.ticksRemaining <= 0) {
                    iter.remove();
                    removeRecordFromSpatialCollections(r);
                }
            }

            ticksSinceCleanup++;
            if (SoundAttractConfig.COMMON.enableRaycastCache.get()) {
                int interval = SoundAttractConfig.COMMON.raycastCacheCleanupIntervalTicks.get();
                if (interval > 0 && (ticksSinceCleanup % interval == 0)) {
                    cleanupRaycastCache();
                }
            } else {

                RAYCAST_CACHE.clear();
            }


            if (SoundAttractConfig.COMMON.enableTaskQueue.get()) {
                int budget = Math.max(0, SoundAttractConfig.COMMON.maxSoundEvalsPerTick.get());
                int processed = 0;
                for (int i = 0; i < budget && !EVAL_QUEUE.isEmpty(); i++) {
                    EvalRequest req = EVAL_QUEUE.pollFirst();
                    if (req == null || req.level == null) continue;

                    long t0 = System.nanoTime();
                    SoundRecord rec = findNearestSoundInternal(req.level, req.pos, req.eye, req.profile);
                    long dt = System.nanoTime() - t0;
                    EVAL_CACHE.put(req.mobId, new CachedEval(rec, currentTickCounter));

                    double micros = dt / 1000.0;
                    avgEvalMicros = (avgEvalMicros == 0.0) ? micros : (avgEvalMicros * 0.9 + micros * 0.1);
                    processed++;
                }
                lastProcessedCount = processed;
                lastQueueSize = EVAL_QUEUE.size();
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    if (processed > 0 || (currentTickCounter % 40 == 0)) {
                        SoundAttractMod.LOGGER.debug("[Queue] processed={}, remaining={}, avgEvalMicros={}", processed, lastQueueSize, String.format(java.util.Locale.ROOT, "%.2f", avgEvalMicros));
                    }
                }
            } else {
                EVAL_QUEUE.clear();
                EVAL_CACHE.clear();
            }
        } finally {
            writeLock.unlock();
        }
    }


    private static SoundRecord findNearestSoundInternal(Level level, BlockPos mobPos, Vec3 mobEyePos, com.example.soundattract.config.MobProfile mobProfile) {


        readLock.lock();
        try {
            String dimensionKey = level.dimension().location().toString();
            List<SoundRecord> nearbyCandidates = getNearbySounds(dimensionKey, mobPos);
            if (nearbyCandidates.isEmpty()) return null;


            Map<String, CandidateEval> bestById = new HashMap<>();

            double noveltyBonusValue = SoundAttractConfig.COMMON.soundNoveltyBonusWeight.get();
            int noveltyTicks = SoundAttractConfig.COMMON.soundNoveltyTimeTicks.get();
            int maxLifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            for (SoundRecord r : nearbyCandidates) {
                if (r == null || r.pos == null) continue;
                ResourceLocation rl = r.soundId != null ? ResourceLocation.tryParse(r.soundId) : null;
                if (rl != null && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(rl)) {
                    continue;
                }
                double effRange = r.range;
                double effWeight = r.weight;

                if (mobProfile != null && rl != null) {
                    java.util.Optional<com.example.soundattract.config.SoundOverride> ov = mobProfile.getSoundOverride(rl);
                    if (ov.isPresent()) {
                        com.example.soundattract.config.SoundOverride so = ov.get();
                        effRange = so.getRange();
                        effWeight = so.getWeight();
                    }
                }
                double[] muffled = applyBlockMuffling(level, r.pos, mobPos, effRange, effWeight, r.soundId != null ? r.soundId : "unknown");
                double muffledRange = muffled[0];
                double muffledWeight = muffled[1];
                double distSqr = mobPos.distSqr(r.pos);
                if (distSqr > (muffledRange * muffledRange)) continue;
                double novelty = (r.ticksRemaining > (maxLifetime - noveltyTicks)) ? noveltyBonusValue : 0.0;
                double finalW = muffledWeight + novelty;
                String key = (r.soundId != null) ? r.soundId : "unknown";
                CandidateEval prev = bestById.get(key);
                if (prev == null || finalW > prev.finalWeight || (Math.abs(finalW - prev.finalWeight) < 0.001 && distSqr < prev.distSqr)) {
                    bestById.put(key, new CandidateEval(r, muffledRange, muffledWeight, finalW, distSqr));
                }
            }

            CandidateEval best = null;
            for (CandidateEval ce : bestById.values()) {
                if (best == null || ce.finalWeight > best.finalWeight || (Math.abs(ce.finalWeight - best.finalWeight) < 0.001 && ce.distSqr < best.distSqr)) {
                    best = ce;
                }
            }
            if (best != null) {
                return new SoundRecord(best.rec.sound, best.rec.soundId, best.rec.pos, best.rec.ticksRemaining, best.rec.dimensionKey, best.muffledRange, best.muffledWeight);
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }

    public static SoundRecord findNearestSound(Mob mob, Level level, BlockPos mobPos, Vec3 mobEyePos) {
        readLock.lock();
        try {
            String dimensionKey = level.dimension().location().toString();
            List<SoundRecord> nearbyCandidates = getNearbySounds(dimensionKey, mobPos);

            if (nearbyCandidates.isEmpty()) return null;

            MobProfile profile = SoundAttractConfig.getMatchingProfile(mob);

            Map<String, CandidateEval> bestById = new HashMap<>();
            
            double noveltyBonusValue = SoundAttractConfig.COMMON.soundNoveltyBonusWeight.get();
            int noveltyTicks = SoundAttractConfig.COMMON.soundNoveltyTimeTicks.get();
            int maxLifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            for (SoundRecord r : nearbyCandidates) {
                if (r == null || r.pos == null) continue;
                
                ResourceLocation rl = r.soundId != null ? ResourceLocation.tryParse(r.soundId) : null;
                if (rl != null && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(rl)) {
                    continue;
                }

                double effectiveInitialRange = r.range;
                double effectiveInitialWeight = r.weight;
                if (profile != null && rl != null) {
                    Optional<SoundOverride> ov = profile.getSoundOverride(rl);
                    if (ov.isPresent()) {
                        effectiveInitialRange = ov.get().getRange();
                        effectiveInitialWeight = ov.get().getWeight();
                    }
                }

                double[] muffled = applyBlockMuffling(level, r.pos, mobPos, effectiveInitialRange, effectiveInitialWeight, r.soundId != null ? r.soundId : "unknown");
                double muffledRange = muffled[0];
                double muffledWeight = muffled[1];
                double distSqr = mobPos.distSqr(r.pos);

                if (distSqr > (muffledRange * muffledRange)) continue;

                double noveltyBonus = 0.0;
                if (r.ticksRemaining > (maxLifetime - noveltyTicks)) {
                    noveltyBonus = noveltyBonusValue;
                }
                double finalComparisonWeight = muffledWeight + noveltyBonus;
                String key = (r.soundId != null) ? r.soundId : "unknown";
                CandidateEval prev = bestById.get(key);
                if (prev == null || finalComparisonWeight > prev.finalWeight || (Math.abs(finalComparisonWeight - prev.finalWeight) < 0.001 && distSqr < prev.distSqr)) {
                    bestById.put(key, new CandidateEval(r, muffledRange, muffledWeight, finalComparisonWeight, distSqr));
                }
            }

            CandidateEval best = null;
            for (CandidateEval ce : bestById.values()) {
                if (best == null || ce.finalWeight > best.finalWeight || (Math.abs(ce.finalWeight - best.finalWeight) < 0.001 && ce.distSqr < best.distSqr)) {
                    best = ce;
                }
            }
            if (best != null) {
                return new SoundRecord(best.rec.sound, best.rec.soundId, best.rec.pos, best.rec.ticksRemaining, best.rec.dimensionKey, best.muffledRange, best.muffledWeight);
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }

    private static List<SoundRecord> getNearbySounds(String dim, BlockPos pos) {
        List<SoundRecord> result = new ArrayList<>();
        Map<GridKey3D, List<SoundRecord>> dimMap = SPATIAL_SOUNDS.get(dim);
        if (dimMap != null) {
            GridKey3D centerKey = gridKey(pos);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        GridKey3D neighborKey = new GridKey3D(centerKey.x() + dx, centerKey.y() + dy, centerKey.z() + dz);
                        List<SoundRecord> list = dimMap.get(neighborKey);
                        if (list != null) {
                            result.addAll(list);
                        }
                    }
                }
            }
        }
        for (SoundRecord r : LARGE_RANGE_SOUNDS) {
            if (r.dimensionKey.equals(dim)) {
                result.add(r);
            }
        }
        return result;
    }
    private static class RaycastCacheKey {
        public final BlockPos mobPos;
        public final BlockPos soundPos;
        public final String soundId;
        public final String dimensionKey;
        public RaycastCacheKey(BlockPos mobPos, BlockPos soundPos, String soundId, String dimensionKey) {
            this.mobPos = mobPos;
            this.soundPos = soundPos;
            this.soundId = soundId;
            this.dimensionKey = dimensionKey;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RaycastCacheKey other)) return false;
            return mobPos.equals(other.mobPos) && soundPos.equals(other.soundPos) && soundId.equals(other.soundId) && dimensionKey.equals(other.dimensionKey);
        }
        @Override
        public int hashCode() {
            return mobPos.hashCode() ^ soundPos.hashCode() ^ soundId.hashCode() ^ dimensionKey.hashCode();
        }
    }
    private static final class RaycastCacheEntry {
        final double[] result;
        long lastAccessTick;
        RaycastCacheEntry(double[] result, long lastAccessTick) {
            this.result = result;
            this.lastAccessTick = lastAccessTick;
        }
    }
    private static final Map<RaycastCacheKey, RaycastCacheEntry> RAYCAST_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static long currentTickCounter = 0;
    private static void advanceTickCounter() {
        currentTickCounter++;
    }
    private static void cleanupRaycastCache() {
        long now = currentTickCounter;
        int ttl = SoundAttractConfig.COMMON.raycastCacheTtlTicks.get();
        int maxSize = SoundAttractConfig.COMMON.raycastCacheMaxEntries.get();
        if (ttl <= 0) ttl = 1;

        if (!RAYCAST_CACHE.isEmpty()) {
            Iterator<Map.Entry<RaycastCacheKey, RaycastCacheEntry>> it = RAYCAST_CACHE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<RaycastCacheKey, RaycastCacheEntry> e = it.next();
                if (now - e.getValue().lastAccessTick > ttl) {
                    it.remove();
                }
            }
        }

        if (RAYCAST_CACHE.size() > maxSize) {
            int toRemove = RAYCAST_CACHE.size() - maxSize;
            List<Map.Entry<RaycastCacheKey, RaycastCacheEntry>> entries = new ArrayList<>(RAYCAST_CACHE.entrySet());
            entries.sort(Comparator.comparingLong(e -> e.getValue().lastAccessTick));
            for (int i = 0; i < toRemove && i < entries.size(); i++) {
                RAYCAST_CACHE.remove(entries.get(i).getKey());
            }
        }
    }

    private static final double NO_MUFFLING_RANGE = 1.0;
    private static final double NO_MUFFLING_WEIGHT = 1.0;

    public static double[] applyBlockMuffling(Level level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId) {
        if (!SoundAttractConfig.COMMON.enableBlockMuffling.get()) {
            return new double[]{origRange, origWeight};
        }

        String dimKey = level.dimension().location().toString();
        RaycastCacheKey cacheKey = new RaycastCacheKey(dst, src, soundId, dimKey);
        if (SoundAttractConfig.COMMON.enableRaycastCache.get()) {
            RaycastCacheEntry cached = RAYCAST_CACHE.get(cacheKey);
            if (cached != null) {
                cached.lastAccessTick = currentTickCounter;
                return cached.result;
            }
        }

        double currentRange = origRange;
        double currentWeight = origWeight;
        int blocksHit = 0;

        Vec3 start = Vec3.atCenterOf(src);
        Vec3 end = Vec3.atCenterOf(dst);
        BlockHitResult result = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos currentPos = result.getBlockPos();
            Vec3 currentHitVec = result.getLocation();

            int maxChecks = SoundAttractConfig.COMMON.maxMufflingBlocksToCheck.get();

            for (int i = 0; i < maxChecks && currentRange > 0.1 && currentWeight > 0.01; ++i) {
                BlockState blockState = level.getBlockState(currentPos);
                Block block = blockState.getBlock();
                double rangeMultiplier = NO_MUFFLING_RANGE;
                double weightMultiplier = NO_MUFFLING_WEIGHT;

                if (isCustomWool(blockState, block, level, currentPos)) {
                    rangeMultiplier = SoundAttractConfig.COMMON.mufflingFactorWool.get();
                    weightMultiplier = SoundAttractConfig.COMMON.mufflingFactorWool.get();
                } else if (isCustomLiquid(blockState, block, level, currentPos)) {
                    rangeMultiplier = SoundAttractConfig.COMMON.mufflingFactorLiquid.get();
                    weightMultiplier = SoundAttractConfig.COMMON.mufflingFactorLiquid.get();
                } else if (isCustomThin(blockState, block, level, currentPos)) {
                    rangeMultiplier = SoundAttractConfig.COMMON.mufflingFactorThin.get();
                    weightMultiplier = SoundAttractConfig.COMMON.mufflingFactorThin.get();
                } else if (isCustomSolid(blockState, block, level, currentPos)) {
                    rangeMultiplier = SoundAttractConfig.COMMON.mufflingFactorSolid.get();
                    weightMultiplier = SoundAttractConfig.COMMON.mufflingFactorSolid.get();
                } else if (isCustomNonSolid(blockState, block, level, currentPos)) {
                    rangeMultiplier = SoundAttractConfig.COMMON.mufflingFactorNonSolid.get();
                    weightMultiplier = SoundAttractConfig.COMMON.mufflingFactorNonSolid.get();
                }
            
                currentRange *= rangeMultiplier;
                currentWeight *= weightMultiplier;
                blocksHit++;

                Vec3 direction = end.subtract(start).normalize();
                currentHitVec = currentHitVec.add(direction.scale(0.1));
                BlockHitResult nextResult = level.clip(new ClipContext(currentHitVec, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

                if (nextResult.getType() != HitResult.Type.BLOCK || nextResult.getBlockPos().equals(currentPos)) {
                    break;
                }
                currentPos = nextResult.getBlockPos();
                currentHitVec = nextResult.getLocation();
            }
        }
        if (SoundAttractConfig.COMMON.debugLogging.get() && blocksHit > 0) {
            SoundAttractMod.LOGGER.debug("Muffling for sound {} from {} to {}: {} blocks hit. Range: {} -> {}, Weight: {} -> {}",
                soundId, src, dst, blocksHit, origRange, currentRange, origWeight, currentWeight);
        }

        double[] finalResult = new double[]{Math.max(0, currentRange), Math.max(0, currentWeight)};
        if (SoundAttractConfig.COMMON.enableRaycastCache.get()) {
            RAYCAST_CACHE.put(cacheKey, new RaycastCacheEntry(finalResult, currentTickCounter));
        }
        return finalResult;
    }

    private static boolean isBlockInConfigList(BlockState state, Block block, java.util.List<String> configList) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;
        String blockIdStr = id.toString();
        for (String configEntry : configList) {
            if (configEntry.endsWith("*")) { 
                TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, ResourceLocation.parse(configEntry.substring(0, configEntry.length() - 1)));
                if (state.is(tagKey)) return true;
            } else {
                if (blockIdStr.equals(configEntry)) return true;
            }
        }
        return false;
    }

    private static boolean safeBlockStateCheck(BlockState state, Level level, BlockPos pos, java.util.function.BiPredicate<BlockState, BlockGetter> check, boolean defaultValueOnNPE) {
        if (level == null || pos == null) { 
            SoundAttractMod.LOGGER.warn("safeBlockStateCheck called with null level or pos for block {}. Defaulting.", BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            return defaultValueOnNPE;
        }
        try {

            return check.test(state, level); 
                                        
        } catch (NullPointerException npe) {
            SoundAttractMod.LOGGER.warn("A NullPointerException occurred during a block state check for block {} at {}. This might be a mod incompatibility. Defaulting.", BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos, npe);
            return defaultValueOnNPE;
        } catch (Exception e) { 
            SoundAttractMod.LOGGER.error("An unexpected error occurred during a block state check for block {} at {}. Defaulting.", BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos, e);
            return defaultValueOnNPE;
        }
    }

    private static boolean isCustomWool(BlockState state, Block block, Level level, BlockPos pos) {
        if (isBlockInConfigList(state, block, SoundAttractConfig.COMMON.customWoolBlocks.get().stream().map(String::valueOf).toList())) {
            return true;
        }

        try {
            return state.is(BlockTags.WOOL);
        } catch (Exception e) { 
            SoundAttractMod.LOGGER.warn("Exception checking BlockTags.WOOL for block {} at {}. Defaulting to false.", BuiltInRegistries.BLOCK.getKey(block), pos, e);
            return false;
        }
    }

    private static boolean isCustomSolid(BlockState state, Block block, Level level, BlockPos pos) {
        if  (isBlockInConfigList(state, block, SoundAttractConfig.COMMON.customSolidBlocks.get().stream().map(String::valueOf).toList())) {
            return true;
        }
        try {
            return state.isSolidRender(level, pos);
        } catch (NullPointerException npe) {
            SoundAttractMod.LOGGER.warn("NPE in state.isSolidRender() for block {} at {}. Defaulting to solid.", BuiltInRegistries.BLOCK.getKey(block), pos, npe);
            return true; 
        }   catch (Exception e) {
            SoundAttractMod.LOGGER.error("Error in state.isSolidRender() for block {} at {}. Defaulting to solid.", BuiltInRegistries.BLOCK.getKey(block), pos, e);
            return true;
        }
    }

    private static boolean isCustomNonSolid(BlockState state, Block block, Level level, BlockPos pos) {
        if (isBlockInConfigList(state, block, SoundAttractConfig.COMMON.customNonSolidBlocks.get().stream().map(String::valueOf).toList())) {
            return true; 
        }
        boolean isNormallySolid;
        try {
            isNormallySolid = state.isSolid();
        } catch (NullPointerException npe) {
            SoundAttractMod.LOGGER.warn("NPE in state.isSolid() for block {} at {}. Defaulting to solid (meaning not 'non-solid').", BuiltInRegistries.BLOCK.getKey(block), pos, npe);
            isNormallySolid = true;
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("Error in state.isSolid() for block {} at {}. Defaulting to solid.", BuiltInRegistries.BLOCK.getKey(block), pos, e);
            isNormallySolid = true;
        }
        return !isNormallySolid;
    }
    private static boolean isCustomThin(BlockState state, Block block, Level level, BlockPos pos) {
        if (isBlockInConfigList(state, block, SoundAttractConfig.COMMON.customThinBlocks.get().stream().map(String::valueOf).toList())) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("pane") || path.contains("iron_bars") || path.contains("painting") || path.contains("fence") ||
               path.contains("trapdoor") || path.contains("door") || path.contains("ladder") || path.contains("scaffolding") ||
               path.contains("rail");
    }

    private static boolean isCustomLiquid(BlockState state, Block block, Level level, BlockPos pos) { 
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id != null && SoundAttractConfig.CUSTOM_LIQUID_BLOCKS_CACHE.contains(id)) {
            return true;
        }
        return false; 
    }

}