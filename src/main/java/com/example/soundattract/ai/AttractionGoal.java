package com.example.soundattract.ai;

import com.example.soundattract.SoundTracker;
import com.example.soundattract.SoundAttractMod;
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
    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;
    private int scanCooldown = 0;
    private BlockPos lastPos = null;
    private int stuckTicks = 0;
    private static final int STUCK_THRESHOLD = 10;
    private static final int RECALC_THRESHOLD = 30;

    public AttractionGoal(Mob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null || mob.getLastHurtByMob() != null) return false;
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
        if (mob.getTarget() != null || mob.getLastHurtByMob() != null) return false;
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
        lastPos = null;
        stuckTicks = 0;
    }

    @Override
    public void tick() {
        if (mob.getTarget() != null || mob.getLastHurtByMob() != null) {
            stop();
            return;
        }
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

        if (this.mob.getNavigation().isDone()) {
            if (distSqr >= arrivalThresholdSqr) {
                Vec3 mobPosVec2 = mob.position();
                Vec3 soundVec = Vec3.atCenterOf(targetSoundPos);
                Vec3 direction = soundVec.subtract(mobPosVec2).normalize();
                double stepDistance = Math.min(32.0, Math.sqrt(distSqr));
                Vec3 stepTarget = mobPosVec2.add(direction.scale(stepDistance));
                BlockPos stepBlockPos = new BlockPos((int)Math.round(stepTarget.x), (int)Math.round(stepTarget.y), (int)Math.round(stepTarget.z));
                this.mob.getNavigation().moveTo(stepBlockPos.getX() + 0.5, stepBlockPos.getY(), stepBlockPos.getZ() + 0.5, this.moveSpeed);
            }
        }
        if (this.mob.getNavigation().getTargetPos() == null || !this.mob.getNavigation().getTargetPos().equals(this.targetSoundPos)) {
            this.mob.getNavigation().moveTo(goalVec.x, goalVec.y, goalVec.z, moveSpeed);
        }
        this.mob.getNavigation().moveTo(goalVec.x, goalVec.y, goalVec.z, moveSpeed);
    }

    private SoundTracker.SoundRecord findInterestingSoundRecord() {
        Level level = mob.level();
        if (level.isClientSide()) return null;
        BlockPos mobPos = mob.blockPosition();
        Vec3 mobEyePos = mob.getEyePosition(1.0F); 
        return SoundTracker.findNearestSound(level, mobPos, mobEyePos);
    }
}