package com.example.soundattract;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;

public class StealthUtils {
    public static void clearTargetAndMemories(MobEntity mob) {
        mob.setTarget(null);
        try {
            Brain<?> brain = mob.getBrain();
            if (brain != null) {
                // Core vanilla memories:
                brain.forget(MemoryModuleType.ATTACK_TARGET);
                brain.forget(MemoryModuleType.ANGRY_AT);
                brain.forget(MemoryModuleType.HURT_BY);
                brain.forget(MemoryModuleType.HURT_BY_ENTITY);
                brain.forget(MemoryModuleType.WALK_TARGET);
                brain.forget(MemoryModuleType.LOOK_TARGET);
                brain.forget(MemoryModuleType.INTERACTION_TARGET);
                // Wipe out any memory with “target”/“anger”/“attacker” in its name:
                brain.getMemories().keySet().forEach(memoryType -> {
                    String name = memoryType.toString().toLowerCase();
                    if (name.contains("target") || name.contains("anger") || name.contains("attacker")) {
                        try { brain.forget(memoryType); } catch (Exception ignored) {}
                    }
                });
            }
        } catch (Exception e) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[StealthUtils] Error clearing brain memories: {}", e.toString());
            }
        }
    }
}
