package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.accessor.FleeOnDamageAccessor;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A flee goal tailored for Phantoms (flying mob). It doesn't rely on PathAwareEntity-only helpers.
 */
public class PhantomFleeFromUnseenAttackerGoal extends Goal {

    private final PhantomEntity phantom;
    private final double speedModifier;

    @Nullable private Vec3d fleeFromPos;
    @Nullable private Vec3d fleeToPos;

    public PhantomFleeFromUnseenAttackerGoal(PhantomEntity phantom, double speedModifier) {
        this.phantom = phantom;
        this.speedModifier = speedModifier;
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    @Override
    public boolean canStart() {
        this.fleeFromPos = ((FleeOnDamageAccessor) this.phantom).soundattract_getFleeFromLocation();
        if (this.fleeFromPos == null) {
            return false;
        }

        // Compute a point ~16 blocks away in the opposite direction of the attacker.
        Vec3d here = this.phantom.getPos();
        Vec3d dir = here.subtract(this.fleeFromPos);
        if (dir.lengthSquared() < 1.0E-6) {
            dir = new Vec3d(this.phantom.getRandom().nextDouble() - 0.5, 0.2, this.phantom.getRandom().nextDouble() - 0.5);
        }
        dir = dir.normalize();

        double horizontalDistance = 16.0;
        double verticalOffset = 4.0 + this.phantom.getRandom().nextDouble() * 6.0; // fly upwards a bit

        Vec3d target = here.add(dir.multiply(horizontalDistance)).add(0.0, verticalOffset, 0.0);

        // Clamp Y within world bounds
        int minY = this.phantom.getWorld().getBottomY();
        int maxY = this.phantom.getWorld().getTopY() - 1;
        double clampedY = MathHelper.clamp(target.y, minY + 1, maxY - 1);
        this.fleeToPos = new Vec3d(target.x, clampedY, target.z);

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[PhantomFleeGoal] canStart: fleeFrom={}, fleeTo={}", this.fleeFromPos, this.fleeToPos);
        }

        return true;
    }

    @Override
    public boolean shouldContinue() {
        return this.fleeToPos != null && !this.phantom.getNavigation().isIdle();
    }

    @Override
    public void start() {
        if (this.fleeToPos == null) return;

        // Clear target and start flying away
        this.phantom.setTarget(null);
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[PhantomFleeGoal] start: Starting navigation for {} to {}", this.phantom.getName().getString(), this.fleeToPos);
        }
        this.phantom.getNavigation().startMovingTo(this.fleeToPos.x, this.fleeToPos.y, this.fleeToPos.z, this.speedModifier);
    }

    @Override
    public void stop() {
        if (this.fleeFromPos != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[PhantomFleeGoal] stop: Resetting flee trigger for {}", this.phantom.getName().getString());
        }
        ((FleeOnDamageAccessor) this.phantom).soundattract_setFleeFromLocation(null);
        this.fleeFromPos = null;
        this.fleeToPos = null;
    }
}
