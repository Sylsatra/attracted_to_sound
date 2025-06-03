package com.example.soundattract;

import com.example.soundattract.config.MobProfile;
import com.example.soundattract.config.SoundAttractConfigData; // Not directly used, but CONFIG is
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.SpatialPartitioner;

import net.minecraft.util.math.BlockPos;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries; // Not directly used but Registries.BLOCK is
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
import java.util.Objects; // Added for Objects.equals

public class SoundTracker {

    public static class SoundRecord {
        public final SoundEvent sound; // Can be null for virtual sounds or if only ID is known
        public final String soundId;   // The string identifier of the sound
        public final BlockPos pos;
        public int ticksRemaining;
        public final String dimensionKey;
        public final double range;     // This should store the *effective* range after profiles/muffling if used by AttractionGoal
        public final double weight;    // This should store the *effective* weight after profiles/muffling if used by AttractionGoal
        public final java.util.Set<Long> coveredCells = new java.util.HashSet<>();
        
        // DEFAULT_TICKS_REMAINING will be initialized when SoundAttractMod.CONFIG is loaded.
        // Ensure SoundAttractMod.CONFIG is initialized before this class is heavily used.
        public static final int DEFAULT_TICKS_REMAINING = SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.soundLifetimeTicks : 200; // Fallback if config not loaded

        // Constructor primarily used internally or when soundId is definitively known
        public SoundRecord(SoundEvent sound, String soundId, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this.sound = sound;
            this.soundId = soundId; // Ensure this is never null if possible, or handle nulls gracefully
            this.pos = pos;
            this.ticksRemaining = lifetime;
            this.dimensionKey = dimensionKey;
            this.range = range;
            this.weight = weight;
        }

