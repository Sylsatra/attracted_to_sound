package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.FovEvents;
import com.example.soundattract.ai.BlockBreakerManager;
import com.example.soundattract.ai.BlockBreakerPosGoal;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrackTargetGoal.class)
public abstract class TrackTargetGoalMixin {

    @Shadow @Final protected MobEntity mob;

    /**
     * Injects a universal rule: if a mob's target is a player, and the mob can no longer
     * see that player according to our stealth rules, it should stop its current goal.
     */
    @Inject(
        method = "shouldContinue()Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onShouldContinue_StealthChecks(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity currentTarget = this.mob.getTarget();


        if (currentTarget instanceof PlayerEntity player) {
            boolean visible = FovEvents.hasSmartLineOfSight(this.mob, player);
            if (!visible) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info(
                        "[TrackTargetGoalMixin] Cancelling shouldContinue for {}: target {} no longer visible.",
                        this.mob.getName().getString(),
                        player.getName().getString()
                    );
                }
                cir.setReturnValue(false);
                return;
            }



            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.enableBlockBreaking) {
                double reach = 2.75;
                if (this.mob.distanceTo(player) > reach && this.mob.getNavigation().isIdle()) {
                    BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                        this.mob,
                        player.getBlockPos(),
                        SoundAttractMod.CONFIG.blockBreakTimeMultiplier,
                        SoundAttractMod.CONFIG.blockBreakToolOnly,
                        SoundAttractMod.CONFIG.blockBreakProperToolOnly,
                        SoundAttractMod.CONFIG.blockBreakProperToolRequired
                    );
                    BlockBreakerManager.scheduleAdd(this.mob, breaker, 2);
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                            "[TrackTargetGoalMixin] Scheduling BlockBreakerPosGoal for {} toward player {} at {} (nav idle, out of reach)",
                            this.mob.getName().getString(), player.getName().getString(), player.getBlockPos()
                        );
                    }
                }
            }
        }
    }
}