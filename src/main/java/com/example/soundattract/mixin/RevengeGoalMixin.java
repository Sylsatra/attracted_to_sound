package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.StealthUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RevengeGoal.class)
public abstract class RevengeGoalMixin {

    // Accessor to grab "mob" from TrackTargetGoal (superclass of RevengeGoal):
    private MobEntity getMob() {
        return ((TrackTargetGoalAccessor)(Object)this).getMob();
    }

    @Inject(
        method = "canStart()Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onCanStart_StealthChecks(CallbackInfoReturnable<Boolean> cir) {
        MobEntity mob = getMob();
        LivingEntity attacker = mob.getAttacker();
        if (!(attacker instanceof PlayerEntity player)) {
            // If the attacker is not a player, let vanilla resume its own canStart()
            return;
        }

        double dist = mob.distanceTo(player);
        double allowed = StealthDetectionEvents.computeFullDetectionRange(mob, player, mob.getWorld());
        if (!mob.canSee(player)) {
            allowed *= 0.5;
        }

        if (dist > allowed) {
            // Player is outside our stealth range → cancel revenge entirely
            StealthUtils.clearTargetAndMemories(mob);
            cir.setReturnValue(false);
        }
        // Otherwise: player is in stealth range → let vanilla check lastAttackedTime, universal anger, noRevengeTypes, etc.
    }
}
