package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.accessor.FleeOnDamageAccessor;
import com.example.soundattract.ai.FleeFromUnseenAttackerGoal;
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
    public @Nullable Vec3d soundattract_getFleeFromLocation() {
        return this.soundattract$fleeFromLocation;
    }

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void soundattract_addFleeGoal(EntityType<?> type, World world, CallbackInfo ci) {
        MobEntity self = (MobEntity) (Object) this;
        this.goalSelector.add(3, new FleeFromUnseenAttackerGoal(self, 1.2D));
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[PhantomEntityMixin] Injected FleeFromUnseenAttackerGoal into phantom {}.", this.getName().getString());
        }
    }
}
