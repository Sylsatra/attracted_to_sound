package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        SoundAttractMod.LOGGER.trace("[SoundTracker] Adding sound {} at {} (Dim: {}) Range: {}, Weight: {}, Lifetime: {}", 
            se != null ? se.getLocation() : "null", pos, dimensionKey, range, weight, lifetime);
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

    public static synchronized SoundRecord findNearestSound(net.minecraft.world.level.Level level, BlockPos mobPos) {
        String dimensionKey = level.dimension().location().toString();

        SoundRecord bestSound = null;
        double closestDistSqr = Double.MAX_VALUE;
        double highestWeight = -Double.MAX_VALUE;

        for (SoundRecord r : RECENT_SOUNDS) {
            if (!r.dimensionKey.equals(dimensionKey)) continue;
            
            double rangeSqr = r.range * r.range;
            double distSqr = mobPos.distSqr(r.pos);
            
            if (distSqr <= rangeSqr) {
                if (r.weight > highestWeight || (r.weight == highestWeight && distSqr < closestDistSqr)) {
                    highestWeight = r.weight;
                    closestDistSqr = distSqr;
                    bestSound = r;
                }
            }
        }
        if (bestSound != null) {
             SoundAttractMod.LOGGER.trace("[SoundTracker] Found best sound for mob at {}: Pos={}, Range={}, Weight={}", mobPos, bestSound.pos, bestSound.range, bestSound.weight);
        } else {
        }
        return bestSound;
    }
}
