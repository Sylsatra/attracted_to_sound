package com.example.soundattract.mixin;

import com.example.soundattract.FovEvents;
import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.StealthUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.CreeperIgniteGoal;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreeperIgniteGoal.class)
public abstract class CreeperIgniteGoalMixin {

    @Accessor("creeper")
    abstract CreeperEntity getCreeper();

    /**
     * This method now only contains the standard stealth checks.
     * The special "startle" reaction is handled by LivingEntityDamageMixin.
     */
    private boolean isTargetValid(CreeperEntity creeper, LivingEntity target) {
        if (!(target instanceof PlayerEntity player)) {
            return true;
        }

        return creeper.canSee(player);
    } 

    @Inject(method = "canStart()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelIgniteCanStart(CallbackInfoReturnable<Boolean> cir) {
        CreeperEntity creeper = this.getCreeper();
        LivingEntity target = creeper.getTarget();

        if (target == null) {
            return;
        }
        if (!isTargetValid(creeper, target)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelIgniteTick(CallbackInfo ci) {
        CreeperEntity creeper = this.getCreeper();
        LivingEntity target = creeper.getTarget();

        if (target == null || !target.isAlive()) {
            creeper.setFuseSpeed(-1);
            ci.cancel();
            return;
        }
        if (!isTargetValid(creeper, target)) {
            creeper.setFuseSpeed(-1);
            StealthUtils.clearTargetAndMemories(creeper);
            ci.cancel();
        }
    }
}