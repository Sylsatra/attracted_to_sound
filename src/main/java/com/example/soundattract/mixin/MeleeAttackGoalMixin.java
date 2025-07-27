package com.example.soundattract.mixin;

import com.example.soundattract.FovEvents;
import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.StealthUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;



@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin {


    @Accessor("mob")
    abstract PathAwareEntity getMob();

    private boolean isTargetValid(MobEntity mob, LivingEntity target) {
        if (!(target instanceof PlayerEntity player)) {
            return true;
        }

        return mob.canSee(player);
    }

    /**
     * Prevent the goal from starting if the player is not a valid stealth target.
     */
    @Inject(method = "canStart()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelMeleeCanStart(CallbackInfoReturnable<Boolean> cir) {

        MobEntity mob = this.getMob();
        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive()) {
            return;
        }

        if (!isTargetValid(mob, target)) {

            StealthUtils.clearTargetAndMemories(mob);
            cir.setReturnValue(false);
        }
    }

    /**
     * Prevent the goal from continuing if the player is no longer a valid stealth target.
     */
    @Inject(method = "shouldContinue()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelMeleeShouldContinue(CallbackInfoReturnable<Boolean> cir) {

        MobEntity mob = this.getMob();
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