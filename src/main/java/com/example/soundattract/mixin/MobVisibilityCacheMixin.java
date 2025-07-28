package com.example.soundattract.mixin;

import com.example.soundattract.FovEvents;
import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.SoundAttractMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.MobVisibilityCache;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;



@Mixin(MobVisibilityCache.class)
public abstract class MobVisibilityCacheMixin {


    @Accessor("owner")
    abstract MobEntity getOwner();

    @Inject(
        method = "canSee(Lnet/minecraft/entity/Entity;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void soundattract$onCanSee(Entity entity, CallbackInfoReturnable<Boolean> cir) {

        if (!(entity instanceof PlayerEntity player)) {
            return;
        }


        MobEntity mob = this.getOwner();


        if (mob == null || !(mob.getWorld() instanceof ServerWorld world)) {
            return;
        }





        if (!FovEvents.isTargetInFov(mob, player, false)) {

            cir.setReturnValue(false);
            return;
        }


        double dist = mob.distanceTo(player);
        double detectionRange = StealthDetectionEvents.computeFullDetectionRange(mob, player, world);

        if (dist > detectionRange) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                    "[MobVisibilityCacheMixin] Mob {} cannot see {}: out of range (dist {:.2f} > allowed {:.2f})",
                    mob.getName().getString(), player.getName().getString(), dist, detectionRange
                );
            }
            cir.setReturnValue(false);
            return;
        }
        


    }
}