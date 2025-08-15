package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.StealthUtils;
import com.example.soundattract.FovEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;



@Mixin(ProjectileAttackGoal.class)
public abstract class ProjectileAttackGoalMixin {


    @Accessor("mob")
    abstract MobEntity getMob();

    /**
     * Cancel canStart() if the player is not visible according to our stealth rules.
     */
    @Inject(method = "canStart()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelProjectileCanStart(CallbackInfoReturnable<Boolean> cir) {

        MobEntity mob = this.getMob();
        LivingEntity target = mob.getTarget();


        if (target instanceof PlayerEntity player) {




            if (!FovEvents.hasSmartLineOfSight(mob, player)) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[ProjectileAttackGoal] Cancelling canStart for {}: target {} not visible (smart LOS).", mob.getName().getString(), player.getName().getString());
                }

                StealthUtils.clearTargetAndMemories(mob);
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Cancel shouldContinue() if the player becomes not visible.
     */
    @Inject(method = "shouldContinue()Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelProjectileShouldContinue(CallbackInfoReturnable<Boolean> cir) {

        MobEntity mob = this.getMob();
        LivingEntity target = mob.getTarget();

        if (target instanceof PlayerEntity player) {

            if (!FovEvents.hasSmartLineOfSight(mob, player)) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[ProjectileAttackGoal] Cancelling shouldContinue for {}: target {} no longer visible.", mob.getName().getString(), player.getName().getString());
                }

                cir.setReturnValue(false);
            }
        }
    }
}