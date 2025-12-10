package com.example.soundattract;

import com.example.soundattract.config.MobProfile;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.*;

public class SoundTracker {

    public static class SoundRecord {
        public final UUID id = UUID.randomUUID();
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
            super(null, resolveVirtualSoundId(animationClass), pos, lifetime, dimensionKey, range, weight);
            this.sourcePlayer = sourcePlayer;
            this.animationClass = animationClass;
        }

        private static String resolveVirtualSoundId(String animationClass) {
            if ("voice_chat".equals(animationClass)) {
                return SoundMessage.VOICE_CHAT_SOUND_ID.toString();
            }
            String key = (animationClass == null || animationClass.isEmpty())
                    ? "virtual"
                    : animationClass.toLowerCase(java.util.Locale.ROOT);
            return com.example.soundattract.SoundAttractMod.MOD_ID + ":" + key;
        }
    }



    /**
     * Helper method to get the Identifier from a ResourceKey.
     * In 1.21.4+, ResourceKey.location() was renamed.
     * We parse it from the string representation.
     */
    public static Identifier getKeyLocation(ResourceKey<?> key) {
        String keyStr = key.toString();
        int lastSlash = keyStr.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < keyStr.length() - 1) {
            String locStr = keyStr.substring(lastSlash + 1).trim();
            if (locStr.endsWith("]")) {
                locStr = locStr.substring(0, locStr.length() - 1);
            }
            Identifier result = Identifier.tryParse(locStr);
            if (result != null) return result;
        }
        return Identifier.withDefaultNamespace("unknown");
    }

    /**
     * Helper method to get dimension key string from a Level.
     */
    public static String getDimensionKeyString(Level level) {
        return getKeyLocation(level.dimension()).toString();
    }

    /**
     * Helper method to get dimension key Identifier from a Level.
     */
    public static Identifier getDimensionKey(Level level) {
        return getKeyLocation(level.dimension());
    }

    private static final ConcurrentHashMap<String, ConcurrentHashMap<GridKey3D, ConcurrentHashMap<UUID, SoundRecord>>> SPATIAL_SOUNDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SoundRecord> LARGE_RANGE_SOUNDS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Long> RECENT_DUPLICATE_SUPPRESSION = new ConcurrentHashMap<>();
    private static final long DEDUPLICATE_WINDOW_TICKS = 2L;
    private static final int PREFILTER_LIMIT = 24;
    private static final double WEIGHT_EPSILON = 1.0e-4;

    private record GridKey3D(int x, int y, int z) {}


    private static GridKey3D gridKey(BlockPos pos) {
        int x = pos.getX() >> 4; 
        int y = pos.getY() >> 4;
        int z = pos.getZ() >> 4;
        return new GridKey3D(x, y, z);
    }

    private static void addRecordToSpatialCollections(SoundRecord r) {
        double threshold = SoundAttractConfig.COMMON.largeSoundRangeThreshold.get();
        if (r.range > threshold) {
            LARGE_RANGE_SOUNDS.put(r.id, r);
        } else {
            SPATIAL_SOUNDS.computeIfAbsent(r.dimensionKey, d -> new ConcurrentHashMap<>())
                          .computeIfAbsent(gridKey(r.pos), k -> new ConcurrentHashMap<>())
                          .put(r.id, r);
        }
    }

    private static void removeRecordFromSpatialCollections(SoundRecord r) {
        double threshold = SoundAttractConfig.COMMON.largeSoundRangeThreshold.get();
        if (r.range > threshold) {
            LARGE_RANGE_SOUNDS.remove(r.id);
        } else {
            ConcurrentHashMap<GridKey3D, ConcurrentHashMap<UUID, SoundRecord>> dimMap = SPATIAL_SOUNDS.get(r.dimensionKey);
            if (dimMap != null) {
                GridKey3D key = gridKey(r.pos);
                ConcurrentHashMap<UUID, SoundRecord> cellMap = dimMap.get(key);
                if (cellMap != null) {
                    cellMap.remove(r.id);
                    if (cellMap.isEmpty()) {
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
        // In 1.21.2+, SoundEvent is a record, so use location() instead of getLocation()
        String soundIdToUse = (explicitSoundId != null) ? explicitSoundId : (se != null && se.location() != null ? se.location().toString() : "unknown");
        if (soundIdToUse == null) {
            soundIdToUse = "unknown";
        }

        Identifier loc = Identifier.tryParse(soundIdToUse);
        if (loc != null && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("Sound {} not in whitelist, ignoring.", soundIdToUse);
            }
            return;
        }

        String dedupKey = dimensionKey + "|" + soundIdToUse + "|" + pos.asLong();
        long nowTick = currentTickCounter;
        Long lastTick = RECENT_DUPLICATE_SUPPRESSION.get(dedupKey);
        if (lastTick != null && nowTick - lastTick <= DEDUPLICATE_WINDOW_TICKS) {
            return;
        }
        RECENT_DUPLICATE_SUPPRESSION.put(dedupKey, nowTick);


        SoundRecord record = new SoundRecord(se, soundIdToUse, pos, lifetime, dimensionKey, range, weight);
        addRecordToSpatialCollections(record);
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[addSound] Stored {} (Range={}, Weight={})", soundIdToUse, range, weight);
        }
    }

    public static void addVirtualSound(BlockPos pos, String dimensionKey, double range, double weight, int lifetime, UUID sourcePlayer, String animationClass) {
        VirtualSoundRecord record = new VirtualSoundRecord(pos, lifetime, dimensionKey, range, weight, sourcePlayer, animationClass);
        addRecordToSpatialCollections(record);
    }
    
    public static void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        addSound(se, pos, dimensionKey, range, weight, lifetime, null);
    }
    public static void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        addSound(se, pos, dimensionKey, 16.0, 1.0, SoundAttractConfig.COMMON.soundLifetimeTicks.get());
    }

    private static long ticksSinceCleanup = 0;


    private static ExecutorService soundScoringExecutor;
    private static final ConcurrentLinkedQueue<AsyncSoundResult> ASYNC_RESULTS = new ConcurrentLinkedQueue<>();
    private static final Map<UUID, CachedEval> EVAL_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SUBMIT_COOLDOWNS = new ConcurrentHashMap<>();

    private record AsyncSoundResult(UUID mobId, SoundRecord result, long completionTick) {}

    public static void initialize() {
        int workerThreads = SoundAttractConfig.COMMON.workerThreads.get();
        if (workerThreads > 0) {
            soundScoringExecutor = Executors.newFixedThreadPool(workerThreads);
            SoundAttractMod.LOGGER.info("Initialized SoundTracker with {} worker threads.", workerThreads);
        }
    }

    public static void shutdown() {
        if (soundScoringExecutor != null) {
            soundScoringExecutor.shutdown();
            soundScoringExecutor = null;
            SoundAttractMod.LOGGER.info("Shut down SoundTracker worker threads.");
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

    private static class SoundScoringTask implements Runnable {
        private final UUID mobId;
        private final Level level;
        private final BlockPos pos;
        private final Vec3 eye;
        private final MobProfile profile;

        SoundScoringTask(UUID mobId, Level level, BlockPos pos, Vec3 eye, MobProfile profile) {
            this.mobId = mobId;
            this.level = level;
            this.pos = pos;
            this.eye = eye;
            this.profile = profile;
        }

        @Override
        public void run() {
            try {
                SoundRecord result = findNearestSoundInternal(level, pos, eye, profile);
                ASYNC_RESULTS.add(new AsyncSoundResult(mobId, result, currentTickCounter));
            } catch (Exception e) {
                SoundAttractMod.LOGGER.error("Error during async sound scoring for mob {}", mobId, e);
            }
        }
    }


    private static final class ApproxCandidate {
        final SoundRecord rec;
        final double effRange;
        final double effWeight;
        final double novelty;
        final double approxWeight;
        final double distSqr;
        final String soundKey;
        final Identifier baseSoundId;
        double muffledRange;
        double muffledWeight;

        ApproxCandidate(SoundRecord rec, double effRange, double effWeight, double novelty, double approxWeight, double distSqr, String soundKey, Identifier baseSoundId) {
            this.rec = rec;
            this.effRange = effRange;
            this.effWeight = effWeight;
            this.novelty = novelty;
            this.approxWeight = approxWeight;
            this.distSqr = distSqr;
            this.soundKey = soundKey;
            this.baseSoundId = baseSoundId;
            this.muffledRange = effRange;
            this.muffledWeight = effWeight;
        }

        double finalWeight() {
            return this.muffledWeight + this.novelty;
        }
    }

    public static SoundRecord getCachedOrRequestNearest(Mob mob, Level level, BlockPos pos, Vec3 eyePos) {
        if (soundScoringExecutor == null || soundScoringExecutor.isShutdown()) {
            return findNearestSound(mob, level, pos, eyePos);
        }

        UUID id = mob.getUUID();
        int ttl = SoundAttractConfig.COMMON.asyncResultTtlTicks.get();
        CachedEval cached = EVAL_CACHE.get(id);


        if (cached != null && (currentTickCounter - cached.tick) < ttl) {
            return cached.record;
        }


        long now = currentTickCounter;
        long cooldownUntil = SUBMIT_COOLDOWNS.getOrDefault(id, 0L);
        if (now < cooldownUntil) {
            return (cached != null) ? cached.record : null;
        }


        MobProfile profile = SoundAttractConfig.getMatchingProfile(mob);
        soundScoringExecutor.submit(new SoundScoringTask(id, level, pos.immutable(), eyePos, profile));


        int cooldownTicks = SoundAttractConfig.COMMON.soundScoringSubmitCooldownTicks.get();
        SUBMIT_COOLDOWNS.put(id, now + cooldownTicks);


        return (cached != null) ? cached.record : null;
    }

    public static void tick() {
        currentTickCounter++;


        SPATIAL_SOUNDS.forEach((dim, grid) -> {
            grid.forEach((key, cell) -> {
                cell.forEach((id, record) -> {
                    record.ticksRemaining--;
                    if (record.ticksRemaining <= 0) {
                        cell.remove(id);
                    }
                });
                if (cell.isEmpty()) {
                    grid.remove(key);
                }
            });
            if (grid.isEmpty()) {
                SPATIAL_SOUNDS.remove(dim);
            }
        });

        LARGE_RANGE_SOUNDS.forEach((id, record) -> {
            record.ticksRemaining--;
            if (record.ticksRemaining <= 0) {
                LARGE_RANGE_SOUNDS.remove(id);
            }
        });

        ticksSinceCleanup++;
        if (SoundAttractConfig.COMMON.enableRaycastCache.get()) {
            int interval = SoundAttractConfig.COMMON.raycastCacheCleanupIntervalTicks.get();
            if (interval > 0 && (ticksSinceCleanup % interval == 0)) {
                cleanupRaycastCache();
            }
        } else {
            RAYCAST_CACHE.clear();
        }

        RECENT_DUPLICATE_SUPPRESSION.entrySet().removeIf(entry -> currentTickCounter - entry.getValue() > DEDUPLICATE_WINDOW_TICKS);

        AsyncSoundResult result;
        while ((result = ASYNC_RESULTS.poll()) != null) {
            EVAL_CACHE.put(result.mobId(), new CachedEval(result.result(), result.completionTick()));
        }

        if (currentTickCounter % 200 == 0) {
            long now = currentTickCounter;
            SUBMIT_COOLDOWNS.entrySet().removeIf(entry -> now > entry.getValue() + 400L);
            EVAL_CACHE.entrySet().removeIf(entry -> now > entry.getValue().tick + SoundAttractConfig.COMMON.asyncResultTtlTicks.get() * 2L);
        }
    }

    private static SoundRecord findNearestSoundInternal(Level level, BlockPos mobPos, Vec3 mobEyePos, com.example.soundattract.config.MobProfile mobProfile) {
        String dimensionKey = getDimensionKeyString(level);
        List<SoundRecord> nearbyCandidates = getNearbySounds(dimensionKey, mobPos);
        if (nearbyCandidates.isEmpty()) {
            return null;
        }

        Map<String, ApproxCandidate> approxBySound = new HashMap<>();

        double noveltyBonusValue = SoundAttractConfig.COMMON.soundNoveltyBonusWeight.get();
        int noveltyTicks = SoundAttractConfig.COMMON.soundNoveltyTimeTicks.get();
        int maxLifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

        for (SoundRecord r : nearbyCandidates) {
            if (r == null || r.pos == null) {
                continue;
            }

            String soundKey = (r.soundId != null) ? r.soundId : "unknown";
            Identifier rl = Identifier.tryParse(soundKey);
            if (rl != null && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(rl)) {
                continue;
            }

            double effRange = r.range;
            double effWeight = r.weight;

            if (mobProfile != null && rl != null) {
                java.util.Optional<com.example.soundattract.config.MobProfile.SoundOverride> ov = mobProfile.getSoundOverride(rl);
                if (ov.isPresent()) {
                    com.example.soundattract.config.MobProfile.SoundOverride so = ov.get();
                    effRange = so.range();
                    effWeight = so.weight();
                }
            }

            double distSqr = mobPos.distSqr(r.pos);
            if (distSqr > (effRange * effRange)) {
                continue;
            }

            double novelty = (r.ticksRemaining > (maxLifetime - noveltyTicks)) ? noveltyBonusValue : 0.0;
            double approxWeight = effWeight + novelty;
            if (approxWeight <= 0.0) {
                continue;
            }

            ApproxCandidate existing = approxBySound.get(soundKey);
            if (existing == null || approxWeight > existing.approxWeight || (Math.abs(approxWeight - existing.approxWeight) < 0.001 && distSqr < existing.distSqr)) {
                approxBySound.put(soundKey, new ApproxCandidate(r, effRange, effWeight, novelty, approxWeight, distSqr, soundKey, rl));
            }
        }

        if (approxBySound.isEmpty()) {
            return null;
        }

        List<ApproxCandidate> shortlist = new ArrayList<>(approxBySound.values());
        shortlist.sort((a, b) -> {
            int cmp = Double.compare(b.approxWeight, a.approxWeight);
            if (cmp != 0) {
                return cmp;
            }
            return Double.compare(a.distSqr, b.distSqr);
        });
        if (shortlist.size() > PREFILTER_LIMIT) {
            shortlist = new ArrayList<>(shortlist.subList(0, PREFILTER_LIMIT));
        }

        ApproxCandidate bestCandidate = null;
        double bestFinalWeight = -1.0;
        double bestFinalDist = Double.MAX_VALUE;

        for (ApproxCandidate candidate : shortlist) {
            if (bestCandidate != null && candidate.approxWeight < bestFinalWeight - WEIGHT_EPSILON) {
                continue;
            }

            double[] muffled = applyBlockMuffling(level, candidate.rec.pos, mobPos, candidate.effRange, candidate.effWeight, candidate.soundKey);
            candidate.muffledRange = muffled[0];
            candidate.muffledWeight = muffled[1];

            if (candidate.distSqr > (candidate.muffledRange * candidate.muffledRange)) {
                continue;
            }

            double finalWeight = candidate.finalWeight();
            if (finalWeight > bestFinalWeight || (Math.abs(finalWeight - bestFinalWeight) < 0.001 && candidate.distSqr < bestFinalDist)) {
                bestCandidate = candidate;
                bestFinalWeight = finalWeight;
                bestFinalDist = candidate.distSqr;
            }
        }

        if (bestCandidate != null) {
            return new SoundRecord(bestCandidate.rec.sound, bestCandidate.rec.soundId, bestCandidate.rec.pos, bestCandidate.rec.ticksRemaining, bestCandidate.rec.dimensionKey, bestCandidate.muffledRange, bestCandidate.muffledWeight);
        }
        return null;
    }

    public static SoundRecord findNearestSound(Mob mob, Level level, BlockPos mobPos, Vec3 mobEyePos) {

        return findNearestSoundInternal(level, mobPos, mobEyePos, SoundAttractConfig.getMatchingProfile(mob));
    }


    private static List<SoundRecord> getNearbySounds(String dim, BlockPos pos) {
        List<SoundRecord> result = new ArrayList<>();
        ConcurrentHashMap<GridKey3D, ConcurrentHashMap<UUID, SoundRecord>> dimMap = SPATIAL_SOUNDS.get(dim);

        if (dimMap != null) {
            GridKey3D centerKey = gridKey(pos);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        GridKey3D neighborKey = new GridKey3D(centerKey.x() + dx, centerKey.y() + dy, centerKey.z() + dz);
                        ConcurrentHashMap<UUID, SoundRecord> cell = dimMap.get(neighborKey);
                        if (cell != null) {
                            result.addAll(cell.values());
                        }
                    }
                }
            }
        }

        for (SoundRecord r : LARGE_RANGE_SOUNDS.values()) {
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

        String dimKey = getDimensionKeyString(level);
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
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;
        String blockIdStr = id.toString();
        for (String configEntry : configList) {
            if (configEntry.endsWith("*")) { 
                TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, Identifier.parse(configEntry.substring(0, configEntry.length() - 1)));
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
            // In 1.21.11, isSolidRender() no longer takes level and pos parameters
            return state.isSolidRender();
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
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("pane") || path.contains("iron_bars") || path.contains("painting") || path.contains("fence") ||
               path.contains("trapdoor") || path.contains("door") || path.contains("ladder") || path.contains("scaffolding") ||
               path.contains("rail");
    }

    private static boolean isCustomLiquid(BlockState state, Block block, Level level, BlockPos pos) { 
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id != null && SoundAttractConfig.CUSTOM_LIQUID_BLOCKS_CACHE.contains(id)) {
            return true;
        }
        return false; 
    }

}
