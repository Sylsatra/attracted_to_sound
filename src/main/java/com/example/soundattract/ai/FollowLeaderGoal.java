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
        leader = MobGroupManager.getLeader(mob);
        if (leader == null || leader == mob) return false; 
        if (this.mob.distanceToSqr(leader) > getGroupDistance() * getGroupDistance()) return false; 
        boolean smartEdge = com.example.soundattract.config.SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        if (smartEdge && MobGroupManager.isEdgeMob(mob)) return false;
        if (!leader.isAlive()) return false;
        leaderAttractionGoal = null;
        leader.goalSelector.getRunningGoals().forEach(goal -> {
            if (goal.getGoal() instanceof AttractionGoal ag) {
                leaderAttractionGoal = ag;
            }
        });
        if (leaderAttractionGoal == null || !leaderAttractionGoal.isPursuingSound()) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (leader == null || !leader.isAlive()) return false;
        if (this.mob.distanceToSqr(leader) > getGroupDistance() * getGroupDistance()) return false;
        leaderAttractionGoal = StreamSupport.stream(
            leader.goalSelector.getRunningGoals().spliterator(), false)
            .map(WrappedGoal::getGoal)
            .filter(g -> g instanceof AttractionGoal)
            .map(g -> (AttractionGoal) g)
            .findFirst()
            .orElse(null);

        if (leaderAttractionGoal == null || !leaderAttractionGoal.isPursuingSound()) return false;
        if (leader.getNavigation().isDone()) return false;
        return true;
    }



    @Override
    public void tick() {
        if (leader == null) return;
        if (leaderAttractionGoal == null || !leaderAttractionGoal.isPursuingSound()) return;
        if (com.example.soundattract.config.SoundAttractConfig.COMMON.debugLogging.get()) {
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
            java.lang.reflect.Field f = leaderAttractionGoal.getClass().getDeclaredField("targetSoundPos");
            f.setAccessible(true);
            soundPos = (BlockPos) f.get(leaderAttractionGoal);
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
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        leader = null;
        hasPickedDest = false;
        lastRandomDest = null;
    }
}
