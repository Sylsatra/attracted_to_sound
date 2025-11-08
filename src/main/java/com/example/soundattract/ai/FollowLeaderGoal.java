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
    private int stuckTicks = 0;
    private int stuckThreshold = com.example.soundattract.config.SoundAttractConfig.COMMON.scanCooldownTicks.get();
    private int dynamicTickCounter = 0;
    private AttractionGoal leaderAttractionGoal = null;
    private BlockPos leaderObjectivePos;
    private BlockPos myStableDestination;
    private BlockBreakerPosGoal followerBreaker = null;
    private Vec3 lastPosVec = null;
    private Vec3 lastPosSample = null;
    private int navIdleTicks = 0;

    private void moveTowardsLeader(double speed) {
        if (leader == null) return;
        BlockPos leaderBlock = leader.blockPosition();
        this.mob.getNavigation().moveTo(
            leaderBlock.getX() + 0.5,
            leaderBlock.getY(),
            leaderBlock.getZ() + 0.5,
            speed
        );
    }

    private void startMovingToDestination(double speed) {
        if (this.myStableDestination != null) {
            this.mob.getNavigation().moveTo(
                this.myStableDestination.getX() + 0.5,
                this.myStableDestination.getY(),
                this.myStableDestination.getZ() + 0.5,
                speed
            );
        }
    }

    private BlockPos calculateStableDestination(BlockPos leaderTarget) {
        if (leaderTarget == null) return null;
        double arrival = com.example.soundattract.config.SoundAttractConfig.COMMON.arrivalDistance.get();
        long seed = this.mob.getUUID().getMostSignificantBits() ^ leaderTarget.asLong();
        java.util.Random rand = new java.util.Random(seed);
        double angle = rand.nextDouble() * Math.PI * 2.0;
        double radius = arrival * (0.5 + rand.nextDouble() * 0.5);
        int x = leaderTarget.getX() + (int) Math.floor(Math.cos(angle) * radius);
        int z = leaderTarget.getZ() + (int) Math.floor(Math.sin(angle) * radius);
        return new BlockPos(x, leaderTarget.getY(), z);
    }

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

        leaderAttractionGoal = this.leader.goalSelector.getAvailableGoals().stream()
            .map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
            .filter(goal -> goal instanceof AttractionGoal)
            .map(goal -> (AttractionGoal) goal)
            .filter(AttractionGoal::isPursuingSound)
            .findFirst()
            .orElse(null);

        if (leaderAttractionGoal == null && !com.example.soundattract.ai.RaidManager.isRaidAdvancing(this.leader)) {
            return false;
        }

        this.leaderObjectivePos = leaderAttractionGoal != null ? leaderAttractionGoal.getTargetSoundPos() : com.example.soundattract.ai.RaidManager.getRaidTarget(this.leader);
        this.myStableDestination = calculateStableDestination(this.leaderObjectivePos != null ? this.leaderObjectivePos : this.leader.blockPosition());
        startMovingToDestination(this.moveSpeed);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (leader == null || !leader.isAlive()) return false;

        if (com.example.soundattract.ai.RaidManager.isRaidAdvancing(this.leader) || com.example.soundattract.ai.RaidManager.isRaidTicking(this.leader)) {
            return true;
        }

        if (leaderAttractionGoal == null || !leaderAttractionGoal.isPursuingSound()) {
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

        if (com.example.soundattract.ai.RaidManager.isRaidAdvancing(this.leader)) {
            BlockPos raidTarget = com.example.soundattract.ai.RaidManager.getRaidTarget(this.leader);
            if (raidTarget != null) {
                this.leaderObjectivePos = raidTarget;
                this.myStableDestination = calculateStableDestination(this.leaderObjectivePos);
                startMovingToDestination(speed);
                return;
            }
        }

        if (leaderAttractionGoal != null && leaderAttractionGoal.isPursuingSound()) {
            BlockPos currentLeaderTarget = leaderAttractionGoal.getTargetSoundPos();
            if (currentLeaderTarget != null && !currentLeaderTarget.equals(this.leaderObjectivePos)) {
                if (this.leaderObjectivePos == null || this.leaderObjectivePos.distSqr(currentLeaderTarget) > 100.0) {
                    this.leaderObjectivePos = currentLeaderTarget;
                    this.myStableDestination = calculateStableDestination(this.leaderObjectivePos);
                    startMovingToDestination(this.moveSpeed);
                }
            }
        }

        if (this.myStableDestination != null) {
            double distSq = curPos.distanceToSqr(Vec3.atCenterOf(this.myStableDestination));
            if (this.mob.getNavigation().isDone() || distSq > 4.0) {
                this.mob.getNavigation().moveTo(
                    this.myStableDestination.getX() + 0.5,
                    this.myStableDestination.getY(),
                    this.myStableDestination.getZ() + 0.5,
                    speed
                );
                startMovingToDestination(speed);
            }
        } else if (curPos.distanceToSqr(leaderPos) > 4.0) {
            moveTowardsLeader(speed);
        }

        if (lastPosSample != null && curPos.distanceToSqr(lastPosSample) < 0.04) {
            stuckTicks++;
            if (stuckTicks > stuckThreshold) {
                stuckTicks = 0;
                startMovingToDestination(speed);
            }
        } else {
            stuckTicks = 0;
        }
        lastPosSample = curPos;

        if (lastPosVec != null && curPos.distanceToSqr(lastPosVec) < 0.01) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPosVec = curPos;
            if (this.followerBreaker != null) {
                BlockBreakerManager.scheduleRemove(this.mob, this.followerBreaker);
                this.followerBreaker = null;
            }
        }

        if (com.example.soundattract.config.SoundAttractConfig.COMMON.enableBlockBreaking.get()) {
            double distSqToTarget = this.myStableDestination != null
                ? this.mob.position().distanceToSqr(Vec3.atCenterOf(this.myStableDestination))
                : curPos.distanceToSqr(leaderPos);
            boolean navIdleAndFar = this.mob.getNavigation().isDone() && distSqToTarget > 4.0;
            boolean trulyStuck = stuckTicks >= 10;

            if (this.followerBreaker != null) {
                boolean running = this.mob.goalSelector.getAvailableGoals().stream()
                    .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                    .anyMatch(wrapped -> wrapped.getGoal() == this.followerBreaker);
                if (!running) {
                    this.followerBreaker = null;
                }
            }

            if (this.followerBreaker == null && (navIdleAndFar || trulyStuck)) {
                BlockPos dest = this.myStableDestination != null ? this.myStableDestination : this.leader.blockPosition();
                BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                    this.mob,
                    dest,
                    com.example.soundattract.config.SoundAttractConfig.COMMON.blockBreakTimeMultiplier.get(),
                    com.example.soundattract.config.SoundAttractConfig.COMMON.blockBreakToolOnly.get(),
                    com.example.soundattract.config.SoundAttractConfig.COMMON.blockBreakProperToolOnly.get(),
                    com.example.soundattract.config.SoundAttractConfig.COMMON.blockBreakProperToolRequired.get()
                );
                BlockBreakerManager.scheduleAdd(this.mob, breaker, 2);
                this.followerBreaker = breaker;
                this.stuckTicks = 0;
            }
        }

        if (leaderAttractionGoal == null && !com.example.soundattract.ai.RaidManager.isRaidAdvancing(this.leader) && this.mob.distanceToSqr(this.leader) < getGroupDistance() * getGroupDistance()) {
            this.mob.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        leader = null;
        leaderAttractionGoal = null;
        leaderObjectivePos = null;
        myStableDestination = null;
        lastPosVec = null;
        if (this.followerBreaker != null) {
            BlockBreakerManager.scheduleRemove(this.mob, this.followerBreaker);
            this.followerBreaker = null;
        }
    }
}
