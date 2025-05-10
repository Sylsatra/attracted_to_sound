package com.example.soundattract.ai;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class EdgeRelayManager {
    public static class RelayState {
        public final BlockPos soundSource;
        public final long startTime;
        public final long delayMillis;
        public boolean investigating = true;
        public boolean cancelled = false;
        public boolean completed = false;
        public RelayState(BlockPos soundSource, long startTime, long delayMillis) {
            this.soundSource = soundSource;
            this.startTime = startTime;
            this.delayMillis = delayMillis;
        }
    }

    private static final Map<UUID, RelayState> relayStates = new HashMap<>();

    public static void startRelay(MobEntity edgeMob, BlockPos soundSource, long delayMillis, long currentTimeMillis) {
        relayStates.put(edgeMob.getUuid(), new RelayState(soundSource, currentTimeMillis, delayMillis));
    }

    public static RelayState getRelayState(MobEntity edgeMob) {
        return relayStates.get(edgeMob.getUuid());
    }

    public static void cancelRelay(MobEntity edgeMob) {
        RelayState state = relayStates.get(edgeMob.getUuid());
        if (state != null) {
            state.cancelled = true;
            state.investigating = false;
        }
    }

    public static void completeRelay(MobEntity edgeMob) {
        RelayState state = relayStates.get(edgeMob.getUuid());
        if (state != null) {
            state.completed = true;
            state.investigating = false;
        }
    }

    public static void cleanupExpired(long currentTimeMillis) {
        relayStates.entrySet().removeIf(entry -> {
            RelayState state = entry.getValue();
            return state.completed || state.cancelled || (currentTimeMillis - state.startTime > state.delayMillis + 60_000); // 1 min grace
        });
    }
}
