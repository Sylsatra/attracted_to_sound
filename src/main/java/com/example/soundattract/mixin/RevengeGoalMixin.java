package com.example.soundattract.mixin;


import com.example.soundattract.StealthDetectionEvents; 
import com.example.soundattract.SoundAttractMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RevengeGoal.class)
public abstract class RevengeGoalMixin extends TrackTargetGoal {


    public RevengeGoalMixin(MobEntity mob, boolean checkVisibility) {
        super(mob, checkVisibility);
    }

    /**
     * Prevents the mob from retaliating if it was attacked by a player it cannot detect.
     * This logic now perfectly mirrors the stealth attack trigger in LivingEntityDamageMixin.
     */
    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void soundattract_preventRevengeOnStealthHit(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity attacker = this.mob.getAttacker();
        

        if (!(attacker instanceof PlayerEntity player)) {
            return;
        }



        boolean isUndetected = !StealthDetectionEvents.canMobDetectPlayer(this.mob, player);

        if (isUndetected) {




            if (this.mob.distanceTo(player) >= 5.0) {


                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info(
                        "[RevengeGoal] CANCELLING for {}: Player {} is undetectable and far away (>= 5 blocks).",
                        this.mob.getName().getString(),
                        player.getName().getString()
                    );
                }
                cir.setReturnValue(false);
            } else {


                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info(
                        "[RevengeGoal] ALLOWING for {}: Player {} is undetectable but too close (< 5 blocks) to ignore.",
                        this.mob.getName().getString(),
                        player.getName().getString()
                    );
                }

            }
        }
    }
}