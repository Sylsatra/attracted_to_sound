package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.StealthUtils;
import com.example.soundattract.SoundAttractMod;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.CrossbowAttackGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Prevents any CrossbowAttackGoal from starting or continuing if its target
 * is out of stealth range. Uses reflection by field-type to locate the private
 * MobEntity actor field inside CrossbowAttackGoal.
 */
@Mixin(CrossbowAttackGoal.class)
public abstract class CrossbowAttackGoalMixin {
    /**
     * Cancel canStart() if the player is outside stealth range.
     */
    @Inject(method = "canStart()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelCrossbowCanStart(CallbackInfoReturnable<Boolean> cir) {
        CrossbowAttackGoal goal = (CrossbowAttackGoal) (Object) this;
        MobEntity mob = extractMob(goal);
        if (mob == null) return;

        LivingEntity target = mob.getTarget();
        if (target instanceof PlayerEntity player) {
            double dist = mob.distanceTo(player);
            double allowed = StealthDetectionEvents.computeFullDetectionRange(mob, player, mob.getWorld());
            if (!mob.canSee(player)) allowed *= 0.5;
            if (dist > allowed) {
                StealthUtils.clearTargetAndMemories(mob);
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Cancel shouldContinue() if the player moves out of stealth range.
     */
    @Inject(method = "shouldContinue()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelCrossbowShouldContinue(CallbackInfoReturnable<Boolean> cir) {
        CrossbowAttackGoal goal = (CrossbowAttackGoal) (Object) this;
        MobEntity mob = extractMob(goal);
        if (mob == null) return;

        LivingEntity target = mob.getTarget();
        if (target instanceof PlayerEntity player) {
            double dist = mob.distanceTo(player);
            double allowed = StealthDetectionEvents.computeFullDetectionRange(mob, player, mob.getWorld());
            if (!mob.canSee(player)) allowed *= 0.5;
            if (dist > allowed) {
                StealthUtils.clearTargetAndMemories(mob);
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Locate and return the private MobEntity field inside CrossbowAttackGoal by type.
     */
    private MobEntity extractMob(CrossbowAttackGoal goal) {
        for (Field f : goal.getClass().getDeclaredFields()) {
            if (MobEntity.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    return (MobEntity) f.get(goal);
                } catch (Exception e) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.warn("[CrossbowAttackGoalMixin] Failed to read field {}: {}", f.getName(), e.toString());
                    }
                    return null;
                }
            }
        }
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            StringBuilder sb = new StringBuilder("[CrossbowAttackGoalMixin] No MobEntity field found in CrossbowAttackGoal. Fields: ");
            for (Field f : goal.getClass().getDeclaredFields()) sb.append(f.getName()).append(" ");
            SoundAttractMod.LOGGER.warn(sb.toString());
        }
        return null;
    }
}
