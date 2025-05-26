package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfigData;
import com.example.soundattract.SoundAttractMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.tag.TagKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

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
        this(sound, sound != null && sound.getId() != null ? sound.getId().toString() : null, pos, lifetime, dimensionKey, range, weight);
    }
}

private static int getGridRadiusForRange(double range, int partitionSize) {
    if (range < 8) return 1;
    for (int i = 1; i <= 20; i++) {
        if (range < 8 + 16 * i) {
            return i + 1;
        }
    }
    return 21;
}

public static java.util.Set<Long> getCoveredCells(BlockPos pos, double range, int partitionSize) {
    java.util.Set<Long> cells = new java.util.HashSet<>();
    int gridRadius = getGridRadiusForRange(range, partitionSize);
    int baseX = pos.getX() / partitionSize;
    int baseZ = pos.getZ() / partitionSize;
    for (int dx = -gridRadius; dx <= gridRadius; dx++) {
        for (int dz = -gridRadius; dz <= gridRadius; dz++) {
            int cellX = baseX + dx;
            int cellZ = baseZ + dz;
            long key = (((long)cellX) << 32) | (cellZ & 0xFFFFFFFFL);
            cells.add(key);
        }
    }
    return cells;
}

    public static class VirtualSoundRecord extends SoundRecord {
        public final UUID sourcePlayer;
        public final String animationClass;
        public VirtualSoundRecord(BlockPos pos, int lifetime, String dimensionKey, double range, double weight, UUID sourcePlayer, String animationClass) {
            super(null, null, pos, lifetime, dimensionKey, range, weight);
            this.sourcePlayer = sourcePlayer;
            this.animationClass = animationClass;
        }
    }

    public static final List<SoundRecord> RECENT_SOUNDS = new ArrayList<>();

    private static final int GRID_SIZE = 16; 
    private static final Map<String, Map<Long, List<SoundRecord>>> SPATIAL_SOUNDS = new HashMap<>();


    private static final double LARGE_SOUND_RANGE_THRESHOLD = 16.0;

    private static List<SoundRecord> getNearbySounds(String dim, BlockPos pos) {
    Map<Long, List<SoundRecord>> dimMap = SPATIAL_SOUNDS.get(dim);
    if (dimMap == null) return Collections.emptyList();
    List<SoundRecord> result = new ArrayList<>();

    int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
    long mobCell = SpatialPartitioner.getKey(pos, partitionSize);

    for (List<SoundRecord> soundList : dimMap.values()) {
        for (SoundRecord r : soundList) {
            if (r.coveredCells.contains(mobCell)) {
                result.add(r);
            }
        }
    }
    return result;
}

    private static void updateSpatialSounds() {
    SPATIAL_SOUNDS.clear();
    int partitionSize = SoundAttractMod.CONFIG.spatialPartitionSize;
    for (SoundRecord r : RECENT_SOUNDS) {
        r.coveredCells.clear();
        java.util.Set<Long> cells = getCoveredCells(r.pos, r.range, partitionSize);
        r.coveredCells.addAll(cells);
        Map<Long, List<SoundRecord>> dimMap = SPATIAL_SOUNDS.computeIfAbsent(r.dimensionKey, d -> new HashMap<>());
        for (Long key : cells) {
            dimMap.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
    }
}

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime, String explicitSoundId) {
        String soundId = se != null && se.getId() != null ? se.getId().toString() : explicitSoundId;
        if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty()
            && (soundId == null || !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundId))
            && (soundId == null || !soundId.equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID.toString()))) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[SoundTracker] Skipped non-whitelist sound: {} at {} (dim: {}), range={}, weight={}",
                            soundId != null ? soundId : (se != null ? se.getId() : "null"), pos, dimensionKey, range, weight);
                }
            }
            return;
        }
        if (range < 0) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.warn("[SoundTracker] Attempted to register sound {} at {} (dim: {}) with negative range={}, skipping.",
                            soundId != null ? soundId : (se != null ? se.getId() : "null"), pos, dimensionKey, range, weight);
                }
            }
            return;
        }
        RECENT_SOUNDS.removeIf(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight < weight);
        boolean higherExists = RECENT_SOUNDS.stream().anyMatch(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight > weight);
        if (!higherExists) {
            RECENT_SOUNDS.removeIf(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight == weight);
            RECENT_SOUNDS.add(new SoundRecord(se, soundId, pos, lifetime, dimensionKey, range, weight));
            if (SoundAttractMod.CONFIG.debugLogging) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[SoundTracker] Registered sound {} at {} (dim: {}), range={}, weight={}",
                            soundId != null ? soundId : (se != null ? se.getId() : "null"), pos, dimensionKey, range, weight);
                }
            }
            updateSpatialSounds();
        } else {
            if (SoundAttractMod.CONFIG.debugLogging) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[SoundTracker] Skipped adding sound {} at {} (dim: {}) with weight {} because higher-weight sound exists", soundId != null ? soundId : (se != null ? se.getId() : "null"), pos, dimensionKey, weight);
                }
            }
        }
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        addSound(se, pos, dimensionKey, range, weight, lifetime, null);
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        int lifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;
        addSound(se, pos, dimensionKey, 16.0, 1.0, lifetime);
    }

    public static synchronized void addVirtualSound(BlockPos pos, String dimensionKey, double range, double weight, int lifetime, UUID sourcePlayer, String animationClass) {
        RECENT_SOUNDS.removeIf(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight < weight);
        boolean higherExists = RECENT_SOUNDS.stream().anyMatch(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight > weight);
        if (!higherExists) {
            RECENT_SOUNDS.removeIf(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight == weight);
            RECENT_SOUNDS.add(new VirtualSoundRecord(pos, lifetime, dimensionKey, range, weight, sourcePlayer, animationClass));
            updateSpatialSounds();
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
            if (!(o instanceof RaycastCacheKey other)) return false;
            return mobPos.equals(other.mobPos) && soundPos.equals(other.soundPos) && soundId.equals(other.soundId);
        }
        @Override
        public int hashCode() {
            return mobPos.hashCode() ^ soundPos.hashCode() ^ soundId.hashCode();
        }
    }
    private static final WeakHashMap<RaycastCacheKey, double[]> RAYCAST_CACHE = new WeakHashMap<>();
    private static final int RAYCAST_CACHE_DIST_THRESHOLD = 1; 
    private static final int MAX_RECENT_SOUNDS = 32;

    public static double[] applyBlockMuffling(World level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId) {
        if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() && (soundId == null || !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundId))) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[SoundTracker] Skipped muffling for sound {} at {} -> {} due to whitelist", soundId, src, dst);
                }
            }
            return new double[]{0, 0};
        }
        RaycastCacheKey key = new RaycastCacheKey(dst, src, soundId);
        double[] cached = RAYCAST_CACHE.get(key);
        if (cached != null) return cached;
        double range = origRange;
        double weight = origWeight;
        int x0 = src.getX(), y0 = src.getY(), z0 = src.getZ();
        int x1 = dst.getX(), y1 = dst.getY(), z1 = dst.getZ();
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0), sy = Integer.compare(y1, y0), sz = Integer.compare(z1, z0);
        int n = 1 + dx + dy + dz;
        int x = x0, y = y0, z = z0;
        int err1 = dx - dy, err2 = dx - dz;
        int blocksMuffled = 0;
        int areaRadius = SoundAttractMod.CONFIG.mufflingAreaRadius;
        for (int i = 0; i < n && range > 0 && weight > 0; i++) {
            boolean muffledThisStep = false;
            for (int dxr = -areaRadius; dxr <= areaRadius && !muffledThisStep; dxr++) {
                for (int dyr = -areaRadius; dyr <= areaRadius && !muffledThisStep; dyr++) {
                    for (int dzr = -areaRadius; dzr <= areaRadius && !muffledThisStep; dzr++) {
                        BlockPos pos = new BlockPos(x + dxr, y + dyr, z + dzr);
                        BlockState state = level.getBlockState(pos);
                        Block block = state.getBlock();
                        if (block == Blocks.AIR) continue;
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
                        } else if ((state.getFluidState() != null && !state.getFluidState().isEmpty()) || isCustomLiquid(block) && SoundAttractMod.CONFIG.liquidMufflingEnabled) {
                            range -= SoundAttractMod.CONFIG.liquidBlockRangeReduction;
                            weight -= SoundAttractMod.CONFIG.liquidBlockWeightReduction;
                            blocksMuffled++;
                            muffledThisStep = true;
                        }
                    }
                }
            }
            if (x == x1 && y == y1 && z == z1) break;
            int e2 = 2 * err1;
            int e3 = 2 * err2;
            if (e2 > -dy) { err1 -= dy; x += sx; }
            if (e2 < dx)  { err1 += dx; y += sy; }
            if (e3 > -dz) { err2 -= dz; x += sx; }
            if (e3 < dx)  { err2 += dx; z += sz; }
        }
        if (range < 0) range = 0;
        if (weight < 0) weight = 0;
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[BlockMuffling] soundId={}, src={}, dst={}, origRange={}, origWeight={}, muffledRange={}, muffledWeight={}, blocksMuffled={}",
                soundId, src, dst, origRange, origWeight, range, weight, blocksMuffled);
        }
        double[] result = new double[]{range, weight};
        RAYCAST_CACHE.put(key, result);
        return result;
    }

    private static boolean isBlockInConfigList(BlockState state, Block block, java.util.List<String> configList) {
        Identifier id = net.minecraft.registry.Registries.BLOCK.getId(block);
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
        return isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customWoolBlocks) || state.isIn(BlockTags.WOOL);
    }
    private static boolean isCustomSolid(BlockState state, Block block) {
        return isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customSolidBlocks) || state.isSolid();
    }
    private static boolean isCustomNonSolid(BlockState state, Block block) {
        return isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customNonSolidBlocks) || !state.isSolid();
    }
    private static boolean isCustomThin(BlockState state, Block block) {
        if (isBlockInConfigList(state, block, SoundAttractMod.CONFIG.customThinBlocks)) return true;
        Identifier id = net.minecraft.registry.Registries.BLOCK.getId(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("pane") || path.contains("iron_bars") || path.contains("painting") || path.contains("fence") || path.contains("trapdoor") || path.contains("door") || path.contains("ladder") || path.contains("scaffolding") || path.contains("rail");
    }

    private static boolean isCustomLiquid(Block block) {
        String blockId = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
        return SoundAttractMod.CONFIG.customLiquidBlocks.contains(blockId);
    }

    private static int pruneIndex = 0;
    private static final int SOUNDS_PRUNED_PER_TICK = 5;

    public static void pruneIrrelevantSounds(World level) {
        if (RECENT_SOUNDS.isEmpty()) return;
        int total = RECENT_SOUNDS.size();
        if (pruneIndex >= total) pruneIndex = 0;
        int pruned = 0;
        int checked = 0;
        List<SoundRecord> snapshot = new ArrayList<>(RECENT_SOUNDS);
        for (int i = 0; i < snapshot.size() && pruned < SOUNDS_PRUNED_PER_TICK; i++) {
            int idx = (pruneIndex + i) % snapshot.size();
            SoundRecord sound = snapshot.get(idx);
            if (sound.ticksRemaining > 5) continue;
            boolean inRange = false;
            int cellSize = 16;
            int soundCellX = sound.pos.getX() >> 4;
            int soundCellZ = sound.pos.getZ() >> 4;
            double checkRadius = sound.range + 8;
            int cellsRadius = (int)Math.ceil(checkRadius / cellSize);
            for (int dx = -cellsRadius; dx <= cellsRadius; dx++) {
                for (int dz = -cellsRadius; dz <= cellsRadius; dz++) {
                    int cellX = soundCellX + dx;
                    int cellZ = soundCellZ + dz;
                    long cellKey = (((long)cellX) << 32) | (cellZ & 0xFFFFFFFFL);
                    List<net.minecraft.entity.mob.MobEntity> mobs = level.getEntitiesByClass(
                        net.minecraft.entity.mob.MobEntity.class,
                        new net.minecraft.util.math.Box(
                            (cellX << 4), level.getBottomY(), (cellZ << 4),
                            ((cellX + 1) << 4), level.getTopY(), ((cellZ + 1) << 4)
                        ),
                        mob -> true
                    );
                    for (net.minecraft.entity.mob.MobEntity mob : mobs) {
                        if (!mob.isAlive()) continue;
                        BlockPos mobPos = mob.getBlockPos();
                        String soundId = sound.soundId != null ? sound.soundId : (sound.sound != null && sound.sound.getId() != null ? sound.sound.getId().toString() : "unknown");
                        double[] muffled = applyBlockMuffling(level, sound.pos, mobPos, sound.range, sound.weight, soundId);
                        double range = muffled[0];
                        double getSquaredDistance = mobPos.getSquaredDistance(sound.pos);
                        if (getSquaredDistance <= range * range) {
                            inRange = true;
                            break;
                        }
                    }
                    if (inRange) break;
                }
                if (inRange) break;
            }
            if (!inRange) {
                RECENT_SOUNDS.remove(sound);
                pruned++;
            }
            checked++;
        }
        pruneIndex = (pruneIndex + checked) % Math.max(1, RECENT_SOUNDS.size());
        updateSpatialSounds();
    }

    public static synchronized SoundRecord findNearestSound(World level, BlockPos mobPos, Vec3d mobEyePos) {
        String dimensionKey = level.getRegistryKey().getValue().toString();

        SoundRecord bestSound = null;
        double highestWeight = -1.0;
        double closestDistSqr = Double.MAX_VALUE;
        for (SoundRecord r : RECENT_SOUNDS) {
            String soundId = r.soundId != null ? r.soundId : (r.sound != null && r.sound.getId() != null ? r.sound.getId().toString() : null);
            if (!r.dimensionKey.equals(dimensionKey)) {
                continue;
            }
            if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty() && (soundId == null || !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundId))) {
                continue;
            }
            double getSquaredDistance = mobPos.getSquaredDistance(r.pos);
            double origRangeSqr = r.range * r.range;
            if (getSquaredDistance > origRangeSqr) {
                continue;
            }
            double[] muffled = applyBlockMuffling(level, r.pos, mobPos, r.range, r.weight, soundId != null ? soundId : "unknown");
            double muffledRange = muffled[0];
            double muffledWeight = muffled[1];
            double rangeSqr = muffledRange * muffledRange;
            if (getSquaredDistance > rangeSqr) {
                continue;
            }
            if (muffledWeight > highestWeight || (Math.abs(muffledWeight - highestWeight) < 0.001 && getSquaredDistance < closestDistSqr)) {
                highestWeight = muffledWeight;
                closestDistSqr = getSquaredDistance;
                bestSound = r;
            }
        }
        return bestSound;
    }

    /**
     * Efficiently finds all mobs within the relevant chunk area for a given sound, using a filter predicate.
     * This enables chunk-based optimization for mob attraction logic (edge/leader/deserter/etc).
     *
     * @param world The world to query mobs from.
     * @param sound The sound record to use for spatial lookup.
     * @param filter A predicate to select eligible mobs (e.g., edge/leader/deserter).
     * @return List of mobs in range and matching the filter.
     */
    public static java.util.List<net.minecraft.entity.mob.MobEntity> getMobsForSound(
            net.minecraft.world.World world,
            SoundRecord sound,
            java.util.function.Predicate<net.minecraft.entity.mob.MobEntity> filter
    ) {
        java.util.List<net.minecraft.entity.mob.MobEntity> result = new java.util.ArrayList<>();
        if (sound == null || world == null) return result;
        int range = (int)Math.ceil(sound.range);
        int chunkRadius = Math.max(0, (int)Math.ceil(range / 16.0));
        int chunkX = sound.pos.getX() >> 4;
        int chunkZ = sound.pos.getZ() >> 4;
        int minY = world.getBottomY();
        int maxY = world.getTopY();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int cx = chunkX + dx;
                int cz = chunkZ + dz;
                int minBlockX = (cx << 4);
                int minBlockZ = (cz << 4);
                int maxBlockX = minBlockX + 15;
                int maxBlockZ = minBlockZ + 15;
                net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                        minBlockX, minY, minBlockZ,
                        maxBlockX + 1, maxY, maxBlockZ + 1
                );
                java.util.List<net.minecraft.entity.mob.MobEntity> mobs = world.getEntitiesByClass(
                        net.minecraft.entity.mob.MobEntity.class,
                        box,
                        filter
                );
                for (net.minecraft.entity.mob.MobEntity mob : mobs) {
                    if (mob.getBlockPos().getSquaredDistance(sound.pos) <= sound.range * sound.range) {
                        result.add(mob);
                    }
                }
            }
        }
        return result;
    }

    public static java.util.List<SoundRecord> getRecentSounds() {
        return RECENT_SOUNDS;
    }
}


