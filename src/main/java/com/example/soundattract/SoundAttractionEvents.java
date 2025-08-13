package com.example.soundattract;

import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.ai.FollowLeaderGoal;
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

import java.util.HashSet;
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

    public static int onServerTick(ServerWorld serverWorld) {
        if (com.example.soundattract.SoundAttractMod.CONFIG == null) {
            return 0;
        }


        SoundTracker.pruneIrrelevantSounds(serverWorld);
        SoundTracker.tick(serverWorld);

        com.example.soundattract.ai.MobGroupManager.scheduleNearbyCellsForPlayers(serverWorld, serverWorld.getTime());
        com.example.soundattract.DynamicScanCooldownManager.tickScheduler(serverWorld, serverWorld.getTime());

        int mobCount = 0;
        java.util.Set<String> attractedTypesForCount = new HashSet<>(com.example.soundattract.SoundAttractMod.CONFIG.attractedEntities);
        for (net.minecraft.entity.Entity entity : serverWorld.iterateEntities()) {
            if (entity instanceof MobEntity mob) {
                if (attractedTypesForCount.contains(Registries.ENTITY_TYPE.getId(mob.getType()).toString())) {
                    mobCount++;
                }
            }
        }



        if (!com.example.soundattract.DynamicScanCooldownManager.shouldScanThisTick(0, serverWorld.getTime())) {
            return mobCount;
        }

        try {
            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Firing heavy scan/update tick for dimension {}.", serverWorld.getRegistryKey().getValue());
            }

            java.util.Set<String> attractedTypes = new HashSet<>(com.example.soundattract.SoundAttractMod.CONFIG.attractedEntities);
            int checkedEntities = 0;

            java.util.Map<String, java.util.Map<Long, java.util.List<MobEntity>>> stackedMobs = new java.util.HashMap<>();
            for (net.minecraft.entity.Entity entity : serverWorld.iterateEntities()) {
                checkedEntities++;
                if (!(entity instanceof MobEntity mob)) continue;
                if (!serverWorld.getWorldBorder().contains(mob.getBlockPos())) continue;

                String mobTypeId = Registries.ENTITY_TYPE.getId(mob.getType()).toString();
                if (attractedTypes.contains(mobTypeId)) {
                    long cellKey = (long) (mob.getBlockPos().getX() >> 4) << 32 | (mob.getBlockPos().getZ() >> 4 & 0xFFFFFFFFL);
                    stackedMobs.computeIfAbsent(mobTypeId, k -> new java.util.HashMap<>())
                        .computeIfAbsent(cellKey, k -> new java.util.ArrayList<>())
                        .add(mob);
                }
            }

            java.util.List<MobEntity> mobEntities = new java.util.ArrayList<>();
            for (var typeEntry : stackedMobs.entrySet()) {
                for (var cellEntry : typeEntry.getValue().entrySet()) {
                    if (!cellEntry.getValue().isEmpty()) {
                        mobEntities.add(cellEntry.getValue().get(0));
                    }
                }
            }

            com.example.soundattract.ai.MobGroupManager.updateGroups(serverWorld);


            for (SoundTracker.SoundRecord sound : SoundTracker.getRecentSounds(serverWorld)) {
                java.util.List<MobEntity> mobs = SoundTracker.getMobsForSound(
                    mobEntities,
                    sound,
                    mob -> com.example.soundattract.ai.MobGroupManager.isEdgeMobEntity(mob)
                            || com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob
                            || com.example.soundattract.ai.MobGroupManager.isDeserter(mob)
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
                    } else if (isEdge) {
                        MobEntity leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                         if (leader != null && leader != mob) {
                            com.example.soundattract.ai.AttractionGoal.handleRelayToLeader(leader, sound, mob);
                         }
                    } else if (isLeader) {
                        com.example.soundattract.ai.AttractionGoal.handleLeaderObjective(mob, sound);
                    }
                }
            }
            
            if (com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[SoundAttractionEvents] Heavy scan complete. Stacked mob groups: {}, Total attracted entities counted: {}", mobEntities.size(), mobCount);
            }

        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[SoundAttractionEvents] Exception in heavy scan tick for dimension " + serverWorld.getRegistryKey().getValue(), e);
        }
        return mobCount;
    }

    public static void onWorldTick(ServerWorld serverWorld) {

    }


    public static void onEntityJoinWorld(MobEntity mob) {
        if (mob.getWorld().isClient()) return;
        Identifier entityId = Registries.ENTITY_TYPE.getId(mob.getType());
        String entityIdStr = entityId.toString();
        if (!SoundAttractMod.CONFIG.attractedEntities.contains(entityIdStr)) {
            return;
        }
        double moveSpeed = SoundAttractMod.CONFIG.mobMoveSpeed;
        boolean attractionGoalExists = ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().getGoals().stream()
                .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof AttractionGoal);
        if (!attractionGoalExists) {
            ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().add(0, new AttractionGoal(mob, moveSpeed));
        }
        boolean followLeaderGoalExists = ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().getGoals().stream()
                .anyMatch(prioritizedGoal -> prioritizedGoal.getGoal() instanceof FollowLeaderGoal);
        if (!followLeaderGoalExists) {
            ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().add(1, new FollowLeaderGoal(mob, moveSpeed));
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