        // Convenience constructor if SoundEvent is available
        public SoundRecord(SoundEvent sound, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this(sound, 
                 (sound != null && sound.getId() != null ? sound.getId().toString() : "unknown_sound_event_id"), // Provide a fallback
                 pos, lifetime, dimensionKey, range, weight);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SoundRecord other)) return false; // Pattern matching for instanceof
            return Objects.equals(soundId, other.soundId) && // Use Objects.equals for null-safety
                   Objects.equals(pos, other.pos) &&
                   Objects.equals(dimensionKey, other.dimensionKey) &&
                   Double.compare(weight, other.weight) == 0 && // Prefer Double.compare for doubles
                   Double.compare(range, other.range) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(soundId, pos, dimensionKey, range, weight); // Use Objects.hash
        }
    }

    private static int getGridRadiusForRange(double range, int partitionSize) {
        if (range < 8) return 1;
        if (partitionSize <= 0) partitionSize = 16; // Safety
        for (int i = 1; i <= 20; i++) { // Consider making 20 configurable or dynamic
            if (range < 8 + (double)partitionSize * i) { // Use partitionSize in calculation
                return i + 1;
            }
        }
        return 21; // Max radius
    }


    public static java.util.Set<Long> getCoveredCells(BlockPos pos, double range, int partitionSize) {
        java.util.Set<Long> cells = new java.util.HashSet<>();
        if (partitionSize <= 0) partitionSize = 16; // Safety check
        int gridRadius = getGridRadiusForRange(range, partitionSize);
        int baseX = pos.getX() / partitionSize;
        int baseZ = pos.getZ() / partitionSize;
        for (int dx = -gridRadius; dx <= gridRadius; dx++) {
            for (int dz = -gridRadius; dz <= gridRadius; dz++) {
                // Optional: Add a circular check if precise coverage is needed
                // double distToCellCorner = ...; if (distToCellCorner > range) continue;
                int cellX = baseX + dx;
                int cellZ = baseZ + dz;
                long key = (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);
                cells.add(key);
            }
        }
        return cells;
    }

    public static class VirtualSoundRecord extends SoundRecord {
        public final UUID sourcePlayer; // Can be null
        public final String animationClass; // Can be null

        public VirtualSoundRecord(BlockPos pos, int lifetime, String dimensionKey, double range, double weight, UUID sourcePlayer, String animationClass) {
            // For virtual sounds, soundId might be a predefined string like "virtual_player_action"
            super(null, "virtual_sound:" + (animationClass != null ? animationClass : "unknown"), pos, lifetime, dimensionKey, range, weight);
            this.sourcePlayer = sourcePlayer;
            this.animationClass = animationClass;
        }
    }

    // Use ConcurrentLinkedDeque if accessed by multiple threads, or ensure synchronization
    public static final List<SoundRecord> RECENT_SOUNDS = Collections.synchronizedList(new ArrayList<>());
    // GRID_SIZE seems unused, consider removing if spatialPartitionSize from config is used everywhere
    // private static final int GRID_SIZE = 16; 
    private static final Map<String, Map<Long, List<SoundRecord>>> SPATIAL_SOUNDS = Collections.synchronizedMap(new HashMap<>());
    // LARGE_SOUND_RANGE_THRESHOLD seems unused, consider removing
    // private static final double LARGE_SOUND_RANGE_THRESHOLD = 16.0;

    // getNearbySounds seems unused, replaced by findNearestSound. Consider removing.
    /*
    private static List<SoundRecord> getNearbySounds(String dim, BlockPos pos) {
        Map<Long, List<SoundRecord>> dimMap = SPATIAL_SOUNDS.get(dim);
        if (dimMap == null) return Collections.emptyList();
        List<SoundRecord> result = new ArrayList<>();

        int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
        long mobCell = SpatialPartitioner.getKey(pos, partitionSize);

        // This is inefficient. findNearestSound iterates RECENT_SOUNDS directly.
        // The purpose of SPATIAL_SOUNDS is to optimize findNearestSound if it were to use it.
        for (List<SoundRecord> soundList : dimMap.values()) {
            for (SoundRecord r : soundList) {
                if (r.coveredCells.contains(mobCell)) {
                    result.add(r);
                }
            }
        }
        return result;
    }
    */

    private static synchronized void updateSpatialSounds() { // Ensure this is synchronized if RECENT_SOUNDS is modified
        SPATIAL_SOUNDS.clear(); // This needs to be carefully managed if sounds are added/removed frequently
        if (SoundAttractMod.CONFIG == null) return; // Config might not be loaded yet
        int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
        if (partitionSize <= 0) partitionSize = 16; // Default safety

        List<SoundRecord> currentSounds = new ArrayList<>(RECENT_SOUNDS); // Iterate over a snapshot

        for (SoundRecord r : currentSounds) {
            r.coveredCells.clear(); // Clear old cells
            java.util.Set<Long> cells = getCoveredCells(r.pos, r.range, partitionSize);
            r.coveredCells.addAll(cells); // Update with new cells

            Map<Long, List<SoundRecord>> dimMap = SPATIAL_SOUNDS.computeIfAbsent(r.dimensionKey, d -> Collections.synchronizedMap(new HashMap<>()));
            for (Long key : cells) {
                // Ensure list within map is also synchronized if accessed by multiple threads
                dimMap.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add(r);
            }
        }
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime, String explicitSoundId) {
        if (SoundAttractMod.CONFIG == null) { // Config not loaded, cannot proceed
            System.err.println("[SoundTracker] Config not loaded, cannot add sound."); // Use a proper logger if available early
            return;
        }

        String soundIdToUse = explicitSoundId;
        if (soundIdToUse == null && se != null && se.getId() != null) {
            soundIdToUse = se.getId().toString();
        }
        // If soundIdToUse is still null here, it's an issue. Assign a placeholder or log error.
        if (soundIdToUse == null) {
            soundIdToUse = "unknown_sound_id_at_" + pos.toShortString(); // Fallback
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

        // Refresh Logic: Remove any existing sound with the same ID, position, and dimension.
        // This ensures the newest instance (with reset lifetime) is used.
        boolean removed = RECENT_SOUNDS.removeIf(r -> 
            r.pos.equals(pos) &&
            Objects.equals(r.soundId, finalSoundId) && // Compare soundId
            Objects.equals(r.dimensionKey, dimensionKey)
        );

        if (removed && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Refreshed sound (removed old instance): {} at {} (dim: {})",
                finalSoundId, pos, dimensionKey
            );
        }

        // Add the new sound record
        RECENT_SOUNDS.add(new SoundRecord(se, finalSoundId, pos, lifetime, dimensionKey, range, weight));
        
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Registered sound {} at {} (dim: {}), range={}, weight={}, lifetime={}",
                finalSoundId, pos, dimensionKey, String.format("%.2f",range), String.format("%.2f",weight), lifetime
            );
        }
        updateSpatialSounds(); // Update spatial map after any modification to RECENT_SOUNDS
    }

    // Convenience overload
    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        addSound(se, pos, dimensionKey, range, weight, lifetime, null);
    }

    // Convenience overload with default range/weight
    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        if (SoundAttractMod.CONFIG == null) {
             System.err.println("[SoundTracker] Config not loaded for default lifetime sound.");
             return;
        }
        int lifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;
        // Define default range/weight or make them configurable
        addSound(se, pos, dimensionKey, 16.0, 1.0, lifetime); 
    }

    public static synchronized void addVirtualSound(BlockPos pos, String dimensionKey, double range, double weight, int lifetime, UUID sourcePlayer, String animationClass) {
        if (SoundAttractMod.CONFIG == null) {
             System.err.println("[SoundTracker] Config not loaded, cannot add virtual sound.");
             return;
        }

        // Construct the complete virtualSoundId first
        String constructedVirtualSoundId = "virtual_sound:" + (animationClass != null ? animationClass : "player_action");
        if (sourcePlayer != null) {
            String playerPart = sourcePlayer.toString();
            // Ensure substring is safe if playerPart is shorter than 8
            constructedVirtualSoundId += ":" + (playerPart.length() > 8 ? playerPart.substring(0, 8) : playerPart);
        }
        // Now, make it final (or it's already effectively final for the lambda)
        final String finalVirtualSoundId = constructedVirtualSoundId;


        // Refresh Logic for virtual sounds
        boolean removed = RECENT_SOUNDS.removeIf(r -> 
            r.pos.equals(pos) &&
            Objects.equals(r.soundId, finalVirtualSoundId) && // Use the final generated virtualSoundId
            Objects.equals(r.dimensionKey, dimensionKey)
        );
         if (removed && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[SoundTracker] Refreshed virtual sound (removed old instance): {} at {} (dim: {})",
                finalVirtualSoundId, pos, dimensionKey
            );
        }

        // The VirtualSoundRecord constructor itself will generate its soundId.
        // We pass null for the explicit soundId parameter to SoundRecord's constructor
        // as VirtualSoundRecord handles its own ID generation.
        RECENT_SOUNDS.add(new VirtualSoundRecord(pos, lifetime, dimensionKey, range, weight, sourcePlayer, animationClass));
        
        if (SoundAttractMod.CONFIG.debugLogging) {
            // For logging, we use the ID that was used for the removeIf check, 
            // assuming VirtualSoundRecord's internal ID generation matches this logic.
            // Or, if VirtualSoundRecord has a getSoundId() method, that would be more robust for logging the *actual* ID.
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
        if (soundsChanged) { // Only update spatial sounds if something was actually removed
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
        public final String soundId; // Should be non-null

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
                   soundId.equals(other.soundId); // soundId is guaranteed non-null
        }
        @Override
        public int hashCode() {
            return Objects.hash(mobPos, soundPos, soundId);
        }
    }
    // Consider using a Guava cache with expiration if this grows too large or causes issues.
    private static final WeakHashMap<RaycastCacheKey, double[]> RAYCAST_CACHE = new WeakHashMap<>();
    // RAYCAST_CACHE_DIST_THRESHOLD seems unused
    // private static final int RAYCAST_CACHE_DIST_THRESHOLD = 1;
    // MAX_RECENT_SOUNDS seems unused, RECENT_SOUNDS is unbounded
    // private static final int MAX_RECENT_SOUNDS = 32; 

    public static double[] applyBlockMuffling(World level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId) {
        if (SoundAttractMod.CONFIG == null) {
            System.err.println("[SoundTracker] Config not loaded, cannot apply muffling.");
            return new double[]{origRange, origWeight}; // Return original if no config
        }
        String currentSoundId = soundId != null ? soundId : "unknown_muffling_sound";

        if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() &&
            !SoundAttractMod.CONFIG.soundIdWhitelist.contains(currentSoundId) &&
            !currentSoundId.startsWith("virtual_sound:") && // Allow virtual sounds to be muffled
            !currentSoundId.equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID.toString())) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                    "[SoundTracker] Skipped muffling for sound {} at {} -> {} due to whitelist",
                    currentSoundId, src, dst
                );
            }
            // Return 0,0 to indicate it's not heard due to whitelist, or original if whitelist is only for adding sounds.
            // For muffling, if it's not on whitelist, it likely means it wasn't added, so muffling is moot.
            // However, if it *was* added (e.g. voice chat or virtual), then muffling should proceed.
            // The check above should be sufficient. If it passes, proceed with muffling.
            // If it's not on the whitelist and NOT a special case, it shouldn't be in RECENT_SOUNDS anyway.
            // Let's assume if this function is called, the sound is considered for attraction.
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
        
        int nMaxSteps = dx + dy + dz + 1; // Max steps in a 3D Bresenham line
        int x = x0, y = y0, z = z0;
        
        // Bresenham's line algorithm variables
        int err_xy = dx - dy;
        int err_xz = dx - dz;
        // For 3D, need to adjust. Simpler is often better for raycasting blocks.
        // Let's use a simpler iterative approach based on dominant axis.

        int blocksMuffled = 0;
        int areaRadius = SoundAttractMod.CONFIG.mufflingAreaRadius;

        // Simplified raycasting: Iterate along the line, checking blocks.
        // This is a basic DDA-like approach. For more accuracy, Bresenham or Voxel Traversal.
        Vec3d startVec = Vec3d.ofCenter(src);
        Vec3d endVec = Vec3d.ofCenter(dst);
        Vec3d direction = endVec.subtract(startVec).normalize();
        double totalDistance = startVec.distanceTo(endVec);
        double step = 0.5; // Check every half block

        for (double d = 0; d <= totalDistance && range > 0 && weight > 0; d += step) {
            Vec3d currentPoint = startVec.add(direction.multiply(d));
            BlockPos currentBlockPos = BlockPos.ofFloored(currentPoint);
            
            boolean muffledThisStep = false;
            for (int dxr = -areaRadius; dxr <= areaRadius && !muffledThisStep; dxr++) {
                for (int dyr = -areaRadius; dyr <= areaRadius && !muffledThisStep; dyr++) {
                    for (int dzr = -areaRadius; dzr <= areaRadius && !muffledThisStep; dzr++) {
                        BlockPos posToCheck = currentBlockPos.add(dxr, dyr, dzr);
                        // Avoid re-checking the exact start/end points if areaRadius is 0 unless they are the currentBlockPos
                        if (d > step && posToCheck.equals(src)) continue; 
                        if (d < totalDistance - step && posToCheck.equals(dst)) continue;

                        if (!level.isChunkLoaded(posToCheck)) continue; // Skip unloaded chunks
                        BlockState state = level.getBlockState(posToCheck);
                        Block block = state.getBlock();
                        
                        if (block == Blocks.AIR || state.isAir()) continue; // Skip air blocks

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
        if (blocksMuffled > 0 || origRange == 0) { // Cache if actually muffled or if it was a zero-range sound (to cache the 0)
             RAYCAST_CACHE.put(key, result);
        }
        return result;
    }

    private static boolean isBlockInConfigList(BlockState state, Block block, java.util.List<String> configList) {
        if (configList == null || configList.isEmpty()) return false;
        Identifier id = Registries.BLOCK.getId(block); // Use the correct registry
        if (id != null && configList.contains(id.toString())) return true;
        for (String entry : configList) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                TagKey<Block> tag = TagKey.of(net.minecraft.registry.RegistryKeys.BLOCK, new Identifier(tagName));
                if (state.isIn(tag)) return true;
            }
        }
        return false;
    }
    private static boolean isCustomWool(BlockState state, Block block) {
        if (SoundAttractMod.CONFIG == null) return state.isIn(BlockTags.WOOL);
        return isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customWoolBlocks) || state.isIn(BlockTags.WOOL);
    }
    private static boolean isCustomSolid(BlockState state, Block block) {
         if (SoundAttractMod.CONFIG == null) return state.isSolid();
        return isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customSolidBlocks) || state.isSolid();
    }
    private static boolean isCustomNonSolid(BlockState state, Block block) {
         if (SoundAttractMod.CONFIG == null) return !state.isSolid();
        // This logic is tricky: if it's not solid, it IS non-solid.
        // The config list should be for blocks that are non-solid but NOT already covered by !state.isSolid()
        // or for blocks that ARE solid but you want to treat as non-solid for muffling.
        // Current: if in custom list OR it's generally not solid.
        return isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customNonSolidBlocks) || !state.isSolid();
    }
    private static boolean isCustomThin(BlockState state, Block block) {
        if (SoundAttractMod.CONFIG == null) return false; // No config, can't check custom
        if (isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customThinBlocks)) return true;
        
        Identifier id = Registries.BLOCK.getId(block);
        if (id == null) return false;
        String path = id.getPath();
        // This list could be made configurable too
        return path.contains("pane") || path.contains("iron_bars") || path.contains("painting") ||
               path.contains("fence") || path.contains("trapdoor") || path.contains("door") ||
               path.contains("ladder") || path.contains("scaffolding") || path.contains("rail") ||
               path.contains("chain"); // Added chain
    }

    private static boolean isCustomLiquid(Block block) {
        if (SoundAttractMod.CONFIG == null) return false;
        String blockId = Registries.BLOCK.getId(block).toString();
        return SoundAttractMod.CONFIG.customLiquidBlocks.contains(blockId);
    }

    // pruneIrrelevantSounds seems complex and potentially performance-intensive due to iterating all mobs in chunks.
    // Consider if this is truly necessary or if the natural tick-down of sounds is sufficient.
    // If kept, ensure its performance impact is acceptable.
    private static int pruneIndex = 0;
    private static final int SOUNDS_PRUNED_PER_TICK = 5; // Make configurable?

    public static synchronized void pruneIrrelevantSounds(World level) {
        if (RECENT_SOUNDS.isEmpty() || SoundAttractMod.CONFIG == null) return;
        
        int total = RECENT_SOUNDS.size();
        if (pruneIndex >= total) pruneIndex = 0;
        
        int prunedThisTick = 0;
        int checkedThisTick = 0;
        
        // Iterate over a snapshot to avoid ConcurrentModificationException if RECENT_SOUNDS is modified elsewhere
        // (though it shouldn't be if this method is also synchronized and called from a single thread like server tick)
        List<SoundRecord> soundsToPrune = new ArrayList<>();

        for (int i = 0; i < total && checkedThisTick < total && prunedThisTick < SOUNDS_PRUNED_PER_TICK; i++) {
            int currentIndex = (pruneIndex + i) % total;
            if (currentIndex >= RECENT_SOUNDS.size()) continue; // Should not happen with snapshot, but safety
            SoundRecord sound = RECENT_SOUNDS.get(currentIndex);
            checkedThisTick++;

            // Only consider pruning sounds that are very old (e.g., less than 5 ticks left)
            // or sounds that have a very small effective range after potential initial muffling.
            if (sound.ticksRemaining > SoundAttractMod.CONFIG.scanCooldownTicks) continue; // Don't prune sounds likely to be active

            boolean isRelevantToAnyMob = false;
            // Check against mobs in the cells this sound covers
            for (Long cellKey : sound.coveredCells) {
                // This requires access to mobs per cell, which might be in SpatialPartitionModule
                // For simplicity, if SpatialPartitionModule is not directly usable here, we might need a broader check
                // or this pruning needs to be done where mob lists per cell are available.
                // Let's assume a broader check for now if direct cell mob access isn't easy.
                
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
                        continue; // Skip mobs not attracted by this mod
                    }
                    // Re-evaluate the sound for this specific mob (profiles + muffling)
                    // This is essentially what findNearestSound does for one sound-mob pair
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
            updateSpatialSounds(); // Update spatial map if sounds were removed
        }
        pruneIndex = (pruneIndex + checkedThisTick) % Math.max(1, RECENT_SOUNDS.size());
    }


    public static synchronized SoundRecord findNearestSound(
            World level,
            MobEntity mob,
            BlockPos mobPos,
            Vec3d mobEyePos // mobEyePos is not used in current logic, but kept for signature
    ) {
        if (SoundAttractMod.CONFIG == null) {
            System.err.println("[SoundTracker] Config not loaded, cannot find nearest sound.");
            return null;
        }
        String dimensionKey = level.getRegistryKey().getValue().toString();
        MobProfile profile = SoundAttractMod.CONFIG.getMatchingProfile(mob);

        SoundRecord bestFinalSoundRecord = null;
        double highestEffectiveWeight = -1.0;
        double closestDistSqrForBestWeight = Double.MAX_VALUE;

        // Iterate over a snapshot of RECENT_SOUNDS to avoid ConcurrentModificationException
        // if sounds are added/removed by other threads during this iteration.
        List<SoundRecord> currentSoundsSnapshot = new ArrayList<>(RECENT_SOUNDS);

        for (SoundRecord r : currentSoundsSnapshot) {
            if (!Objects.equals(r.dimensionKey, dimensionKey)) {
                continue;
            }

            // soundId should ideally not be null in SoundRecord.
            // If it can be, ensure null checks or use a placeholder.
            String soundId = r.soundId != null ? r.soundId : "unknown_sound_in_recent_list";

            if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() &&
                !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundId) &&
                !soundId.startsWith("virtual_sound:") && // Allow virtual sounds
                !soundId.equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID.toString())) {
                continue;
            }

            double distSqrToMob = mobPos.getSquaredDistance(r.pos);

            // Initial broad phase check using raw range (before muffling/profiles)
            // This might be too aggressive if muffling severely reduces range.
            // Consider if this check is still beneficial or if we should always muffle.
            // For now, keeping it as a potential quick filter.
            if (distSqrToMob > r.range * r.range * 4) { // x4 to be generous before muffling
                // A sound very far outside its raw range is unlikely to be heard after muffling.
                // However, profiles can *increase* range. So this might be risky.
                // Let's remove this aggressive pre-filter for now and rely on post-muffling check.
                // continue; 
            }

            double baseRange = r.range;
            double baseWeight = r.weight;

            if (profile != null && soundId != null) {
                Identifier rl = Identifier.tryParse(soundId); // soundId could be "unknown..."
                if (rl != null) { // only proceed if soundId is a valid Identifier
                    Optional<com.example.soundattract.config.SoundOverride> ov = profile.getSoundOverride(rl);
                    if (ov.isPresent()) {
                        baseRange = ov.get().getRange();
                        baseWeight = ov.get().getWeight();
                    }
                }
            }

            double[] muffled = applyBlockMuffling(level, r.pos, mobPos, baseRange, baseWeight, soundId);
            double effectiveMuffledRange = muffled[0];
            double effectiveMuffledWeight = muffled[1];

            if (effectiveMuffledWeight <= 0 || effectiveMuffledRange <= 0) { // No attraction if no weight or range
                continue;
            }

            if (distSqrToMob <= effectiveMuffledRange * effectiveMuffledRange) {
                if (effectiveMuffledWeight > highestEffectiveWeight) {
                    highestEffectiveWeight = effectiveMuffledWeight;
                    closestDistSqrForBestWeight = distSqrToMob;
                    // Create a new record for the return, reflecting the effective properties for *this* mob
                    bestFinalSoundRecord = new SoundRecord(r.sound, soundId, r.pos, r.ticksRemaining, r.dimensionKey, effectiveMuffledRange, effectiveMuffledWeight);
                } else if (Math.abs(effectiveMuffledWeight - highestEffectiveWeight) < 0.001 && distSqrToMob < closestDistSqrForBestWeight) {
                    closestDistSqrForBestWeight = distSqrToMob;
                    bestFinalSoundRecord = new SoundRecord(r.sound, soundId, r.pos, r.ticksRemaining, r.dimensionKey, effectiveMuffledRange, effectiveMuffledWeight);
                }
            }
        }
        return bestFinalSoundRecord;
    }

    public static java.util.List<net.minecraft.entity.mob.MobEntity> getMobsForSound(
            World world, // Changed from net.minecraft.world.World for consistency
            SoundRecord sound,
            java.util.function.Predicate<MobEntity> filter // Changed from net.minecraft.entity.mob.MobEntity
    ) {
        java.util.List<MobEntity> result = new java.util.ArrayList<>();
        if (sound == null || world == null || SoundAttractMod.CONFIG == null) return result;
        
        // Use sound's effective range if available, otherwise raw range
        double queryRange = sound.range; // This range in SoundRecord should ideally be the one this mob would hear it at
        int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
        if (partitionSize <= 0) partitionSize = 16;

        // Determine search radius in terms of cells based on sound's range
        int cellSearchRadius = (int) Math.ceil(queryRange / partitionSize);
        
        int centerCellX = sound.pos.getX() / partitionSize;
        int centerCellZ = sound.pos.getZ() / partitionSize;
        
        int minY = world.getBottomY();
        int maxY = world.getTopY();

        for (int dx = -cellSearchRadius; dx <= cellSearchRadius; dx++) {
            for (int dz = -cellSearchRadius; dz <= cellSearchRadius; dz++) {
                int currentCellX = centerCellX + dx;
                int currentCellZ = centerCellZ + dz;

                // Define the bounding box for the current cell
                net.minecraft.util.math.Box cellBox = new net.minecraft.util.math.Box(
                        currentCellX * partitionSize, minY, currentCellZ * partitionSize,
                        (currentCellX + 1) * partitionSize, maxY, (currentCellZ + 1) * partitionSize
                );

                // Get mobs in this cell that match the filter
                List<MobEntity> mobsInCell = world.getEntitiesByClass(
                        MobEntity.class,
                        cellBox,
                        filter // Apply the provided filter
                );

                for (MobEntity mob : mobsInCell) {
                    // Final precise distance check against the sound's actual effective range for *this* mob
                    // This is tricky because 'sound.range' here is the one stored, which might be raw
                    // or an effective one from when it was found by another mob.
                    // Ideally, we'd re-evaluate the sound for *this* mob here.
                    // For now, using the stored sound.range as a proxy.
                    if (mob.getBlockPos().getSquaredDistance(sound.pos) <= queryRange * queryRange) {
                        result.add(mob);
                    }
                }
            }
        }
        return result;
    }

    public static synchronized java.util.List<SoundRecord> getRecentSounds() {
        // Return a copy to prevent modification issues if the caller iterates and modifies
        return new ArrayList<>(RECENT_SOUNDS);
    }
}