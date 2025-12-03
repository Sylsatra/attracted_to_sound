package com.example.soundattract.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import java.util.EnumSet;
import java.util.stream.StreamSupport;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

public class FollowLeaderGoal extends Goal {
    private final Mob mob;
    private final double moveSpeed;
    private Mob leader;
    private AttractionGoal leaderAttractionGoal = null;
    private Goal leaderPursuitGoal = null;
    private static final double MAX_DISTANCE = 12.0; 
    private Vec3 lastPos = null;
    private int stuckTicks = 0;
    private int stuckThreshold = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
    private int dynamicTickCounter = 0;
    private Vec3 lastRandomDest = null;
    private boolean hasPickedDest = false;

    public FollowLeaderGoal(Mob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private double getGroupDistance() {
        return com.example.soundattract.config.SoundAttractConfig.COMMON.groupDistance.get(); 
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) return false;
        leader = MobGroupManager.getLeader(mob);
        if (leader == null || leader == mob) return false; 
        if (!leader.isAlive()) return false;


        if (RaidManager.isRaidTicking(leader)) {
            return true;
        }

        if (RaidManager.isRaidAdvancing(leader)) {
            return true;
        }


        if (this.mob.distanceToSqr(leader) > getGroupDistance() * getGroupDistance()) return false; 
        boolean smartEdge = com.example.soundattract.config.SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        if (smartEdge && MobGroupManager.isEdgeMob(mob)) return false;
        leaderAttractionGoal = null;
        leaderPursuitGoal = null;
        leader.goalSelector.getRunningGoals().forEach(goal -> {
            if (leaderPursuitGoal == null) {
                if (goal.getGoal() instanceof AttractionGoal ag) {
                    leaderAttractionGoal = ag;
                    leaderPursuitGoal = ag;
                } else if (goal.getGoal() instanceof LeaderAttractionGoal lag) {
                    leaderPursuitGoal = lag;
                }
            }
        });
        if (leaderPursuitGoal == null) return false;
        if (leaderPursuitGoal instanceof AttractionGoal ag && !ag.isPursuingSound()) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) return false;
        if (leader == null || !leader.isAlive()) return false;


        if (RaidManager.isRaidTicking(leader)) {
            return true;
        }

        if (RaidManager.isRaidAdvancing(leader)) {
            net.minecraft.core.BlockPos raidTarget = RaidManager.getRaidTarget(leader);
            if (raidTarget == null) return false;
            double arrivalDistance = com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.get();
            if (this.mob.blockPosition().distSqr(raidTarget) < (arrivalDistance + 2.0) * (arrivalDistance + 2.0)) {
                return false;
            }
            return true;
        }

        if (this.mob.distanceToSqr(leader) > getGroupDistance() * getGroupDistance()) return false;
        leaderAttractionGoal = null;
        leaderPursuitGoal = StreamSupport.stream(
            leader.goalSelector.getRunningGoals().spliterator(), false)
            .map(WrappedGoal::getGoal)
            .filter(g -> (g instanceof AttractionGoal) || (g instanceof LeaderAttractionGoal))
            .findFirst()
            .orElse(null);

