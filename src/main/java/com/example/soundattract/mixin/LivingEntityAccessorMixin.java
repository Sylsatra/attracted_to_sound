package com.example.soundattract.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessorMixin {

    @Invoker("actuallyHurt")
    void invokeActuallyHurt(ServerLevel level, DamageSource damageSource, float amount);

    @Invoker("getHurtSound")
    SoundEvent invokeGetHurtSound(DamageSource p_21161_);
}
