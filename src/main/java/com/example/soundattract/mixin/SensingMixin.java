package com.example.soundattract.mixin;

import com.example.soundattract.event.FovEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sensing.class)
public abstract class SensingMixin {

    @Shadow @Final private Mob mob;

    @Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void soundattract$hasLineOfSight(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!(target instanceof Player player)) {
            return;
        }

        boolean visible = FovEvents.hasSmartLineOfSight(this.mob, player);
        cir.setReturnValue(visible);
    }
}
