package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.StealthUtils;
import com.example.soundattract.SoundAttractMod;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Stops any ProjectileAttackGoal when the player is out of stealth range.
 */
@Mixin(ProjectileAttackGoal.class)
public abstract class ProjectileAttackGoalMixin {
    /**
     * Cancel canStart() if the player is outside stealth range.
     */
    @Inject(method = "canStart()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelProjectileCanStart(CallbackInfoReturnable<Boolean> cir) {
        ProjectileAttackGoal goal = (ProjectileAttackGoal) (Object) this;
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
    private void soundattract$cancelProjectileShouldContinue(CallbackInfoReturnable<Boolean> cir) {
        ProjectileAttackGoal goal = (ProjectileAttackGoal) (Object) this;
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
     * Reflectively find the private field of type MobEntity inside ProjectileAttackGoal.
     */
    private MobEntity extractMob(ProjectileAttackGoal goal) {
        for (Field f : goal.getClass().getDeclaredFields()) {
            if (MobEntity.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    return (MobEntity) f.get(goal);
                } catch (Exception e) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.warn("[ProjectileAttackGoalMixin] Failed to read field "
                            + f.getName() + ": " + e);
                    }
                    return null;
                }
            }
        }
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            StringBuilder sb = new StringBuilder("[ProjectileAttackGoalMixin] No MobEntity field found in ProjectileAttackGoal. Available fields: ");
            for (Field f : goal.getClass().getDeclaredFields()) sb.append(f.getName()).append(" ");
            SoundAttractMod.LOGGER.warn(sb.toString());
        }
        return null;
    }
}
