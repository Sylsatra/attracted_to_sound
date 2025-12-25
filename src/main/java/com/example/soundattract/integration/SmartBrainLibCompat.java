package com.example.soundattract.integration;

import com.example.soundattract.DynamicScanCooldownManager;
import com.example.soundattract.SoundAttractMod;

import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.ModList;

public final class SmartBrainLibCompat {
    private static final boolean IS_SBL_LOADED = ModList.get().isLoaded("smartbrainlib");

    private SmartBrainLibCompat() {}

    public static boolean tryAttachSoundAttractBrain(Mob mob) {
        if (!IS_SBL_LOADED || mob == null) return false;
        try {
            return Proxy.tryAttach(mob);
        } catch (Throwable t) {
            if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[SmartBrainLibCompat] Failed to attach brain integration for {}", mob.getUUID());
            }
            return false;
        }
    }

    private static final class Proxy {
        private static final String OLD_BEHAVIOUR_CLASS = "com.example.soundattract.integration.sbl.SoundAttractSblBehaviour";
        private static final String BEHAVIOUR_CLASS = "com.example.soundattract.integration.sbl.MoveToSoundBehaviour";

        private static boolean tryAttach(Mob mob) {
            if (!(mob.getBrain() instanceof net.tslat.smartbrainlib.api.core.SmartBrain smartBrain)) {
                return false;
            }

            boolean hasBehaviour = net.tslat.smartbrainlib.util.BrainUtils.getAllBehaviours(mob.getBrain())
                .anyMatch(b -> b != null && b.getClass().getName().equals(BEHAVIOUR_CLASS));

            if (hasBehaviour) {
                return true;
            }

            net.tslat.smartbrainlib.util.BrainUtils.removeBehaviour(mob, (priority, activity, behaviour, parent) -> behaviour != null && behaviour.getClass().getName().equals(OLD_BEHAVIOUR_CLASS));

            net.tslat.smartbrainlib.util.BrainUtils.addMemories(mob.getBrain(),
                net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                com.example.soundattract.integration.sbl.SoundAttractSensor.SOUND_ATTRACT_TARGET);

            smartBrain.addSensor(new com.example.soundattract.integration.sbl.SoundAttractSensor<>());

            com.example.soundattract.integration.sbl.MoveToSoundBehaviour behaviour = new com.example.soundattract.integration.sbl.MoveToSoundBehaviour();
            behaviour.cooldownFor(e -> Math.max(1, DynamicScanCooldownManager.currentScanCooldownTicks));

            smartBrain.addBehaviour(1, net.minecraft.world.entity.schedule.Activity.IDLE, behaviour);
            return true;
        }
    }
}
