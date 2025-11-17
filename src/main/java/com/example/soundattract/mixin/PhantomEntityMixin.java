package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.accessor.FleeOnDamageAccessor;
import com.example.soundattract.ai.PhantomFleeFromUnseenAttackerGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhantomEntity.class)
public abstract class PhantomEntityMixin extends MobEntity implements FleeOnDamageAccessor {

    @Unique @Nullable private Vec3d soundattract$fleeFromLocation;

    protected PhantomEntityMixin(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public void soundattract_setFleeFromLocation(@Nullable Vec3d pos) {
        this.soundattract$fleeFromLocation = pos;
    }

    @Override
    @Nullable
    public Vec3d soundattract_getFleeFromLocation() {
        return this.soundattract$fleeFromLocation;
    }

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void soundattract_addFleeGoal(EntityType<?> type, World world, CallbackInfo ci) {
        PhantomEntity self = (PhantomEntity) (Object) this;
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.enableFleeFromUnseenAttackerGoal) {
            this.goalSelector.add(1, new PhantomFleeFromUnseenAttackerGoal(self, 1.4D));
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[PhantomEntityMixin] Injected PhantomFleeFromUnseenAttackerGoal into {}.", this.getName().getString());
            }
        }
    }
}
