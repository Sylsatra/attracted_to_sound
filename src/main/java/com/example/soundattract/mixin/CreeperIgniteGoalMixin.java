package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.StealthUtils;
import com.example.soundattract.SoundAttractMod;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.CreeperIgniteGoal;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/** 
 * Stops a Creeper from igniting (or continuing to ignite) if the player is outside stealth range. 
 */
@Mixin(CreeperIgniteGoal.class)
public abstract class CreeperIgniteGoalMixin {
    /**
     * Prevent canStart() if the player is already out of stealth range.
     */
    @Inject(method = "canStart()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelIgniteCanStart(CallbackInfoReturnable<Boolean> cir) {
        CreeperIgniteGoal goal = (CreeperIgniteGoal) (Object) this;
        CreeperEntity creeper = extractCreeper(goal);
        if (creeper == null) return;

        LivingEntity target = creeper.getTarget();
        if (target instanceof PlayerEntity player) {
            double distSq = creeper.squaredDistanceTo(player);
            double allowed = StealthDetectionEvents.computeFullDetectionRange(creeper, player, creeper.getWorld());
            if (!creeper.canSee(player)) allowed *= 0.5;
            double allowedSq = allowed * allowed;

            if (distSq > allowedSq) {

                StealthUtils.clearTargetAndMemories(creeper);
                creeper.setFuseSpeed(-1); 
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * In tick(), if the player moves out of stealth range, force the Creeper to stop its fuse.
     */
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void soundattract$cancelIgniteTick(CallbackInfo ci) {
        CreeperIgniteGoal goal = (CreeperIgniteGoal) (Object) this;
        CreeperEntity creeper = extractCreeper(goal);
        if (creeper == null) return;

        LivingEntity target = creeper.getTarget();
        if (!(target instanceof PlayerEntity player)) return;

        double distSq = creeper.squaredDistanceTo(player);
        double allowed = StealthDetectionEvents.computeFullDetectionRange(creeper, player, creeper.getWorld());
        if (!creeper.canSee(player)) allowed *= 0.5;
        double allowedSq = allowed * allowed;

        if (distSq > allowedSq) {
            creeper.setFuseSpeed(-1);
            StealthUtils.clearTargetAndMemories(creeper);
        }
    }

    /**
     * Reflectively find the private field of type CreeperEntity inside CreeperIgniteGoal.
     */
    private CreeperEntity extractCreeper(CreeperIgniteGoal goal) {
        for (Field f : goal.getClass().getDeclaredFields()) {
            if (f.getType() == CreeperEntity.class) {
                try {
                    f.setAccessible(true);
                    return (CreeperEntity) f.get(goal);
                } catch (Exception e) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.warn("[CreeperIgniteGoalMixin] Failed to read field " 
                            + f.getName() + ": " + e);
                    }
                    return null;
                }
            }
        }
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            StringBuilder sb = new StringBuilder("[CreeperIgniteGoalMixin] No CreeperEntity field found in CreeperIgniteGoal. Available fields: ");
            for (Field f : goal.getClass().getDeclaredFields()) sb.append(f.getName()).append(" ");
            SoundAttractMod.LOGGER.warn(sb.toString());
        }
        return null;
    }
}
