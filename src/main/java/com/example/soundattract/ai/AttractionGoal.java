package com.example.soundattract.ai;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.FovEvents;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class AttractionGoal extends Goal {
    private final MobEntity mob;
    private final double moveSpeed;

    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;
    private SoundTracker.SoundRecord cachedSound = null;

    private int scanTickCounter = 0;

    private boolean isPursuingSound = false;
    private int pursuingSoundTicksRemaining = 0;
    private BlockPos lastNavigationTarget;
    private int edgeTickCounter = 0;
    private int deserterTickCounter = 0;
    private Vec3d chosenDest = null;
    private boolean hasPicked = false;
    private int stuckTicks = 0;
    private Vec3d lastPosVec = null;
    private BlockBreakerPosGoal blockBreakerGoal = null;
    private enum EdgeMobState { GOING_TO_SOUND, RETURNING_TO_LEADER }
    private EdgeMobState edgeMobState = null;
    private boolean foundPlayerOrHit = false;
    private boolean relayedToLeader = false;
    private int edgeArrivalTicks = 0;
    private SoundTracker.SoundRecord cachedBestSound;
    private int bestSoundCacheTicks;
    private static final Map<MobEntity, DelayedRelay> pendingDelayedRelays = new HashMap<>();

    private static class DelayedRelay {
        public final MobEntity leader;
        public final BlockPos soundPos;
        public final long triggerTime;
        public boolean cancelled = false;
        public DelayedRelay(MobEntity leader, BlockPos soundPos, long triggerTime) {
            this.leader = leader;
            this.soundPos = soundPos;
            this.triggerTime = triggerTime;
        }
    }

    public AttractionGoal(MobEntity mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setControls(EnumSet.of(Control.MOVE));
        this.scanTickCounter = 0;
    }

    private double getArrivalDistance() {
        return SoundAttractMod.CONFIG.arrivalDistance;
    }

    private int getWaitTicks() {
        return SoundAttractMod.CONFIG.scanCooldownTicks;
    }

    public BlockPos getTargetSoundPos() {
        return this.targetSoundPos;
    }

    @Override
    public void start() {
        if (MobGroupManager.getLeader(this.mob) == this.mob) {
            List<MobGroupManager.SoundRelay> consumed = MobGroupManager.consumeRelayedSounds(this.mob);
            if (SoundAttractMod.CONFIG.debugLogging && !consumed.isEmpty()) {
                SoundAttractMod.LOGGER.info("[AttractionGoal] Leader {} has consumed {} relayed sounds upon starting goal.", 
                    this.mob.getName().getString(), consumed.size());
            }
        }
    }

    @Override
    public boolean canStart() {
        if (this.isPursuingSound && this.targetSoundPos != null) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info(
                "[AttractionGoal] canStart() is TRUE for {} because it was given an external target: {}",
                    this.mob.getName().getString(),
                    this.targetSoundPos
                );
            }
            return true;
        }

        if (this.mob.hasVehicle() || this.mob.isSleeping() || this.mob.getTarget() != null) {
            return false;
        }

        if (this.scanTickCounter > 0) {
            this.scanTickCounter--;
            return false;
        }

        SoundTracker.SoundRecord newSound = findInterestingSoundRecord();
        if (newSound == null) {
            this.scanTickCounter = SoundAttractMod.CONFIG.scanCooldownTicks;
            return false;
        }

        this.scanTickCounter = SoundAttractMod.CONFIG.scanCooldownTicks;

        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[AttractionGoal] canStart() is TRUE for {} because it found a sound on its own: pos={}, range={}, weight={}",
                mob.getName().getString(), newSound.pos,
                String.format("%.2f", newSound.range), String.format("%.2f", newSound.weight)
            );
        }

        this.cachedSound = newSound;
        this.targetSoundPos = newSound.pos;
        this.currentTargetWeight = newSound.weight;
        this.isPursuingSound = true;
        this.pursuingSoundTicksRemaining = newSound.ticksRemaining;
        return true;
    }

    @Override
    public boolean shouldContinue() {

        if (this.mob.hasVehicle() || this.mob.isSleeping() || this.mob.getTarget() != null) return false;
        boolean pursuing = this.isPursuingSound && this.targetSoundPos != null;
        return pursuing || this.blockBreakerGoal != null;
    }

    @Override
    public void stop() {
        boolean navIdle = this.mob.getNavigation().isIdle();
        if (this.blockBreakerGoal != null) {
            BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
            this.blockBreakerGoal = null;
        }
        this.mob.getNavigation().stop();
        this.lastNavigationTarget = null;

        this.stuckTicks = 0;
        this.lastPosVec = null;
        this.chosenDest = null;
        this.hasPicked = false;
        this.edgeMobState = null;
        this.foundPlayerOrHit = false;
        this.relayedToLeader = false;
        this.edgeArrivalTicks = 0;
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info(
                "[AttractionGoal] Goal stopped for {}. pursuingSound={}, target={}, breakerActive(beforeStop)=false, navIdle={}",
                mob.getName().getString(), this.isPursuingSound, this.targetSoundPos, navIdle
            );
        }
    }

    @Override
    public void tick() {
        MobEntity leader = MobGroupManager.getLeader(mob);
        boolean isLeader = (leader == this.mob);
        if (isLeader && SoundAttractMod.CONFIG.debugLogging && this.isPursuingSound) {
            SoundAttractMod.LOGGER.info(
                "[AttractionGoal Tick] Leader {} is ticking. Target: {}. Is navigation idle? {}",
                this.mob.getName().getString(),
                this.targetSoundPos,
                this.mob.getNavigation().isIdle()
            );
        }
        boolean isFollower = !isLeader;
        boolean smartEdgeEnabled = SoundAttractMod.CONFIG.edgeMobSmartBehavior;

        if (this.bestSoundCacheTicks >0) {
            this.bestSoundCacheTicks--;
        }
        if (!this.isPursuingSound || this.targetSoundPos == null) {
            return; 
        }


        if (this.blockBreakerGoal != null) {
            boolean running = false;
            try {
                running = ((com.example.soundattract.mixin.MobEntityAccessor) this.mob)
                        .getGoalSelector()
                        .getGoals()
                        .stream()
                        .anyMatch(w -> w.getGoal() == this.blockBreakerGoal && w.isRunning());
            } catch (ClassCastException e) {
                SoundAttractMod.LOGGER.error("[AttractionGoal] Failed to access goal selector for running goals.", e);
            }

            if (running) {

                SoundTracker.SoundRecord bestPossible = SoundTracker.findNearestSound(mob.getWorld(), mob, mob.getBlockPos(), mob.getEyePos());
                if (bestPossible != null && this.cachedSound != null) {
                    boolean sameId = java.util.Objects.equals(bestPossible.soundId, this.cachedSound.soundId);
                    boolean samePos = bestPossible.pos.equals(this.cachedSound.pos);
                    double switchRatio = SoundAttractMod.CONFIG.soundSwitchRatio;
                    if (!(sameId && samePos) && bestPossible.weight > (this.cachedSound.weight * switchRatio)) {
                        if (SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.info("[AttractionGoal] Mining mob {} found a better sound ({} > {}). Stopping block breaking.", this.mob.getName().getString(), bestPossible.weight, this.cachedSound.weight);
                        }
                        BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                        this.blockBreakerGoal = null;
                        this.stop();
                        return;
                    }
                }


                Vec3d cp = mob.getPos();
                if (lastPosVec != null && cp.squaredDistanceTo(lastPosVec) >= 1.0) {
                    BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                    this.blockBreakerGoal = null;
                    this.stuckTicks = 0;
                    this.lastPosVec = cp;
                }
                return;
            } else {

                this.blockBreakerGoal = null;
            }
        }

        if (this.pursuingSoundTicksRemaining > 0) {
            this.pursuingSoundTicksRemaining--;
        } else {
            this.isPursuingSound = false; 
            return;
        }
        SoundTracker.SoundRecord freshSound = findInterestingSoundRecord();
        if (freshSound != null && !freshSound.pos.equals(this.targetSoundPos)) {
            double switchRatio = SoundAttractMod.CONFIG.soundSwitchRatio;
            boolean isBetterByWeight = freshSound.weight > this.currentTargetWeight * switchRatio;
            boolean isTieButCloser = false;
            if (!isBetterByWeight && Math.abs(freshSound.weight - this.currentTargetWeight) < 0.001) {
                double freshDistSq = freshSound.pos.getSquaredDistance(this.mob.getBlockPos());
                double currentDistSq = this.targetSoundPos.getSquaredDistance(this.mob.getBlockPos());
                isTieButCloser = freshDistSq < currentDistSq;
            }

            if (isBetterByWeight || isTieButCloser) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[AttractionGoal] {} stopping pursuit: better or closer sound appeared (betterByWeight={}, tieButCloser={}).", mob.getName().getString(), isBetterByWeight, isTieButCloser);
                }
                this.isPursuingSound = false;
                return;
            }
        }

        Vec3d currentPos = mob.getPos();
        if (lastPosVec != null && currentPos.squaredDistanceTo(lastPosVec) < 0.01) {
            stuckTicks++;
            if (SoundAttractMod.CONFIG.debugLogging && (stuckTicks % 10 == 0)) {
                SoundAttractMod.LOGGER.info("[AttractionGoal] {} appears stuck for {} ticks at {}", mob.getName().getString(), stuckTicks, mob.getBlockPos());
            }
            if (stuckTicks >= 10) { 
                this.mob.getNavigation().stop();

                if (SoundAttractMod.CONFIG.enableBlockBreaking && this.targetSoundPos != null && this.blockBreakerGoal == null) {
                    BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                            this.mob,
                            this.targetSoundPos,
                            SoundAttractMod.CONFIG.blockBreakTimeMultiplier,
                            SoundAttractMod.CONFIG.blockBreakToolOnly,
                            SoundAttractMod.CONFIG.blockBreakProperToolOnly,
                            SoundAttractMod.CONFIG.blockBreakProperToolRequired
                    );

                    BlockBreakerManager.scheduleAdd(this.mob, breaker, 0);
                    this.blockBreakerGoal = breaker;
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[AttractionGoal] Scheduling BlockBreakerPosGoal for {} toward {}", this.mob.getName().getString(), this.targetSoundPos);
                    }
                }
                else if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[AttractionGoal] Stuck detected but not scheduling breaker. enableBlockBreaking={} targetSoundPos={}", SoundAttractMod.CONFIG.enableBlockBreaking, this.targetSoundPos);
                }
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
            this.lastPosVec = currentPos;
        }

        double arrivalDistSq = getArrivalDistance() * getArrivalDistance();
        double distSqToTarget = mob.getPos().squaredDistanceTo(Vec3d.ofCenter(this.targetSoundPos));
        boolean hasArrived = distSqToTarget < arrivalDistSq;

        if (isLeader) {
            List<MobGroupManager.SoundRelay> relays = MobGroupManager.peekRelayedSounds(mob);
            if (relays != null && !relays.isEmpty()) {

            if (SoundAttractMod.CONFIG.debugLogging) {
                 SoundAttractMod.LOGGER.info("Leader {} received {} sound relays.", mob.getName().getString(), relays.size());
            }
        }


        if (this.blockBreakerGoal == null && SoundAttractMod.CONFIG.enableBlockBreaking && this.targetSoundPos != null) {
            if (this.mob.getNavigation().isIdle() && distSqToTarget > 4.0) {
                BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                        this.mob,
                        this.targetSoundPos,
                        SoundAttractMod.CONFIG.blockBreakTimeMultiplier,
                        SoundAttractMod.CONFIG.blockBreakToolOnly,
                        SoundAttractMod.CONFIG.blockBreakProperToolOnly,
                        SoundAttractMod.CONFIG.blockBreakProperToolRequired
                );
                BlockBreakerManager.scheduleAdd(this.mob, breaker, 0);
                this.blockBreakerGoal = breaker;
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[AttractionGoal] Nav idle fallback: scheduling BlockBreakerPosGoal for {} toward {}", this.mob.getName().getString(), this.targetSoundPos);
                }
            }
        }

        if (hasArrived) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[AttractionGoal] {} arrived at target {}, stopping pursuit.", mob.getName().getString(), this.targetSoundPos);
            }
            this.isPursuingSound = false;
            return;
        }


        if (this.chosenDest != null) {
            navigateTo(BlockPos.ofFloored(this.chosenDest), this.moveSpeed);
        }        
    }

    else if (isFollower && smartEdgeEnabled) {
        if (edgeMobState == null) {
            edgeMobState = EdgeMobState.GOING_TO_SOUND;
        }

        if (edgeMobState == EdgeMobState.GOING_TO_SOUND) {
            navigateTo(this.targetSoundPos, this.moveSpeed);

            if (hasArrived) {

                this.edgeArrivalTicks++;
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("Follower {} arrived at sound, waiting... (ticks={})", mob.getName().getString(), edgeArrivalTicks);
                }


                if (edgeArrivalTicks >= 40) {
                    edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                }
            } else {

                if (SoundAttractMod.CONFIG.enableBlockBreaking && (mob.getWorld().getTime() % 20L == 0L)) {
                    double maxRange = SoundAttractMod.CONFIG.standingDetectionRange * 2.0;
                    net.minecraft.util.math.Box searchBox = new net.minecraft.util.math.Box(mob.getBlockPos()).expand(maxRange);
                    List<PlayerEntity> playersNearby = mob.getWorld().getEntitiesByClass(PlayerEntity.class, searchBox, PlayerEntity::isAlive);
                    for (PlayerEntity p : playersNearby) {
                        if (!FovEvents.hasSmartLineOfSight(mob, p)) continue;
                        double detectRange = StealthDetectionEvents.computeFullDetectionRange(mob, p, mob.getWorld());
                        if (p.distanceTo(mob) > detectRange) continue;


                        BlockPos playerPos = p.getBlockPos();
                        boolean pathStarted = this.mob.getNavigation().startMovingTo(p, this.moveSpeed);
                        boolean navIdle = this.mob.getNavigation().isIdle();
                        boolean trulyStuck = this.stuckTicks >= 10;
                        if (!pathStarted || navIdle || trulyStuck) {
                            if (!this.mob.getNavigation().isIdle()) this.mob.getNavigation().stop();
                            if (this.blockBreakerGoal != null) {
                                BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                                this.blockBreakerGoal = null;
                            }
                            BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                                    this.mob,
                                    playerPos,
                                    SoundAttractMod.CONFIG.blockBreakTimeMultiplier,
                                    false,
                                    false,
                                    false
                            );
                            BlockBreakerManager.scheduleAdd(this.mob, breaker, 0);
                            this.blockBreakerGoal = breaker;
                            if (SoundAttractMod.CONFIG.debugLogging) {
                                SoundAttractMod.LOGGER.info("[AttractionGoal] Pre-arrival FORCE Scheduling BlockBreakerPosGoal for {} toward detected player {} at {} (pathStarted={}, navIdle={}, stuckTicks={})",
                                        this.mob.getName().getString(), p.getName().getString(), playerPos, pathStarted, navIdle, this.stuckTicks);
                            }
                            this.stuckTicks = 0;
                            break;
                        }
                    }
                }
            }
        } else if (edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {
            if (leader != null && !leader.isRemoved()) {
                navigateTo(leader.getBlockPos(), this.moveSpeed * 0.8);
                

                if (mob.getPos().squaredDistanceTo(leader.getPos()) < arrivalDistSq) {
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("Follower {} has returned to leader {}.", mob.getName().getString(), leader.getName().getString());
                    }
                    this.isPursuingSound = false;
                }
            } else {

                this.isPursuingSound = false;
            }
        }
    }

    else {
        if (hasArrived) {
            this.isPursuingSound = false;
            return;
        }
        navigateTo(this.targetSoundPos, this.moveSpeed);
    }
}

    private void handleSmartEdgeDeserterLogic(boolean isEdge, boolean isDeserter) {
        MobEntity leader = MobGroupManager.getLeader(mob);

        if (edgeMobState == null) {
            SoundTracker.SoundRecord soundToInvestigate = findInterestingSoundRecord();
            if (soundToInvestigate != null) {
                this.cachedSound = soundToInvestigate; 
                this.targetSoundPos = this.cachedSound.pos;
                this.currentTargetWeight = this.cachedSound.weight;
                edgeMobState = EdgeMobState.GOING_TO_SOUND;
                foundPlayerOrHit = false;
                relayedToLeader = false;
                edgeArrivalTicks = 0;
                mob.getNavigation().stop();

                if (leader != null && leader != mob && this.targetSoundPos != null) {
                    long triggerTime = mob.getWorld().getTime() + SoundAttractMod.CONFIG.delayedRelayTicks;
                    DelayedRelay relay = new DelayedRelay(leader, this.targetSoundPos, triggerTime);
                    pendingDelayedRelays.put(mob, relay);
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                            "[AttractionGoal] Smart {} {} scheduled delayed relay to {} for sound at {} (trigger @ {})",
                            isEdge ? "Edge" : "Deserter",
                            mob.getName().getString(),
                            leader.getName().getString(),
                            this.targetSoundPos,
                            triggerTime
                        );
                    }
                }
            } else { 
                this.cachedSound = null;
                this.targetSoundPos = null;
                this.currentTargetWeight = -1.0;
                if (leader != null && leader != mob && mob.distanceTo(leader) > 16.0) {
                    mob.getNavigation().startMovingTo(leader, this.moveSpeed * 0.8);
                }
                return;
            }
        }

        if (edgeMobState == EdgeMobState.GOING_TO_SOUND) {
            if (this.targetSoundPos == null) { 
                if (SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.warn("[AttractionGoal] Smart {} {} lost targetSoundPos in GOING_TO_SOUND", isEdge ? "Edge" : "Deserter", mob.getName().getString());
                edgeMobState = EdgeMobState.RETURNING_TO_LEADER; 
                mob.getNavigation().stop();
                return;
            }

            mob.getNavigation().startMovingTo(
                targetSoundPos.getX() + 0.5, targetSoundPos.getY() + 0.5, targetSoundPos.getZ() + 0.5,
                moveSpeed
            );

            if (mob.getPos().distanceTo(Vec3d.ofCenter(targetSoundPos)) < getArrivalDistance()) {
                edgeArrivalTicks++;
                mob.getNavigation().stop();

                double maxRange = SoundAttractMod.CONFIG.standingDetectionRange * 2.0; 
                net.minecraft.util.math.Box searchBox = new net.minecraft.util.math.Box(targetSoundPos).expand(maxRange);
                List<PlayerEntity> playersAtSound = mob.getWorld().getEntitiesByClass(PlayerEntity.class, searchBox, PlayerEntity::isAlive);
                PlayerEntity detectedPlayer = null;

                for (PlayerEntity player : playersAtSound) {
                     double actualDetectionRange = StealthDetectionEvents.computeFullDetectionRange(mob, player, mob.getWorld());
                     if (player.distanceTo(mob) <= actualDetectionRange) {
                        detectedPlayer = player;
                        break;
                     }
                }

                if (detectedPlayer != null) {
                    foundPlayerOrHit = true;
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[AttractionGoal] Smart {} {} detected player {} near sound at {} (ticks at spot={})",
                            isEdge ? "Edge" : "Deserter", mob.getName().getString(), detectedPlayer.getName().getString(), targetSoundPos, edgeArrivalTicks);
                    }

                    if (SoundAttractMod.CONFIG.enableBlockBreaking) {
                        BlockPos playerPos = detectedPlayer.getBlockPos();


                        if (!this.mob.getNavigation().isIdle()) this.mob.getNavigation().stop();


                        if (this.blockBreakerGoal != null) {
                            BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                            this.blockBreakerGoal = null;
                        }

                        BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                                this.mob,
                                playerPos,
                                SoundAttractMod.CONFIG.blockBreakTimeMultiplier,
                                false,
                                false,
                                false
                        );
                        BlockBreakerManager.scheduleAdd(this.mob, breaker, 0);
                        this.blockBreakerGoal = breaker;
                        if (SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.info("[AttractionGoal] FORCE Scheduling BlockBreakerPosGoal for {} toward detected player {} at {}",
                                    this.mob.getName().getString(), detectedPlayer.getName().getString(), playerPos);
                        }
                        this.stuckTicks = 0;
                    }
                }
            }
        } else if (edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {
            if (leader == null || leader == mob || !leader.isAlive()) { 
                edgeMobState = null;
                mob.getNavigation().stop();
                if (pendingDelayedRelays.containsKey(mob)) {
                    pendingDelayedRelays.get(mob).cancelled = true;
                }
                return;
            }

            mob.getNavigation().startMovingTo(leader, moveSpeed);
            if (mob.distanceTo(leader) < getArrivalDistance() + 2.0) { 
                if (foundPlayerOrHit && !relayedToLeader && this.targetSoundPos != null && this.cachedSound != null) {
                    MobGroupManager.relaySoundToLeader(
                        mob, targetSoundPos.getX(), targetSoundPos.getY(), targetSoundPos.getZ(),
                        this.cachedSound.range,
                        this.cachedSound.weight, 
                        mob.getWorld().getTime()
                    );
                    relayedToLeader = true;
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                            "[AttractionGoal] Smart {} {} relayed sound (player found) at {} to leader {}.",
                            isEdge ? "Edge" : "Deserter", mob.getName().getString(), targetSoundPos, leader.getName().getString()
                        );
                    }
                } else if (!foundPlayerOrHit && SoundAttractMod.CONFIG.debugLogging) {
                     SoundAttractMod.LOGGER.info(
                        "[AttractionGoal] Smart {} {} returned to leader {}, found nothing at sound.",
                        isEdge ? "Edge" : "Deserter", mob.getName().getString(), leader.getName().getString()
                    );
                }

                if (pendingDelayedRelays.containsKey(mob)) { 
                    pendingDelayedRelays.get(mob).cancelled = true;
                    if (SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Delayed relay for {} cancelled (returned to leader).", mob.getName().getString());
                }
                
                edgeMobState = null; 
                foundPlayerOrHit = false;
                relayedToLeader = false;
                edgeArrivalTicks = 0;
                this.cachedSound = null; 
                this.targetSoundPos = null;
                this.currentTargetWeight = -1.0;
                mob.getNavigation().stop();
            }
        }
    }

    private void navigateTo(BlockPos targetPos, double speed) {
        if (targetPos == null) {

            if (!this.mob.getNavigation().isIdle()) {
                this.mob.getNavigation().stop();
            }
            return;
        }


        if (targetPos.equals(this.lastNavigationTarget) && !this.mob.getNavigation().isIdle()) {

            return;
        }


        this.mob.getNavigation().startMovingTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, speed);

        this.lastNavigationTarget = targetPos;
    }
    private SoundTracker.SoundRecord findInterestingSoundRecord() {
        if (this.bestSoundCacheTicks > 0) {
            if (this.cachedBestSound != null && SoundTracker.getRecentSounds(this.mob.getWorld()).contains(this.cachedBestSound)) {
                this.cachedBestSound = null;
            }
            return this.cachedBestSound;
        }
        this.bestSoundCacheTicks = 10;

        World world = mob.getWorld();
        if (world.isClient()) {
            this.cachedBestSound = null;
            return null;
        }

        BlockPos mobPos = mob.getBlockPos();
        MobEntity leader = MobGroupManager.getLeader(mob);

        SoundTracker.SoundRecord bestNewSoundEvent;

        if (leader == mob) {
            SoundTracker.SoundRecord directSound = SoundTracker.findNearestSound(world, mob, mobPos, mob.getEyePos());
            List<MobGroupManager.SoundRelay> relays = MobGroupManager.consumeRelayedSounds(mob);

            bestNewSoundEvent = directSound;
            if (relays != null) {
                for (MobGroupManager.SoundRelay relay : relays) {
                    SoundTracker.SoundRecord relayedSoundAsRecord = new SoundTracker.SoundRecord(
                        null, 
                        "relayed_sound_" + relay.hashCode(), 
                        new BlockPos((int)Math.round(relay.x), (int)Math.round(relay.y), (int)Math.round(relay.z)),
                        SoundTracker.SoundRecord.DEFAULT_TICKS_REMAINING,
                        world.getRegistryKey().getValue().toString(),
                        relay.range,
                        relay.weight
                    );

                    if (bestNewSoundEvent == null ||
                        relayedSoundAsRecord.weight > bestNewSoundEvent.weight ||
                        (Math.abs(relayedSoundAsRecord.weight - bestNewSoundEvent.weight) < 0.001 && relayedSoundAsRecord.range > bestNewSoundEvent.range)) {
                        bestNewSoundEvent = relayedSoundAsRecord;
                    }
                }
            }
        } else {
            bestNewSoundEvent = SoundTracker.findNearestSound(world, mob, mobPos, mob.getEyePos());
        }

        SoundTracker.SoundRecord finalDecisionSound;

        if (this.cachedSound == null) {
            finalDecisionSound = bestNewSoundEvent;
        } else { 
            if (bestNewSoundEvent == null) {
                finalDecisionSound = null; 
            } else {
                boolean sameSoundId = Objects.equals(this.cachedSound.soundId, bestNewSoundEvent.soundId);
                boolean samePosition = this.cachedSound.pos.equals(bestNewSoundEvent.pos);
                
                double effectiveValueToleranceRange = Math.max(1.0, this.cachedSound.range * 0.1); 
                double effectiveValueToleranceWeight = Math.max(0.1, this.cachedSound.weight * 0.1); 
                
                boolean similarRange = Math.abs(this.cachedSound.range - bestNewSoundEvent.range) < effectiveValueToleranceRange;
                boolean similarWeight = Math.abs(this.cachedSound.weight - bestNewSoundEvent.weight) < effectiveValueToleranceWeight;

                if (sameSoundId && samePosition && similarRange && similarWeight) {
                    finalDecisionSound = bestNewSoundEvent; 
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                            "[AttractionGoal] {} REFRESHED target: soundId={}, pos={}, newWeight={}, newRange={}, newTicks={}",
                            mob.getName().getString(), bestNewSoundEvent.soundId, bestNewSoundEvent.pos,
                            String.format("%.2f", bestNewSoundEvent.weight), String.format("%.2f", bestNewSoundEvent.range), bestNewSoundEvent.ticksRemaining
                        );
                    }
                } else {
                    double switchRatio = SoundAttractMod.CONFIG.soundSwitchRatio;
                    boolean shouldSwitch = bestNewSoundEvent.weight > this.cachedSound.weight * switchRatio;
                    
                    if (SoundAttractMod.CONFIG.useRangeInSoundSwitch && !shouldSwitch) {
                         shouldSwitch = (Math.abs(bestNewSoundEvent.weight - this.cachedSound.weight * switchRatio) < 0.001 &&
                                         bestNewSoundEvent.range > this.cachedSound.range * switchRatio);
                    }

                    if (shouldSwitch) {
                        finalDecisionSound = bestNewSoundEvent;
                        if (SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.info(
                                "[AttractionGoal] {} SWITCHED target from {} (w:{}, r:{}) to {} (w:{}, r:{})",
                                mob.getName().getString(), 
                                this.cachedSound.soundId, String.format("%.2f", this.cachedSound.weight), String.format("%.2f", this.cachedSound.range),
                                bestNewSoundEvent.soundId, String.format("%.2f", bestNewSoundEvent.weight), String.format("%.2f", bestNewSoundEvent.range)
                            );
                        }
                    } else {
                        finalDecisionSound = this.cachedSound; 
                    }
                }
            }
        }
        this.cachedBestSound = finalDecisionSound;
        return this.cachedBestSound;
    }

    public boolean isPursuingSound() {
        return isPursuingSound;
    }

    public static void handleSoundAttraction(MobEntity mob, SoundTracker.SoundRecord sound) {
        AttractionGoal goal = getAttractionGoal(mob);
        if (goal != null && sound != null) {
            goal.cachedSound = sound;
            goal.targetSoundPos = sound.pos;
            goal.currentTargetWeight = sound.weight;
            goal.isPursuingSound = true;
            goal.pursuingSoundTicksRemaining = SoundAttractMod.CONFIG.soundLifetimeTicks; 
            goal.edgeMobState = null; 
            goal.foundPlayerOrHit = false;
            goal.relayedToLeader = false;
            if (goal.mob.getNavigation().isIdle() || 
                (goal.mob.getNavigation().getTargetPos() != null && !goal.mob.getNavigation().getTargetPos().equals(sound.pos))) {
                goal.mob.getNavigation().startMovingTo(sound.pos.getX() + 0.5, sound.pos.getY() + 0.5, sound.pos.getZ() + 0.5, goal.moveSpeed);
            }
             if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[AttractionGoal] {} commanded to attract to sound: {}", mob.getName().getString(), sound.soundId);
            }
        }
    }

    public static void handleRelayToLeader(MobEntity leader, SoundTracker.SoundRecord soundFromRelay, MobEntity edgeMob) {
        AttractionGoal goal = getAttractionGoal(leader);
        if (goal != null && soundFromRelay != null) {
            SoundTracker.SoundRecord leaderEvaluatedRelay = soundFromRelay; 

            boolean takeNewSound = goal.cachedSound == null ||
                                   leaderEvaluatedRelay.weight > goal.cachedSound.weight * SoundAttractMod.CONFIG.soundSwitchRatio ||
                                   (edgeMob != null && MobGroupManager.isEdgeMobEntity(edgeMob));

            if (takeNewSound) {
                goal.cachedSound = leaderEvaluatedRelay;
                goal.targetSoundPos = leaderEvaluatedRelay.pos;
                goal.currentTargetWeight = leaderEvaluatedRelay.weight;
                goal.isPursuingSound = true;
                goal.pursuingSoundTicksRemaining = SoundAttractMod.CONFIG.scanCooldownTicks * 2;
                goal.edgeMobState = null;
                goal.relayedToLeader = true;

                if (SoundAttractMod.CONFIG.debugLogging) {
                     SoundAttractMod.LOGGER.info("[AttractionGoal] Leader {} taking relayed sound {} from {}", leader.getName().getString(), leaderEvaluatedRelay.pos, edgeMob != null ? edgeMob.getName().getString() : "unknown source");
                }
                if (goal.mob.getNavigation().isIdle() || 
                    (goal.mob.getNavigation().getTargetPos() != null && !goal.mob.getNavigation().getTargetPos().equals(leaderEvaluatedRelay.pos))) {
                    goal.mob.getNavigation().startMovingTo(leaderEvaluatedRelay.pos.getX() + 0.5, leaderEvaluatedRelay.pos.getY() + 0.5, leaderEvaluatedRelay.pos.getZ() + 0.5, goal.moveSpeed);
                }
            }
        }
    }

    public static void handleLeaderObjective(MobEntity leader, SoundTracker.SoundRecord sound) {
        AttractionGoal goal = getAttractionGoal(leader);
        if (goal != null && sound != null) {
            goal.cachedSound = sound;
            goal.targetSoundPos = sound.pos;
            goal.currentTargetWeight = sound.weight;
            goal.isPursuingSound = true;
            goal.pursuingSoundTicksRemaining = SoundAttractMod.CONFIG.scanCooldownTicks * 2;
            goal.edgeMobState = null;
            goal.relayedToLeader = false; 
            if (SoundAttractMod.CONFIG.debugLogging) {
                 SoundAttractMod.LOGGER.info("[AttractionGoal] Leader {} received direct objective sound {}", leader.getName().getString(), sound.soundId);
            }
            if (goal.mob.getNavigation().isIdle() || 
                (goal.mob.getNavigation().getTargetPos() != null && !goal.mob.getNavigation().getTargetPos().equals(sound.pos))) {
                goal.mob.getNavigation().startMovingTo(sound.pos.getX() + 0.5, sound.pos.getY() + 0.5, sound.pos.getZ() + 0.5, goal.moveSpeed);
            }
        }
    }


    public static void handleEdgeInvestigate(MobEntity edge, SoundTracker.SoundRecord sound) {
        AttractionGoal goal = getAttractionGoal(edge);
        if (goal != null && sound != null && SoundAttractMod.CONFIG.edgeMobSmartBehavior) {
            goal.cachedSound = sound;
            goal.targetSoundPos = sound.pos;
            goal.currentTargetWeight = sound.weight;
            goal.isPursuingSound = true; 
            goal.pursuingSoundTicksRemaining = SoundAttractMod.CONFIG.scanCooldownTicks * 2;
            
            goal.edgeMobState = EdgeMobState.GOING_TO_SOUND;
            goal.foundPlayerOrHit = false;
            goal.relayedToLeader = false;
            goal.edgeArrivalTicks = 0;
            goal.mob.getNavigation().stop(); 

            MobEntity leader = MobGroupManager.getLeader(edge);
            if (leader != null && leader != edge && goal.targetSoundPos != null) {
                if (pendingDelayedRelays.containsKey(edge)) {
                    pendingDelayedRelays.get(edge).cancelled = true;
                }
                long triggerTime = edge.getWorld().getTime() + SoundAttractMod.CONFIG.delayedRelayTicks;
                DelayedRelay relay = new DelayedRelay(leader, goal.targetSoundPos, triggerTime);
                pendingDelayedRelays.put(edge, relay);
                 if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[AttractionGoal] Smart Edge {} commanded to investigate {}, scheduling delayed relay.", edge.getName().getString(), sound.pos);
                }
            }
             if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} instructed to investigate sound at {}", edge.getName().getString(), sound.soundId);
            }
        }
    }

    public static AttractionGoal getAttractionGoal(MobEntity mob) {
        if (mob == null) return null;
        try {
            net.minecraft.entity.ai.goal.GoalSelector goalSelector = ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector();
            for (net.minecraft.entity.ai.goal.PrioritizedGoal prioritizedGoal : goalSelector.getGoals()) {
                 Goal task = prioritizedGoal.getGoal();
                if (task instanceof AttractionGoal ag) {
                    return ag;
                }
            }
        } catch (ClassCastException e) {
            SoundAttractMod.LOGGER.error("Failed to cast MobEntity to MobEntityAccessor for getAttractionGoal. Ensure mixin is applied correctly.", e);
        }
        return null;
    }
}