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

/**
 * Mixin for ALL pathfinding mobs. This is better than MobEntity because it targets
 * the constructor, ensuring the goal is injected even if a subclass overrides initGoals().
 */
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

    /**
     * Injects our FleeGoal into every PathAwareEntity at the end of its constructor.
     * This is the most reliable way to add a goal, as it runs after the mob's
     * own initGoals() method (even overridden ones) has completed.
     */
    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void soundattract_addGoalsOnConstruct(EntityType<?> type, World world, CallbackInfo ci) {

        PathAwareEntity thisMob = (PathAwareEntity) (Object) this;
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.enableFleeFromUnseenAttackerGoal) {
            this.goalSelector.add(1, new FleeFromUnseenAttackerGoal(thisMob, 1.2D));

            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[PathAwareEntityMixin] Injected FleeFromUnseenAttackerGoal into {}.", this.getName().getString());
            }
        }
    }
}