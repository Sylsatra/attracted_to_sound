package com.example.soundattract.mixin;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.accessor.FleeOnDamageAccessor;
import com.example.soundattract.accessor.SoundAttractMobAccessor;
import com.example.soundattract.ai.FleeFromUnseenAttackerGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PathAwareEntity.class)
public abstract class PathAwareEntityMixin extends MobEntity implements SoundAttractMobAccessor, FleeOnDamageAccessor {


    @Unique @Nullable private Vec3d lastUnseenDamageSource;
    @Unique @Nullable private Vec3d fleeFromLocation;
    @Unique
    private int losingTargetTicks = 0;

    public int soundattract_getLosingTargetTicks() {
        return this.losingTargetTicks;
    }

    public void soundattract_setLosingTargetTicks(int ticks) {
        this.losingTargetTicks = ticks;
    }

    @Override public void soundattract_setLastUnseenDamageSource(@Nullable Vec3d pos) { this.lastUnseenDamageSource = pos; }
    @Override @Nullable public Vec3d soundattract_getLastUnseenDamageSource() { return this.lastUnseenDamageSource; }
    @Override public void soundattract_setFleeFromLocation(@Nullable Vec3d pos) { this.fleeFromLocation = pos; }
    @Override @Nullable public Vec3d soundattract_getFleeFromLocation() { return this.fleeFromLocation; }


    protected PathAwareEntityMixin(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }


    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void soundattract_addGoalsOnConstruct(EntityType<?> type, World world, CallbackInfo ci) {

        if (!SoundAttractMod.CONFIG.enableFleeFromUnseenAttackerGoal) {
            return;
        }

        MobEntity thisMob = (MobEntity) (Object) this;

        this.goalSelector.add(3, new FleeFromUnseenAttackerGoal(thisMob, 1.2D));

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[PathAwareEntityMixin] Injected FleeFromUnseenAttackerGoal into {}.", this.getName().getString());
        }
    }
}