package com.example.soundattract.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import com.example.soundattract.mixin.MobEntityAccessor;
import com.example.soundattract.SoundAttractMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple goal scheduler so we only mutate goal selectors on the main tick.
 */
public final class BlockBreakerManager {

    private BlockBreakerManager() {}

    private record GoalAction(ActionType type, Goal goal, int priority) {}
    private enum ActionType { ADD, REMOVE }

    private static final Map<MobEntity, GoalAction> PENDING = new ConcurrentHashMap<>();

    public static void scheduleAdd(MobEntity mob, Goal goal, int priority) {
        if (mob == null || goal == null) return;
        PENDING.put(mob, new GoalAction(ActionType.ADD, goal, priority));
    }

    public static void scheduleRemove(MobEntity mob, Goal goal) {
        if (mob == null || goal == null) return;
        PENDING.put(mob, new GoalAction(ActionType.REMOVE, goal, 2));
    }

    public static void processPendingActions() {
        if (PENDING.isEmpty()) return;
        for (MobEntity mob : PENDING.keySet()) {
            GoalAction action = PENDING.remove(mob);
            if (action == null) continue;
            if (!mob.isAlive() || mob.isRemoved()) continue;
            switch (action.type) {
                case ADD -> {

                    ((MobEntityAccessor) mob).getGoalSelector().getGoals().stream()
                        .filter(w -> w.getGoal().getClass().equals(action.goal.getClass()))
                        .findFirst()
                        .ifPresent(w -> ((MobEntityAccessor) mob).getGoalSelector().remove(w.getGoal()));
                    ((MobEntityAccessor) mob).getGoalSelector().add(action.priority, action.goal);
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[BlockBreakerManager] Added goal {} for {} at priority {}", action.goal.getClass().getSimpleName(), mob.getName().getString(), action.priority);
                    }
                }
                case REMOVE -> {
                    ((MobEntityAccessor) mob).getGoalSelector().remove(action.goal);
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[BlockBreakerManager] Removed goal {} for {}", action.goal.getClass().getSimpleName(), mob.getName().getString());
                    }
                }
            }
        }
    }
}
