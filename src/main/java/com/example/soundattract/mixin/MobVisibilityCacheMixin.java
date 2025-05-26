package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.mob.MobVisibilityCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobVisibilityCache.class)
public abstract class MobVisibilityCacheMixin {
    @Inject(method = "canSee", at = @At("HEAD"), cancellable = true)
    private void soundattract$customCanSee(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PlayerEntity player) {
            MobEntity mob = (Object)this instanceof MobEntity m ? m : null;
            if (mob != null && mob.getWorld() instanceof ServerWorld world) {
                double dist = mob.distanceTo(player);
                boolean isNight = world.getTimeOfDay() > 13000 && world.getTimeOfDay() < 23000;
                double detectionRange = StealthDetectionEvents.getRealisticStealthDetectionRange(player, world, isNight, dist);
                if (dist > detectionRange) {
                    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                        com.example.soundattract.SoundAttractMod.LOGGER.info("[MobVisibilityCacheMixin] Mob {} cannot see player {}: dist={} > detectionRange={}", mob.getName().getString(), player.getName().getString(), dist, detectionRange);
                    }
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
