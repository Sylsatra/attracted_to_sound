package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.config.SoundOverride;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.Set;
import java.util.HashSet;

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
        public SoundRecord(SoundEvent sound, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this(sound, sound != null && sound.getLocation() != null ? sound.getLocation().toString() : null, pos, lifetime, dimensionKey, range, weight);
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

    private static final int GRID_SIZE = 16; 
    private static final Map<String, Map<Long, List<SoundRecord>>> SPATIAL_SOUNDS = new HashMap<>();

    private static long gridKey(BlockPos pos) {
        int x = pos.getX() >> 4;
        int z = pos.getZ() >> 4;
        return (((long)x) << 32) | (z & 0xFFFFFFFFL);
    }

    private static final double LARGE_SOUND_RANGE_THRESHOLD = 16.0;

    private static List<SoundRecord> getNearbySounds(String dim, BlockPos pos) {
        Map<Long, List<SoundRecord>> dimMap = SPATIAL_SOUNDS.get(dim);
        if (dimMap == null) return Collections.emptyList();
        Set<SoundRecord> candidates = new HashSet<>();
        long key = gridKey(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long neighborKey = (((key >> 32) + dx) << 32) | (((key & 0xFFFFFFFFL) + dz) & 0xFFFFFFFFL);
                List<SoundRecord> list = dimMap.get(neighborKey);
                if (list != null) {
                    candidates.addAll(list);
                }
            }
        }
        for (SoundRecord r : RECENT_SOUNDS) {
            if (r.dimensionKey.equals(dim) && r.range > LARGE_SOUND_RANGE_THRESHOLD) {
                candidates.add(r);
            }
        }
        return new ArrayList<>(candidates);
    }

    private static void updateSpatialSounds() {
        SPATIAL_SOUNDS.clear();
        for (SoundRecord r : RECENT_SOUNDS) {
            SPATIAL_SOUNDS.computeIfAbsent(r.dimensionKey, d -> new HashMap<>())
                .computeIfAbsent(gridKey(r.pos), k -> new ArrayList<>()).add(r);
        }
    }

    public static synchronized void addSound(SoundEvent se,
                                         BlockPos      pos,
                                         String        dimensionKey,
                                         double        range,
                                         double        weight,
                                         int           lifetime,
                                         String        explicitSoundId) {
        ResourceLocation realSoundLoc = (se != null && se.getLocation() != null) ? se.getLocation() : null;
        if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() &&
        (realSoundLoc == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(realSoundLoc))) {
            if (SoundAttractConfig.COMMON.debugLogging.get())
            SoundAttractMod.LOGGER.debug("Sound {} not in whitelist, ignoring.", realSoundLoc);
            return;
        }
        String soundIdForDuplicates = (explicitSoundId != null) ? explicitSoundId : (realSoundLoc != null ? realSoundLoc.toString() : "unknown");
        for (Iterator<SoundRecord> it = RECENT_SOUNDS.iterator(); it.hasNext(); ) {
            SoundRecord existing = it.next();
            if (existing.dimensionKey.equals(dimensionKey) &&
                existing.pos.equals(pos) &&
                Objects.equals(existing.soundId, soundIdForDuplicates)) {
                    if (weight >= existing.weight) {
                        if (SoundAttractConfig.COMMON.debugLogging.get())
                        SoundAttractMod.LOGGER.info("[addSound] replacing weaker duplicate {} ({} -> {})", soundIdForDuplicates, existing.weight, weight);
                        it.remove();
                        break;
                } else {
                    existing.ticksRemaining = Math.max(existing.ticksRemaining, lifetime);
                    if (SoundAttractConfig.COMMON.debugLogging.get())
                        SoundAttractMod.LOGGER.info("[addSound] refreshed lifetime of existing sound {}", soundIdForDuplicates);
                    return;
                }
            }
        }
        if (SoundAttractConfig.COMMON.debugLogging.get())
        SoundAttractMod.LOGGER.info("[addSound] stored {} (type: {}) (range={}, weight={})",
                                    realSoundLoc, soundIdForDuplicates, range, weight);
        SoundRecord record = new SoundRecord(se, soundIdForDuplicates, pos, lifetime, dimensionKey, range, weight);
        RECENT_SOUNDS.add(record);
        RECENT_SOUNDS.sort((a, b) -> {
            int cmp = Double.compare(b.range, a.range);
            if (Math.abs(a.range - b.range) < 5.0)  
                cmp = Double.compare(b.weight, a.weight);
            return cmp;
        });
        int cap = SoundAttractConfig.COMMON.maxSoundsTracked.get();
        if (RECENT_SOUNDS.size() > cap) {
            RECENT_SOUNDS.subList(cap, RECENT_SOUNDS.size()).clear();
        }
        updateSpatialSounds();
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        addSound(se, pos, dimensionKey, range, weight, lifetime, null);
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        addSound(se, pos, dimensionKey, 16.0, 1.0, lifetime);
    }

    public static synchronized void addVirtualSound(BlockPos pos, String dimensionKey, double range, double weight, int lifetime, UUID sourcePlayer, String animationClass) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundTracker] addVirtualSound called with pos: {}, weight: {}. RECENT_SOUNDS size before processing: {}", pos, weight, RECENT_SOUNDS.size());
        RECENT_SOUNDS.removeIf(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight < weight);
        boolean higherExists = RECENT_SOUNDS.stream().anyMatch(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight > weight);
        com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundTracker] 'higherExists' check result: {}", higherExists);
        if (!higherExists) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundTracker] No higher-weight sound found. Proceeding to add new virtual sound.");
            RECENT_SOUNDS.removeIf(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight == weight);
            RECENT_SOUNDS.add(new VirtualSoundRecord(pos, lifetime, dimensionKey, range, weight, sourcePlayer, animationClass));
            updateSpatialSounds();
            com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundTracker] RECENT_SOUNDS size after processing: {}", RECENT_SOUNDS.size());
        }
    }

    public static synchronized void tick() {
        Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
        while (iter.hasNext()) {
            SoundRecord r = iter.next();
            r.ticksRemaining--;
            if (r.ticksRemaining <= 0) {
                iter.remove();
            }
        }
        updateSpatialSounds();
    }

    public static synchronized void removeSoundAt(BlockPos pos, String dimensionKey) {
        Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
        while (iter.hasNext()) {
            SoundRecord r = iter.next();
            if (r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey)) {
                iter.remove();
                break;
            }
        }
        updateSpatialSounds();
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
            if (this == o) return true;
            if (!(o instanceof RaycastCacheKey other)) return false;
            return Objects.equals(this.mobPos, other.mobPos) &&
                   Objects.equals(this.soundPos, other.soundPos) &&
                   Objects.equals(this.soundId, other.soundId);        }
        @Override
        public int hashCode() {
            int soundIdHash = (soundId != null ? soundId.hashCode() : 0);
            return mobPos.hashCode() ^ soundPos.hashCode() ^ soundIdHash;
        }
    }
    private static final Map<RaycastCacheKey, double[]> RAYCAST_CACHE = new WeakHashMap<>();

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
                else if (blockState.isAir()) {
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

    private static boolean isCustomLiquid(BlockState state, Block block, Level level, BlockPos pos) { // Added level, pos for signature consistency
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (SoundAttractConfig.CUSTOM_LIQUID_BLOCKS_CACHE.contains(blockId)) {
            return true;
        }
        return false; 
    }

    public static void pruneIrrelevantSounds(Level level) {
        if (RECENT_SOUNDS.isEmpty()) return;
        List<net.minecraft.world.entity.Mob> mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, level.getWorldBorder().getCollisionShape().bounds());
        Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
        while (iter.hasNext()) {
            SoundRecord sound = iter.next();
            if (sound.ticksRemaining > 5) continue;
            boolean inRange = false;
            for (net.minecraft.world.entity.Mob mob : mobs) {
                if (!mob.isAlive()) continue;
                BlockPos mobPos = mob.blockPosition();
                String soundId = sound.soundId != null ? sound.soundId : (sound.sound != null && sound.sound.getLocation() != null ? sound.sound.getLocation().toString() : "unknown");
                double[] muffled = applyBlockMuffling(level, sound.pos, mobPos, sound.range, sound.weight, soundId);
                double range = muffled[0];
                double distSqr = mobPos.distSqr(sound.pos);
                if (distSqr <= range * range) {
                    inRange = true;
                    break;
                }
            }
            if (!inRange) iter.remove();
        }
        updateSpatialSounds();
    }

    public static synchronized SoundRecord findNearestSound(Mob mob, Level level, BlockPos mobPos, Vec3 mobEyePos) {
        String dimensionKey = level.dimension().location().toString();
        List<SoundRecord> nearbyCandidates = getNearbySounds(dimensionKey, mobPos);
        com.example.soundattract.config.MobProfile profile = SoundAttractConfig.getMatchingProfile(mob);

        SoundRecord bestSound = null;
        double highestWeight = -1.0;
        double closestDistSqr = Double.MAX_VALUE;

        for (SoundRecord r : nearbyCandidates) {
            if (r == null || r.pos == null) {
                continue;
            }

            ResourceLocation publicSoundId = (r.sound != null && r.sound.getLocation() != null) ? r.sound.getLocation() : null;
            if (publicSoundId == null) {
                publicSoundId = ResourceLocation.tryParse(r.soundId);
            }

            if (!(r instanceof VirtualSoundRecord)) {
                if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() &&
                    (publicSoundId == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(publicSoundId))) {
                    continue;
                }
            }

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[findNearest] considering {} (internal type: {})", publicSoundId, r.soundId);
            }

            double effectiveInitialRange = r.range;
            double effectiveInitialWeight = r.weight;

            if (profile != null && publicSoundId != null) {
                Optional<SoundOverride> ov = profile.getSoundOverride(publicSoundId);
                if (ov.isPresent()) {
                    effectiveInitialRange = ov.get().getRange();
                    effectiveInitialWeight = ov.get().getWeight();
                }
            }

            double[] muffled = applyBlockMuffling(level, r.pos, mobPos,
                                              effectiveInitialRange, effectiveInitialWeight,
                                              r.soundId != null ? r.soundId : "unknown");

            double muffledRange = muffled[0];
            double muffledWeight = muffled[1];
            double distSqr = mobPos.distSqr(r.pos);
            double rangeSqr = muffledRange * muffledRange;

            if (distSqr > rangeSqr) {
                continue;
            }
            if (muffledWeight > highestWeight || (Math.abs(muffledWeight - highestWeight) < 0.001 && distSqr < closestDistSqr)) {
                highestWeight = muffledWeight;
                closestDistSqr = distSqr;
                bestSound = r;
            }
        }
        return bestSound;
    }
}
