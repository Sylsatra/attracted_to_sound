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

public class FollowLeaderGoal extends Goal {
    private final MobEntity mob;
    private final double moveSpeed;
    private MobEntity leader;
    private AttractionGoal leaderAttractionGoal = null;
    private static final double MAX_DISTANCE = 12.0; 
    private Vec3d lastPos = null;
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
        if (leader == null) return;
        if (leaderAttractionGoal == null || !leaderAttractionGoal.isPursuingSound()) return;
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
    SoundAttractMod.LOGGER.warn("[FollowLeaderGoal] MobEntity {} following leader {} (leader is pursuing sound)", mob.getName().getString(), leader.getName().getString());
}


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
        double arrivalDistance = SoundAttractMod.CONFIG.arrivalDistance;
        double distToSound = mob.getPos().distanceTo(Vec3d.ofCenter(soundPos));
        if (distToSound <= arrivalDistance) {
            mob.getNavigation().stop();
            return;
        }
        long seed = mob.getUuid().getMostSignificantBits() ^ mob.getUuid().getLeastSignificantBits() ^ soundPos.hashCode();
        java.util.Random rand = new java.util.Random(seed);
        double angle = rand.nextDouble() * 2 * Math.PI;
        double radius = arrivalDistance * (0.5 + rand.nextDouble() * 0.5);
        double offsetX = Math.cos(angle) * radius;
        double offsetZ = Math.sin(angle) * radius;
        double offsetY = (rand.nextDouble() - 0.5) * 2.0;
        Vec3d offsetTarget = Vec3d.ofCenter(soundPos).add(offsetX, offsetY, offsetZ);
        BlockPos dest = new BlockPos((int)offsetTarget.x, (int)offsetTarget.y, (int)offsetTarget.z);
        BlockPos currentTarget = mob.getNavigation().getTargetPos();
        if (currentTarget == null || currentTarget.getSquaredDistance(dest) > 2.25) {
            mob.getNavigation().startMovingTo(offsetTarget.x, offsetTarget.y, offsetTarget.z, moveSpeed);
        }
        Vec3d curPos = mob.getPos();
        if (lastPos != null && curPos.squaredDistanceTo(lastPos) < 0.04) {
            stuckTicks++;
            if (stuckTicks > stuckThreshold) {
                double newAngle = angle + Math.PI / 4;
                double nX = Math.cos(newAngle) * 1.5;
                double nZ = Math.sin(newAngle) * 1.5;
                Vec3d newOffset = new Vec3d(nX, 0, nZ);
                Vec3d newTarget = Vec3d.ofCenter(soundPos).add(newOffset);
                mob.getNavigation().startMovingTo(newTarget.x, newTarget.y, newTarget.z, moveSpeed);
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

    @Override
    public boolean canStart() {
        return canUse();
    }

    @Override
    public boolean shouldContinue() {
        return canContinueToUse();
    }

    @Override
    public void start() {
    }
}
