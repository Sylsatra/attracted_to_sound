package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.StealthUtils;
import com.example.soundattract.SoundAttractMod;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ActiveTargetGoal.class)
public abstract class ActiveTargetGoalMixin {
    @Shadow @Final protected Class<? extends LivingEntity> targetClass;
    @Shadow protected TargetPredicate targetPredicate;
    @Shadow private int reciprocalChance;
    @Shadow @Nullable protected LivingEntity targetEntity;

    // Accessor interface must exist in your code:
    // @Mixin(TrackTargetGoal.class)
    // public interface TrackTargetGoalAccessor {
    //     @Accessor("mob") MobEntity getMob();
    // }
    private MobEntity getMob() {
        return ((TrackTargetGoalAccessor)(Object)this).getMob();
    }

    @Inject(
        method = "canStart()Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onCanStart_StealthChecks(CallbackInfoReturnable<Boolean> cir) {
        // Only intercept if targeting PlayerEntity or ServerPlayerEntity
        if (targetClass != PlayerEntity.class && targetClass != ServerPlayerEntity.class) {
            return;
        }

        MobEntity mob = getMob();
        World world = mob.getWorld();

        // Use vanilla getClosestPlayer(TargetPredicate, sourceEntity, x, y, z):
        PlayerEntity closest = world.getClosestPlayer(
            targetPredicate,
            mob,
            mob.getX(),
            mob.getEyeY(),
            mob.getZ()
        );

        // If no player was found by vanilla’s predicate → cancel immediately
        if (closest == null) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                    "[ActiveTargetGoalMixin] canStart → no player in vanilla range"
                );
            }
            StealthUtils.clearTargetAndMemories(mob);
            cir.setReturnValue(false);
            return;
        }

        // Compute actual distance and stealth‐based allowed range
        double actualDistance = mob.distanceTo(closest);
        double allowedRange   = StealthDetectionEvents.computeFullDetectionRange(mob, closest, world);

        if (!mob.canSee(closest)) {
            allowedRange *= 0.5;
        }

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[ActiveTargetGoalMixin] canStart for mob={} player={}: dist={} allowed={}",
                mob.getName().getString(),
                closest.getName().getString(),
                String.format("%.2f", actualDistance),
                String.format("%.2f", allowedRange)
            );
        }

        // If player is outside our stealth range → cancel entire canStart
        if (actualDistance > allowedRange) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                    "[ActiveTargetGoalMixin] Cancelling canStart: out of stealth range."
                );
            }
            StealthUtils.clearTargetAndMemories(mob);
            cir.setReturnValue(false);
            return;
        }

        // Otherwise: player is within stealth range → let vanilla continue
        // (i.e. reciprocalChance check, findClosestTarget(), return targetEntity != null)
    }
}
