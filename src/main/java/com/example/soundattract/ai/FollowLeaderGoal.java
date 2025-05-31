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
            com.example.soundattract.SoundAttractMod.LOGGER.info("[FollowLeaderGoal] Mob {} following leader {} (leader is pursuing sound)", mob.getName().getString(), leader.getName().getString());
        }
        int scanCooldown = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
        int updateInterval = Math.max(1, scanCooldown / 2);
        dynamicTickCounter = (dynamicTickCounter + 1) % updateInterval;
        if (dynamicTickCounter != 0) return;

        BlockPos soundPos = null;
        if (leaderAttractionGoal != null && leaderAttractionGoal.isPursuingSound()) {
            try {
                java.lang.reflect.Field f = leaderAttractionGoal.getClass().getDeclaredField("targetSoundPos");
                f.setAccessible(true);
                soundPos = (BlockPos) f.get(leaderAttractionGoal);
            } catch (Exception e) {
            }
        }
        if (soundPos == null) return;
        double arrivalDistance = com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.get();
        long seed = mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits() ^ soundPos.hashCode();
        java.util.Random rand = new java.util.Random(seed);
        double angle = rand.nextDouble() * 2 * Math.PI;
        double radius = arrivalDistance * (0.5 + rand.nextDouble() * 0.5);
        double offsetX = Math.cos(angle) * radius;
        double offsetZ = Math.sin(angle) * radius;
        double offsetY = (rand.nextDouble() - 0.5) * 2.0;
        Vec3 offsetTarget = Vec3.atCenterOf(soundPos).add(offsetX, offsetY, offsetZ);
        BlockPos dest = new BlockPos((int)offsetTarget.x, (int)offsetTarget.y, (int)offsetTarget.z);
        BlockPos currentTarget = mob.getNavigation().getTargetPos();
        if (currentTarget == null || currentTarget.distSqr(dest) > 2.25) {
            mob.getNavigation().moveTo(offsetTarget.x, offsetTarget.y, offsetTarget.z, moveSpeed);
        }
        Vec3 curPos = mob.position();
        if (lastPos != null && curPos.distanceToSqr(lastPos) < 0.04) {
            stuckTicks++;
            if (stuckTicks > stuckThreshold) {
                double newAngle = angle + Math.PI / 4;
                double nX = Math.cos(newAngle) * 1.5;
                double nZ = Math.sin(newAngle) * 1.5;
                Vec3 newOffset = new Vec3(nX, 0, nZ);
                Vec3 newTarget = Vec3.atCenterOf(soundPos).add(newOffset);
                mob.getNavigation().moveTo(newTarget.x, newTarget.y, newTarget.z, moveSpeed);
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = curPos;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        leader = null;
    }
}
