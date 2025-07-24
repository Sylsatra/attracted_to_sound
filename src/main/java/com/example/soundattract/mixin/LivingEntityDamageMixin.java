package com.example.soundattract.mixin;

import com.example.soundattract.FovEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable; 

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"))
    private void soundattract_onDamageRememberAttacker(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = (LivingEntity) (Object) this;

        if (target instanceof MobEntity mob) {
            net.minecraft.entity.Entity attacker = source.getAttacker();
            if (attacker == null || attacker == mob) return;

            if (!FovEvents.isTargetInFov(mob, attacker, true)) {
                if (mob.getBrain() != null && attacker instanceof LivingEntity livingAttacker) {
                    mob.getBrain().remember(MemoryModuleType.ATTACK_TARGET, livingAttacker, 200L);
                    mob.getBrain().remember(MemoryModuleType.HURT_BY, source, 200L);
                }
            }
        }
    }
    
    @ModifyVariable(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"), argsOnly = true)
    private float soundattract_modifyDamageAmount(float originalAmount, DamageSource source) {
        LivingEntity target = (LivingEntity) (Object) this;
        return FovEvents.getModifiedDamage(target, source, originalAmount);
    }
}