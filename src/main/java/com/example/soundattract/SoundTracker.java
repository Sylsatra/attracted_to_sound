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
    private static final double LARGE_SOUND_RANGE_THRESHOLD = 64.0;

    private static GridKey3D gridKey(BlockPos pos) {
        int x = pos.getX() >> 4; 
        int y = pos.getY() >> 4;
        int z = pos.getZ() >> 4;
        return new GridKey3D(x, y, z);
    }

    private static void addRecordToSpatialCollections(SoundRecord r) {
        if (r.range > LARGE_SOUND_RANGE_THRESHOLD) {
            LARGE_RANGE_SOUNDS.add(r);
        } else {
            SPATIAL_SOUNDS.computeIfAbsent(r.dimensionKey, d -> new HashMap<>())
                          .computeIfAbsent(gridKey(r.pos), k -> new ArrayList<>())
                          .add(r);
        }
    }

    private static void removeRecordFromSpatialCollections(SoundRecord r) {
        if (r.range > LARGE_SOUND_RANGE_THRESHOLD) {
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

    public static void tick() {
        writeLock.lock();
        try {
            Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
            while (iter.hasNext()) {
                SoundRecord r = iter.next();
                r.ticksRemaining--;
                if (r.ticksRemaining <= 0) {
                    iter.remove();
                    removeRecordFromSpatialCollections(r);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    public static SoundRecord findNearestSound(Mob mob, Level level, BlockPos mobPos, Vec3 mobEyePos) {
        readLock.lock();
        try {
            String dimensionKey = level.dimension().location().toString();
            List<SoundRecord> nearbyCandidates = getNearbySounds(dimensionKey, mobPos);

            if (nearbyCandidates.isEmpty()) return null;

            MobProfile profile = SoundAttractConfig.getMatchingProfile(mob);
            SoundRecord bestSound = null;
            double highestWeight = -1.0;
            double closestDistSqr = Double.MAX_VALUE;
            
            double bestSoundEffectiveRange = 0;
            double bestSoundEffectiveWeight = 0;
            
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

                if (finalComparisonWeight > highestWeight || (Math.abs(finalComparisonWeight - highestWeight) < 0.001 && distSqr < closestDistSqr)) {
                    highestWeight = finalComparisonWeight;
                    closestDistSqr = distSqr;
                    bestSound = r;
                    bestSoundEffectiveRange = muffledRange;
                    bestSoundEffectiveWeight = muffledWeight;
                }
            }

            if (bestSound != null) {
                return new SoundRecord(bestSound.sound, bestSound.soundId, bestSound.pos, bestSound.ticksRemaining, bestSound.dimensionKey, bestSoundEffectiveRange, bestSoundEffectiveWeight);
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
        public RaycastCacheKey(BlockPos mobPos, BlockPos soundPos, String soundId) {
            this.mobPos = mobPos;
            this.soundPos = soundPos;
            this.soundId = soundId;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RaycastCacheKey other)) return false;
            return mobPos.equals(other.mobPos) && soundPos.equals(other.soundPos) && soundId.equals(other.soundId);
        }
        @Override
        public int hashCode() {
            return mobPos.hashCode() ^ soundPos.hashCode() ^ soundId.hashCode();
        }
    }
    private static final Map<RaycastCacheKey, double[]> RAYCAST_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final double NO_MUFFLING_RANGE = 1.0;
    private static final double NO_MUFFLING_WEIGHT = 1.0;


    public static double[] applyBlockMuffling(Level level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId) {
        if (!SoundAttractConfig.COMMON.enableBlockMuffling.get()) {
            return new double[]{origRange, origWeight};
        }

        RaycastCacheKey cacheKey = new RaycastCacheKey(dst, src, soundId);
        double[] cachedResult = RAYCAST_CACHE.get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
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
        RAYCAST_CACHE.put(cacheKey, finalResult);
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
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (SoundAttractConfig.CUSTOM_LIQUID_BLOCKS_CACHE.contains(blockId)) {
            return true;
        }
        return false; 
    }

}