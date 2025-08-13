package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.accessor.FleeOnDamageAccessor;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class FleeFromUnseenAttackerGoal extends Goal {

    protected final MobEntity mob;

    private final double speedModifier;

    @Nullable private Vec3d fleeFromPos;
    @Nullable private Vec3d fleeToPos;

    public FleeFromUnseenAttackerGoal(MobEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    @Override
    public boolean canStart() {

        this.fleeFromPos = ((FleeOnDamageAccessor) this.mob).soundattract_getFleeFromLocation();
        if (this.fleeFromPos == null) {
            return false;
        }


        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[FleeGoal] canStart: Received flee order for {}. Finding escape path.", this.mob.getName().getString());
        }

        if (this.mob instanceof PathAwareEntity pathAware) {
            this.fleeToPos = FuzzyTargeting.findFrom(pathAware, 16, 7, this.fleeFromPos);
        } else {
            Vec3d dir = this.mob.getPos().subtract(this.fleeFromPos).normalize();
            if (Double.isFinite(dir.length()) && dir.lengthSquared() > 0.0001) {
                this.fleeToPos = this.mob.getPos().add(dir.multiply(16.0));
            } else {
                this.fleeToPos = null;
            }
        }

        if (this.fleeToPos == null) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[FleeGoal] canStart: FAILED to find escape path for {}.", this.mob.getName().getString());
            }
            stop();
            return false;
        }
        
        return true;
    }


    @Override
    public boolean shouldContinue() { return !this.mob.getNavigation().isIdle(); }


    @Override
    public void start() {



        this.mob.setTarget(null);
        
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[FleeGoal] start: Target cleared. Starting navigation for {} to flee.", this.mob.getName().getString());
        }
        this.mob.getNavigation().startMovingTo(this.fleeToPos.x, this.fleeToPos.y, this.fleeToPos.z, this.speedModifier);
    }

    @Override
    public void stop() {
        if (this.fleeFromPos != null) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[FleeGoal] stop: Stopping flee goal and resetting trigger.", this.mob.getName().getString());
            }
        }
        ((FleeOnDamageAccessor) this.mob).soundattract_setFleeFromLocation(null);
        this.fleeFromPos = null;
        this.fleeToPos = null;
    }
}