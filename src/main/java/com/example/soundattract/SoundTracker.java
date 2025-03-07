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

        public SoundRecord(SoundEvent sound, BlockPos pos, int lifetime, String dimensionKey) {
            this.sound = sound;
            this.pos = pos;
            this.ticksRemaining = lifetime;
            this.dimensionKey = dimensionKey;
        }
    }

    private static final List<SoundRecord> RECENT_SOUNDS = new ArrayList<>();

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        int lifetime = SoundAttractConfig.SOUND_LIFETIME_TICKS;
        RECENT_SOUNDS.add(new SoundRecord(se, pos, lifetime, dimensionKey));
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

        SoundRecord nearest = null;
        double closestDistSqr = Double.MAX_VALUE;

        for (SoundRecord r : RECENT_SOUNDS) {
            if (!r.dimensionKey.equals(dimensionKey)) continue;
            if (!SoundAttractConfig.SOUNDS_TO_ATTRACT.contains(r.sound)) continue;

            double distSqr = mobPos.distSqr(r.pos);
            if (distSqr < closestDistSqr) {
                closestDistSqr = distSqr;
                nearest = r;
            }
        }
        return nearest;
    }
}
