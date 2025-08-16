package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple goal scheduler so we only mutate goal selectors on the main tick.
 */
public final class BlockBreakerManager {

    private BlockBreakerManager() {}

    private record GoalAction(ActionType type, Goal goal, int priority) {}
    private enum ActionType { ADD, REMOVE }

    private static final Map<Mob, GoalAction> PENDING = new ConcurrentHashMap<>();

    public static void scheduleAdd(Mob mob, Goal goal, int priority) {
        if (mob == null || goal == null) return;
        PENDING.put(mob, new GoalAction(ActionType.ADD, goal, priority));
    }

    public static void scheduleRemove(Mob mob, Goal goal) {
        if (mob == null || goal == null) return;
        PENDING.put(mob, new GoalAction(ActionType.REMOVE, goal, 0));
    }

    public static void processPendingActions() {
        if (PENDING.isEmpty()) return;
        for (Mob mob : PENDING.keySet()) {
            GoalAction action = PENDING.remove(mob);
            if (action == null) continue;
            if (!mob.isAlive() || mob.isRemoved()) continue;
            switch (action.type) {
                case ADD -> {

                    mob.goalSelector.getAvailableGoals().stream()
                        .filter(w -> w.getGoal().getClass().equals(action.goal.getClass()))
                        .findFirst()
                        .ifPresent(w -> mob.goalSelector.removeGoal(w.getGoal()));
                    mob.goalSelector.addGoal(action.priority, action.goal);
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[BlockBreakerManager] Added goal {} for {} at priority {}", action.goal.getClass().getSimpleName(), mob.getName().getString(), action.priority);
                    }
                }
                case REMOVE -> {
                    mob.goalSelector.removeGoal(action.goal);
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[BlockBreakerManager] Removed goal {} for {}", action.goal.getClass().getSimpleName(), mob.getName().getString());
                    }
                }
            }
        }
    }
}
