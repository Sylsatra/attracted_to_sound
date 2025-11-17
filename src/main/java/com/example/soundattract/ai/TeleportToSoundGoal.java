package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;

public class TeleportToSoundGoal extends Goal {
    private static final UUID MOVEMENT_SPEED_MODIFIER_UUID = UUID.fromString("31506c13-0cbd-4f60-be15-62445a6d0842");

    private final MobEntity mob;

    private MobEntity toTeleport;
    private int unreachableTime;
    private int cooldown;
    private int teleportTick;
    private SoundTracker.SoundRecord targetSound;

    public TeleportToSoundGoal(MobEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.TARGET, Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (SoundAttractMod.CONFIG == null) return false;
        if (!SoundAttractMod.CONFIG.enableTeleportToSound) return false;
        if (--this.cooldown > 0) return false;
        if (this.mob.isBaby()) return false;

        String teleporterTagStr = SoundAttractMod.CONFIG.teleportCanTeleportTag;
        if (teleporterTagStr == null || teleporterTagStr.isBlank()) return false;
        TagKey<EntityType<?>> teleporterTag = TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier(teleporterTagStr));
        if (!this.mob.getType().isIn(teleporterTag)) return false;

        double chance = SoundAttractMod.CONFIG.teleportChance;
        if (this.mob.getRandom().nextDouble() >= chance) return false;

        World world = this.mob.getWorld();
        if (world.isClient()) return false;

        this.targetSound = SoundTracker.findNearestSound(world, mob, mob.getBlockPos(), mob.getEyePos());
        if (this.targetSound == null) return false;

        String targetTagStr = SoundAttractMod.CONFIG.teleportCanBeTeleportedTag;
        if (targetTagStr == null || targetTagStr.isBlank()) return false;
        TagKey<EntityType<?>> targetTag = TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier(targetTagStr));

        double followRange = this.mob.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
        double range = followRange > 0 ? followRange : 16.0;

        java.util.List<MobEntity> candidates = world.getEntitiesByClass(
                MobEntity.class,
                this.mob.getBoundingBox().expand(range),
                other -> other != this.mob && other.isAlive() && other.getType().isIn(targetTag));

        double closestDistSq = Double.MAX_VALUE;
        MobEntity closest = null;
        for (MobEntity candidate : candidates) {
            double d = this.mob.squaredDistanceTo(candidate);
            if (d < closestDistSq) {
                closestDistSq = d;
                closest = candidate;
            }
        }

        this.toTeleport = closest;
        return this.toTeleport != null;
    }

    @Override
    public boolean shouldContinue() {
        return this.toTeleport != null && this.toTeleport.isAlive() && this.targetSound != null && this.targetSound.ticksRemaining > 0;
    }

    @Override
    public void start() {
        this.mob.getLookControl().lookAt(this.toTeleport);
        this.mob.getNavigation().stop();
        this.mob.getNavigation().startMovingTo(this.toTeleport, 1.5f);
        this.toTeleport.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.toTeleport = null;
        this.unreachableTime = 0;
        this.targetSound = null;
        this.teleportTick = 0;
    }

    @Override
    public void tick() {
        if (this.targetSound == null || this.toTeleport == null) {
            this.stop();
            return;
        }

        BlockPos soundPos = this.targetSound.pos;
        if (this.teleportTick <= 0) {
            this.mob.getLookControl().lookAt(this.toTeleport);
            this.toTeleport.getNavigation().stop();
            if (this.mob.getNavigation().isIdle()) {
                this.mob.getNavigation().startMovingTo(this.toTeleport, 1.5f);
            }
            if (this.mob.squaredDistanceTo(this.toTeleport) <= 4.0f) {
                hide(this.mob);
                hide(this.toTeleport);
                this.teleportTick = 30;
            }
        } else {
            if (--this.teleportTick <= 0) {
                show(this.mob);
                teleportSafely(this.toTeleport, soundPos.getX() + 0.5, soundPos.getY(), soundPos.getZ() + 0.5);
                show(this.toTeleport);
                this.cooldown = SoundAttractMod.CONFIG.teleportCooldownTicks;
                this.stop();
                return;
            }
        }

        if (++this.unreachableTime > 120) {
            this.cooldown = SoundAttractMod.CONFIG.teleportCooldownTicks;
            this.stop();
        }
    }

    private void hide(LivingEntity entity) {
        applySpeedFreeze(entity);
        if (entity.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.PORTAL, entity.getX(), entity.getEyeY(), entity.getZ(), 200, 0.5, 0.5, 0.5, 0.5);
        }
        entity.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 4f, 0.5f);
        entity.setNoGravity(true);
        entity.setInvisible(true);
    }

    private void show(LivingEntity entity) {
        removeSpeedFreeze(entity);
        entity.setNoGravity(false);
        entity.setInvisible(false);
        entity.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 2f);
    }

    private void applySpeedFreeze(LivingEntity entity) {
        EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst != null && inst.getModifier(MOVEMENT_SPEED_MODIFIER_UUID) == null) {
            inst.addTemporaryModifier(new EntityAttributeModifier(MOVEMENT_SPEED_MODIFIER_UUID, "TeleportToSound freeze", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private void removeSpeedFreeze(LivingEntity entity) {
        EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(MOVEMENT_SPEED_MODIFIER_UUID);
        }
    }

    private void teleportSafely(LivingEntity entity, double x, double y, double z) {
        World world = entity.getWorld();
        BlockPos.Mutable mutable = new BlockPos.Mutable((int) x, (int) y, (int) z);
        BlockPos targetPos = mutable.toImmutable();
        Vec3d oldPos = entity.getPos();

        entity.requestTeleport(x, y, z);
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.emitGameEvent(GameEvent.TELEPORT, oldPos, GameEvent.Emitter.of(entity));
        }
    }
}
