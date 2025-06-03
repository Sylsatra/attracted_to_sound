package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.StealthUtils;
import com.example.soundattract.SoundAttractMod;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrackTargetGoal.class)
public abstract class TrackTargetGoalMixin {

    @Shadow @Final protected MobEntity mob;
    // We do NOT shadow "target" here because ActiveTargetGoal never writes into TrackTargetGoal.target.
    // Instead, ActiveTargetGoal stores the chosen entity in mob.getTarget().

    @Inject(
        method = "shouldContinue()Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onShouldContinue_StealthChecks(CallbackInfoReturnable<Boolean> cir) {
        // Only run this logic if the current TrackTargetGoal instance is actually an ActiveTargetGoal
        if (!(((Object) this) instanceof ActiveTargetGoal)) {
            return;
        }

        // Find the mob’s current target from mob.getTarget():
        LivingEntity currentTarget = mob.getTarget();
        if (!(currentTarget instanceof PlayerEntity player)) {
            // If it's not a player, skip stealth override; let vanilla continue.
            return;
        }

        // Compute actual distance and allowed stealth range
        double actualDistance = mob.distanceTo(player);
        double allowedRange   = StealthDetectionEvents.computeFullDetectionRange(mob, player, mob.getWorld());
        if (!mob.canSee(player)) {
            allowedRange *= 0.5;
        }

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[TrackTargetGoalMixin] shouldContinue for mob={} player={}: dist={} allowed={}",
                mob.getName().getString(),
                player.getName().getString(),
                String.format("%.2f", actualDistance),
                String.format("%.2f", allowedRange)
            );
        }

        if (actualDistance > allowedRange) {
            // Player has slipped outside our custom “stealth” range: forcibly end this goal
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                    "[TrackTargetGoalMixin] Cancelling shouldContinue: mob={} player={} (out of range).",
                    mob.getName().getString(),
                    player.getName().getString()
                );
            }
            StealthUtils.clearTargetAndMemories(mob);
            cir.setReturnValue(false);
        }
        // Otherwise—player is within stealth range—let vanilla’s shouldContinue() run as normal.
    }
}
