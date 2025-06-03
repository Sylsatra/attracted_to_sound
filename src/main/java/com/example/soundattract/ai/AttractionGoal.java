package com.example.soundattract.ai;

import com.example.soundattract.SoundTracker;
import com.example.soundattract.SoundAttractMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects; 

import com.example.soundattract.ai.MobGroupManager;
import com.example.soundattract.ai.MobGroupManager.SoundRelay;
import com.example.soundattract.StealthDetectionEvents;


public class AttractionGoal extends Goal {
    private final MobEntity mob;
    private final double moveSpeed;

    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;
    private SoundTracker.SoundRecord cachedSound = null;

    private int scanTickCounter = 0;

    private boolean isPursuingSound = false;
    private int pursuingSoundTicksRemaining = 0;

    private int edgeTickCounter = 0;
    private int deserterTickCounter = 0;

    private enum EdgeMobState { GOING_TO_SOUND, RETURNING_TO_LEADER }
    private EdgeMobState edgeMobState = null;
    private boolean foundPlayerOrHit = false;
    private boolean relayedToLeader = false;
    private int edgeArrivalTicks = 0;

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

    @Override
    public boolean canStart() {
        if (mob.getWorld().isClient()) {
            return false;
        }

        boolean isLeader = MobGroupManager.getLeader(mob) == mob;
        boolean isEdge   = MobGroupManager.isEdgeMobEntity(mob);
        boolean isDeserter = MobGroupManager.isDeserter(mob);
        boolean smartEdge = SoundAttractMod.CONFIG.edgeMobSmartBehavior;

        if (smartEdge && (isEdge || isDeserter)
            && edgeMobState == EdgeMobState.RETURNING_TO_LEADER
            && foundPlayerOrHit) {
            return true;
        }

        if (mob.getTarget() != null || mob.getLastAttacker() != null) {
            return false;
        }

        if (!smartEdge || isLeader || isDeserter) {
            if (!(isLeader || isDeserter || (isEdge && !smartEdge))) {
                 return false;
            }
        } else if (isEdge && smartEdge) {
            return true;
        }

        SoundTracker.SoundRecord initialSound = findInterestingSoundRecord();
        if (initialSound == null) {
            return false;
        }

        this.cachedSound = initialSound;
        this.targetSoundPos = initialSound.pos;
        this.currentTargetWeight = initialSound.weight;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (mob.getWorld().isClient()) {
            return false;
        }

        boolean isLeader = MobGroupManager.getLeader(mob) == mob;
        boolean isEdge   = MobGroupManager.isEdgeMobEntity(mob);
        boolean isDeserter = MobGroupManager.isDeserter(mob);
        boolean smartEdge = SoundAttractMod.CONFIG.edgeMobSmartBehavior;

        if (smartEdge && (isEdge || isDeserter)
            && edgeMobState == EdgeMobState.RETURNING_TO_LEADER
            && foundPlayerOrHit) {
            return true;
        }

        if (mob.getTarget() != null || mob.getLastAttacker() != null) {
            return false;
        }

        if (smartEdge && (isEdge || isDeserter) && edgeMobState != null) {
            return true;
        }
        
        return this.isPursuingSound || (cachedSound != null && targetSoundPos != null);
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.targetSoundPos = null;
        this.currentTargetWeight = -1.0;
        this.cachedSound = null;
        this.isPursuingSound = false;
        this.pursuingSoundTicksRemaining = 0;
        this.scanTickCounter = SoundAttractMod.CONFIG.scanCooldownTicks / 2;

        this.edgeMobState = null;
        this.foundPlayerOrHit = false;
        this.relayedToLeader = false;
        this.edgeArrivalTicks = 0;
        if (pendingDelayedRelays.containsKey(mob)) {
             pendingDelayedRelays.get(mob).cancelled = true;
             pendingDelayedRelays.remove(mob);
        }
    }

