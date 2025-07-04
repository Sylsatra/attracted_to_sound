package com.example.soundattract.mixin;

import com.example.soundattract.FovEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MobEntitySeeMixin {


    @Inject(method = "canSee(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void soundattract_onCanSee(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self instanceof MobEntity mob && entity instanceof PlayerEntity) {
            
            if (!FovEvents.isTargetInFov(mob, entity, false)) {
                cir.setReturnValue(false);
            }
        }
    }
}