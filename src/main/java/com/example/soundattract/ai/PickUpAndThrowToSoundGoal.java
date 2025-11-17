package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

public class PickUpAndThrowToSoundGoal extends Goal {
    private final MobEntity mob;
    private MobEntity pickUp;
    private int unreachableTime;
    private int cooldown;
    private SoundTracker.SoundRecord targetSound;

    public PickUpAndThrowToSoundGoal(MobEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.TARGET, Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (SoundAttractMod.CONFIG == null) return false;
        if (!SoundAttractMod.CONFIG.enablePickUpAndThrowToSound) return false;
        if (--this.cooldown > 0) {
            if (this.mob.hasPassengers()) {
                if (this.mob.getFirstPassenger() instanceof MobEntity passenger && passenger.isAlive()) {
                    this.pickUp = passenger;
                    return true;
                }
            }
            return false;
        }

        double chance = SoundAttractMod.CONFIG.pickUpChance;
        if (this.mob.getRandom().nextDouble() >= chance) return false;

        World world = this.mob.getWorld();
        if (world.isClient()) return false;

        this.targetSound = SoundTracker.findNearestSound(world, mob, mob.getBlockPos(), mob.getEyePos());
        if (this.targetSound == null) return false;

        String canPickUpTagStr = SoundAttractMod.CONFIG.pickUpCanPickUpTag;
        if (canPickUpTagStr == null || canPickUpTagStr.isBlank()) return false;
        TagKey<EntityType<?>> canPickUpTag = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(canPickUpTagStr));
        if (!this.mob.getType().isIn(canPickUpTag)) return false;

        int minDist = SoundAttractMod.CONFIG.pickUpMinDistanceToPickUp;
        double distToSound = Math.sqrt(this.mob.getBlockPos().getSquaredDistance(this.targetSound.pos));
        if (distToSound < minDist) return false;

        String canBePickedUpTagStr = SoundAttractMod.CONFIG.pickUpCanBePickedUpTag;
        if (canBePickedUpTagStr == null || canBePickedUpTagStr.isBlank()) return false;
        TagKey<EntityType<?>> canBePickedUpTag = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(canBePickedUpTagStr));

        double followRange = this.mob.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
        double range = followRange > 0 ? followRange : 16.0;

        java.util.List<MobEntity> candidates = world.getEntitiesByClass(
                MobEntity.class,
                this.mob.getBoundingBox().expand(range),
                other -> other != this.mob && other.isAlive() && other.getType().isIn(canBePickedUpTag));

        double closestDistSq = Double.MAX_VALUE;
        MobEntity closest = null;
        for (MobEntity candidate : candidates) {
            double d = this.mob.squaredDistanceTo(candidate);
            if (d < closestDistSq) {
                closestDistSq = d;
                closest = candidate;
            }
        }

        this.pickUp = closest;
        return this.pickUp != null && this.pickUp.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return this.pickUp != null && this.pickUp.isAlive() && this.targetSound != null && this.targetSound.ticksRemaining > 0;
    }

    @Override
    public void start() {
        this.mob.getLookControl().lookAt(this.pickUp);
        this.mob.getNavigation().stop();
        this.mob.getNavigation().startMovingTo(this.pickUp, SoundAttractMod.CONFIG.pickUpSpeedModifier);
        this.pickUp.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.pickUp = null;
        this.unreachableTime = 0;
        this.targetSound = null;
    }

    @Override
    public void tick() {
        World world = this.mob.getWorld();
        if (world.isClient()) return;

        if (this.targetSound == null || this.targetSound.ticksRemaining <= 0) {
            this.targetSound = SoundTracker.findNearestSound(world, mob, mob.getBlockPos(), mob.getEyePos());
            if (this.targetSound == null) {
                this.stop();
                return;
            }
        }

        if (this.pickUp == null || !this.pickUp.isAlive()) {
            this.stop();
            return;
        }

        if (!this.mob.hasPassengers()) {
            this.pickUp.getNavigation().stop();
            if (this.mob.getNavigation().isIdle()) {
                this.mob.getNavigation().startMovingTo(this.pickUp, SoundAttractMod.CONFIG.pickUpSpeedModifier);
            }
            if (this.mob.squaredDistanceTo(this.pickUp) <= 4.0f) {
                this.pickUp.startRiding(this.mob, false);
                this.cooldown = 20;
            }
        } else {
            BlockPos soundPos = this.targetSound.pos;
            int maxThrow = SoundAttractMod.CONFIG.pickUpMaxDistanceToThrow;
            double distToSoundSq = this.mob.getBlockPos().getSquaredDistance(soundPos);
            if (--this.cooldown <= 0 && distToSoundSq <= (double) (maxThrow * maxThrow)) {
                double dx = soundPos.getX() + 0.5 - this.pickUp.getX();
                double dy = soundPos.getY() + 0.5 - this.pickUp.getY();
                double dz = soundPos.getZ() + 0.5 - this.pickUp.getZ();
                double distXZ = Math.sqrt(dx * dx + dz * dz);

                Vec3d motion = new Vec3d(
                        dx * 0.1d,
                        MathHelper.clamp(dy, 4d, 40d) / 10d + distXZ / 100d,
                        dz * 0.1d
                );
                this.pickUp.stopRiding();
                this.pickUp.setVelocity(motion);
                this.mob.playSound(SoundEvents.ITEM_AXE_STRIP, 3f, 1.5F);

                this.cooldown = SoundAttractMod.CONFIG.pickUpCooldownTicks;
                this.stop();
                return;
            }
        }

        if (++this.unreachableTime > 120) {
            this.cooldown = SoundAttractMod.CONFIG.pickUpCooldownTicks;
            this.stop();
        }
    }
}
