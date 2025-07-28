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
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.config.SoundAttractConfig;
 
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
    private static Set<EntityType<?>> CACHED_BLACKLISTED_ENTITY_TYPES = null;
    private static List<String> lastKnownBlacklistConfig_Copy = null;

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
            lastKnownAttractedEntitiesConfig_Copy = currentConfigListMutableCopy;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                String resultingTypes = CACHED_ATTRACTED_ENTITY_TYPES.stream().map(et -> ForgeRegistries.ENTITY_TYPES.getKey(et).toString())
                        .collect(Collectors.joining(", "));
                SoundAttractMod.LOGGER.info("[DIAGNOSTIC] Final attracted EntityType cache contains: [{}]", resultingTypes);
            }
        }
        return CACHED_ATTRACTED_ENTITY_TYPES;
    }

    public static Set<EntityType<?>> getCachedBlacklistedEntityTypes() {
        List<? extends String> currentConfigListFromGetter = SoundAttractConfig.COMMON.mobBlacklist.get();
        List<String> currentConfigListMutableCopy = new ArrayList<>(currentConfigListFromGetter);
        if (CACHED_BLACKLISTED_ENTITY_TYPES == null || lastKnownBlacklistConfig_Copy == null || !lastKnownBlacklistConfig_Copy.equals(currentConfigListMutableCopy)) {
            if (SoundAttractConfig.COMMON.debugLogging.get() && CACHED_BLACKLISTED_ENTITY_TYPES != null) {
                SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Mob blacklist config changed, rebuilding EntityType cache.");
            }
            CACHED_BLACKLISTED_ENTITY_TYPES = currentConfigListFromGetter.stream()
                    .map(idStr -> {
                        try {
                            return ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(idStr));
                        } catch (Exception e) {
                            SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Invalid ResourceLocation for blacklisted entity type in config: {}", idStr, e);
                            return null;
                        }
                    }).filter(Objects::nonNull).collect(Collectors.toSet());
            lastKnownBlacklistConfig_Copy = currentConfigListMutableCopy;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                String resultingTypes = CACHED_BLACKLISTED_ENTITY_TYPES.stream().map(et -> ForgeRegistries.ENTITY_TYPES.getKey(et).toString())
                        .collect(Collectors.joining(", "));
                SoundAttractMod.LOGGER.info("[DIAGNOSTIC] Final blacklisted EntityType cache contains: [{}]", resultingTypes);
            }
        }
        return CACHED_BLACKLISTED_ENTITY_TYPES;
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
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
            if (event.level instanceof ServerLevel serverLevel) {
                com.example.soundattract.ai.MobGroupManager.updateGroups(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public static void onMobJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (event.getLevel().isClientSide()) return;

        Set<EntityType<?>> blacklistedEntityTypes = getCachedBlacklistedEntityTypes();
        if (blacklistedEntityTypes.contains(mob.getType())) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Mob {} is on the blacklist, ignoring.", EntityType.getKey(mob.getType()));
            }
            return;
        }
        
        Set<EntityType<?>> attractedEntityTypes = getCachedAttractedEntityTypes();
        boolean isAttractedByType = attractedEntityTypes.contains(mob.getType());
        boolean hasMatchingprofile = SoundAttractConfig.getMatchingProfile(mob) != null;
        if (!isAttractedByType && !hasMatchingprofile) {
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