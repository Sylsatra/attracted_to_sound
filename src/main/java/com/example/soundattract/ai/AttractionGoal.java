package com.example.soundattract.ai;

import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class AttractionGoal extends Goal {

    private final Mob mob;
    private final double moveSpeed;
    private final int fullScanRadius;
    private BlockPos targetSoundPos;
    private int scanCooldown = 0;

    public AttractionGoal(Mob mob, double moveSpeed, int fullScanRadius) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.fullScanRadius = fullScanRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        targetSoundPos = findInterestingSound();
        return targetSoundPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetSoundPos == null) return false;
        double distSqr = mob.blockPosition().distSqr(targetSoundPos);
        double arrivalThresholdSqr = SoundAttractConfig.ARRIVAL_DISTANCE * SoundAttractConfig.ARRIVAL_DISTANCE;
        return distSqr >= arrivalThresholdSqr;
    }

    @Override
    public void stop() {
        targetSoundPos = null;
        scanCooldown = 0;
    }

    @Override
    public void tick() {
        if (scanCooldown > 0) {
            scanCooldown--;
        } else {
            scanCooldown = SoundAttractConfig.SCAN_COOLDOWN_TICKS;
            BlockPos newSound = findInterestingSound();
            if (newSound != null && !newSound.equals(targetSoundPos)) {
                targetSoundPos = newSound;
            }
        }

        if (targetSoundPos == null) return;

        Vec3 mobPos = mob.position();
        Vec3 goal = Vec3.atCenterOf(targetSoundPos);
        double distSqr = mobPos.distanceToSqr(goal);
        double arrivalThresholdSqr = SoundAttractConfig.ARRIVAL_DISTANCE * SoundAttractConfig.ARRIVAL_DISTANCE;
        if (distSqr < arrivalThresholdSqr) {
            return;
        }
        mob.getNavigation().moveTo(goal.x, goal.y, goal.z, moveSpeed);
    }

    private BlockPos findInterestingSound() {
        if (mob.level.isClientSide) return null;
        BlockPos mobPos = mob.blockPosition();
        var record = SoundTracker.findNearestSound(mob.level, mobPos);
        if (record == null) return null;
        double distSqr = mobPos.distSqr(record.pos);
        double maxSqr = SoundAttractConfig.SOUND_HEARING_RADIUS * SoundAttractConfig.SOUND_HEARING_RADIUS;
        return distSqr <= maxSqr ? record.pos : null;
    }
}
