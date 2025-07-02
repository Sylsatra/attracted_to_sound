package com.example.soundattract;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.config.SoundAttractConfig; 
import java.util.*;
import java.util.concurrent.ConcurrentHashMap; 
import java.util.stream.Collectors; 
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType; 
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent; 
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoundAttractionEvents {

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
                            return ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(idStr));
                        } catch (Exception e) {
                            SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Invalid ResourceLocation for attracted entity type in config: {}", idStr, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            lastKnownAttractedEntitiesConfig_Copy = currentConfigListMutableCopy; // Store the new mutable copy
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Built attracted EntityType cache with {} types.", CACHED_ATTRACTED_ENTITY_TYPES.size());
            }
        }
        return CACHED_ATTRACTED_ENTITY_TYPES;
    }


    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() == null ||
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld() == null) {
                return;
            }
            ServerLevel serverLevel = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
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
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
            if (event.level instanceof ServerLevel serverLevel) {
                SoundTracker.pruneIrrelevantSounds(serverLevel);
                com.example.soundattract.ai.MobGroupManager.updateGroups(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public static void onMobJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (event.getLevel().isClientSide()) return;

        Set<EntityType<?>> attractedEntityTypes = getCachedAttractedEntityTypes();
        if (!attractedEntityTypes.contains(mob.getType())) {
            return;
        }

        double moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();

        scheduleAddGoal(mob, 3, new AttractionGoal(mob, moveSpeed));
        scheduleAddGoal(mob, 4, new FollowLeaderGoal(mob, moveSpeed));

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Scheduled goals for mob {} of type {}", mob.getName().getString(), EntityType.getKey(mob.getType()));
        }

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            com.example.soundattract.ai.MobGroupManager.updateGroups(serverLevel);
        }
    }
}