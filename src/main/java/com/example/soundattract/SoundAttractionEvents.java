package com.example.soundattract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.BlockBreakerManager;
import com.example.soundattract.ai.CombatBlockBreakAssistGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.config.SoundAttractConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class SoundAttractionEvents {

    private static final Map<ResourceKey<Level>, Long> dimensionLoadTimes = new ConcurrentHashMap<>();
    private static final Map<Mob, List<GoalDefinition>> PENDING_GOAL_ADDITIONS = new ConcurrentHashMap<>();

    private static class GoalDefinition {
        final int priority;
        final Goal goalInstance;
        final Class<? extends Goal> goalClass;

        GoalDefinition(int priority, Goal goalInstance) {
            this.priority = priority;
            this.goalInstance = goalInstance;
            this.goalClass = goalInstance.getClass();
        }
    }

    private static void scheduleAddGoal(Mob mob, int priority, Goal goal) {
        PENDING_GOAL_ADDITIONS.computeIfAbsent(mob, k -> new ArrayList<>()).add(new GoalDefinition(priority, goal));
    }

    private static long lastMobCountUpdateTime_ServerTick = -1;
    private static int cachedAttractedMobCount_ServerTick = 0;
    private static Set<EntityType<?>> CACHED_ATTRACTED_ENTITY_TYPES = null;
    private static List<String> lastKnownAttractedEntitiesConfig_Copy = null;


    public static Set<EntityType<?>> getCachedAttractedEntityTypes() {
        List<? extends String> currentConfigListFromGetter = SoundAttractConfig.COMMON.attractedEntities.get();
        List<String> currentConfigListMutableCopy = new ArrayList<>(currentConfigListFromGetter);

        if (CACHED_ATTRACTED_ENTITY_TYPES == null || lastKnownAttractedEntitiesConfig_Copy == null || !lastKnownAttractedEntitiesConfig_Copy.equals(currentConfigListMutableCopy)) {
            if (SoundAttractConfig.COMMON.debugLogging.get() && CACHED_ATTRACTED_ENTITY_TYPES != null) {
                SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Attracted entities config changed, rebuilding EntityType cache.");
            }
            CACHED_ATTRACTED_ENTITY_TYPES = currentConfigListFromGetter.stream()
                    .map(idStr -> {
                        try {
                            return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(idStr));
                        } catch (Exception e) {
                            SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Invalid ResourceLocation for attracted entity type in config: {}", idStr, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            lastKnownAttractedEntitiesConfig_Copy = currentConfigListMutableCopy;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                String resultingTypes = CACHED_ATTRACTED_ENTITY_TYPES.stream().map(et -> BuiltInRegistries.ENTITY_TYPE.getKey(et).toString())
                        .collect(Collectors.joining(", "));
                SoundAttractMod.LOGGER.info("[DIAGNOSTIC] Final attracted EntityType cache contains: [{}]", resultingTypes);
            }
        }
        return CACHED_ATTRACTED_ENTITY_TYPES;
    }


    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (ServerLifecycleHooks.getCurrentServer() == null ||
            ServerLifecycleHooks.getCurrentServer().overworld() == null) {
            return;
        }
        ServerLevel serverLevel = ServerLifecycleHooks.getCurrentServer().overworld();
        long currentTime = serverLevel.getGameTime();
        int mobCountForCooldownManager;

        int mobCountUpdateInterval = SoundAttractConfig.COMMON.groupUpdateInterval.get();
        mobCountUpdateInterval = Math.max(20, mobCountUpdateInterval);

        if (currentTime - lastMobCountUpdateTime_ServerTick >= mobCountUpdateInterval || lastMobCountUpdateTime_ServerTick == -1) {
            int currentMobCount = 0;
            Set<Mob> countedMobsInTick = new HashSet<>();
            Set<EntityType<?>> attractedEntityTypes = getCachedAttractedEntityTypes();

            if (!attractedEntityTypes.isEmpty()) {
                for (ServerPlayer player : serverLevel.players()) {
                    int simDistanceBlocks = player.server.getPlayerList().getViewDistance() * 16;
                    AABB playerSimArea = player.getBoundingBox().inflate(simDistanceBlocks);
                    List<Mob> mobsNearPlayer = serverLevel.getEntitiesOfClass(Mob.class, playerSimArea);

                    for (Mob mob : mobsNearPlayer) {
                        if (mob.isAlive() && !mob.isRemoved() && attractedEntityTypes.contains(mob.getType())) {
                            if (countedMobsInTick.add(mob)) {
                                currentMobCount++;
                            }
                        }
                    }
                }
            }
            cachedAttractedMobCount_ServerTick = currentMobCount;
            lastMobCountUpdateTime_ServerTick = currentTime;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Updated attracted mob count for DynamicScanCooldownManager: {}", cachedAttractedMobCount_ServerTick);
            }
        }
        mobCountForCooldownManager = cachedAttractedMobCount_ServerTick;

        com.example.soundattract.DynamicScanCooldownManager.update(currentTime, mobCountForCooldownManager);
        SoundTracker.tick();


        BlockBreakerManager.processPendingActions();

        if (!PENDING_GOAL_ADDITIONS.isEmpty()) {
            Iterator<Map.Entry<Mob, List<GoalDefinition>>> iterator = PENDING_GOAL_ADDITIONS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Mob, List<GoalDefinition>> entry = iterator.next();
                Mob mob = entry.getKey();
                List<GoalDefinition> goalsToAdd = entry.getValue();

                if (mob.isAlive() && mob.level() != null && !mob.isRemoved()) {
                    for (GoalDefinition def : goalsToAdd) {
                        boolean goalExists = mob.goalSelector.getAvailableGoals().stream()
                                .anyMatch(wrappedGoal -> def.goalClass.isInstance(wrappedGoal.getGoal()));
                        if (!goalExists) {
                            mob.goalSelector.addGoal(def.priority, def.goalInstance);
                            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Added goal {} to mob {}", def.goalClass.getSimpleName(), mob.getName().getString());
                            }
                        }
                    }
                }
                iterator.remove();
            }
        }
    }

    private void tryUpdateGroupsWithDelay(ServerLevel serverLevel) {
        long currentTime = serverLevel.getGameTime();
        ResourceKey<Level> dimensionKey = serverLevel.dimension();

        dimensionLoadTimes.putIfAbsent(dimensionKey, currentTime);

        long timeSinceLoad = currentTime - dimensionLoadTimes.get(dimensionKey);
        int delay = SoundAttractConfig.COMMON.initialGroupComputationDelay.get();

        if (timeSinceLoad >= delay) {
            MobGroupManager.updateGroups(serverLevel);
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!event.getLevel().isClientSide()) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                tryUpdateGroupsWithDelay(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public void onMobJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (event.getLevel().isClientSide()) return;

        Set<EntityType<?>> attractedEntityTypes = getCachedAttractedEntityTypes();
        boolean isAttractedByType = attractedEntityTypes.contains(mob.getType());
        boolean hasMatchingprofile = SoundAttractConfig.getMatchingProfile(mob) != null;
        if (!isAttractedByType && !hasMatchingprofile) {
            return;
        }

        double moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();


        scheduleAddGoal(mob, 2, new CombatBlockBreakAssistGoal(mob));

        scheduleAddGoal(mob, 3, new AttractionGoal(mob, moveSpeed));
        scheduleAddGoal(mob, 4, new FollowLeaderGoal(mob, moveSpeed));

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Scheduled goals for mob {} of type {}", mob.getName().getString(), EntityType.getKey(mob.getType()));
        }

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            tryUpdateGroupsWithDelay(serverLevel);
        }
    }
}