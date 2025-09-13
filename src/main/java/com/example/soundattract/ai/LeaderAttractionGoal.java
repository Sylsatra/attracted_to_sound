package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Leader-only goal for RAID advancing phase.
 * In smart-edge mode, leaders DO NOT adopt sounds directly. They only move once a RAID
 * advances (after countdown). This mirrors the Forge flow where edge followers confirm
 * a player, schedule a raid, then leader advances when ready.
 */
public class LeaderAttractionGoal extends Goal {
    private final MobEntity mob;
    private final double moveSpeed;
    private BlockPos targetPos;
    private Vec3d chosenDest = null;
    private boolean hasPicked = false;
    private Vec3d lastPosVec = null;
    private int stuckTicks = 0;
    private BlockBreakerPosGoal blockBreakerGoal = null;

    public LeaderAttractionGoal(MobEntity mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (mob.hasVehicle() || mob.isSleeping() || mob.getTarget() != null) return false;
        if (MobGroupManager.getLeader(mob) != mob) return false;

        return RaidManager.isRaidAdvancing(mob);
    }

    @Override
    public boolean shouldContinue() {
        if (MobGroupManager.getLeader(mob) != mob) return false;

        return RaidManager.isRaidAdvancing(mob);
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {

        if (!RaidManager.isRaidAdvancing(mob)) {
            this.mob.getNavigation().stop();
            return;
        }
        BlockPos raidTarget = RaidManager.getRaidTarget(mob);
        if (raidTarget == null) {
            this.mob.getNavigation().stop();
            return;
        }
        this.targetPos = raidTarget;


        Vec3d cur = mob.getPos();
        if (lastPosVec != null && cur.squaredDistanceTo(lastPosVec) < 0.01) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPosVec = cur;
            if (this.blockBreakerGoal != null) {
                BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                this.blockBreakerGoal = null;
            }
        }
        if (stuckTicks >= 40 && SoundAttractMod.CONFIG.enableBlockBreaking && this.blockBreakerGoal == null) {
            BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                    this.mob,
                    this.targetPos,
                    SoundAttractMod.CONFIG.blockBreakTimeMultiplier,
                    SoundAttractMod.CONFIG.blockBreakToolOnly,
                    SoundAttractMod.CONFIG.blockBreakProperToolOnly,
                    SoundAttractMod.CONFIG.blockBreakProperToolRequired
            );
            BlockBreakerManager.scheduleAdd(this.mob, breaker, 2);
            this.blockBreakerGoal = breaker;
        }


        double arrivalDist = SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.arrivalDistance : 6.0;
        if (!hasPicked) {
            long seed = mob.getUuid().getMostSignificantBits() ^ mob.getUuid().getLeastSignificantBits() ^ targetPos.asLong();
            Random rand = new Random(seed);
            double angle = rand.nextDouble() * (Math.PI * 2.0);
            double radius = arrivalDist * Math.sqrt(rand.nextDouble());
            double offX = Math.cos(angle) * radius;
            double offZ = Math.sin(angle) * radius;
            double fx = targetPos.getX() + 0.5 + offX;
            double fz = targetPos.getZ() + 0.5 + offZ;
            double fy = targetPos.getY();
            chosenDest = new Vec3d(fx, fy, fz);
            hasPicked = true;
        }
        if (chosenDest != null) {
            Vec3d curPos = mob.getPos();
            if (curPos.squaredDistanceTo(chosenDest) > 1.5 * 1.5) {
                mob.getNavigation().startMovingTo(chosenDest.x, chosenDest.y, chosenDest.z, moveSpeed);
            }
            if (mob.getNavigation().isIdle()) {
                hasPicked = false;
                chosenDest = null;
            }
        }
    }

    private void navigateTo(BlockPos pos, double speed) {
        if (pos == null) {
            if (!this.mob.getNavigation().isIdle()) this.mob.getNavigation().stop();
            return;
        }
        this.mob.getNavigation().startMovingTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }
}
