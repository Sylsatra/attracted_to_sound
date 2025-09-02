package com.example.soundattract.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;

public class BlockBreakerManager {

    private record GoalAction(ActionType type, Goal goal, int priority) {}
    private enum ActionType { ADD, REMOVE }

    private static final Map<Mob, GoalAction> PENDING_ACTIONS = new ConcurrentHashMap<>();

    public static void scheduleAdd(Mob mob, Goal goal, int priority) {
        PENDING_ACTIONS.put(mob, new GoalAction(ActionType.ADD, goal, priority));
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[BlockBreakerManager] Scheduled ADD for mob {}: goal={}, priority={}",
                    mob != null ? mob.getName().getString() : "null",
                    goal != null ? goal.getClass().getSimpleName() : "null",
                    priority);
        }
    }

    public static void scheduleRemove(Mob mob, Goal goal) {
        if (goal != null) {
            PENDING_ACTIONS.put(mob, new GoalAction(ActionType.REMOVE, goal, 0));
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[BlockBreakerManager] Scheduled REMOVE for mob {}: goal={}",
                        mob != null ? mob.getName().getString() : "null",
                        goal.getClass().getSimpleName());
            }
        }
    }

    public static void processPendingActions() {
        if (PENDING_ACTIONS.isEmpty()) {
            return;
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[BlockBreakerManager] Processing {} pending action(s)", PENDING_ACTIONS.size());
        }

        for (Mob mob : PENDING_ACTIONS.keySet()) {
            GoalAction action = PENDING_ACTIONS.remove(mob);
            if (action != null && mob.isAlive() && !mob.isRemoved()) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[BlockBreakerManager] Applying action {} for mob {}: goal={}",
                            action.type,
                            mob.getName().getString(),
                            action.goal != null ? action.goal.getClass().getSimpleName() : "null");
                }
                if (action.type == ActionType.ADD) {
                    mob.goalSelector.getAvailableGoals().stream()
                        .filter(wrappedGoal -> wrappedGoal.getGoal().getClass().equals(action.goal.getClass()))
                        .findFirst()
                        .ifPresent(wrappedGoal -> {
                            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                SoundAttractMod.LOGGER.info("[BlockBreakerManager] Removing existing goal of same class before ADD: {}",
                                        wrappedGoal.getGoal().getClass().getSimpleName());
                            }
                            mob.goalSelector.removeGoal(wrappedGoal.getGoal());
                        });

                    mob.goalSelector.addGoal(action.priority, action.goal);
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[BlockBreakerManager] Added goal {} at priority {}",
                                action.goal.getClass().getSimpleName(), action.priority);
                    }
                } else if (action.type == ActionType.REMOVE) {
                    mob.goalSelector.removeGoal(action.goal);
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[BlockBreakerManager] Removed goal {}",
                                action.goal.getClass().getSimpleName());
                    }
                }
            } else if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[BlockBreakerManager] Skipped action due to null/invalid mob or action");
            }
        }
    }
}
