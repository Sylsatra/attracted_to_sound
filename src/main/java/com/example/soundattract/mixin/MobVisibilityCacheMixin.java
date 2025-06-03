package com.example.soundattract.mixin;

import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.SoundAttractMod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.mob.MobVisibilityCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(MobVisibilityCache.class)
public abstract class MobVisibilityCacheMixin {
    @Inject(method = "canSee", at = @At("HEAD"), cancellable = true)
    private void soundattract$customCanSee(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof net.minecraft.entity.player.PlayerEntity player) {
            MobEntity mob = null;
            try {
                Field ownerField;
                try {
                    ownerField = this.getClass().getDeclaredField("owner");
                } catch (NoSuchFieldException e1) {
                    ownerField = this.getClass().getDeclaredField("field_6691");
                }
                ownerField.setAccessible(true);
                mob = (MobEntity) ownerField.get(this);
            } catch (NoSuchFieldException e) {
                Field[] fields = this.getClass().getDeclaredFields();
                StringBuilder sb = new StringBuilder("[MobVisibilityCacheMixin] Available fields in MobVisibilityCache: ");
                for (Field f : fields) sb.append(f.getName()).append(" ");
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.warn(sb.toString());
                    SoundAttractMod.LOGGER.warn("[MobVisibilityCacheMixin] Could not access 'owner' field: " + e);
                }
            } catch (Exception e) {
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.warn("[MobVisibilityCacheMixin] Could not access 'owner' field: " + e);
                }
            }
            if (mob != null && mob.getWorld() instanceof ServerWorld world) {
                double dist = mob.distanceTo(player);
                double detectionRange = StealthDetectionEvents.computeFullDetectionRange(mob, player, world);

                if (dist > detectionRange) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                            "[MobVisibilityCacheMixin] Mob {} cannot see player {}: dist={} > detectionRange={}",
                            mob.getName().getString(),
                            player.getName().getString(),
                            dist,
                            detectionRange
                        );
                    }
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
