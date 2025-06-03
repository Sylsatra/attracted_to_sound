package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.StealthUtils;

import net.minecraft.entity.ai.goal.BowAttackGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(BowAttackGoal.class)
public abstract class BowAttackGoalMixin {
    /**
     * Prevent the goal from starting if the player is out of stealth range.
     */
    @Inject(method = "canStart()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelBowCanStart(CallbackInfoReturnable<Boolean> cir) {
        BowAttackGoal<?> goal = (BowAttackGoal<?>)(Object)this;
        MobEntity mob = null;
        try {
            Field actorField;
            try {
                actorField = goal.getClass().getDeclaredField("actor");
            } catch (NoSuchFieldException e1) {
                actorField = goal.getClass().getDeclaredField("field_6576");
            }
            actorField.setAccessible(true);
            mob = (MobEntity) actorField.get(goal);
        } catch (NoSuchFieldException e) {
            Field[] fields = goal.getClass().getDeclaredFields();
            StringBuilder sb = new StringBuilder("[BowAttackGoalMixin] Available fields in BowAttackGoal: ");
            for (Field f : fields) sb.append(f.getName()).append(" ");
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn(sb.toString());
                SoundAttractMod.LOGGER.warn("[BowAttackGoalMixin] Could not access 'actor' field: " + e);
            }
            return;
        } catch (Exception e) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[BowAttackGoalMixin] Could not access 'actor' field: " + e);
            }
            return;
        }
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
     * Prevent the goal from continuing if the player moves out of stealth range.
     */
    @Inject(method = "shouldContinue()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelBowShouldContinue(CallbackInfoReturnable<Boolean> cir) {
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[BowAttackGoalMixin] shouldContinue() injected!");
        }

        BowAttackGoal<?> goal = (BowAttackGoal<?>)(Object)this;
        MobEntity mob = null;
        try {
            Field actorField;
            try {
                actorField = goal.getClass().getDeclaredField("actor");
            } catch (NoSuchFieldException e1) {
                actorField = goal.getClass().getDeclaredField("field_6576");
            }
            actorField.setAccessible(true);
            mob = (MobEntity) actorField.get(goal);
        } catch (NoSuchFieldException e) {
            Field[] fields = goal.getClass().getDeclaredFields();
            StringBuilder sb = new StringBuilder("[BowAttackGoalMixin] Available fields in BowAttackGoal: ");
            for (Field f : fields) sb.append(f.getName()).append(" ");
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn(sb.toString());
                SoundAttractMod.LOGGER.warn("[BowAttackGoalMixin] Could not access 'actor' field: " + e);
            }
            return;
        } catch (Exception e) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.warn("[BowAttackGoalMixin] Could not access 'actor' field: " + e);
            }
            return;
        }
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
}
