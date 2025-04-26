package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;

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

        public SoundRecord(SoundEvent sound, String soundId, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this.sound = sound;
            this.soundId = soundId;
            this.pos = pos;
            this.ticksRemaining = lifetime;
            this.dimensionKey = dimensionKey;
            this.range = range;
            this.weight = weight;
        }
        // For backward compatibility
        public SoundRecord(SoundEvent sound, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this(sound, sound != null && sound.getLocation() != null ? sound.getLocation().toString() : null, pos, lifetime, dimensionKey, range, weight);
        }
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
        List<SoundRecord> result = new ArrayList<>();

        boolean hasLargeRange = false;
        for (SoundRecord r : RECENT_SOUNDS) {
            if (r.dimensionKey.equals(dim) && r.range > LARGE_SOUND_RANGE_THRESHOLD) {
                hasLargeRange = true;
                break;
            }
        }

        if (hasLargeRange) {
            for (SoundRecord r : RECENT_SOUNDS) {
                if (r.dimensionKey.equals(dim) && r.range > LARGE_SOUND_RANGE_THRESHOLD) {
                    result.add(r);
                }
            }
            return result;
        }

        long key = gridKey(pos);
        for (long dx = -1; dx <= 1; dx++) {
            for (long dz = -1; dz <= 1; dz++) {
                long nk = ((key >> 32) + dx) << 32 | (((key & 0xFFFFFFFFL) + dz) & 0xFFFFFFFFL);
                List<SoundRecord> list = dimMap.get(nk);
                if (list != null) result.addAll(list);
            }
        }
        return result;
    }

    private static void updateSpatialSounds() {
        SPATIAL_SOUNDS.clear();
        for (SoundRecord r : RECENT_SOUNDS) {
            SPATIAL_SOUNDS.computeIfAbsent(r.dimensionKey, d -> new HashMap<>())
                .computeIfAbsent(gridKey(r.pos), k -> new ArrayList<>()).add(r);
        }
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime, String explicitSoundId) {
        String soundId = se != null && se.getLocation() != null ? se.getLocation().toString() : explicitSoundId;
        // PATCH: Always allow voice chat sound, bypass whitelist
        if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
            && (soundId == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(soundId))
            && (soundId == null || !soundId.equals(com.example.soundattract.SoundMessage.VOICE_CHAT_SOUND_ID.toString()))) {
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundTracker] Skipped non-whitelist sound: {} at {} (dim: {}), range={}, weight={}",
                        soundId != null ? soundId : (se != null ? se.getLocation() : "null"), pos, dimensionKey, range, weight);
            }
            return;
        }
        if (range < 0) {
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                com.example.soundattract.SoundAttractMod.LOGGER.warn("[SoundTracker] Attempted to register sound {} at {} (dim: {}) with negative range={}, skipping.",
                        soundId != null ? soundId : (se != null ? se.getLocation() : "null"), pos, dimensionKey, range);
            }
            return;
        }
        RECENT_SOUNDS.removeIf(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight < weight);
        boolean higherExists = RECENT_SOUNDS.stream().anyMatch(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight > weight);
        if (!higherExists) {
            RECENT_SOUNDS.removeIf(r -> r.pos.equals(pos) && r.dimensionKey.equals(dimensionKey) && r.weight == weight);
            RECENT_SOUNDS.add(new SoundRecord(se, soundId, pos, lifetime, dimensionKey, range, weight));
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundTracker] Registered sound {} at {} (dim: {}), range={}, weight={}",
                        soundId != null ? soundId : (se != null ? se.getLocation() : "null"), pos, dimensionKey, range, weight);
            }
            updateSpatialSounds();
        } else {
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundTracker] Skipped adding sound {} at {} (dim: {}) with weight {} because higher-weight sound exists", soundId != null ? soundId : (se != null ? se.getLocation() : "null"), pos, dimensionKey, weight);
            }
        }
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        addSound(se, pos, dimensionKey, range, weight, lifetime, null);
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        int lifetime = SoundAttractConfig.soundLifetimeTicks.get();
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

    public static double[] applyBlockMuffling(Level level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId) {
        if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && (soundId == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(soundId))) {
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundTracker] Skipped muffling for sound {} at {} -> {} due to whitelist", soundId, src, dst);
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
        int areaRadius = SoundAttractConfig.mufflingAreaRadius.get();
        for (int i = 0; i < n && range > 0 && weight > 0; i++) {
            boolean muffledThisStep = false;
            for (int dxr = -areaRadius; dxr <= areaRadius && !muffledThisStep; dxr++) {
                for (int dyr = -areaRadius; dyr <= areaRadius && !muffledThisStep; dyr++) {
                    for (int dzr = -areaRadius; dzr <= areaRadius && !muffledThisStep; dzr++) {
                        BlockPos pos = new BlockPos(x + dxr, y + dyr, z + dzr);
                        BlockState state = level.getBlockState(pos);
                        Block block = state.getBlock();
                        if (block == Blocks.AIR) continue;
                        if (isCustomWool(state, block) && SoundAttractConfig.woolMufflingEnabled.get()) {
                            range -= SoundAttractConfig.woolBlockRangeReduction.get();
                            weight -= SoundAttractConfig.woolBlockWeightReduction.get();
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (isCustomThin(state, block) && SoundAttractConfig.thinMufflingEnabled.get()) {
                            range -= SoundAttractConfig.thinBlockRangeReduction.get();
                            weight -= SoundAttractConfig.thinBlockWeightReduction.get();
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (isCustomNonSolid(state, block) && SoundAttractConfig.nonSolidMufflingEnabled.get()) {
                            range -= SoundAttractConfig.nonSolidBlockRangeReduction.get();
                            weight -= SoundAttractConfig.nonSolidBlockWeightReduction.get();
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if (isCustomSolid(state, block) && SoundAttractConfig.solidMufflingEnabled.get()) {
                            range -= SoundAttractConfig.solidBlockRangeReduction.get();
                            weight -= SoundAttractConfig.solidBlockWeightReduction.get();
                            blocksMuffled++;
                            muffledThisStep = true;
                        } else if ((state.getFluidState() != null && !state.getFluidState().isEmpty()) || isCustomLiquid(block) && SoundAttractConfig.liquidMufflingEnabled.get()) {
                            range -= SoundAttractConfig.liquidBlockRangeReduction.get();
                            weight -= SoundAttractConfig.liquidBlockWeightReduction.get();
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
        double[] result = new double[]{range, weight};
        RAYCAST_CACHE.put(key, result);
        return result;
    }

    private static boolean isBlockInConfigList(BlockState state, Block block, java.util.List<String> configList) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id != null && configList.contains(id.toString())) return true;
        for (String entry : configList) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, new ResourceLocation(tagName));
                if (state.is(tag)) return true;
            }
        }
        return false;
    }
    private static boolean isCustomWool(BlockState state, Block block) {
        return isBlockInConfigList(state, block, SoundAttractConfig.customWoolBlocks.get().stream().map(String::valueOf).toList()) || state.is(BlockTags.WOOL);
    }
    private static boolean isCustomSolid(BlockState state, Block block) {
        return isBlockInConfigList(state, block, SoundAttractConfig.customSolidBlocks.get().stream().map(String::valueOf).toList()) || state.isSolid();
    }
    private static boolean isCustomNonSolid(BlockState state, Block block) {
        return isBlockInConfigList(state, block, SoundAttractConfig.customNonSolidBlocks.get().stream().map(String::valueOf).toList()) || !state.isSolid();
    }
    private static boolean isCustomThin(BlockState state, Block block) {
        if (isBlockInConfigList(state, block, SoundAttractConfig.customThinBlocks.get().stream().map(String::valueOf).toList())) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("pane") || path.contains("iron_bars") || path.contains("painting") || path.contains("fence") || path.contains("trapdoor") || path.contains("door") || path.contains("ladder") || path.contains("scaffolding") || path.contains("rail");
    }

    private static boolean isCustomLiquid(Block block) {
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        return SoundAttractConfig.CUSTOM_LIQUID_BLOCKS_CACHE.contains(blockId);
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

    public static synchronized SoundRecord findNearestSound(Level level, BlockPos mobPos, Vec3 mobEyePos) {
        String dimensionKey = level.dimension().location().toString();

        SoundRecord bestSound = null;
        double highestWeight = -1.0;
        double closestDistSqr = Double.MAX_VALUE;
        for (SoundRecord r : RECENT_SOUNDS) {
            String soundId = r.soundId != null ? r.soundId : (r.sound != null && r.sound.getLocation() != null ? r.sound.getLocation().toString() : null);
            if (!r.dimensionKey.equals(dimensionKey)) {
                continue;
            }
            if (!com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty() && (soundId == null || !com.example.soundattract.config.SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(soundId))) {
                continue;
            }
            double[] muffled = applyBlockMuffling(level, r.pos, mobPos, r.range, r.weight, soundId != null ? soundId : "unknown");
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