    @Override
    public void tick() {
        if (mob.getWorld().isClient()) {
            return;
        }

        boolean isLeader   = MobGroupManager.getLeader(mob) == mob;
        boolean isEdge     = MobGroupManager.isEdgeMobEntity(mob);
        boolean isDeserter = MobGroupManager.isDeserter(mob);
        boolean smartEdge  = SoundAttractMod.CONFIG.edgeMobSmartBehavior;

        if (smartEdge && (isEdge || isDeserter)) {
            long now = mob.getWorld().getTime();
            Iterator<Map.Entry<MobEntity, DelayedRelay>> it = pendingDelayedRelays.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<MobEntity, DelayedRelay> entry = it.next();
                DelayedRelay relay = entry.getValue();
                if (entry.getKey() == mob && now >= relay.triggerTime && !relay.cancelled) {
                    MobGroupManager.relaySoundToLeader(
                        mob,
                        relay.soundPos.getX(), relay.soundPos.getY(), relay.soundPos.getZ(),
                        8.0, 1.0, 
                        now
                    );
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                            "[AttractionGoal] Delayed relay triggered: edge {} → leader {} at {}",
                            mob.getName().getString(),
                            relay.leader.getName().getString(),
                            relay.soundPos
                        );
                    }
                    it.remove();
                } else if (relay.cancelled || entry.getKey() != mob) { 
                    it.remove();
                }
            }
        }

        if (smartEdge && isEdge && edgeMobState == null) { 
            if (edgeTickCounter-- <= 0) {
                edgeTickCounter = SoundAttractMod.CONFIG.scanCooldownTicks;
                World world = mob.getWorld();
                SoundTracker.SoundRecord detected = SoundTracker.findNearestSound(world, mob, mob.getBlockPos(), mob.getEyePos());
                if (detected != null) {

                    MobGroupManager.relaySoundToLeader(
                        mob,
                        detected.pos.getX(), detected.pos.getY(), detected.pos.getZ(),
                        detected.range, detected.weight, 
                        world.getTime()
                    );
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                            "[AttractionGoal] Smart Edge mob {} proactively relayed sound {} (effective range/weight) → leader",
                            mob.getName().getString(),
                            detected.pos
                        );
                    }
                }
            }
        }

        if (smartEdge && (isEdge || isDeserter)) {
            handleSmartEdgeDeserterLogic(isEdge, isDeserter);
            return; 
        }

        if (mob.getTarget() != null || mob.getAttacker() != null) {
            if(isPursuingSound) stop();
            return;
        }

        if (isDeserter) { 
            if (deserterTickCounter-- > 0) {
                return;
            }
            deserterTickCounter = SoundAttractMod.CONFIG.scanCooldownTicks;
        }

        scanTickCounter--;
        if (scanTickCounter <= 0) {
            scanTickCounter = SoundAttractMod.CONFIG.scanCooldownTicks;
            SoundTracker.SoundRecord newlyEvaluatedSound = findInterestingSoundRecord(); 

            if (!Objects.equals(this.cachedSound, newlyEvaluatedSound)) { 
                if (SoundAttractMod.CONFIG.debugLogging && this.cachedSound != null && newlyEvaluatedSound != null &&
                    this.cachedSound.pos.equals(newlyEvaluatedSound.pos) && Objects.equals(this.cachedSound.soundId, newlyEvaluatedSound.soundId)) {
                } else if (SoundAttractMod.CONFIG.debugLogging && newlyEvaluatedSound != null) {
                     SoundAttractMod.LOGGER.info(
                        "[AttractionGoal] {} updated target to sound at {} (weight={})",
                        mob.getName().getString(),
                        newlyEvaluatedSound.pos,
                        newlyEvaluatedSound.weight
                    );
                } else if (SoundAttractMod.CONFIG.debugLogging && this.cachedSound != null && newlyEvaluatedSound == null) {
                    SoundAttractMod.LOGGER.info(
                        "[AttractionGoal] {} lost track of sound or no new interesting sound found.",
                        mob.getName().getString()
                    );
                }
                
                this.cachedSound = newlyEvaluatedSound; 
                if (this.cachedSound != null) {
                    this.targetSoundPos = this.cachedSound.pos;
                    this.currentTargetWeight = this.cachedSound.weight;
                } else {
                    this.targetSoundPos = null;
                    this.currentTargetWeight = -1.0;
                }
            }

            if (isEdge && !smartEdge && this.cachedSound != null) { 
                 MobGroupManager.relaySoundToLeader(
                    mob,
                    this.cachedSound.pos.getX(), this.cachedSound.pos.getY(), this.cachedSound.pos.getZ(),
                    this.cachedSound.range, this.cachedSound.weight,
                    mob.getWorld().getTime()
                );
            }
        }

        if (this.isPursuingSound && this.cachedSound != null && this.targetSoundPos != null) {
            if (isLeader) {
                if (pursuingSoundTicksRemaining > 0) {
                    pursuingSoundTicksRemaining--;
                } else {

                }
            }

            double distSqToTarget = mob.getPos().squaredDistanceTo(Vec3d.ofCenter(this.targetSoundPos));
            double arrivalDistSq = getArrivalDistance() * getArrivalDistance();

            if (distSqToTarget > arrivalDistSq) {
                this.mob.getNavigation().startMovingTo(
                    this.targetSoundPos.getX() + 0.5,
                    this.targetSoundPos.getY() + 0.5,
                    this.targetSoundPos.getZ() + 0.5,
                    this.moveSpeed
                );
            } else {
                this.mob.getNavigation().stop();
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info(
                        "[AttractionGoal] {} arrived at sound location {}",
                        mob.getName().getString(),
                        this.targetSoundPos
                    );
                }
                this.isPursuingSound = false; 
                this.cachedSound = null; 
                this.targetSoundPos = null;
                this.currentTargetWeight = -1.0;
                scanTickCounter = Math.min(scanTickCounter, SoundAttractMod.CONFIG.scanCooldownTicks / 3); 
            }
        } else {
            if (!this.mob.getNavigation().isIdle()) {
                 this.mob.getNavigation().stop();
            }
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
                    if (mob.canSee(player)) {
                         double actualDetectionRange = StealthDetectionEvents.computeFullDetectionRange(mob, player, mob.getWorld());
                         if (player.distanceTo(mob) <= actualDetectionRange) {
                            detectedPlayer = player;
                            break;
                         }
                    }
                }

                if (detectedPlayer != null) {
                    foundPlayerOrHit = true;
                    edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                            "[AttractionGoal] Smart {} {} found player {} at sound {} (dist {}). Returning to leader.",
                            isEdge ? "Edge" : "Deserter", mob.getName().getString(),
                            detectedPlayer.getName().getString(), targetSoundPos, detectedPlayer.distanceTo(mob)
                        );
                    }
                } else if (edgeArrivalTicks >= getWaitTicks()) { 
                    edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info(
                            "[AttractionGoal] Smart {} {} waited at {} ({} ticks), found nothing. Returning to leader.",
                             isEdge ? "Edge" : "Deserter", mob.getName().getString(), targetSoundPos, edgeArrivalTicks
                        );
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

    private SoundTracker.SoundRecord findInterestingSoundRecord() {
        World world = mob.getWorld();
        if (world.isClient()) return null;

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

        if (finalDecisionSound != null) {
            this.isPursuingSound = true;
            if (this.cachedSound != finalDecisionSound || 
                (this.cachedSound != null && finalDecisionSound != null &&
                 Objects.equals(this.cachedSound.soundId, finalDecisionSound.soundId) &&
                 this.cachedSound.pos.equals(finalDecisionSound.pos))) { 
                this.pursuingSoundTicksRemaining = SoundAttractMod.CONFIG.scanCooldownTicks * 2;
            }
        } else {
            this.isPursuingSound = false;
            this.pursuingSoundTicksRemaining = 0;
        }
        return finalDecisionSound;
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
                                   (edgeMob != null && MobGroupManager.isEdgeMobEntity(edgeMob)); // Prioritize if from an edge mob

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