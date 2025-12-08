package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.StealthDetectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
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

    private long cacheTick = -1L;
    private SoundTracker.SoundRecord soundResultCache = null;

    private BlockPos lastIssuedNavTarget = null;
    private int repathCooldown = 0;
    private static final int REPATH_COOLDOWN_TICKS = 6;

    private BlockBreakerPosGoal blockBreakerGoal = null;

    private enum EdgeMobState { GOING_TO_SOUND, RETURNING_TO_LEADER }
    private EdgeMobState edgeMobState = null;
    private boolean raidScheduled = false;
    private boolean foundPlayerOrHit = false;
    private boolean relayedToLeader = false;
    private int edgeArrivalTicks = 0;
    private static final int EDGE_WAIT_TICKS = 15;
    private Mob cachedReturnLeader = null;
    private int returnLogCooldown = 0;

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

    private SoundTracker.SoundRecord getCachedNearestSound() {
        long currentTick = this.mob.level().getGameTime();
        if (this.cacheTick == currentTick) {
            return this.soundResultCache;
        }
        this.cacheTick = currentTick;
        this.soundResultCache = findNearestSound();
        return this.soundResultCache;
    }

    private SoundTracker.SoundRecord findNearestSound() {
        Level level = this.mob.level();
        if (level.isClientSide()) return null;
        return SoundTracker.findNearestSound(this.mob, level, this.mob.blockPosition(), this.mob.getEyePosition());
    }

    private BlockPos getNavigableTarget(BlockPos soundPos) {
        if (soundPos == null) return null;
        Level level = this.mob.level();
        int x = soundPos.getX();
        int z = soundPos.getZ();
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int soundY = soundPos.getY();
        int dy = Math.abs(groundY - soundY);
        if (dy <= 3 || level.isEmptyBlock(soundPos)) {
            return new BlockPos(x, groundY, z);
        }
        return soundPos;
    }

    private void moveToThrottled(BlockPos dest, double speed, boolean force) {
        if (dest == null) return;
        if (force) {
            this.mob.getNavigation().moveTo(dest.getX(), dest.getY(), dest.getZ(), speed);
            this.lastIssuedNavTarget = dest;
            this.repathCooldown = REPATH_COOLDOWN_TICKS;
            return;
        }
        boolean destChanged = (this.lastIssuedNavTarget == null) || !this.lastIssuedNavTarget.equals(dest);
        boolean navDoneOrStuck = this.mob.getNavigation().isDone() || this.mob.getNavigation().isStuck();
        if (this.repathCooldown > 0 && !destChanged && !navDoneOrStuck) {
            this.repathCooldown--;
            return;
        }

        double distSq = dest.distSqr(this.mob.blockPosition());
        double thresholdSq = 4.0;
        if (destChanged || navDoneOrStuck || distSq > thresholdSq) {
            this.mob.getNavigation().moveTo(dest.getX(), dest.getY(), dest.getZ(), speed);
            this.lastIssuedNavTarget = dest;
            this.repathCooldown = REPATH_COOLDOWN_TICKS;
        }
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

        SoundTracker.SoundRecord newSound = getCachedNearestSound();
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

        SoundTracker.SoundRecord bestSound = getCachedNearestSound();
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
        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        Mob leader = MobGroupManager.getLeader(mob);
        this.mob.getNavigation().stop();
        if (this.mob.isSprinting()) this.mob.setSprinting(false);
        if (leader != mob && smartEdge && edgeMobState == EdgeMobState.GOING_TO_SOUND) {
            edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
            edgeArrivalTicks = 0;
        } else {
            edgeMobState = null;
        }
        foundPlayerOrHit = false;
        relayedToLeader = false;
        edgeArrivalTicks = 0;
        cachedReturnLeader = null;
        returnLogCooldown = 0;
        raidScheduled = false;
        this.lastIssuedNavTarget = null;
        this.repathCooldown = 0;
    }

    @Override
    public void tick() {
        if (scanCooldownCounter > 0) scanCooldownCounter--;

        Mob leader = MobGroupManager.getLeader(mob);
        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        BlockPos navTarget = this.targetSoundPos != null ? getNavigableTarget(this.targetSoundPos) : null;

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
                moveToThrottled(navTarget != null ? navTarget : this.targetSoundPos, this.moveSpeed, false);
            }
            return;
        }

        if (edgeMobState == null) {
            edgeMobState = EdgeMobState.GOING_TO_SOUND;
        }

        if (edgeMobState == EdgeMobState.GOING_TO_SOUND) {
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
                moveToThrottled(navTarget != null ? navTarget : this.targetSoundPos, this.moveSpeed, false);
                BlockPos arrivalCheck = navTarget != null ? navTarget : this.targetSoundPos;
                if (arrivalCheck != null && mob.position().distanceToSqr(Vec3.atCenterOf(arrivalCheck)) < getArrivalDistance() * getArrivalDistance()) {
                    edgeArrivalTicks++;
                    if (edgeArrivalTicks >= EDGE_WAIT_TICKS || foundPlayerOrHit) {
                        if (!MobGroupManager.isDeserter(this.mob)) {
                            Mob raidLeader = MobGroupManager.getLeader(this.mob);
                            if (raidLeader != null && !raidScheduled && !RaidManager.isRaidTicking(raidLeader) && !RaidManager.isRaidAdvancing(raidLeader)) {
                                RaidManager.scheduleRaid(raidLeader, this.targetSoundPos, this.mob.level().getGameTime());
                                raidScheduled = true;
                                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                    SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} arrival -> schedule RAID for leader {} at {}.", mob.getName().getString(), raidLeader.getName().getString(), this.targetSoundPos);
                                }
                            }
                        }
                        if (MobGroupManager.isDeserter(this.mob)) {
                            this.targetSoundPos = null;
                            this.edgeMobState = null;
                            this.cachedReturnLeader = null;
                            return;
                        }
                        this.mob.getNavigation().stop();
                        this.cachedReturnLeader = leader != mob ? leader : null;
                        this.edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                        this.lastIssuedNavTarget = null;
                        edgeArrivalTicks = 0;
                        returnLogCooldown = 0;
                    }
                }
            }
        } else if (edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {
            if (MobGroupManager.isDeserter(this.mob)) {
                this.targetSoundPos = null;
                edgeMobState = null;
                return;
            }
            Mob returnLeader = this.cachedReturnLeader != null ? this.cachedReturnLeader : (leader != mob ? leader : null);
            if (returnLeader != null && !returnLeader.isRemoved() && !returnLeader.isDeadOrDying()) {
                if (!this.mob.isSprinting()) this.mob.setSprinting(true);
                double sprintMult = SoundAttractConfig.COMMON.groupSprintMultiplier.get();
                moveToThrottled(returnLeader.blockPosition(), this.moveSpeed * sprintMult, false);
                double arrive = SoundAttractConfig.COMMON.leaderReturnArrivalDistance.get();
                if (mob.distanceToSqr(returnLeader) < (arrive * arrive)) {
                    if (this.mob.isSprinting()) this.mob.setSprinting(false);
                    this.targetSoundPos = null;
                    this.edgeMobState = null;
                    this.cachedReturnLeader = null;
                    this.lastIssuedNavTarget = null;
                    returnLogCooldown = 0;
                    return;
                }
            } else {
                if (this.mob.isSprinting()) this.mob.setSprinting(false);
                this.targetSoundPos = null;
                this.edgeMobState = null;
                this.cachedReturnLeader = null;
                this.lastIssuedNavTarget = null;
            }
        }

        if (this.edgeMobState != EdgeMobState.RETURNING_TO_LEADER && this.mob.isSprinting()) {
            this.mob.setSprinting(false);
        }
    }
}
