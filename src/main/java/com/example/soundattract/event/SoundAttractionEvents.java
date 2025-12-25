package com.example.soundattract.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.BlockBreakerManager;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.ai.PickUpAndThrowToSoundGoal;
import com.example.soundattract.ai.TeleportToSoundGoal;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.data.DataDrivenTags;
import com.example.soundattract.worker.WorkerScheduler.GroupComputeResult;
import com.example.soundattract.worker.WorkSchedulerManager;
import com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat;
import com.example.soundattract.tracking.SoundTracker;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent; 
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoundAttractionEvents {

    private static final Map<Mob, List<GoalDefinition>> PENDING_GOAL_ADDITIONS = new ConcurrentHashMap<>();

    private static long serverTickCounter = 0;
    private static boolean initialDelayHasPassed = false;

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
    private static Set<EntityType<?>> CACHED_BLACKLISTED_ENTITY_TYPES = null;

    public static void invalidateCachedEntityTypes() {
        CACHED_ATTRACTED_ENTITY_TYPES = null;
        CACHED_BLACKLISTED_ENTITY_TYPES = null;
    }

    private static Set<EntityType<?>> getEntityTypesForTag(TagKey<EntityType<?>> tagKey) {
        Set<EntityType<?>> result = new HashSet<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            if (type == null) {
                continue;
            }
            try {
                if (type.is(tagKey)) {
                    result.add(type);
                }
            } catch (Exception e) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Error checking tag {} for entity type {}", tagKey.location(), ForgeRegistries.ENTITY_TYPES.getKey(type), e);
                }
            }
        }
        return result;
    }

    public static Set<EntityType<?>> getCachedAttractedEntityTypes() {
        if (CACHED_ATTRACTED_ENTITY_TYPES != null) {
            return CACHED_ATTRACTED_ENTITY_TYPES;
        }

        Set<EntityType<?>> configSet = new HashSet<>();
        boolean shouldRetryLater = false;
        for (String idStr : SoundAttractConfig.COMMON.attractedEntities.get()) {
            try {
                ResourceLocation id = ResourceLocation.parse(idStr);
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
                if (type != null) {
                    configSet.add(type);
                } else {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Unknown attracted entity type in config: {}", idStr);
                    }
                    if (id != null && !"minecraft".equals(id.getNamespace()) && ModList.get().isLoaded(id.getNamespace())) {
                        shouldRetryLater = true;
                    }
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Invalid ResourceLocation for attracted entity type in config: {}", idStr, e);
            }
        }

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        Set<EntityType<?>> tagSet = enableDataDriven ? getEntityTypesForTag(DataDrivenTags.ATTRACTED_MOBS) : java.util.Collections.emptySet();

        Set<EntityType<?>> result = new HashSet<>();
        if (!enableDataDriven || tagSet.isEmpty()) {
            result.addAll(configSet);
        } else {
            String priority = SoundAttractConfig.COMMON.datapackPriority.get();
            boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
            if (datapackOverConfig) {
                result.addAll(tagSet);
            } else {
                result.addAll(configSet);
                result.addAll(tagSet);
            }
        }

        if (shouldRetryLater) {
            return result;
        }

        CACHED_ATTRACTED_ENTITY_TYPES = result;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            String resultingTypes = result.stream()
                    .map(et -> ForgeRegistries.ENTITY_TYPES.getKey(et).toString())
                    .collect(Collectors.joining(", "));
            SoundAttractMod.LOGGER.info("[DIAGNOSTIC] Final attracted EntityType cache contains: [{}]", resultingTypes);
        }

        return CACHED_ATTRACTED_ENTITY_TYPES;
    }

    public static Set<EntityType<?>> getCachedBlacklistedEntityTypes() {
        if (CACHED_BLACKLISTED_ENTITY_TYPES != null) {
            return CACHED_BLACKLISTED_ENTITY_TYPES;
        }

        Set<EntityType<?>> configSet = new HashSet<>();
        boolean shouldRetryLater = false;
        for (String idStr : SoundAttractConfig.COMMON.mobBlacklist.get()) {
            try {
                ResourceLocation id = ResourceLocation.parse(idStr);
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
                if (type != null) {
                    configSet.add(type);
                } else {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Unknown blacklisted entity type in config: {}", idStr);
                    }
                    if (id != null && !"minecraft".equals(id.getNamespace()) && ModList.get().isLoaded(id.getNamespace())) {
                        shouldRetryLater = true;
                    }
                }
            } catch (Exception e) {
                SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Invalid ResourceLocation for blacklisted entity type in config: {}", idStr, e);
            }
        }

        boolean enableDataDriven = SoundAttractConfig.COMMON.enableDataDriven.get();
        Set<EntityType<?>> tagSet = enableDataDriven ? getEntityTypesForTag(DataDrivenTags.BLACKLISTED_MOBS) : java.util.Collections.emptySet();

        Set<EntityType<?>> result = new HashSet<>();
        if (!enableDataDriven || tagSet.isEmpty()) {
            result.addAll(configSet);
        } else {
            String priority = SoundAttractConfig.COMMON.datapackPriority.get();
            boolean datapackOverConfig = "datapack_over_config".equalsIgnoreCase(priority);
            if (datapackOverConfig) {
                result.addAll(tagSet);
            } else {
                result.addAll(configSet);
                result.addAll(tagSet);
            }
        }

        if (shouldRetryLater) {
            return result;
        }

        CACHED_BLACKLISTED_ENTITY_TYPES = result;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            String resultingTypes = result.stream()
                    .map(et -> ForgeRegistries.ENTITY_TYPES.getKey(et).toString())
                    .collect(Collectors.joining(", "));
            SoundAttractMod.LOGGER.info("[DIAGNOSTIC] Final blacklisted EntityType cache contains: [{}]", resultingTypes);
        }

        return CACHED_BLACKLISTED_ENTITY_TYPES;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            serverTickCounter++;
            if (!initialDelayHasPassed) {
                int delay = SoundAttractConfig.COMMON.initialGroupComputationDelay.get();
                if (serverTickCounter >= delay) {
                    initialDelayHasPassed = true;
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Initial group computation delay of {} ticks has passed. Enabling group updates.", delay);
                    }
                }
            }
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            if (!initialDelayHasPassed) return;
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

            com.example.soundattract.runtime.DynamicScanCooldownManager.update(currentTime, mobCountForCooldownManager);
            SoundTracker.tick();
            BlockBreakerManager.processPendingActions();
            try {
                                List<GroupComputeResult> results = WorkSchedulerManager.get().drainGroupResults();
                if (!results.isEmpty()) {
                    for (GroupComputeResult r : results) {
                        if (r.dimension() == null) continue;
                        ServerLevel level = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, r.dimension()));
                        if (level != null) {
                            com.example.soundattract.ai.MobGroupManager.applyGroupResult(level, r);
                        }
                    }
                }
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.error("[SoundAttractionEvents] Applying group results failed", t);
                }
            }
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
                if (initialDelayHasPassed) {
                    com.example.soundattract.ai.MobGroupManager.updateGroups(serverLevel);

                    com.example.soundattract.ai.RaidManager.tick(serverLevel);
                }
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

        if (SoundAttractConfig.COMMON.enableSmartBrainLibIntegration.get()) {
            if (SmartBrainLibCompat.tryAttachSoundAttractBrain(mob)) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Using SmartBrainLib integration for mob {} of type {}", mob.getName().getString(), EntityType.getKey(mob.getType()));
                }
                return;
            }
        }

        double moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();

        // Enhanced AI-inspired special actions
        // Teleport to sound
        if (SoundAttractConfig.COMMON.enableTeleportToSound.get()) {
            try {
                TagKey<EntityType<?>> canTeleportTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(SoundAttractConfig.COMMON.teleportCanTeleportTag.get()));
                if (mob.getType().is(canTeleportTag)) {
                    scheduleAddGoal(mob, 2, new TeleportToSoundGoal(mob));
                }
            } catch (Exception e) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Failed to apply TeleportToSoundGoal tag check for mob {}: {}", EntityType.getKey(mob.getType()), e.getMessage());
                }
            }
        }

        // Pick up and throw to sound
        if (SoundAttractConfig.COMMON.enablePickUpAndThrowToSound.get()) {
            try {
                TagKey<EntityType<?>> canPickUpTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(SoundAttractConfig.COMMON.pickUpCanPickUpTag.get()));
                if (mob.getType().is(canPickUpTag)) {
                    scheduleAddGoal(mob, 2, new PickUpAndThrowToSoundGoal(mob));
                }
            } catch (Exception e) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Failed to apply PickUpAndThrowToSoundGoal tag check for mob {}: {}", EntityType.getKey(mob.getType()), e.getMessage());
                }
            }
        }

        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {



            scheduleAddGoal(mob, 3, new LeaderAttractionGoal(mob, moveSpeed));
            scheduleAddGoal(mob, 1, new FollowerEdgeRelayGoal(mob, moveSpeed));
        } else {

            scheduleAddGoal(mob, 3, new AttractionGoal(mob, moveSpeed));
        }
        scheduleAddGoal(mob, 4, new FollowLeaderGoal(mob, moveSpeed));

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Scheduled goals for mob {} of type {}", mob.getName().getString(), EntityType.getKey(mob.getType()));
        }

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (initialDelayHasPassed) {
                com.example.soundattract.ai.MobGroupManager.updateGroups(serverLevel);
            }
        }
    }
}
