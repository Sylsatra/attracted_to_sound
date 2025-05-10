package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.SoundAttractMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ActiveTargetGoal.class)
public abstract class TargetGoalMixin {
    @Shadow protected LivingEntity targetEntity;

    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void soundattract$cancelTargeting(CallbackInfoReturnable<Boolean> cir) {
        if (targetEntity instanceof PlayerEntity player) {
            try {
                Class<?> superClass = this.getClass().getSuperclass();
                java.lang.reflect.Field mobField = null;
                while (superClass != null) {
                    try {
                        mobField = superClass.getDeclaredField("field_6660");
                        break;
                    } catch (NoSuchFieldException e) {
                        superClass = superClass.getSuperclass();
                    }
                }
                if (mobField != null) {
                    mobField.setAccessible(true);
                    Object mobObj = mobField.get(this);
                    if (mobObj instanceof MobEntity mob) {
                        double dist = mob.distanceTo(player);
                        boolean isNight = mob.getWorld() instanceof ServerWorld world && world.getTimeOfDay() > 13000 && world.getTimeOfDay() < 23000;
                        double detectionRange = StealthDetectionEvents.getRealisticStealthDetectionRange(player, mob.getWorld(), isNight, dist);
                        if (!mob.canSee(player)) detectionRange *= 0.5;
                        if (dist > detectionRange) {
                            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                                SoundAttractMod.LOGGER.info("[StealthDetectionEvents/Mixin] Prevented mob {} from targeting player {}: dist={} > detectionRange={}", mob.getName().getString(), player.getName().getString(), dist, detectionRange);
                            }
                            mob.setTarget(null);
                            cir.setReturnValue(false);
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
    }
}

