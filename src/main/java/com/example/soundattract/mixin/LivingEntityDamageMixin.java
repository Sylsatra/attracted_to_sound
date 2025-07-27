package com.example.soundattract.mixin;


import com.example.soundattract.StealthDetectionEvents; 
import com.example.soundattract.FovEvents;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.accessor.FleeOnDamageAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"))
    private void soundattract_onDamageTriggerStealthReactions(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof MobEntity mob)) return;
        
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof PlayerEntity player) || attacker == mob) return;




        boolean isUnexpectedHit;
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
            isUnexpectedHit = false;
        } else {

            boolean inFov = FovEvents.isTargetInFov(mob, player, false);
            double range = StealthDetectionEvents.computeFullDetectionRange(mob, player, mob.getWorld());
            boolean inRange = mob.distanceTo(player) <= range;
            


            isUnexpectedHit = !(inFov && inRange);
        }

        if (!isUnexpectedHit) return;
        

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[DamageMixin] Unexpected hit on {} by {}. Deciding reaction...", mob.getName().getString(), player.getName().getString());
        }


        if (mob instanceof HostileEntity || mob instanceof Angerable) {
            double distance = mob.distanceTo(player);
            if (distance >= 5.0) {
                if (SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("... Target is HOSTILE and far away. Setting flee location.", mob.getName().getString());
                ((FleeOnDamageAccessor) mob).soundattract_setFleeFromLocation(attacker.getPos());
            } else {
                if (mob instanceof CreeperEntity creeper) {
                    if (SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("... Target is a Creeper at close range. IGNITING.", mob.getName().getString());
                    creeper.ignite();
                } else {
                    if (SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("... Target is HOSTILE and close. Allowing reflex revenge.", mob.getName().getString());
                }
            }
        } else {
            if (SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("... Target is PASSIVE. Setting flee location.", mob.getName().getString());
            ((FleeOnDamageAccessor) mob).soundattract_setFleeFromLocation(attacker.getPos());
        }
    }
    
    

    @ModifyVariable(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"), argsOnly = true)
    private float soundattract_modifyDamageAmount(float originalAmount, DamageSource source) {
        LivingEntity target = (LivingEntity) (Object) this;

        float modifiedDamage = FovEvents.getModifiedDamage(target, source, originalAmount);

        if (modifiedDamage > originalAmount && SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[DamageMixin] Backstab bonus applied to {}. Damage: {} -> {}", target.getName().getString(), originalAmount, modifiedDamage);
        }

        return modifiedDamage;
    }
}