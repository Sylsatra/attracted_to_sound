package com.example.soundattract;

import com.example.soundattract.config.MobProfile;
import com.example.soundattract.config.SoundAttractConfigData; 
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.SpatialPartitioner;

import net.minecraft.util.math.BlockPos;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries; 
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.entity.mob.MobEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;
import java.util.Objects;
import com.example.soundattract.util.ThreadingChecks;

public class SoundTracker {

    public static class SoundRecord {
        public final SoundEvent sound; 
        public final String soundId;   
        public final BlockPos pos;
        public int ticksRemaining;
        public final String dimensionKey;
        public final double range;    
        public final double weight;   
        public final java.util.Set<Long> coveredCells = new java.util.HashSet<>();
        
        public static final int DEFAULT_TICKS_REMAINING = SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.soundLifetimeTicks : 200;

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
            this(sound, 
                 (sound != null && sound.getId() != null ? sound.getId().toString() : "unknown_sound_event_id"),
                 pos, lifetime, dimensionKey, range, weight);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SoundRecord other)) return false; 
            return Objects.equals(soundId, other.soundId) && 
                   Objects.equals(pos, other.pos) &&
                   Objects.equals(dimensionKey, other.dimensionKey) &&
                   Double.compare(weight, other.weight) == 0 && 
                   Double.compare(range, other.range) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(soundId, pos, dimensionKey, range, weight); 
        }
    }

    private static int getGridRadiusForRange(double range, int partitionSize) {
        if (range < 8) return 1;
        if (partitionSize <= 0) partitionSize = 16; 
        for (int i = 1; i <= 20; i++) { 
            if (range < 8 + (double)partitionSize * i) { 
                return i + 1;
            }
        }
        return 21; 
    }


    public static java.util.Set<Long> getCoveredCells(BlockPos pos, double range, int partitionSize) {
        java.util.Set<Long> cells = new java.util.HashSet<>();
        if (partitionSize <= 0) partitionSize = 16; 
        int gridRadius = getGridRadiusForRange(range, partitionSize);
        int baseX = pos.getX() / partitionSize;
        int baseZ = pos.getZ() / partitionSize;
        for (int dx = -gridRadius; dx <= gridRadius; dx++) {
            for (int dz = -gridRadius; dz <= gridRadius; dz++) {
                int cellX = baseX + dx;
                int cellZ = baseZ + dz;
                long key = (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);
                cells.add(key);
            }
        }
        return cells;
    }

    public static class VirtualSoundRecord extends SoundRecord {
        public final UUID sourcePlayer;
        public final String animationClass; 

        public VirtualSoundRecord(BlockPos pos, int lifetime, String dimensionKey, double range, double weight, UUID sourcePlayer, String animationClass) {
            super(null, "virtual_sound:" + (animationClass != null ? animationClass : "unknown"), pos, lifetime, dimensionKey, range, weight);
            this.sourcePlayer = sourcePlayer;
            this.animationClass = animationClass;
        }
    }

    public static final List<SoundRecord> RECENT_SOUNDS = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Map<Long, List<SoundRecord>>> SPATIAL_SOUNDS = Collections.synchronizedMap(new HashMap<>());


    private static final class CachedBest {
        final SoundRecord best;
        final long createdAtNanos;
        final String dimensionKey;

        CachedBest(SoundRecord best, long createdAtNanos, String dimensionKey) {
            this.best = best;
            this.createdAtNanos = createdAtNanos;
            this.dimensionKey = dimensionKey;
        }
    }
    private static final Map<java.util.UUID, CachedBest> ASYNC_BEST_BY_MOB = new java.util.concurrent.ConcurrentHashMap<>();
    private static long getAsyncResultTtlNanos() {
        long ms = 2500L;
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.asyncResultTtlMs > 0) {
            ms = SoundAttractMod.CONFIG.asyncResultTtlMs;
        }
        return java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(ms);
    }

    public static void applySoundScoreResult(com.example.soundattract.util.WorkerScheduler.SoundScoreResult result) {
        if (result == null || result.mobUuid == null) return;
        CachedBest cb = new CachedBest(result.best, result.createdAtNanos, result.dimensionKey);
        ASYNC_BEST_BY_MOB.put(result.mobUuid, cb);
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.debug("[SoundTracker] Applied async best for mob {} in dim {}", result.mobUuid, result.dimensionKey);
        }
    }

    public static SoundRecord getCachedBestFor(MobEntity mob, String dimensionKey) {
        if (mob == null) return null;
        CachedBest cb = ASYNC_BEST_BY_MOB.get(mob.getUuid());
        if (cb == null) return null;
        if (dimensionKey != null && cb.dimensionKey != null && !dimensionKey.equals(cb.dimensionKey)) return null;
        long ttl = getAsyncResultTtlNanos();
        long age = System.nanoTime() - cb.createdAtNanos;
        if (age > ttl) {
            ASYNC_BEST_BY_MOB.remove(mob.getUuid());
            return null;
        }
        return cb.best;
    }
    public static void clearCachedBest(java.util.UUID mobUuid) {
        if (mobUuid != null) ASYNC_BEST_BY_MOB.remove(mobUuid);
    }

    private static synchronized void updateSpatialSounds() {
        SPATIAL_SOUNDS.clear(); 
        if (SoundAttractMod.CONFIG == null) return;
        int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
        if (partitionSize <= 0) partitionSize = 16; 

        List<SoundRecord> currentSounds = new ArrayList<>(RECENT_SOUNDS); 

        for (SoundRecord r : currentSounds) {
            r.coveredCells.clear(); 
            java.util.Set<Long> cells = getCoveredCells(r.pos, r.range, partitionSize);
            r.coveredCells.addAll(cells); 

            Map<Long, List<SoundRecord>> dimMap = SPATIAL_SOUNDS.computeIfAbsent(r.dimensionKey, d -> Collections.synchronizedMap(new HashMap<>()));
            for (Long key : cells) {
                dimMap.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add(r);
            }
        }
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime, String explicitSoundId) {
        if (SoundAttractMod.CONFIG == null) { 
            System.err.println("[SoundTracker] Config not loaded, cannot add sound.");
            return;
        }

        String soundIdToUse = explicitSoundId;
        if (soundIdToUse == null && se != null && se.getId() != null) {
            soundIdToUse = se.getId().toString();
        }
        if (soundIdToUse == null) {
            soundIdToUse = "unknown_sound_id_at_" + pos.toShortString(); 
            if (SoundAttractMod.CONFIG.debugLogging) {
                 SoundAttractMod.LOGGER.warn("[SoundTracker] SoundEvent or explicitSoundId resulted in null soundId. Using placeholder: {}", soundIdToUse);
            }
        }
        final String finalSoundId = soundIdToUse;


        if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() &&
            !SoundAttractMod.CONFIG.soundIdWhitelist.contains(finalSoundId) &&
            !finalSoundId.equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID.toString())) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                    "[SoundTracker] Skipped non-whitelist sound: {} at {} (dim: {}), range={}, weight={}",
                    finalSoundId, pos, dimensionKey, range, weight
                );
            }
            return;
        }

        if (range < 0) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn(
                    "[SoundTracker] Attempted to register sound {} at {} (dim: {}) with negative range={}, skipping.",
                    finalSoundId, pos, dimensionKey, range
                );
            }
            return;
        }

        boolean removed = RECENT_SOUNDS.removeIf(r -> 
            r.pos.equals(pos) &&
            Objects.equals(r.soundId, finalSoundId) && 
            Objects.equals(r.dimensionKey, dimensionKey)
        );

        if (removed && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Refreshed sound (removed old instance): {} at {} (dim: {})",
                finalSoundId, pos, dimensionKey
            );
        }

        RECENT_SOUNDS.add(new SoundRecord(se, finalSoundId, pos, lifetime, dimensionKey, range, weight));
        
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Registered sound {} at {} (dim: {}), range={}, weight={}, lifetime={}",
                finalSoundId, pos, dimensionKey, String.format("%.2f",range), String.format("%.2f",weight), lifetime
            );
        }
        updateSpatialSounds();
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        addSound(se, pos, dimensionKey, range, weight, lifetime, null);
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        if (SoundAttractMod.CONFIG == null) {
             System.err.println("[SoundTracker] Config not loaded for default lifetime sound.");
             return;
        }
        int lifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;
        addSound(se, pos, dimensionKey, 16.0, 1.0, lifetime); 
    }

    public static synchronized void addVirtualSound(BlockPos pos, String dimensionKey, double range, double weight, int lifetime, UUID sourcePlayer, String animationClass) {
        if (SoundAttractMod.CONFIG == null) {
             System.err.println("[SoundTracker] Config not loaded, cannot add virtual sound.");
             return;
        }

        String constructedVirtualSoundId = "virtual_sound:" + (animationClass != null ? animationClass : "player_action");
        if (sourcePlayer != null) {
            String playerPart = sourcePlayer.toString();
            constructedVirtualSoundId += ":" + (playerPart.length() > 8 ? playerPart.substring(0, 8) : playerPart);
        }
        final String finalVirtualSoundId = constructedVirtualSoundId;


        boolean removed = RECENT_SOUNDS.removeIf(r -> 
            r.pos.equals(pos) &&
            Objects.equals(r.soundId, finalVirtualSoundId) &&
            Objects.equals(r.dimensionKey, dimensionKey)
        );
         if (removed && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Refreshed virtual sound (removed old instance): {} at {} (dim: {})",
                finalVirtualSoundId, pos, dimensionKey
            );
        }


        RECENT_SOUNDS.add(new VirtualSoundRecord(pos, lifetime, dimensionKey, range, weight, sourcePlayer, animationClass));
        
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Registered virtual sound (intended ID for refresh check: {}) at {} (dim: {}), range={}, weight={}, lifetime={}",
                finalVirtualSoundId, 
                pos, dimensionKey, String.format("%.2f",range), String.format("%.2f",weight), lifetime
            );
        }
        updateSpatialSounds();
    }

    public static synchronized void tick() {
        boolean soundsChanged = false;
        Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
        while (iter.hasNext()) {
            SoundRecord r = iter.next();
            r.ticksRemaining--;
            if (r.ticksRemaining <= 0) {
                iter.remove();
                soundsChanged = true;
            }
        }
        if (soundsChanged) { 
            updateSpatialSounds();
        }
    }

    public static synchronized void removeSoundAt(BlockPos pos, String dimensionKey) {
        boolean removed = RECENT_SOUNDS.removeIf(r -> r.pos.equals(pos) && Objects.equals(r.dimensionKey, dimensionKey));
        if (removed) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                 SoundAttractMod.LOGGER.info("[SoundTracker] Removed sound(s) at pos {} in dim {}", pos, dimensionKey);
            }
            updateSpatialSounds();
        }
    }

    private static class RaycastCacheKey {
        public final BlockPos mobPos;
        public final BlockPos soundPos;
        public final String soundId; 

        public RaycastCacheKey(BlockPos mobPos, BlockPos soundPos, String soundId) {
            this.mobPos = mobPos;
            this.soundPos = soundPos;
            this.soundId = Objects.requireNonNull(soundId, "soundId cannot be null for RaycastCacheKey");
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RaycastCacheKey other)) return false;
            return mobPos.equals(other.mobPos) && 
                   soundPos.equals(other.soundPos) && 
                   soundId.equals(other.soundId); 
        }
        @Override
        public int hashCode() {
            return Objects.hash(mobPos, soundPos, soundId);
        }
    }
    private static final WeakHashMap<RaycastCacheKey, double[]> RAYCAST_CACHE = new WeakHashMap<>();


    private static boolean isCustomWool(BlockState state, Block block) {
        try {
            if (state.isIn(net.minecraft.registry.tag.BlockTags.WOOL)) return true;
        } catch (Throwable ignored) {}
        String id = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
        return id.contains("wool") || id.contains("carpet");
    }

    private static boolean isCustomThin(BlockState state, Block block) {
        String id = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
        return id.contains("pane") || id.contains("bars") || id.contains("trapdoor") || id.contains("carpet") || id.contains("fence") || id.contains("wall") || id.contains("slab") || id.contains("door");
    }

    private static boolean isCustomNonSolid(BlockState state, Block block) {
        if (state.isAir()) return true;
        String id = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
        return id.contains("leaves") || id.contains("flower") || id.contains("plant") || id.contains("torch") || id.contains("vine") || id.contains("grass") || id.contains("mushroom") || id.contains("kelp") || id.contains("coral") || id.contains("cactus") || id.contains("sugar_cane") || id.contains("ladder");
    }

    private static boolean isCustomSolid(BlockState state, Block block) {

        return !isCustomThin(state, block) && !isCustomNonSolid(state, block);
    }

    private static boolean isCustomLiquid(Block block) {
        if (block instanceof net.minecraft.block.FluidBlock) return true;
        String id = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
        return id.contains("water") || id.contains("lava");
    }

    public static double[] applyBlockMuffling(World level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId) {
        ThreadingChecks.warnIfOffServerThread(level, "SoundTracker.applyBlockMuffling");
        if (SoundAttractMod.CONFIG == null) {
            System.err.println("[SoundTracker] Config not loaded, cannot apply muffling.");
            return new double[]{origRange, origWeight}; 
        }
        String currentSoundId = soundId != null ? soundId : "unknown_muffling_sound";

        if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() &&
            !SoundAttractMod.CONFIG.soundIdWhitelist.contains(currentSoundId) &&
            !currentSoundId.startsWith("virtual_sound:") && 
            !currentSoundId.equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID.toString())) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                    "[SoundTracker] Skipped muffling for sound {} at {} -> {} due to whitelist",
                    currentSoundId, src, dst
                );
            }
        }

        RaycastCacheKey key = new RaycastCacheKey(dst, src, currentSoundId);
        double[] cached = RAYCAST_CACHE.get(key);
        if (cached != null) return cached;

        double range = origRange;
        double weight = origWeight;
        int x0 = src.getX(), y0 = src.getY(), z0 = src.getZ();
        int x1 = dst.getX(), y1 = dst.getY(), z1 = dst.getZ();
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0), sy = Integer.compare(y1, y0), sz = Integer.compare(z1, z0);
        
        int nMaxSteps = dx + dy + dz + 1; 
        int x = x0, y = y0, z = z0;
        
        int err_xy = dx - dy;
        int err_xz = dx - dz;

        int blocksMuffled = 0;
        int areaRadius = SoundAttractMod.CONFIG.mufflingAreaRadius;

        Vec3d startVec = Vec3d.ofCenter(src);
        Vec3d endVec = Vec3d.ofCenter(dst);
        Vec3d direction = endVec.subtract(startVec).normalize();
        double totalDistance = startVec.distanceTo(endVec);
        double step = 0.5; 
        for (double d = 0; d <= totalDistance && range > 0 && weight > 0; d += step) {
            Vec3d currentPoint = startVec.add(direction.multiply(d));
            BlockPos currentBlockPos = BlockPos.ofFloored(currentPoint);
            
            boolean muffledThisStep = false;
            for (int dxr = -areaRadius; dxr <= areaRadius && !muffledThisStep; dxr++) {
                for (int dyr = -areaRadius; dyr <= areaRadius && !muffledThisStep; dyr++) {
                    for (int dzr = -areaRadius; dzr <= areaRadius && !muffledThisStep; dzr++) {
                        BlockPos posToCheck = currentBlockPos.add(dxr, dyr, dzr);
                        if (d > step && posToCheck.equals(src)) continue; 
                        if (d < totalDistance - step && posToCheck.equals(dst)) continue;

                        if (!level.isChunkLoaded(posToCheck)) continue; 
                        BlockState state = level.getBlockState(posToCheck);
                        Block block = state.getBlock();
                        
                        if (block == Blocks.AIR || state.isAir()) continue;

                        if (isCustomWool(state, block) && SoundAttractMod.CONFIG.woolMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.woolBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.woolBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (isCustomThin(state, block) && SoundAttractMod.CONFIG.thinMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.thinBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.thinBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (isCustomNonSolid(state, block) && SoundAttractMod.CONFIG.nonSolidMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.nonSolidBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.nonSolidBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (isCustomSolid(state, block) && SoundAttractMod.CONFIG.solidMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.solidBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.solidBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (((state.getFluidState() != null && !state.getFluidState().isEmpty()) || isCustomLiquid(block)) 
                                   && SoundAttractMod.CONFIG.liquidMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.liquidBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.liquidBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        }
                    }
                }
            }
        }


        if (range < 0) range = 0;
        if (weight < 0) weight = 0;

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[BlockMuffling] soundId={}, src={}, dst={}, origRange={}, origWeight={}, muffledRange={}, muffledWeight={}, blocksMuffled={}",
                currentSoundId, src, dst, String.format("%.2f",origRange), String.format("%.2f",origWeight), String.format("%.2f",range), String.format("%.2f",weight), blocksMuffled
            );
        }
        double[] result = new double[]{range, weight};
        if (blocksMuffled > 0 || origRange == 0) {
             RAYCAST_CACHE.put(key, result);
        }
        return result;
    }

    private static int pruneIndex = 0;
    private static final int SOUNDS_PRUNED_PER_TICK = 1; 

    public static synchronized void pruneIrrelevantSounds(World level) {
        ThreadingChecks.warnIfOffServerThread(level, "SoundTracker.pruneIrrelevantSounds");
        if (RECENT_SOUNDS.isEmpty() || SoundAttractMod.CONFIG == null) return;
        
        int total = RECENT_SOUNDS.size();
        if (pruneIndex >= total) pruneIndex = 0;
        
        int prunedThisTick = 0;
        int checkedThisTick = 0;
        
        List<SoundRecord> soundsToPrune = new ArrayList<>();

        for (int i = 0; i < total && checkedThisTick < total && prunedThisTick < SOUNDS_PRUNED_PER_TICK; i++) {
            int currentIndex = (pruneIndex + i) % total;
            if (currentIndex >= RECENT_SOUNDS.size()) continue; 
            SoundRecord sound = RECENT_SOUNDS.get(currentIndex);
            checkedThisTick++;

            if (sound.ticksRemaining > SoundAttractMod.CONFIG.scanCooldownTicks) continue;

            boolean isRelevantToAnyMob = false;
            for (Long cellKey : sound.coveredCells) {

                int cellX = (int) (cellKey >> 32);
                int cellZ = (int) (cellKey & 0xFFFFFFFFL);
                int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
                if (partitionSize <= 0) partitionSize = 16;

                net.minecraft.util.math.Box checkBox = new net.minecraft.util.math.Box(
                    cellX * partitionSize, level.getBottomY(), cellZ * partitionSize,
                    (cellX + 1) * partitionSize, level.getTopY(), (cellZ + 1) * partitionSize
                );

                List<MobEntity> mobsInCell = level.getEntitiesByClass(MobEntity.class, checkBox, m -> m.isAlive());

                for (MobEntity mob : mobsInCell) {
                    if (!SoundAttractMod.CONFIG.attractedEntities.contains(Registries.ENTITY_TYPE.getId(mob.getType()).toString())) {
                        continue;
                    }
                    MobProfile profile = SoundAttractMod.CONFIG.getMatchingProfile(mob);
                    double effectiveRange = sound.range;
                    double effectiveWeight = sound.weight;
                    if (profile != null && sound.soundId != null) {
                        Identifier rl = Identifier.tryParse(sound.soundId);
                        if (rl != null) {
                            Optional<com.example.soundattract.config.SoundOverride> ov = profile.getSoundOverride(rl);
                            if (ov.isPresent()) {
                                effectiveRange = ov.get().getRange();
                                effectiveWeight = ov.get().getWeight();
                            }
                        }
                    }
                    double[] muffled = applyBlockMuffling(level, sound.pos, mob.getBlockPos(), effectiveRange, effectiveWeight, sound.soundId);
                    double finalRange = muffled[0];
                    if (mob.getBlockPos().getSquaredDistance(sound.pos) <= finalRange * finalRange) {
                        isRelevantToAnyMob = true;
                        break; 
                    }
                }
                if (isRelevantToAnyMob) break;
            }

            if (!isRelevantToAnyMob) {
                soundsToPrune.add(sound);
                prunedThisTick++;
            }
        }
        
        if (!soundsToPrune.isEmpty()) {
            RECENT_SOUNDS.removeAll(soundsToPrune);
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[SoundTracker] Pruned {} irrelevant sounds.", soundsToPrune.size());
            }
            updateSpatialSounds();
        }
        pruneIndex = (pruneIndex + checkedThisTick) % Math.max(1, RECENT_SOUNDS.size());
    }


    public static synchronized SoundRecord findNearestSound(
        World level,
        MobEntity mob,
        BlockPos mobPos,
        Vec3d mobEyePos
) {
        ThreadingChecks.warnIfOffServerThread(level, "SoundTracker.findNearestSound");
        if (SoundAttractMod.CONFIG == null) {
            System.err.println("[SoundTracker] Config not loaded, cannot find nearest sound.");
            return null;
        }

        String dimensionKey = level.getRegistryKey().getValue().toString();
        List<SoundRecord> currentSoundsSnapshot = new ArrayList<>(RECENT_SOUNDS);
        if (currentSoundsSnapshot.isEmpty()) {
            return null;
        }

        MobProfile profile = SoundAttractMod.CONFIG.getMatchingProfile(mob);
        SoundRecord bestSound = null;
        double highestComparisonWeight = -1.0;
        double closestDistSqrForBest = Double.MAX_VALUE;

        double noveltyBonusValue = SoundAttractMod.CONFIG.soundNoveltyBonusWeight;
        int noveltyTicks = SoundAttractMod.CONFIG.soundNoveltyTimeTicks;
        int maxLifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;

        for (SoundRecord r : currentSoundsSnapshot) {
            if (r == null || r.pos == null || !Objects.equals(r.dimensionKey, dimensionKey)) {
                continue;
            }

            String soundId = r.soundId != null ? r.soundId : "unknown_sound_in_recent_list";

            if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() &&
                !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundId) &&
                !soundId.startsWith("virtual_sound:") &&
                !soundId.equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID.toString())) {
                continue;
            }

            double effectiveInitialRange = r.range;
            double effectiveInitialWeight = r.weight;
            if (profile != null) {
                Identifier rl = Identifier.tryParse(soundId);
                if (rl != null) {
                    Optional<com.example.soundattract.config.SoundOverride> ov = profile.getSoundOverride(rl);
                    if (ov.isPresent()) {
                        effectiveInitialRange = ov.get().getRange();
                        effectiveInitialWeight = ov.get().getWeight();
                    }
                }
            }

            double[] muffled = applyBlockMuffling(level, r.pos, mobPos, effectiveInitialRange, effectiveInitialWeight, soundId);
            double muffledRange = muffled[0];
            double muffledWeight = muffled[1];
            double distSqr = mobPos.getSquaredDistance(r.pos);

            if (muffledWeight <= 0 || muffledRange <= 0 || distSqr > (muffledRange * muffledRange)) {
                continue;
            }

            double noveltyBonus = 0.0;
            if (noveltyBonusValue > 0 && r.ticksRemaining > (maxLifetime - noveltyTicks)) {
                noveltyBonus = noveltyBonusValue;
            }

            double finalComparisonWeight = muffledWeight + noveltyBonus;

            if (finalComparisonWeight > highestComparisonWeight || (Math.abs(finalComparisonWeight - highestComparisonWeight) < 0.001 && distSqr < closestDistSqrForBest)) {
                highestComparisonWeight = finalComparisonWeight;
                closestDistSqrForBest = distSqr;
                bestSound = new SoundRecord(r.sound, soundId, r.pos, r.ticksRemaining, r.dimensionKey, muffledRange, muffledWeight);
            }
        }

        return bestSound;
    }


    private static final Map<java.util.UUID, Long> LAST_SUBMIT_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static int getSubmitCooldownTicks() {
        int ticks = 10;
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.asyncSubmitCooldownTicks > 0) {
            ticks = SoundAttractMod.CONFIG.asyncSubmitCooldownTicks;
        }
        return ticks;
    }

    private static final class CandidateSnapshot {
        final BlockPos pos;
        final String soundId;
        final double muffledRange;
        final double muffledWeight;
        final double distSqr;
        final int ticksRemaining;
        CandidateSnapshot(BlockPos pos, String soundId, double muffledRange, double muffledWeight, double distSqr, int ticksRemaining) {
            this.pos = pos;
            this.soundId = soundId;
            this.muffledRange = muffledRange;
            this.muffledWeight = muffledWeight;
            this.distSqr = distSqr;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static final class SoundScoreRequestSnapshot {
        final java.util.UUID mobUuid;
        final String dimensionKey;
        final BlockPos mobPos;
        final int noveltyTicks;
        final double noveltyBonusValue;
        final int maxLifetime;
        final java.util.List<CandidateSnapshot> candidates;
        SoundScoreRequestSnapshot(java.util.UUID mobUuid, String dimensionKey, BlockPos mobPos,
                                  int noveltyTicks, double noveltyBonusValue, int maxLifetime,
                                  java.util.List<CandidateSnapshot> candidates) {
            this.mobUuid = mobUuid;
            this.dimensionKey = dimensionKey;
            this.mobPos = mobPos;
            this.noveltyTicks = noveltyTicks;
            this.noveltyBonusValue = noveltyBonusValue;
            this.maxLifetime = maxLifetime;
            this.candidates = candidates;
        }
    }

    public static void submitAsyncSoundScore(World level, MobEntity mob, BlockPos mobPos) {
        ThreadingChecks.warnIfOffServerThread(level, "SoundTracker.submitAsyncSoundScore");
        if (level == null || mob == null || SoundAttractMod.CONFIG == null) return;
        if (!(level instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;

        long nowTick = serverWorld.getTime();
        Long last = LAST_SUBMIT_TICK.get(mob.getUuid());
        int cooldown = getSubmitCooldownTicks();
        if (last != null && (nowTick - last) < cooldown) return;
        LAST_SUBMIT_TICK.put(mob.getUuid(), nowTick);

        String dimensionKey = level.getRegistryKey().getValue().toString();
        java.util.List<SoundRecord> current = new java.util.ArrayList<>(RECENT_SOUNDS);
        if (current.isEmpty()) return;

        MobProfile profile = SoundAttractMod.CONFIG.getMatchingProfile(mob);
        java.util.List<CandidateSnapshot> cands = new java.util.ArrayList<>(current.size());

        for (SoundRecord r : current) {
            if (r == null || r.pos == null || !java.util.Objects.equals(r.dimensionKey, dimensionKey)) continue;
            String soundId = (r.soundId != null) ? r.soundId : "unknown_sound_in_recent_list";
            if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() &&
                !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundId) &&
                !soundId.startsWith("virtual_sound:") &&
                !soundId.equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID.toString())) {
                continue;
            }

            double effRange = r.range;
            double effWeight = r.weight;
            if (profile != null) {
                Identifier rl = Identifier.tryParse(soundId);
                if (rl != null) {
                    java.util.Optional<com.example.soundattract.config.SoundOverride> ov = profile.getSoundOverride(rl);
                    if (ov.isPresent()) {
                        effRange = ov.get().getRange();
                        effWeight = ov.get().getWeight();
                    }
                }
            }

            double[] muffled = applyBlockMuffling(level, r.pos, mobPos, effRange, effWeight, soundId);
            double mr = muffled[0];
            double mw = muffled[1];
            if (mw <= 0 || mr <= 0) continue;
            double distSqr = mobPos.getSquaredDistance(r.pos);
            if (distSqr > mr * mr) continue;
            cands.add(new CandidateSnapshot(r.pos, soundId, mr, mw, distSqr, r.ticksRemaining));
        }

        if (cands.isEmpty()) return;

        int noveltyTicks = SoundAttractMod.CONFIG.soundNoveltyTimeTicks;
        double noveltyBonus = SoundAttractMod.CONFIG.soundNoveltyBonusWeight;
        int maxLifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;
        SoundScoreRequestSnapshot req = new SoundScoreRequestSnapshot(mob.getUuid(), dimensionKey, mobPos, noveltyTicks, noveltyBonus, maxLifetime, java.util.Collections.unmodifiableList(cands));
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.debug("[SoundTracker] Submitting async sound score for mob {} in dim {} with {} candidates", mob.getUuid(), dimensionKey, cands.size());
        }

        com.example.soundattract.util.WorkerScheduler.submitSoundTask(() -> {
            CandidateSnapshot best = null;
            double bestWeight = -1.0;
            double bestDist = Double.MAX_VALUE;
            for (CandidateSnapshot c : req.candidates) {
                double bonus = (req.noveltyBonusValue > 0 && c.ticksRemaining > (req.maxLifetime - req.noveltyTicks)) ? req.noveltyBonusValue : 0.0;
                double w = c.muffledWeight + bonus;
                if (w > bestWeight || (Math.abs(w - bestWeight) < 0.001 && c.distSqr < bestDist)) {
                    bestWeight = w;
                    bestDist = c.distSqr;
                    best = c;
                }
            }
            com.example.soundattract.SoundTracker.SoundRecord bestRecord = null;
            if (best != null) {
                bestRecord = new com.example.soundattract.SoundTracker.SoundRecord(
                        null,
                        best.soundId,
                        best.pos,
                        Math.min(req.maxLifetime, Math.max(1, best.ticksRemaining)),
                        req.dimensionKey,
                        best.muffledRange,
                        best.muffledWeight
                );
            }
            return new com.example.soundattract.util.WorkerScheduler.SoundScoreResult(req.mobUuid, req.dimensionKey, bestRecord);
        });
    }

    public static java.util.List<net.minecraft.entity.mob.MobEntity> getMobsForSound(
        World world, 
        SoundRecord sound,
        java.util.function.Predicate<MobEntity> filter 
) {
        ThreadingChecks.warnIfOffServerThread(world, "SoundTracker.getMobsForSound");
        java.util.List<MobEntity> result = new java.util.ArrayList<>();
        if (sound == null || world == null || SoundAttractMod.CONFIG == null) return result;
        
        double queryRange = sound.range; 
        int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
        if (partitionSize <= 0) partitionSize = 16;

        int cellSearchRadius = (int) Math.ceil(queryRange / partitionSize);
        
        int centerCellX = sound.pos.getX() / partitionSize;
        int centerCellZ = sound.pos.getZ() / partitionSize;
        
        int minY = world.getBottomY();
        int maxY = world.getTopY();

        for (int dx = -cellSearchRadius; dx <= cellSearchRadius; dx++) {
            for (int dz = -cellSearchRadius; dz <= cellSearchRadius; dz++) {
                int currentCellX = centerCellX + dx;
                int currentCellZ = centerCellZ + dz;

                net.minecraft.util.math.Box cellBox = new net.minecraft.util.math.Box(
                        currentCellX * partitionSize, minY, currentCellZ * partitionSize,
                        (currentCellX + 1) * partitionSize, maxY, (currentCellZ + 1) * partitionSize
                );

                List<MobEntity> mobsInCell = world.getEntitiesByClass(
                        MobEntity.class,
                        cellBox,
                        filter 
                );

                for (MobEntity mob : mobsInCell) {

                    if (mob.getBlockPos().getSquaredDistance(sound.pos) <= queryRange * queryRange) {
                        result.add(mob);
                    }
                }
            }
        }
        return result;
    }

    public static synchronized java.util.List<SoundRecord> getRecentSounds() {
        return new ArrayList<>(RECENT_SOUNDS);
    }
}