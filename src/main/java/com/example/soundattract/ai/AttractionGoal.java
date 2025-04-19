package com.example.soundattract.ai;

import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.level.Level; 
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class AttractionGoal extends Goal {

    private final Mob mob;
    private final double moveSpeed;
    private final int fullScanRadius;
    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;
    private int scanCooldown = 0;

    public AttractionGoal(Mob mob, double moveSpeed, int fullScanRadius) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.fullScanRadius = fullScanRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        SoundTracker.SoundRecord initialSound = findInterestingSoundRecord();
        if (initialSound != null) {
            this.targetSoundPos = initialSound.pos;
            this.currentTargetWeight = initialSound.weight;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetSoundPos == null) return false;
        SoundTracker.SoundRecord currentBest = findInterestingSoundRecord();

        double arrivalDistance = SoundAttractConfig.COMMON.arrivalDistance.get();
        double arrivalThresholdSqr = arrivalDistance * arrivalDistance;
        boolean hasArrived = mob.blockPosition().distSqr(targetSoundPos) < arrivalThresholdSqr;

        boolean targetStillValid = currentBest != null && currentBest.pos.equals(targetSoundPos) && currentBest.weight >= this.currentTargetWeight;

        return !hasArrived && targetStillValid;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        targetSoundPos = null;
        currentTargetWeight = -1.0;
        scanCooldown = 0;
    }

    @Override
    public void tick() {
        if (targetSoundPos == null) return;

        Vec3 mobPosVec = mob.position();
        Vec3 goalVec = Vec3.atCenterOf(targetSoundPos);
        double distSqr = mobPosVec.distanceToSqr(goalVec);
        double arrivalDistance = SoundAttractConfig.COMMON.arrivalDistance.get();
        double arrivalThresholdSqr = arrivalDistance * arrivalDistance;

        if (distSqr < arrivalThresholdSqr) {
            this.mob.getNavigation().stop();
            return;
        }

        if (scanCooldown > 0) {
            scanCooldown--;
        } else {
            scanCooldown = SoundAttractConfig.COMMON.scanCooldownTicks.get();
            SoundTracker.SoundRecord potentialNewSound = findInterestingSoundRecord();

            if (potentialNewSound != null && potentialNewSound.weight >= this.currentTargetWeight) {
                if (!potentialNewSound.pos.equals(this.targetSoundPos)) {
                    this.targetSoundPos = potentialNewSound.pos;
                    this.currentTargetWeight = potentialNewSound.weight;
                    this.mob.getNavigation().moveTo(goalVec.x, goalVec.y, goalVec.z, moveSpeed);
                } else {
                    this.currentTargetWeight = potentialNewSound.weight;
                }
            }
        }

        if (this.mob.getNavigation().isDone() || !this.mob.getNavigation().getTargetPos().equals(this.targetSoundPos)) {
            this.mob.getNavigation().moveTo(goalVec.x, goalVec.y, goalVec.z, moveSpeed);
        }
    }

    private SoundTracker.SoundRecord findInterestingSoundRecord() {
        Level level = mob.level();
        if (level.isClientSide()) return null;
        BlockPos mobPos = mob.blockPosition();
        return SoundTracker.findNearestSound(level, mobPos);
    }
}