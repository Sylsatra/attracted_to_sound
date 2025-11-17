package com.example.soundattract;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
import com.example.soundattract.ai.BlockBreakerManager;
import com.example.soundattract.ai.TeleportToSoundGoal;
import com.example.soundattract.ai.PickUpAndThrowToSoundGoal;
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

    public static void onServerTick(net.minecraft.server.MinecraftServer server) {
        for (ServerWorld serverWorld : server.getWorlds()) {
            onWorldTick(serverWorld);
        }
    }

    private static void processWorldTick(ServerWorld serverWorld) {
        com.example.soundattract.ai.MobGroupManager.scheduleNearbyCellsForPlayers(serverWorld, serverWorld.getTime());
        com.example.soundattract.DynamicScanCooldownManager.tickScheduler(serverWorld, serverWorld.getTime());

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

            java.util.List<MobEntity> mobEntities = new java.util.ArrayList<>();
            for (net.minecraft.entity.Entity entity : serverWorld.iterateEntities()) {
                if (entity instanceof MobEntity mob) {
                    String mobTypeId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(mob.getType()).toString();
                    if (attractedTypes.contains(mobTypeId)) {
                        mobEntities.add(mob);
                    }
                }
            }

            for (SoundTracker.SoundRecord sound : SoundTracker.getRecentSounds(serverWorld)) {
                com.example.soundattract.ai.MobGroupManager.scheduleCellsForSound(sound.pos, sound.range, serverWorld, serverWorld.getTime());

                java.util.List<MobEntity> mobs = SoundTracker.getMobsForSound(
                    mobEntities,
                    sound,
                    mob -> {
                        return com.example.soundattract.ai.MobGroupManager.isEdgeMobEntity(mob)
                            || com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob
                            || com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
                    }
                );
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Processing sound at {} (range={}) found {} eligible mobs", sound.pos, sound.range, mobs.size());
                }
                for (MobEntity mob : mobs) {
                    boolean isLeader = com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob;
                    boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMobEntity(mob);
                    boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
                    if (isDeserter) {
                        com.example.soundattract.ai.AttractionGoal.handleSoundAttraction(mob, sound);
                        if (SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Deserter {} acts on sound {}", mob.getUuid(), sound.pos);
                        }
                        continue;
                    }
                    if (isEdge) {
                        if (!com.example.soundattract.SoundAttractMod.CONFIG.edgeMobSmartBehavior) {
                            MobEntity leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                            double maxLeaderEdgeDistance = com.example.soundattract.SoundAttractMod.CONFIG.groupDistance;
                            if (leader != null && leader != mob && leader.squaredDistanceTo(mob) <= maxLeaderEdgeDistance * maxLeaderEdgeDistance) {
                                com.example.soundattract.ai.AttractionGoal.handleRelayToLeader(leader, sound, mob);
                                if (SoundAttractMod.CONFIG.debugLogging) {
                                    SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Edge {} relays sound {} to leader {} immediately", mob.getUuid(), sound.pos, leader.getUuid());
                                }
                            }
                        } else {
                            long now = System.currentTimeMillis();
                            com.example.soundattract.ai.EdgeRelayManager.RelayState state = com.example.soundattract.ai.EdgeRelayManager.getRelayState(mob);
                            if (state == null) {
                                com.example.soundattract.ai.EdgeRelayManager.startRelay(mob, sound.pos, 2 * 60 * 1000L, now);
                                com.example.soundattract.ai.AttractionGoal.handleEdgeInvestigate(mob, sound);
                                if (SoundAttractMod.CONFIG.debugLogging) {
                                    SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Edge {} starts delayed relay for sound {}", mob.getUuid(), sound.pos);
                                }
                            } else if (!state.cancelled && !state.completed) {
                                if (now - state.startTime > state.delayMillis) {
                                    MobEntity leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                                    if (leader != null && leader != mob) {
                                        com.example.soundattract.ai.AttractionGoal.handleRelayToLeader(leader, sound, mob);
                                        com.example.soundattract.ai.EdgeRelayManager.completeRelay(mob);
                                        if (SoundAttractMod.CONFIG.debugLogging) {
                                            SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Edge {} delayed relay expired, relaying sound {} to leader {}", mob.getUuid(), sound.pos, leader.getUuid());
                                        }
                                    }
                                }
                            }
                        }
                        continue;
                    }
                    if (isLeader) {
                        com.example.soundattract.ai.AttractionGoal.handleLeaderObjective(mob, sound);
                        if (SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Leader {} ready to update group objective for sound {}", mob.getUuid(), sound.pos);
                        }
                        continue;
                    }
                }
            }

            com.example.soundattract.DynamicScanCooldownManager.update(serverWorld.getTime(), mobEntities.size());
            SoundTracker.tick(serverWorld);

            BlockBreakerManager.processPendingActions();
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[SoundAttractionEvents] Exception in onServerTick", e);
        }
    }

    public static void onWorldTick(ServerWorld serverWorld) {
        SoundTracker.pruneIrrelevantSounds(serverWorld);
        com.example.soundattract.DynamicScanCooldownManager.update(serverWorld.getTime(), 0);
        if (!com.example.soundattract.DynamicScanCooldownManager.shouldScanThisTick(0, serverWorld.getTime())) {
            return;
        }
        com.example.soundattract.ai.MobGroupManager.updateGroups(serverWorld);

        processWorldTick(serverWorld);

        BlockBreakerManager.processPendingActions();
    }

    public static void onEntityJoinWorld(MobEntity mob) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(mob.getType());
        if (entityId == null) return;
        String entityIdStr = entityId.toString();
        if (!SoundAttractMod.CONFIG.attractedEntities.contains(entityIdStr)) {
            return;
        }
        double moveSpeed = SoundAttractMod.CONFIG.mobMoveSpeed;
        com.example.soundattract.mixin.MobEntityAccessor accessor = (com.example.soundattract.mixin.MobEntityAccessor) mob;

        boolean attractionGoalExists = accessor.getGoalSelector().getGoals().stream()
                .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof AttractionGoal);
        if (!attractionGoalExists) {
            accessor.getGoalSelector().add(3, new AttractionGoal(mob, moveSpeed));
        }

        boolean followLeaderGoalExists = accessor.getGoalSelector().getGoals().stream()
                .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof FollowLeaderGoal);
        if (!followLeaderGoalExists) {
            accessor.getGoalSelector().add(3, new FollowLeaderGoal(mob, moveSpeed));
        }

        if (SoundAttractMod.CONFIG.enableTeleportToSound) {
            boolean hasTeleport = accessor.getGoalSelector().getGoals().stream()
                    .anyMatch(pg -> pg.getGoal() instanceof TeleportToSoundGoal);
            if (!hasTeleport) {
                accessor.getGoalSelector().add(3, new TeleportToSoundGoal(mob));
            }
        }

        if (SoundAttractMod.CONFIG.enablePickUpAndThrowToSound) {
            boolean hasPickup = accessor.getGoalSelector().getGoals().stream()
                    .anyMatch(pg -> pg.getGoal() instanceof PickUpAndThrowToSoundGoal);
            if (!hasPickup) {
                accessor.getGoalSelector().add(3, new PickUpAndThrowToSoundGoal(mob));
            }
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