        if (leaderPursuitGoal == null) return false;
        if (leaderPursuitGoal instanceof AttractionGoal ag && !ag.isPursuingSound()) return false;
        if (leader.getNavigation().isDone()) return false;
        return true;
    }



    @Override
    public void tick() {
        if (leader == null) return;

        boolean debug = com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get();


        if (RaidManager.isRaidTicking(leader)) {
            if (debug) {
                com.example.soundattract.SoundAttractMod.LOGGER.info(
                    "[FollowLeaderGoal][RAID Rally] Mob {} rallying to leader {}",
                    mob.getName().getString(), leader.getName().getString());
            }
            if (!mob.isSprinting()) mob.setSprinting(true);
            double sprintMult = com.example.soundattract.config.SoundAttractConfig.COMMON.groupSprintMultiplier.get();
            mob.getNavigation().moveTo(leader.getX(), leader.getY(), leader.getZ(), moveSpeed * sprintMult);
            return;
        }


        if (RaidManager.isRaidAdvancing(leader)) {
            int scanCooldown = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
            int updateInterval = Math.max(1, scanCooldown / 2);
            dynamicTickCounter = (dynamicTickCounter + 1) % updateInterval;
            if (dynamicTickCounter != 0) return;

            BlockPos raidTarget = RaidManager.getRaidTarget(leader);
            if (raidTarget == null) return;
            double arrivalDistance = com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.get();

            if (!mob.isSprinting()) mob.setSprinting(true);

            if (!hasPickedDest) {
                java.util.Random rand = new java.util.Random(
                    mob.getUUID().hashCode() ^ raidTarget.hashCode()
                );
                double angle = rand.nextDouble() * 2 * Math.PI;
                double radius = arrivalDistance * Math.sqrt(rand.nextDouble());
                double offsetX = Math.cos(angle) * radius;
                double offsetZ = Math.sin(angle) * radius;

                int destX = raidTarget.getX() + (int) Math.floor(offsetX);
                int destZ = raidTarget.getZ() + (int) Math.floor(offsetZ);
                int groundY = mob.level().getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    destX, destZ
                );

                double finalX = destX + 0.5;
                double finalY = groundY;
                double finalZ = destZ + 0.5;
                lastRandomDest = new Vec3(finalX, finalY, finalZ);
                hasPickedDest = true;
                if (debug) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info(
                        "[FollowLeaderGoal][RAID Advance] Mob {} advancing to raid target area near {}",
                        mob.getName().getString(), raidTarget);
                }
            }

            if (lastRandomDest != null) {
                Vec3 curPos = mob.position();
                if (curPos.distanceToSqr(lastRandomDest) > 2.25) {
                    double sprintMult = com.example.soundattract.config.SoundAttractConfig.COMMON.groupSprintMultiplier.get();
                    mob.getNavigation().moveTo(
                        lastRandomDest.x, lastRandomDest.y, lastRandomDest.z, moveSpeed * sprintMult
                    );
                }

                if (lastPos != null && curPos.distanceToSqr(lastPos) < 0.04) {
                    stuckTicks++;
                    if (stuckTicks > stuckThreshold) {
                        hasPickedDest = false;
                        stuckTicks = 0;
                    }
                } else {
                    stuckTicks = 0;
                }
                lastPos = curPos;
            }
            return;
        }


        if (leaderPursuitGoal == null) return;
        if (leaderPursuitGoal instanceof AttractionGoal ag && !ag.isPursuingSound()) return;
        if (debug) {
            com.example.soundattract.SoundAttractMod.LOGGER.info(
                "[FollowLeaderGoal] Mob {} following leader {} (leader is pursuing sound)",
                mob.getName().getString(),
                leader.getName().getString()
            );
        }

        int scanCooldown = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
        int updateInterval = Math.max(1, scanCooldown / 2);
        dynamicTickCounter = (dynamicTickCounter + 1) % updateInterval;
        if (dynamicTickCounter != 0) return;

        BlockPos soundPos = null;
        try {
            Object pursuit = (leaderPursuitGoal != null) ? leaderPursuitGoal : leaderAttractionGoal;
            if (pursuit == null) return;
            java.lang.reflect.Field f = pursuit.getClass().getDeclaredField("targetSoundPos");
            f.setAccessible(true);
            soundPos = (BlockPos) f.get(pursuit);
        } catch (Exception e) {
        }
        if (soundPos == null) return;

        double arrivalDistance = com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.get();

        if (!hasPickedDest) {
            java.util.Random rand = new java.util.Random(
                mob.getUUID().hashCode() ^ soundPos.hashCode()
            );
            double angle = rand.nextDouble() * 2 * Math.PI;
            double radius = arrivalDistance * Math.sqrt(rand.nextDouble());
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            int destX = soundPos.getX() + (int) Math.floor(offsetX);
            int destZ = soundPos.getZ() + (int) Math.floor(offsetZ);
            int groundY = mob.level().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                destX, destZ
            );

            double finalX = destX + 0.5;
            double finalY = groundY;
            double finalZ = destZ + 0.5;
            lastRandomDest = new Vec3(finalX, finalY, finalZ);
            hasPickedDest = true;
        }

        if (lastRandomDest != null) {
            Vec3 curPos = mob.position();
            if (curPos.distanceToSqr(lastRandomDest) > 2.25) {
                mob.getNavigation().moveTo(
                    lastRandomDest.x, lastRandomDest.y, lastRandomDest.z, moveSpeed
                );
            }

            if (lastPos != null && curPos.distanceToSqr(lastPos) < 0.04) {
                stuckTicks++;
                if (stuckTicks > stuckThreshold) {
                    hasPickedDest = false;
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0;
            }
            lastPos = curPos;
        }

        if (leader.getNavigation().isDone()) {
            hasPickedDest = false;
            lastRandomDest = null;
        }


        if (!RaidManager.isRaidTicking(leader) && !RaidManager.isRaidAdvancing(leader) && mob.isSprinting()) {
            mob.setSprinting(false);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        if (mob.isSprinting()) mob.setSprinting(false);
        leader = null;
        hasPickedDest = false;
        lastRandomDest = null;
    }
}
