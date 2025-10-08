package com.example.soundattract.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import java.util.EnumSet;

public class FollowLeaderGoal extends Goal {
    private final Mob mob;
    private final double moveSpeed;
    private Mob leader;
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

        if (this.mob.distanceToSqr(this.leader) < getGroupDistance() * getGroupDistance()) {
            return false;
        }

        boolean smartEdge = com.example.soundattract.config.SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        if (smartEdge && MobGroupManager.isEdgeMob(mob)) return false;
        if (!leader.isAlive()) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (leader == null || !leader.isAlive()) return false;

        if (this.mob.distanceToSqr(this.leader) < getGroupDistance() * getGroupDistance()) {
            return false;
        }
        return true;
    }



    @Override
    public void tick() {
        if (leader == null || !leader.isAlive()) {
            return;
        }

        int scanCooldown = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
        int updateInterval = Math.max(1, scanCooldown / 2);
        dynamicTickCounter = (dynamicTickCounter + 1) % updateInterval;
        if (dynamicTickCounter != 0) return;

        double sprintMult = com.example.soundattract.config.SoundAttractConfig.COMMON.groupSprintMultiplier.get();
        boolean raidAdvancing = com.example.soundattract.ai.RaidManager.isRaidAdvancing(leader);
        double speed = raidAdvancing ? (moveSpeed * sprintMult) : moveSpeed;

        Vec3 leaderPos = leader.position();
        Vec3 curPos = mob.position();
        if (curPos.distanceToSqr(leaderPos) > 2.25) {
            mob.getNavigation().moveTo(leader.getX(), leader.getY(), leader.getZ(), speed);
        }

        if (lastPos != null && curPos.distanceToSqr(lastPos) < 0.04) {
            stuckTicks++;
            if (stuckTicks > stuckThreshold) {
                stuckTicks = 0;
                // Nudge by repathing without changing destination to break spin
                mob.getNavigation().moveTo(leader.getX(), leader.getY(), leader.getZ(), speed);
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = curPos;

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
