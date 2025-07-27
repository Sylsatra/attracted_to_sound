package com.example.soundattract.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.example.soundattract.SoundAttractMod;

import java.util.EnumSet;
import java.util.Random;

public class FollowLeaderGoal extends Goal {
    private final MobEntity mob;
    private final double moveSpeed;
    private MobEntity leader;
    private AttractionGoal leaderAttractionGoal;

    private BlockPos leaderObjectivePos;
    private BlockPos myStableDestination;

    private int updateTimer;
    

    private int timeToLive;
    private static final int MAX_TIME_TO_LIVE = 10;

    public FollowLeaderGoal(MobEntity mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (this.mob.getTarget() != null) return false;

        this.leader = MobGroupManager.getLeader(this.mob);
        if (this.leader == null || this.leader == this.mob || !this.leader.isAlive()) return false;

        this.leaderAttractionGoal = AttractionGoal.getAttractionGoal(this.leader);
        if (this.leaderAttractionGoal == null || !this.leaderAttractionGoal.isPursuingSound()) return false;

        return this.leaderAttractionGoal.getTargetSoundPos() != null;
    }

    @Override
    public boolean shouldContinue() {


        return this.timeToLive > 0 
               && this.mob.getTarget() == null 
               && this.leader != null && this.leader.isAlive();
    }

    @Override
    public void start() {
        this.updateTimer = 0;
        this.timeToLive = MAX_TIME_TO_LIVE;
        
        this.leaderObjectivePos = this.leaderAttractionGoal.getTargetSoundPos();
        this.myStableDestination = calculateMyStableDestination(this.leaderObjectivePos);
        
        startMovingToDestination();
    }

    @Override
    public void stop() {

        this.mob.getNavigation().stop();
        this.leader = null;
        this.leaderAttractionGoal = null;
        this.leaderObjectivePos = null;
        this.myStableDestination = null;
    }

    @Override
    public void tick() {


        if (this.leaderAttractionGoal != null && this.leaderAttractionGoal.isPursuingSound()) {

            this.timeToLive = MAX_TIME_TO_LIVE;
        } else {

            this.timeToLive--;
        }



        
        if (this.leaderObjectivePos != null) {
            this.mob.getLookControl().lookAt(Vec3d.ofCenter(this.leaderObjectivePos));
        }


        if (this.leaderAttractionGoal.isPursuingSound() && ++this.updateTimer % 20 == 0) {
            BlockPos currentLeaderObjective = this.leaderAttractionGoal.getTargetSoundPos();


            if (currentLeaderObjective != null && !currentLeaderObjective.equals(this.leaderObjectivePos)) {
                if (this.leaderObjectivePos == null || this.leaderObjectivePos.getSquaredDistance(currentLeaderObjective) > 100.0) {
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[FollowLeaderGoal] {} updating target to {}. Recalculating destination.", this.mob.getName().getString(), currentLeaderObjective);
                    }
                    this.leaderObjectivePos = currentLeaderObjective;
                    this.myStableDestination = calculateMyStableDestination(this.leaderObjectivePos);
                    startMovingToDestination();
                }
            }
        }
        

        if (myStableDestination != null && !this.mob.getNavigation().isFollowingPath() && this.mob.getBlockPos().getSquaredDistance(myStableDestination) > 4.0) {
            startMovingToDestination();
        }
    }
    
    private void startMovingToDestination() {
        if (this.myStableDestination != null) {
            this.mob.getNavigation().startMovingTo(
                this.myStableDestination.getX() + 0.5,
                this.myStableDestination.getY(),
                this.myStableDestination.getZ() + 0.5,
                this.moveSpeed
            );
        }
    }
    
    private BlockPos calculateMyStableDestination(BlockPos leaderTarget) {
        if (leaderTarget == null) return null;
        double arrivalDistance = SoundAttractMod.CONFIG.arrivalDistance;
        long seed = this.mob.getUuid().getMostSignificantBits() ^ leaderTarget.asLong();
        Random rand = new Random(seed);
        double angle = rand.nextDouble() * 2 * Math.PI;
        double radius = arrivalDistance * (0.5 + rand.nextDouble() * 0.5);
        return BlockPos.ofFloored(
            leaderTarget.getX() + Math.cos(angle) * radius,
            leaderTarget.getY(),
            leaderTarget.getZ() + Math.sin(angle) * radius
        );
    }
}