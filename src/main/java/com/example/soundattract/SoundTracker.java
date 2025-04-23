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

public class SoundTracker {

    public static class SoundRecord {
        public final SoundEvent sound;
        public final BlockPos pos;
        public int ticksRemaining;
        public final String dimensionKey;
        public final double range;
        public final double weight;

        public SoundRecord(SoundEvent sound, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this.sound = sound;
            this.pos = pos;
            this.ticksRemaining = lifetime;
            this.dimensionKey = dimensionKey;
            this.range = range;
            this.weight = weight;
        }
    }

    private static final List<SoundRecord> RECENT_SOUNDS = new ArrayList<>();

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        SoundAttractConfig.SoundConfig config = SoundAttractConfig.SOUND_CONFIGS_CACHE.get(se);
        if (config == null) return;
        
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        addSound(se, pos, dimensionKey, config.range, config.weight, lifetime);
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        ResourceLocation soundId = BuiltInRegistries.SOUND_EVENT.getKey(se);
        RECENT_SOUNDS.add(new SoundRecord(se, pos, lifetime, dimensionKey, range, weight));
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
        for (int i = 0; i < n && range > 0 && weight > 0; i++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block == Blocks.AIR) {
            } else if (isCustomWool(state, block)) {
                range -= SoundAttractConfig.WOOL_BLOCK_RANGE_REDUCTION_CACHE;
                weight -= SoundAttractConfig.WOOL_BLOCK_WEIGHT_REDUCTION_CACHE;
                blocksMuffled++;
            } else if (isCustomThin(state, block)) {
                range -= SoundAttractConfig.THIN_BLOCK_RANGE_REDUCTION_CACHE;
                weight -= SoundAttractConfig.THIN_BLOCK_WEIGHT_REDUCTION_CACHE;
                blocksMuffled++;
            } else if (isCustomNonSolid(state, block)) {
                range -= SoundAttractConfig.NON_SOLID_BLOCK_RANGE_REDUCTION_CACHE;
                weight -= SoundAttractConfig.NON_SOLID_BLOCK_WEIGHT_REDUCTION_CACHE;
                blocksMuffled++;
            } else if (isCustomSolid(state, block)) {
                range -= SoundAttractConfig.SOLID_BLOCK_RANGE_REDUCTION_CACHE;
                weight -= SoundAttractConfig.SOLID_BLOCK_WEIGHT_REDUCTION_CACHE;
                blocksMuffled++;
            } else {
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
        return isBlockInConfigList(state, block, SoundAttractConfig.CUSTOM_WOOL_BLOCKS_CACHE) || state.is(BlockTags.WOOL);
    }
    private static boolean isCustomSolid(BlockState state, Block block) {
        return isBlockInConfigList(state, block, SoundAttractConfig.CUSTOM_SOLID_BLOCKS_CACHE) || state.isSolid();
    }
    private static boolean isCustomNonSolid(BlockState state, Block block) {
        return isBlockInConfigList(state, block, SoundAttractConfig.CUSTOM_NON_SOLID_BLOCKS_CACHE) || !state.isSolid();
    }
    private static boolean isCustomThin(BlockState state, Block block) {
        if (isBlockInConfigList(state, block, SoundAttractConfig.CUSTOM_THIN_BLOCKS_CACHE)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("pane") || path.contains("iron_bars") || path.contains("painting") || path.contains("fence") || path.contains("trapdoor") || path.contains("door") || path.contains("ladder") || path.contains("scaffolding") || path.contains("rail");
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
                String soundId = sound.sound != null && sound.sound.getLocation() != null ? sound.sound.getLocation().toString() : "unknown";
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
    }

    public static synchronized SoundRecord findNearestSound(Level level, BlockPos mobPos, Vec3 mobEyePos) {
        String dimensionKey = level.dimension().location().toString();

        SoundRecord bestSound = null;
        double closestDistSqr = Double.MAX_VALUE;
        double highestWeight = -Double.MAX_VALUE;

        for (SoundRecord r : RECENT_SOUNDS) {
            if (!r.dimensionKey.equals(dimensionKey)) continue;

            double[] muffled = applyBlockMuffling(level, r.pos, mobPos, r.range, r.weight, r.sound != null && r.sound.getLocation() != null ? r.sound.getLocation().toString() : "unknown");
            double muffledRange = muffled[0];
            double muffledWeight = muffled[1];

            double rangeSqr = muffledRange * muffledRange;
            double distSqr = mobPos.distSqr(r.pos);

            if (distSqr <= rangeSqr) {
                if (muffledWeight > highestWeight || (Math.abs(muffledWeight - highestWeight) < 0.001 && distSqr < closestDistSqr)) {
                    highestWeight = muffledWeight;
                    closestDistSqr = distSqr;
                    bestSound = r;
                }
            }
        }

        if (bestSound != null) {
             SoundAttractMod.LOGGER.trace("[SoundTracker] Found best sound for mob at {}: {}", mobPos, bestSound);
        }
        return bestSound;
    }
}
