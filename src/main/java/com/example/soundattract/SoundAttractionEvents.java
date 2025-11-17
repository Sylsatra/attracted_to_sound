package com.example.soundattract;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.ai.BlockBreakerManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.Identifier;

public class SoundAttractionEvents {

    private enum PlayerAction {
        IDLE, CRAWLING, SNEAKING, WALKING, SPRINTING, SPRINT_JUMPING
    }

    private static final double IDLE_THRESHOLD_SQ = 0.001 * 0.001;
    private static final double CRAWLING_THRESHOLD_SQ = 0.03 * 0.03;
    private static final double SNEAKING_SPEED_SQ = 0.066 * 0.066;
    private static final double WALKING_SPEED_SQ = 0.216 * 0.216;

    public static void onServerTick(ServerWorld serverWorld) {
        com.example.soundattract.ai.MobGroupManager.scheduleNearbyCellsForPlayers(serverWorld, serverWorld.getTime());
        com.example.soundattract.DynamicScanCooldownManager.tickScheduler(serverWorld, serverWorld.getTime());
        long tickStart = System.nanoTime();
    if (com.example.soundattract.SoundAttractMod.CONFIG == null) {
        com.example.soundattract.SoundAttractMod.LOGGER.error("[SoundAttractionEvents] CONFIG is null in onServerTick! Skipping tick.");
        return;
    }
    if (!com.example.soundattract.DynamicScanCooldownManager.shouldScanThisTick(0, serverWorld.getTime())) {
        return;
    }
    try {
    java.util.Set<String> attractedTypes = new java.util.HashSet<>();
    for (Object o : com.example.soundattract.SoundAttractMod.CONFIG.attractedEntities) {
        attractedTypes.add(o.toString());
    }
    int mobCount = 0;
    int nullMobTypeCount = 0;
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
        com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Starting entity scan in onServerTick");
    }
            java.util.Map<String, java.util.Map<Long, java.util.List<MobEntity>>> stackedMobs = new java.util.HashMap<>();
            int checkedEntities = 0;
            int skippedEntities = 0;
            if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Scanning entities manually with stacking and spatial partitioning");
            }
            long scanStart = System.nanoTime();
            for (net.minecraft.entity.Entity entity : serverWorld.iterateEntities()) {
                checkedEntities++;
                if (!(entity instanceof MobEntity mob)) continue;
                try {
                    if (mob == null) {
                        skippedEntities++;
                        com.example.soundattract.SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Manual scan: null MobEntity encountered");
                        continue;
                    }
                    BlockPos pos = null;
                    try {
                        pos = mob.getBlockPos();
                    } catch (Exception ex) {
                        skippedEntities++;
                        com.example.soundattract.SoundAttractMod.LOGGER.error("[SoundAttractionEvents] Manual scan: Exception getting block pos", ex);
                        continue;
                    }
                    if (pos == null || !serverWorld.getWorldBorder().contains(pos)) {
                        continue;
                    }
                    String mobTypeId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(mob.getType()).toString();
                    if (!attractedTypes.contains(mobTypeId)) {
                        continue;
                    }
                    long cellKey = (pos.getX() >> 4) << 32 | (pos.getZ() >> 4 & 0xFFFFFFFFL);
                    stackedMobs.computeIfAbsent(mobTypeId, k -> new java.util.HashMap<>())
                        .computeIfAbsent(cellKey, k -> new java.util.ArrayList<>())
                        .add(mob);
                } catch (Exception ex) {
                    skippedEntities++;
                    com.example.soundattract.SoundAttractMod.LOGGER.error("[SoundAttractionEvents] Manual scan: Exception processing entity", ex);
                }
            }
            long scanEnd = System.nanoTime();
            long stackStart = scanEnd;
            java.util.List<MobEntity> mobEntities = new java.util.ArrayList<>();
            for (var typeEntry : stackedMobs.entrySet()) {
                for (var cellEntry : typeEntry.getValue().entrySet()) {
                    java.util.List<MobEntity> group = cellEntry.getValue();
                    if (!group.isEmpty()) {
                        mobEntities.add(group.get(0));
                    }
                }
            }
            long stackEnd = System.nanoTime();
            if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundAttractionEvents] mobEntities.size: {}, checked: {}, skipped: {}", mobEntities.size(), checkedEntities, skippedEntities);
            }
            long processStart = System.nanoTime();


            for (MobEntity mob : mobEntities) {
                try {
                    com.example.soundattract.SoundTracker.submitAsyncSoundScore(serverWorld, mob, mob.getBlockPos());
                } catch (Throwable t) {
                    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                        com.example.soundattract.SoundAttractMod.LOGGER.debug("[SoundAttractionEvents] submitAsyncSoundScore failed for mob {}: {}", mob.getUuid(), t.toString());
                    }
                }
            }
            for (SoundTracker.SoundRecord sound : SoundTracker.getRecentSounds()) {
                java.util.List<MobEntity> mobs = SoundTracker.getMobsForSound(
                    serverWorld,
                    sound,
                    mob -> {
                        return com.example.soundattract.ai.MobGroupManager.isEdgeMobEntity(mob)
                            || com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob
                            || com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
                    }
                );
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Processing sound at {} (range={}) found {} eligible mobs", sound.pos, sound.range, mobs.size());
                }
                for (MobEntity mob : mobs) {
                    boolean isLeader = com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob;
                    boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMobEntity(mob);
                    boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
                    if (isDeserter) {
                        com.example.soundattract.ai.AttractionGoal.handleSoundAttraction(mob, sound);
                        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Deserter {} acts on sound {}", mob.getUuid(), sound.pos);
                        }
                        continue;
                    }
                    if (isEdge) {
                        if (!com.example.soundattract.SoundAttractMod.CONFIG.edgeMobSmartBehavior) {
                            MobEntity leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                            if (leader != null && leader != mob && leader.squaredDistanceTo(mob) <= sound.range * sound.range) {
                                com.example.soundattract.ai.AttractionGoal.handleRelayToLeader(leader, sound, mob);
                                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                                    SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Edge {} relays sound {} to leader {} immediately", mob.getUuid(), sound.pos, leader.getUuid());
                                }
                            }
                        } else {

                        }
                        continue;
                    }
                    if (isLeader) {

                        continue;
                    }
                }
            }



            if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundAttractionEvents] mobEntities.size: {}, checked: {}, skipped: {}", mobEntities.size(), checkedEntities, skippedEntities);
                if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundAttractionEvents] AttractedTypes size: {}, Stacked mob groups: {}, Null mob types: {}, Attracted mob count: {}", attractedTypes.size(), mobEntities.size(), nullMobTypeCount, mobCount);
}
            }
            com.example.soundattract.DynamicScanCooldownManager.update(serverWorld.getTime(), mobEntities.size());
            SoundTracker.tick();

            for (com.example.soundattract.SoundTracker.SoundRecord sound : com.example.soundattract.SoundTracker.RECENT_SOUNDS) {
                com.example.soundattract.ai.MobGroupManager.scheduleCellsForSound(sound.pos, sound.range, serverWorld, serverWorld.getTime());
            }

            BlockBreakerManager.processPendingActions();
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[SoundAttractionEvents] Exception in onServerTick", e);
        }
    }

    public static void onWorldTick(ServerWorld serverWorld) {
        SoundTracker.pruneIrrelevantSounds(serverWorld);

        com.example.soundattract.ai.RaidManager.tick(serverWorld);


        com.example.soundattract.DynamicScanCooldownManager.update(serverWorld.getTime(), 0);
        if (com.example.soundattract.DynamicScanCooldownManager.shouldScanThisTick(0, serverWorld.getTime())) {
            com.example.soundattract.ai.MobGroupManager.updateGroups(serverWorld);
        }

        BlockBreakerManager.processPendingActions();
    }

    public static void onEntityJoinWorld(MobEntity mob) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(mob.getType());
        if (entityId == null || SoundAttractMod.CONFIG == null) return;
        String entityIdStr = entityId.toString();
        if (!SoundAttractMod.CONFIG.attractedEntities.contains(entityIdStr)) {
            return;
        }
        double moveSpeed = SoundAttractMod.CONFIG.mobMoveSpeed;
        boolean edgeSmart = SoundAttractMod.CONFIG.edgeMobSmartBehavior;

        try {
            if (SoundAttractMod.CONFIG.enableTeleportToSound && SoundAttractMod.CONFIG.teleportCanTeleportTag != null) {
                Identifier tagId = new Identifier(SoundAttractMod.CONFIG.teleportCanTeleportTag);
                net.minecraft.registry.tag.TagKey<net.minecraft.entity.EntityType<?>> canTeleportTag =
                        net.minecraft.registry.tag.TagKey.of(Registries.ENTITY_TYPE.getKey(), tagId);
                if (mob.getType().isIn(canTeleportTag)) {
                    boolean exists = ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().getGoals().stream()
                            .anyMatch(pg -> pg.getGoal() instanceof com.example.soundattract.ai.TeleportToSoundGoal);
                    if (!exists) {
                        ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().add(2, new com.example.soundattract.ai.TeleportToSoundGoal(mob));
                    }
                }
            }
        } catch (Exception e) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Failed to apply TeleportToSoundGoal for mob {}: {}", entityIdStr, e.toString());
            }
        }

        try {
            if (SoundAttractMod.CONFIG.enablePickUpAndThrowToSound && SoundAttractMod.CONFIG.pickUpCanPickUpTag != null) {
                Identifier tagId = new Identifier(SoundAttractMod.CONFIG.pickUpCanPickUpTag);
                net.minecraft.registry.tag.TagKey<net.minecraft.entity.EntityType<?>> canPickUpTag =
                        net.minecraft.registry.tag.TagKey.of(Registries.ENTITY_TYPE.getKey(), tagId);
                if (mob.getType().isIn(canPickUpTag)) {
                    boolean exists = ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().getGoals().stream()
                            .anyMatch(pg -> pg.getGoal() instanceof com.example.soundattract.ai.PickUpAndThrowToSoundGoal);
                    if (!exists) {
                        ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().add(2, new com.example.soundattract.ai.PickUpAndThrowToSoundGoal(mob));
                    }
                }
            }
        } catch (Exception e) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[SoundAttractionEvents] Failed to apply PickUpAndThrowToSoundGoal for mob {}: {}", entityIdStr, e.toString());
            }
        }
        if (edgeSmart) {

            boolean leaderGoalExists = ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().getGoals().stream()
                    .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof com.example.soundattract.ai.LeaderAttractionGoal);
            if (!leaderGoalExists) {
                ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().add(0, new com.example.soundattract.ai.LeaderAttractionGoal(mob, moveSpeed));
            }
            boolean followerGoalExists = ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().getGoals().stream()
                    .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof com.example.soundattract.ai.FollowerEdgeRelayGoal);
            if (!followerGoalExists) {
                ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().add(0, new com.example.soundattract.ai.FollowerEdgeRelayGoal(mob, moveSpeed));
            }
        } else {

            boolean attractionGoalExists = ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().getGoals().stream()
                    .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof AttractionGoal);
            if (!attractionGoalExists) {
                ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().add(4, new AttractionGoal(mob, moveSpeed));
            }
        }
        boolean followLeaderGoalExists = ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().getGoals().stream()
                .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof FollowLeaderGoal);
        if (!followLeaderGoalExists) {
            ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().add(4, new FollowLeaderGoal(mob, moveSpeed));
        }
    }

    public static void onEntityJoinWorld(MobEntity mob, ServerWorld level) {
        if (level.isClient()) return;
        java.util.Set<String> attractedTypes = new java.util.HashSet<>();
        for (Object o : com.example.soundattract.SoundAttractMod.CONFIG.attractedEntities) {
            attractedTypes.add(o.toString());
        }
        String mobTypeId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(mob.getType()).toString();
        if (attractedTypes.contains(mobTypeId)) {
            com.example.soundattract.ai.MobGroupManager.updateGroups(level);
        }
    }

    public static class SoundMapping {
        public final Identifier soundEvent;
        public final int range;
        public final double weight;

        public SoundMapping(Identifier soundEvent, int range, double weight) {
            this.soundEvent = soundEvent;
            this.range = range;
            this.weight = weight;
        }

        public static SoundMapping forAnimator(Class<?> animatorClass) {
            return null;
        }
    }
}
