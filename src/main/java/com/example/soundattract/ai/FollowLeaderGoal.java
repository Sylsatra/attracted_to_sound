package com.example.soundattract.ai;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import com.example.soundattract.SoundAttractMod;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import java.util.EnumSet;
import net.minecraft.world.World;
import java.util.Random;

public class FollowLeaderGoal extends Goal {
    private final MobEntity mob;
    private final double moveSpeed;
    private MobEntity leader;
    private AttractionGoal leaderAttractionGoal = null;
    private BlockPos leaderObjectivePos;
    private BlockPos myStableDestination;

    private int updateTimer;
    

    private int timeToLive;
    private static final int MAX_TIME_TO_LIVE = 10;
    private static final double MAX_DISTANCE = 12.0; 
    private BlockBreakerPosGoal followerBreaker = null;
    private Vec3d lastPos = null;
    private Vec3d lastPosVec = null;
    private int stuckTicks = 0;
    private int stuckThreshold = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
    private int dynamicTickCounter = 0;

    public FollowLeaderGoal(MobEntity mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    private double getGroupDistance() {
        return SoundAttractMod.CONFIG.groupDistance;
    }

    public boolean canUse() {
        leader = MobGroupManager.getLeader(mob);
        if (leader == null || leader == mob) return false; 
        boolean smartEdge = SoundAttractMod.CONFIG.edgeMobSmartBehavior;
        if (smartEdge && MobGroupManager.isEdgeMobEntity(mob)) return false;
        if (!leader.isAlive()) return false;
        leaderAttractionGoal = null;
        try {
            java.lang.reflect.Field field = leader.getClass().getSuperclass().getDeclaredField("goalSelector");
            field.setAccessible(true);
            net.minecraft.entity.ai.goal.GoalSelector selector = (net.minecraft.entity.ai.goal.GoalSelector) field.get(leader);
            selector.getRunningGoals().forEach(goal -> {
                if (goal.getGoal() instanceof AttractionGoal ag) {
                    leaderAttractionGoal = ag;
                }
            });
        } catch (Exception e) {
        }
        if (leaderAttractionGoal == null || !leaderAttractionGoal.isPursuingSound()) return false;
        return true;
    }

    public boolean canContinueToUse() {
        if (leader == null || !leader.isAlive()) return false;
        if (leaderAttractionGoal == null || !leaderAttractionGoal.isPursuingSound()) return false;
        return true;
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


        if (SoundAttractMod.CONFIG.enableBlockBreaking && this.leaderObjectivePos != null) {
            double distSqToLeaderTarget = this.mob.getPos().squaredDistanceTo(Vec3d.ofCenter(this.leaderObjectivePos));


            Vec3d curPos = this.mob.getPos();
            if (lastPosVec != null && curPos.squaredDistanceTo(lastPosVec) < 0.01) {
                stuckTicks++;
                if (SoundAttractMod.CONFIG.debugLogging && (stuckTicks % 10 == 0)) {
                    SoundAttractMod.LOGGER.info("[FollowLeaderGoal] {} appears stuck for {} ticks near {} while following leader toward {}", this.mob.getName().getString(), stuckTicks, this.mob.getBlockPos(), this.leaderObjectivePos);
                }
            } else {
                stuckTicks = 0;
                lastPosVec = curPos;
            }

            boolean navIdleAndFar = this.mob.getNavigation().isIdle() && distSqToLeaderTarget > 4.0;
            boolean trulyStuck = stuckTicks >= 10;


            if (this.followerBreaker != null) {
                boolean running = false;
                try {
                    running = ((com.example.soundattract.mixin.MobEntityAccessor) this.mob)
                            .getGoalSelector()
                            .getGoals()
                            .stream()
                            .anyMatch(w -> w.getGoal() == this.followerBreaker && w.isRunning());
                } catch (ClassCastException e) {
                    SoundAttractMod.LOGGER.error("[FollowLeaderGoal] Failed to access running goals for {}", this.mob.getName().getString(), e);
                }
                if (!running) {
                    this.followerBreaker = null;
                }
            }


            if (this.followerBreaker == null && (navIdleAndFar || trulyStuck)) {
                BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                        this.mob,
                        this.leaderObjectivePos,
                        SoundAttractMod.CONFIG.blockBreakTimeMultiplier,
                        SoundAttractMod.CONFIG.blockBreakToolOnly,
                        SoundAttractMod.CONFIG.blockBreakProperToolOnly,
                        SoundAttractMod.CONFIG.blockBreakProperToolRequired
                );
                BlockBreakerManager.scheduleAdd(this.mob, breaker, 2);
                this.followerBreaker = breaker;
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[FollowLeaderGoal] Scheduling BlockBreakerPosGoal for follower {} toward leader target {} (navIdleAndFar={}, stuckTicks={})", this.mob.getName().getString(), this.leaderObjectivePos, navIdleAndFar, stuckTicks);
                }

                this.stuckTicks = 0;
            }
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        if (this.followerBreaker != null) {
            BlockBreakerManager.scheduleRemove(this.mob, this.followerBreaker);
            this.followerBreaker = null;
        }
        this.leader = null;
        this.leaderAttractionGoal = null;
        this.leaderObjectivePos = null;
        this.myStableDestination = null;
        this.lastPosVec = null;
        this.stuckTicks = 0;
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
        return canContinueToUse();
    }

    @Override
    public void start() {
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
