package com.example.soundattract.event;

import com.example.soundattract.ai.FleeFromUnseenAttackerGoal;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public class AIModificationEvents {

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (!SoundAttractConfig.COMMON.enableFleeFromUnseenAttackerGoal.get()) {
            return;
        }

        boolean alreadyHasGoal = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof FleeFromUnseenAttackerGoal);

        if (!alreadyHasGoal) {
            Goal fleeGoal = new FleeFromUnseenAttackerGoal(mob, 1.25D);
            mob.goalSelector.addGoal(3, fleeGoal);
        }
    }
}
