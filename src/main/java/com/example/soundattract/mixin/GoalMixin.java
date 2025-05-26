package com.example.soundattract.mixin;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Goal.class)
public abstract class GoalMixin {
    @Inject(method = "shouldContinue", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelTargetRetention(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof ActiveTargetGoal activeTargetGoal) {
            try {
                var targetEntityField = ActiveTargetGoal.class.getDeclaredField("field_6644");
                targetEntityField.setAccessible(true);
                Object targetEntity = targetEntityField.get(activeTargetGoal);
                if (targetEntity instanceof PlayerEntity player) {
                    MobEntity mob = (MobEntity)ActiveTargetGoal.class.getSuperclass().getDeclaredField("field_6660").get(activeTargetGoal);
                    if (mob != null && (!mob.canSee(player) || mob.squaredDistanceTo(player) > 100)) {
                        cir.setReturnValue(false);
                    }
                }
            } catch (Exception e) {
            }
        }
    }
}
