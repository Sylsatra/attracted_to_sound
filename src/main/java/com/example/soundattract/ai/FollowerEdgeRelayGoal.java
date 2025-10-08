package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.StealthDetectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FollowerEdgeRelayGoal extends Goal {

    private final Mob mob;
    private final double moveSpeed;

    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;

    private int scanCooldownCounter = 0;

    private BlockPos lastPos = null;
    private int stuckTicks = 0;

    private SoundTracker.SoundRecord cachedSound = null;

    private BlockBreakerPosGoal blockBreakerGoal = null;

    private enum EdgeMobState { GOING_TO_SOUND, RETURNING_TO_LEADER }
    private EdgeMobState edgeMobState = null;
    private boolean raidScheduled = false;

    public FollowerEdgeRelayGoal(Mob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private int scanCooldownTicks() {
        return com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
    }

    private boolean isMobEligible() {
        java.util.Set<net.minecraft.world.entity.EntityType<?>> attractedTypes = com.example.soundattract.SoundAttractionEvents.getCachedAttractedEntityTypes();
        boolean byType = attractedTypes.contains(this.mob.getType());
        boolean hasProfile = SoundAttractConfig.getMatchingProfile(this.mob) != null;
        return byType || hasProfile;
    }

    private double getArrivalDistance() {
        return SoundAttractConfig.COMMON.arrivalDistance.get();
    }

    private SoundTracker.SoundRecord findNearestSound() {
        Level level = this.mob.level();
        if (level.isClientSide()) return null;
        return SoundTracker.findNearestSound(this.mob, level, this.mob.blockPosition(), this.mob.getEyePosition());
    }

    @Override
    public boolean canUse() {
        if (this.mob.isVehicle() || this.mob.isSleeping()) return false;
        if (!isMobEligible()) return false;
        if (!SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) return false;
        Mob leader = MobGroupManager.getLeader(mob);
        boolean isDeserter = MobGroupManager.isDeserter(mob);
        if (leader == mob && !isDeserter) return false;
        if (!(MobGroupManager.isEdgeMob(mob) || isDeserter)) return false;
        if (scanCooldownCounter > 0) {
            scanCooldownCounter--;
            return false;
        }
        scanCooldownCounter = scanCooldownTicks();

        SoundTracker.SoundRecord newSound = findNearestSound();
        if (newSound == null) {
            return false;
        }
        this.targetSoundPos = newSound.pos;
        this.currentTargetWeight = newSound.weight;
        this.cachedSound = newSound;
        this.edgeMobState = EdgeMobState.GOING_TO_SOUND;
        this.raidScheduled = false;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] START GOING_TO_SOUND: mob={} leader={} edgeMob={}",
                    mob.getName().getString(),
                    (leader != null ? leader.getName().getString() : "null"),
                    MobGroupManager.isEdgeMob(mob));
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.edgeMobState == EdgeMobState.RETURNING_TO_LEADER) return true;
        if (!isMobEligible() || this.mob.isVehicle() || this.mob.isSleeping()) return false;
        if (this.targetSoundPos == null) return false;

        SoundTracker.SoundRecord bestSound = findNearestSound();
        if (bestSound == null) return false;
        if (bestSound.pos.equals(this.targetSoundPos)) {
            this.cachedSound = bestSound;
            this.currentTargetWeight = bestSound.weight;
            return true;
        }
        double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
        if (bestSound.weight > this.currentTargetWeight * switchRatio) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] canContinueToUse -> FALSE (switch to stronger sound {} > {}) for mob {}.",
                        String.format("%.2f", bestSound.weight), String.format("%.2f", this.currentTargetWeight), mob.getName().getString());
            }
            return false;
        }
        return true;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.edgeMobState = null;
        this.targetSoundPos = null;
        this.cachedSound = null;
        this.raidScheduled = false;
    }

    @Override
    public void tick() {
        if (scanCooldownCounter > 0) scanCooldownCounter--;

        Mob leader = MobGroupManager.getLeader(mob);
        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();

        // Stuck/Block breaking
        if (lastPos != null && mob.position().distanceToSqr(Vec3.atCenterOf(lastPos)) < 1.0) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            if (this.blockBreakerGoal != null) {
                BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                this.blockBreakerGoal = null;
            }
        }
        if (stuckTicks == 0) {
            lastPos = mob.blockPosition();
        }
        if (stuckTicks >= 40 && SoundAttractConfig.COMMON.enableBlockBreaking.get()) {
            BlockPos destination = this.edgeMobState == EdgeMobState.RETURNING_TO_LEADER && leader != null ? leader.blockPosition() : this.targetSoundPos;
            if (this.mob.getNavigation().isDone()) {
                if (destination != null && this.mob.blockPosition().distSqr(destination) > 4.0) {
                    stuckTicks++;
                    if (stuckTicks >= 20 && this.blockBreakerGoal == null) {
                        double timeMultiplier = SoundAttractConfig.COMMON.blockBreakTimeMultiplier.get();
                        boolean toolOnly = SoundAttractConfig.COMMON.blockBreakToolOnly.get();
                        boolean properOnly = SoundAttractConfig.COMMON.blockBreakProperToolOnly.get();
                        boolean properReq = SoundAttractConfig.COMMON.blockBreakProperToolRequired.get();
                        BlockBreakerPosGoal newGoal = new BlockBreakerPosGoal(this.mob, destination, timeMultiplier, toolOnly, properOnly, properReq);
                        BlockBreakerManager.scheduleAdd(this.mob, newGoal, 1);
                        this.blockBreakerGoal = newGoal;
                    }
                } else {
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0;
                if (this.blockBreakerGoal != null) {
                    BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                    this.blockBreakerGoal = null;
                }
            }
        }

        if (!smartEdge) {
            if (this.targetSoundPos != null) {
                this.mob.getNavigation().moveTo(targetSoundPos.getX(), targetSoundPos.getY(), targetSoundPos.getZ(), this.moveSpeed);
            }
            return;
        }

        if (edgeMobState == null) {
            edgeMobState = EdgeMobState.GOING_TO_SOUND;
        }

        if (edgeMobState == EdgeMobState.GOING_TO_SOUND) {
            // If stealth would suppress targeting, signal a RAID and return
            if (StealthDetectionEvents.shouldSuppressTargeting(this.mob)) {
                if (!raidScheduled && !MobGroupManager.isDeserter(this.mob)) {
                    Mob raidLeader = MobGroupManager.getLeader(this.mob);
                    if (raidLeader == this.mob) raidLeader = null;
                    if (raidLeader != null && !RaidManager.isRaidTicking(raidLeader) && !RaidManager.isRaidAdvancing(raidLeader)) {
                        RaidManager.scheduleRaid(raidLeader, this.targetSoundPos, this.mob.level().getGameTime());
                        raidScheduled = true;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} suppression -> schedule RAID for leader {} at {}.", mob.getName().getString(), raidLeader.getName().getString(), this.targetSoundPos);
                        }
                    }
                }
                edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                this.mob.getNavigation().stop();
            } else {
                this.mob.getNavigation().moveTo(targetSoundPos.getX(), targetSoundPos.getY(), targetSoundPos.getZ(), this.moveSpeed);
                if (mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos)) < getArrivalDistance() * getArrivalDistance()) {
                    if (!raidScheduled && !MobGroupManager.isDeserter(this.mob)) {
                        Mob raidLeader = MobGroupManager.getLeader(this.mob);
                        if (raidLeader == this.mob) raidLeader = null;
                        if (raidLeader != null && !RaidManager.isRaidTicking(raidLeader) && !RaidManager.isRaidAdvancing(raidLeader)) {
                            RaidManager.scheduleRaid(raidLeader, this.targetSoundPos, this.mob.level().getGameTime());
                            raidScheduled = true;
                            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} arrival -> schedule RAID for leader {} at {}.", mob.getName().getString(), raidLeader.getName().getString(), this.targetSoundPos);
                            }
                        }
                    }
                    edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                }
            }
        } else if (edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {
            if (MobGroupManager.isDeserter(this.mob)) {
                this.targetSoundPos = null;
                edgeMobState = null;
                return;
            }
            Mob returnLeader = leader != mob ? leader : null;
            if (returnLeader != null && !returnLeader.isRemoved() && !returnLeader.isDeadOrDying()) {
                double sprintMult = SoundAttractConfig.COMMON.groupSprintMultiplier.get();
                this.mob.getNavigation().moveTo(returnLeader.getX(), returnLeader.getY(), returnLeader.getZ(), this.moveSpeed * sprintMult);
                double arrive = SoundAttractConfig.COMMON.leaderReturnArrivalDistance.get();
                if (mob.distanceToSqr(returnLeader) < (arrive * arrive)) {
                    this.targetSoundPos = null;
                    this.edgeMobState = null;
                }
            } else {
                this.targetSoundPos = null;
                this.edgeMobState = null;
            }
        }
    }
}
