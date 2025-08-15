package com.example.soundattract.mixin;

import com.example.soundattract.FovEvents;
import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.StealthUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.CrossbowAttackGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.reflect.Field;

@Mixin(CrossbowAttackGoal.class)
public abstract class CrossbowAttackGoalMixin {


    private MobEntity extractMob(CrossbowAttackGoal goal) {
        for (Field field : goal.getClass().getDeclaredFields()) {
            if (MobEntity.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return (MobEntity) field.get(goal);
                } catch (Exception e) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.error("[CrossbowAttackGoalMixin] Failed to reflectively access mob field: " + e);
                    }
                    return null;
                }
            }
        }
        return null;
    }


    private boolean isTargetValid(MobEntity mob, LivingEntity target) {
        if (!(target instanceof PlayerEntity player)) {
            return true;
        }

        return FovEvents.hasSmartLineOfSight(mob, player);
    }

    @Inject(method = "canStart()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelCrossbowCanStart(CallbackInfoReturnable<Boolean> cir) {
        MobEntity mob = extractMob((CrossbowAttackGoal)(Object)this);
        if (mob == null) return;
        
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        if (!isTargetValid(mob, target)) {
            StealthUtils.clearTargetAndMemories(mob);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldContinue()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelCrossbowShouldContinue(CallbackInfoReturnable<Boolean> cir) {
        MobEntity mob = extractMob((CrossbowAttackGoal)(Object)this);
        if (mob == null) return;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            cir.setReturnValue(false);
            return;
        }
        if (!isTargetValid(mob, target)) {
            cir.setReturnValue(false);
        }
    }
}