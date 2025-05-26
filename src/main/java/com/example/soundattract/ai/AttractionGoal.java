package com.example.soundattract.ai;

import com.example.soundattract.SoundTracker;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.DynamicScanCooldownManager;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class AttractionGoal extends Goal {

    private final MobEntity mob;
    private final double moveSpeed;
    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;
    private int scanCooldown = 0;
    private BlockPos lastPos = null;
    private int stuckTicks = 0;
    private static final int STUCK_THRESHOLD = 10;
    private static final int RECALC_THRESHOLD = 30;
    private int lastSoundTicksRemaining = -1;
    private int scanTickCounter = 0;
    private int scanCooldownCounter = 0;
    private int edgeTickCounter = 0;
    private int deserterTickCounter = 0;
    private BlockPos lastSoundTargetPos = null;
    private int navigationUpdateCounter = 0;
    private SoundTracker.SoundRecord cachedSound = null;
    private boolean isPursuingSound = false;
    private int pursuingSoundTicksRemaining = 0;

    private enum EdgeMobState { GOING_TO_SOUND, RETURNING_TO_LEADER }
    private EdgeMobState edgeMobState = null;
    private boolean foundPlayerOrHit = false;
    private boolean relayedToLeader = false;
    private int edgeArrivalTicks = 0;
    private static final int EDGE_WAIT_TICKS = 15;

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
    private static final Map<MobEntity, DelayedRelay> pendingDelayedRelays = new HashMap<>();

    public AttractionGoal(MobEntity mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = SoundAttractMod.CONFIG.mobMoveSpeed;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    private int scanCooldownTicks() {
        return com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
    }

    private double getArrivalDistance() {
        return SoundAttractMod.CONFIG.arrivalDistance;
    }

    private int getWaitTicks() {
        return SoundAttractMod.CONFIG.scanCooldownTicks;
    }

    private double getDetectionRangeForPlayer(LivingEntity player) {
        boolean isSneaking = player.isSneaking();
        boolean isCrawling = player.getPose().name().equalsIgnoreCase("SWIMMING");
        boolean hasCamouflage = false;
        List<?> camoSets = SoundAttractMod.CONFIG.camouflageSets;
        String[] equipped = new String[4];
        int idx = 0;
        for (ItemStack stack : player.getArmorItems()) {
            if (stack.isEmpty()) {
                equipped[idx++] = null;
                continue;
            }
            Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
            equipped[idx++] = itemId.toString();
        }
        for (Object entry : camoSets) {
            if (!(entry instanceof String s)) continue;
            String[] parts = s.split(";");
            if (parts.length < 5) continue;
            boolean matchesArmor = true;
            for (int i = 0; i < 4; i++) {
                if (equipped[i] == null || !equipped[i].equals(parts[i+1])) {
                    matchesArmor = false;
                    break;
                }
            }
            if (matchesArmor) {
                hasCamouflage = true;
                break;
            }
        }
        if (isCrawling && hasCamouflage) return SoundAttractMod.CONFIG.crawlDetectionRangeCamouflage;
        if (isSneaking && hasCamouflage) return SoundAttractMod.CONFIG.sneakDetectionRangeCamouflage;
        if (isCrawling) return SoundAttractMod.CONFIG.crawlDetectionRange;
        if (isSneaking) return SoundAttractMod.CONFIG.sneakDetectionRange;
        return SoundAttractMod.CONFIG.baseDetectionRange;
    }

    private boolean shouldSuppressTargeting() {
        return false;
    }

    @Override
    public boolean canStart() {
        boolean isLeader = com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob;
        boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMobEntity(mob);
        boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
        boolean smartEdge = SoundAttractMod.CONFIG.edgeMobSmartBehavior;
        if (smartEdge && (isEdge || isDeserter) && edgeMobState == EdgeMobState.RETURNING_TO_LEADER && foundPlayerOrHit) {
            return true;
        }
        if (mob.getTarget() != null || mob.getLastAttacker() != null) return false;
        if (!(isLeader || isDeserter)) return false;
        SoundTracker.SoundRecord initialSound = findInterestingSoundRecord();
        if (initialSound == null) return false;
        if (SoundAttractMod.CONFIG.debugLogging) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] MobEntity {} found sound: pos={}, range={}, weight={}, partitionKey={}", mob.getName().getString(), initialSound.pos, initialSound.range, initialSound.weight, com.example.soundattract.SpatialPartitioner.getKey(initialSound.pos, com.example.soundattract.SoundAttractMod.CONFIG.spatialPartitionSize));
        }
        this.targetSoundPos = initialSound.pos;
        this.currentTargetWeight = initialSound.weight;
        this.lastSoundTicksRemaining = initialSound.ticksRemaining;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        boolean isLeader = com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob;
        boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMobEntity(mob);
        boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
        boolean smartEdge = SoundAttractMod.CONFIG.edgeMobSmartBehavior;
        if (smartEdge && (isEdge || isDeserter) && edgeMobState == EdgeMobState.RETURNING_TO_LEADER && foundPlayerOrHit) {
            return true;
        }
        if (mob.getTarget() != null || mob.getLastAttacker() != null) return false;
        SoundTracker.SoundRecord bestSound = findInterestingSoundRecord();
        if (bestSound == null) return false;
        return true;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        targetSoundPos = null;
        currentTargetWeight = -1.0;
        scanCooldown = 0;
        scanTickCounter = 0;
        lastPos = null;
        stuckTicks = 0;
        lastSoundTicksRemaining = -1;
    }

    @Override
    public void tick() {
        if (mob.getWorld().isClient()) return;
        boolean isLeader = com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob;
        boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMobEntity(mob);
        boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
        if (!isLeader && !isEdge && !isDeserter) {
            return;
        }
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            String role = isLeader ? "leader" : isEdge ? "edge" : isDeserter ? "deserter" : "unknown";
            if (isPursuingSound) {
                SoundAttractMod.LOGGER.info("[AttractionGoal][DEBUG] MobEntity {} ({}) is PURSUING sound at {} (ticks remaining: {}, partitionKey={})", mob.getName().getString(), role, targetSoundPos, pursuingSoundTicksRemaining, com.example.soundattract.SpatialPartitioner.getKey(targetSoundPos, com.example.soundattract.SoundAttractMod.CONFIG.spatialPartitionSize));
                if (targetSoundPos != null) {
                    if (navigationUpdateCounter-- <= 0) {
                        mob.getNavigation().startMovingTo(
                            targetSoundPos.getX() + 0.5,
                            targetSoundPos.getY(),
                            targetSoundPos.getZ() + 0.5,
                            moveSpeed
                        );
                        SoundAttractMod.LOGGER.info("[AttractionGoal][DEBUG] MobEntity {} ({}) moving to sound at {} with speed {}", mob.getName().getString(), role, targetSoundPos, moveSpeed);
                        navigationUpdateCounter = 20;
                    }
                }
            } else {
                SoundAttractMod.LOGGER.info("[AttractionGoal][DEBUG] MobEntity {} ({}) is NOT pursuing a sound", mob.getName().getString(), role);
            }
        }
        boolean smartEdge = SoundAttractMod.CONFIG.edgeMobSmartBehavior;
        if (mob.getWorld() != null && !mob.getWorld().isClient() && smartEdge) {
            long now = mob.getWorld().getTime();
            Iterator<Map.Entry<MobEntity, DelayedRelay>> it = pendingDelayedRelays.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<MobEntity, DelayedRelay> entry = it.next();
                DelayedRelay relay = entry.getValue();
                if (now >= relay.triggerTime && !relay.cancelled) {
                    com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(entry.getKey(), relay.soundPos.getX(), relay.soundPos.getY(), relay.soundPos.getZ(), 8.0, 1.0, now);
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Delayed relay triggered for mob {} to leader {} at pos {}!", entry.getKey().getName().getString(), relay.leader.getName().getString(), relay.soundPos);
                    }
                    it.remove();
                } else if (relay.cancelled) {
                    it.remove();
                }
            }
        }
        if (SoundAttractMod.CONFIG.debugLogging) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] MobEntity {} tick: leader={}, edge={}, deserter={}, smartEdge={}", mob.getName().getString(), isLeader, isEdge, isDeserter, smartEdge);
        }
        if (!isLeader && isEdge) {
    if (edgeTickCounter-- > 0) return;
    edgeTickCounter = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
            World level = mob.getWorld();
            BlockPos mobPos = mob.getBlockPos();
            Vec3d mobEyePos = mob.getEyePos();
            SoundTracker.SoundRecord detected = SoundTracker.findNearestSound(level, mobPos, mobEyePos);
            if (detected != null) {
                double getSquaredDistance = mobPos.getSquaredDistance(detected.pos);
                if (getSquaredDistance <= detected.range * detected.range) {
                    com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(
                        mob,
                        detected.pos.getX(), detected.pos.getY(), detected.pos.getZ(),
                        detected.range, detected.weight, level.getTime()
                    );
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} relayed sound {} to leader", mob.getName().getString(), detected.pos);
                    }
                }
            }
        }
        if ((isLeader && !smartEdge) || isDeserter) {
    if (isDeserter) {
        if (deserterTickCounter-- > 0) return;
        deserterTickCounter = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
    }
            if (mob.getTarget() != null || mob.getAttacker() != null) {
                stop();
                return;
            }
            if ((isLeader || isEdge)) {
                if (!com.example.soundattract.DynamicScanCooldownManager.shouldScanThisTick(mob.getUuid().getMostSignificantBits(), mob.getWorld().getTime())) {
                    return;
                }
            }
            scanCooldownCounter--;
            if ((isLeader || isEdge) && scanCooldownCounter <= 0) {
                cachedSound = findInterestingSoundRecord();
                scanCooldownCounter = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
                if (cachedSound != null && SoundAttractMod.CONFIG.debugLogging) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] MobEntity {} tick found sound: pos={}, range={}, weight={}, partitionKey={}", mob.getName().getString(), cachedSound.pos, cachedSound.range, cachedSound.weight, com.example.soundattract.SpatialPartitioner.getKey(cachedSound.pos, com.example.soundattract.SoundAttractMod.CONFIG.spatialPartitionSize));
                }
                if (isEdge && cachedSound != null) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} relaying sound to leader: pos={}, range={}, weight={}", mob.getName().getString(), cachedSound.pos, cachedSound.range, cachedSound.weight);
                    com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(
                        mob,
                        cachedSound.pos.getX(), cachedSound.pos.getY(), cachedSound.pos.getZ(),
                        cachedSound.range, cachedSound.weight, mob.getWorld().getTime()
                    );
                }
            }
            if (cachedSound == null) {
                if (SoundAttractMod.CONFIG.debugLogging) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[DIAG] MobEntity {} found NO sound to pursue at {}", mob.getName().getString(), mob.getBlockPos());
                }
                return;
            }
            if (SoundAttractMod.CONFIG.debugLogging) {
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[DIAG] MobEntity {} found sound: {} at {} (range={})", mob.getName().getString(), cachedSound.weight, cachedSound.pos, cachedSound.range);
            }
            if (com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob) {
                if (isPursuingSound) {
                    pursuingSoundTicksRemaining--;
                    if (pursuingSoundTicksRemaining <= 0) {
                        isPursuingSound = false;
                    }
                }
            }
            if (isEdge && smartEdge) {
                if (targetSoundPos != null) {
                    Vec3d mobPosVec2 = mob.getPos();
                    BlockPos soundPos = targetSoundPos;
                    double getSquaredDistance = mobPosVec2.squaredDistanceTo(Vec3d.ofCenter(soundPos));
                    double arrivalDistance = getArrivalDistance();
                    double arrivalThresholdSqr = arrivalDistance * arrivalDistance;
                    if (getSquaredDistance > arrivalThresholdSqr) {
                        Vec3d soundVec = Vec3d.ofCenter(soundPos);
                        Vec3d direction = soundVec.subtract(mobPosVec2).normalize();
                        double stepDistance = Math.min(32.0, Math.sqrt(getSquaredDistance));
                        Vec3d stepTarget = mobPosVec2.add(direction.multiply(stepDistance));
                        BlockPos stepBlockPos = new BlockPos((int)Math.round(stepTarget.x), (int)Math.round(stepTarget.y), (int)Math.round(stepTarget.z));
                        this.mob.getNavigation().startMovingTo(stepBlockPos.getX() + 0.5, stepBlockPos.getY(), stepBlockPos.getZ() + 0.5, this.moveSpeed);
                    }
                    if (this.mob.getNavigation().getTargetPos() == null || !this.mob.getNavigation().getTargetPos().equals(soundPos)) {
                        Vec3d goalVec = Vec3d.ofCenter(soundPos);
                        this.mob.getNavigation().startMovingTo(goalVec.x, goalVec.y, goalVec.z, moveSpeed);
                    }
                    if (getSquaredDistance <= arrivalThresholdSqr) {
                        LivingEntity target = null;
                        List<LivingEntity> entities = mob.getWorld().getEntitiesByClass(LivingEntity.class, new net.minecraft.util.math.Box(soundPos).expand(2.0), ent -> true);
                        for (LivingEntity ent : entities) {
                            if (ent instanceof PlayerEntity || mob.getTarget() == ent) {
                                target = ent;
                                break;
                            }
                        }
                        if (target != null) {
                            com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(mob, soundPos.getX(), soundPos.getY(), soundPos.getZ(), 8.0, 1.0, mob.getWorld().getTime());
                            if (SoundAttractMod.CONFIG.debugLogging) {
                                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} found player/target at sound, relaying to leader!", mob.getName().getString());
                            }
                        }
                    }
                }
                return;
            }
            if (this.mob.getNavigation().isIdle()) {
                if (mob.getBlockPos().getSquaredDistance(cachedSound.pos) >= getArrivalDistance() * getArrivalDistance()) {
                    Vec3d mobPosVec2 = mob.getPos();
                    Vec3d soundVec = Vec3d.ofCenter(cachedSound.pos);
                    Vec3d direction = soundVec.subtract(mobPosVec2).normalize();
                    double stepDistance = Math.min(32.0, Math.sqrt(mob.getBlockPos().getSquaredDistance(cachedSound.pos)));
                    Vec3d stepTarget = mobPosVec2.add(direction.multiply(stepDistance));
                    BlockPos stepBlockPos = new BlockPos((int)Math.round(stepTarget.x), (int)Math.round(stepTarget.y), (int)Math.round(stepTarget.z));
                    this.mob.getNavigation().startMovingTo(stepBlockPos.getX() + 0.5, stepBlockPos.getY(), stepBlockPos.getZ() + 0.5, this.moveSpeed);
                }
            }
            BlockPos navTarget = this.mob.getNavigation().getTargetPos();
            double threshold = 1.0;
            Vec3d goalVec = Vec3d.ofCenter(cachedSound.pos);
            if (navTarget == null || navTarget.getSquaredDistance(cachedSound.pos) > threshold * threshold) {
                this.mob.getNavigation().startMovingTo(goalVec.x, goalVec.y, goalVec.z, moveSpeed);
            }
        }
        if (smartEdge && (isEdge || isDeserter)) {
            if (edgeMobState == null) {
                edgeMobState = EdgeMobState.GOING_TO_SOUND;
                foundPlayerOrHit = false;
                relayedToLeader = false;
                edgeArrivalTicks = 0;
                if (!mob.getWorld().isClient() && targetSoundPos != null) {
                    MobEntity leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                    if (leader != null && leader != mob) {
                        long triggerTime = mob.getWorld().getTime() + 2400; 
                        DelayedRelay relay = new DelayedRelay(leader, targetSoundPos, triggerTime);
                        pendingDelayedRelays.put(mob, relay);
                        if (SoundAttractMod.CONFIG.debugLogging) {
                            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Scheduled delayed relay for mob {} to leader {} at pos {} (trigger at {})", mob.getName().getString(), leader.getName().getString(), targetSoundPos, triggerTime);
                        }
                    }
                }
            }
            if (edgeMobState == EdgeMobState.GOING_TO_SOUND) {
                if (targetSoundPos != null) {
                    mob.getNavigation().startMovingTo(targetSoundPos.getX() + 0.5, targetSoundPos.getY() + 0.5, targetSoundPos.getZ() + 0.5, moveSpeed);
                    double dist = mob.getPos().distanceTo(Vec3d.ofCenter(targetSoundPos));
                    if (dist < getArrivalDistance()) {
                        edgeArrivalTicks++;
                        List<LivingEntity> entities = mob.getWorld().getEntitiesByClass(LivingEntity.class, new net.minecraft.util.math.Box(targetSoundPos).expand(getDetectionRangeForPlayer(mob)), ent -> true);
                        boolean seesPlayer = false;
                        LivingEntity detectedPlayer = null;
                        for (LivingEntity ent : entities) {
                            if (ent instanceof net.minecraft.entity.player.PlayerEntity) {
                                double detectRange = getDetectionRangeForPlayer(ent);
                                if (ent.distanceTo(mob) <= detectRange) {
                                    seesPlayer = true;
                                    detectedPlayer = ent;
                                    break;
                                }
                            }
                        }
                        if (seesPlayer || mob.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity) {
                            foundPlayerOrHit = true;
                            mob.setTarget(null);
                            edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                            mob.getNavigation().stop();
                            if (SoundAttractMod.CONFIG.debugLogging) {
                                if (seesPlayer && detectedPlayer != null) {
                                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} detected player {} at sound location, returning to leader.", mob.getName().getString(), detectedPlayer.getName().getString());
                                } else {
                                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} was hit by player at sound location, returning to leader.", mob.getName().getString());
                                }
                            }
                            return;
                        }
                        if (edgeArrivalTicks >= getWaitTicks()) {
                            edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                            mob.getNavigation().stop();
                            if (SoundAttractMod.CONFIG.debugLogging) {
                                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} waited at sound location, found nothing, returning to leader.", mob.getName().getString());
                            }
                        }
                    }
                } else {
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.warn("[AttractionGoal] targetSoundPos was null for mob {} in GOING_TO_SOUND state!", mob.getName().getString());
                    }
                    edgeMobState = null;
                }
            } else if (edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {
                MobEntity leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                if (leader != null && leader != mob) {
                    double offX = (mob.getRandom().nextDouble() - 0.5) * 4.0;
                    double offZ = (mob.getRandom().nextDouble() - 0.5) * 4.0;
                    BlockPos leaderPos = leader.getBlockPos().add((int)offX, 0, (int)offZ);
                    mob.getNavigation().startMovingTo(leaderPos.getX() + 0.5, leaderPos.getY() + 0.5, leaderPos.getZ() + 0.5, moveSpeed);
                    mob.setTarget(null);
                    double distToLeader = mob.getPos().distanceTo(Vec3d.ofCenter(leaderPos));
                    if (distToLeader < 2.0) {
                        if (foundPlayerOrHit && !relayedToLeader) {
                            if (targetSoundPos != null) {
                                com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(mob, targetSoundPos.getX(), targetSoundPos.getY(), targetSoundPos.getZ(), 8.0, 1.0, mob.getWorld().getTime());
                                relayedToLeader = true;
                                if (pendingDelayedRelays.containsKey(mob)) {
                                    pendingDelayedRelays.get(mob).cancelled = true;
                                    if (SoundAttractMod.CONFIG.debugLogging) {
                                        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Delayed relay cancelled for mob {} (returned to leader)", mob.getName().getString());
                                    }
                                }
                                if (SoundAttractMod.CONFIG.debugLogging) {
                                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Edge/Deserter mob {} relayed sound to leader after returning!", mob.getName().getString());
                                }
                            } else {
                                if (SoundAttractMod.CONFIG.debugLogging) {
                                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.warn("[AttractionGoal] targetSoundPos was null when trying to relay sound for mob {}!", mob.getName().getString());
                                }
                            }
                        } else if (!foundPlayerOrHit) {
                            if (pendingDelayedRelays.containsKey(mob)) {
                                pendingDelayedRelays.get(mob).cancelled = true;
                                if (SoundAttractMod.CONFIG.debugLogging) {
                                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Delayed relay cancelled for mob {} (returned to leader, found nothing)", mob.getName().getString());
                                }
                            }
                            if (SoundAttractMod.CONFIG.debugLogging) {
                                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] Edge/Deserter mob {} found nothing at sound, returned to leader, no relay.", mob.getName().getString());
                            }
                        }
                        edgeMobState = null;
                        foundPlayerOrHit = false;
                        relayedToLeader = false;
                        edgeArrivalTicks = 0;
                        mob.getNavigation().stop();
                    }
                }
            }
            return;
        }
        if (mob.getWorld() != null && !mob.getWorld().isClient() && com.example.soundattract.DynamicScanCooldownManager.shouldScanThisTick(mob.getUuid().getMostSignificantBits(), mob.getWorld().getTime())) {
            SoundTracker.SoundRecord newSound = findInterestingSoundRecord();
            if (newSound != null && newSound != cachedSound) {
                cachedSound = newSound;
                targetSoundPos = newSound.pos;
                currentTargetWeight = newSound.weight;
                lastSoundTicksRemaining = newSound.ticksRemaining;
                if (SoundAttractMod.CONFIG.debugLogging) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[AttractionGoal] MobEntity {} switched target to new sound at {}", mob.getName().getString(), newSound.pos);
                }
            }
        }
        if (cachedSound == null) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[DIAG] MobEntity {} found NO sound to pursue at {}", mob.getName().getString(), mob.getBlockPos());
            }
            return;
        }
        if (SoundAttractMod.CONFIG.debugLogging) {
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) SoundAttractMod.LOGGER.info("[DIAG] MobEntity {} found sound: {} at {} (range={})", mob.getName().getString(), cachedSound.weight, cachedSound.pos, cachedSound.range);
        }
        if (com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob) {
            if (isPursuingSound) {
                pursuingSoundTicksRemaining--;
                if (pursuingSoundTicksRemaining <= 0) {
                    isPursuingSound = false;
                }
            }
        }
        if (targetSoundPos != null) {
            Vec3d mobPosVec2 = mob.getPos();
            BlockPos soundPos = targetSoundPos;
            double getSquaredDistance = mobPosVec2.squaredDistanceTo(Vec3d.ofCenter(soundPos));
            double arrivalDistance = getArrivalDistance();
            double arrivalThresholdSqr = arrivalDistance * arrivalDistance;
            long seed = mob.getUuid().getMostSignificantBits() ^ mob.getUuid().getLeastSignificantBits() ^ soundPos.hashCode();
            java.util.Random rand = new java.util.Random(seed);
            double angle = rand.nextDouble() * 2 * Math.PI;
            double radius = arrivalDistance * (0.5 + rand.nextDouble() * 0.5);
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            double offsetY = (rand.nextDouble() - 0.5) * 2.0;
            Vec3d offsetTarget = Vec3d.ofCenter(soundPos).add(offsetX, offsetY, offsetZ);
            if (getSquaredDistance > arrivalThresholdSqr) {
                this.mob.getNavigation().startMovingTo(offsetTarget.x, offsetTarget.y, offsetTarget.z, this.moveSpeed);
            }
        }
    }

    private SoundTracker.SoundRecord findInterestingSoundRecord() {
        World level = mob.getWorld();
        if (level.isClient()) return null;
        BlockPos mobPos = mob.getBlockPos();
        Vec3d mobEyePos = mob.getEyePos();
        MobEntity leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
        SoundTracker.SoundRecord currentTarget = cachedSound;
        SoundTracker.SoundRecord best;
        if (leader == mob) {
            SoundTracker.SoundRecord direct = SoundTracker.findNearestSound(level, mobPos, mobEyePos);
            List<com.example.soundattract.ai.MobGroupManager.SoundRelay> relays = com.example.soundattract.ai.MobGroupManager.consumeRelayedSounds(mob);
            best = direct;
            for (com.example.soundattract.ai.MobGroupManager.SoundRelay relay : relays) {
                if (best == null || relay.weight > best.weight || (Math.abs(relay.weight - best.weight) < 0.001 && relay.range > best.range)) {
                    best = new SoundTracker.SoundRecord(null, new BlockPos((int)relay.x, (int)relay.y, (int)relay.z), 20, level.getRegistryKey().getValue().toString(), relay.range, relay.weight);
                }
            }
        } else {
            best = SoundTracker.findNearestSound(level, mobPos, mobEyePos);
        }
        if (currentTarget != null && best != null && best != currentTarget) {
            double switchRatio = com.example.soundattract.SoundAttractMod.CONFIG.soundSwitchRatio;
            boolean canSwitch = best.weight > currentTarget.weight * switchRatio || best.range > currentTarget.range * switchRatio;
            if (!canSwitch) {
                return currentTarget;
            }
        }
        if (best != null) {
            isPursuingSound = true;
            pursuingSoundTicksRemaining = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
        } else {
            if (leader == mob) {
                List<com.example.soundattract.ai.MobGroupManager.SoundRelay> relays = com.example.soundattract.ai.MobGroupManager.consumeRelayedSounds(mob);
                boolean hasRelays = relays != null && !relays.isEmpty();
                if (!hasRelays) {
                    isPursuingSound = false;
                    pursuingSoundTicksRemaining = 0;
                }
            }
        }
        return best;
    }

    private boolean isPlayerMovementSound(double weight) {
        return weight == 1.2 || weight == 0.6 || weight == 0.2 || weight == 0.1;
    }

    private boolean isParcoolSound(double weight) {
        return weight == 0.4 || weight == 0.5 || weight == 0.6 || weight == 0.7 || weight == 1.0 || weight == 1.25 || weight == 1.5;
    }

    public boolean isPursuingSound() {
        return isPursuingSound;
    }

    public static void handleSoundAttraction(MobEntity mob, SoundTracker.SoundRecord sound) {
        AttractionGoal goal = getAttractionGoal(mob);
        if (goal != null && sound != null) {
            goal.cachedSound = sound;
            goal.isPursuingSound = true;
            goal.pursuingSoundTicksRemaining = DynamicScanCooldownManager.currentScanCooldownTicks;
            goal.targetSoundPos = sound.pos;
            goal.edgeMobState = null;
            goal.foundPlayerOrHit = false;
            goal.relayedToLeader = false;
        }
    }

    public static void handleRelayToLeader(MobEntity leader, SoundTracker.SoundRecord sound, MobEntity edge) {
        AttractionGoal goal = getAttractionGoal(leader);
        if (goal != null && sound != null) {
            goal.cachedSound = sound;
            goal.isPursuingSound = true;
            goal.pursuingSoundTicksRemaining = DynamicScanCooldownManager.currentScanCooldownTicks;
            goal.targetSoundPos = sound.pos;
            goal.edgeMobState = null;
            goal.foundPlayerOrHit = false;
            goal.relayedToLeader = true;
        }
    }

    public static void handleLeaderObjective(MobEntity leader, SoundTracker.SoundRecord sound) {
        AttractionGoal goal = getAttractionGoal(leader);
        if (goal != null && sound != null) {
            goal.cachedSound = sound;
            goal.isPursuingSound = true;
            goal.pursuingSoundTicksRemaining = DynamicScanCooldownManager.currentScanCooldownTicks;
            goal.targetSoundPos = sound.pos;
            goal.edgeMobState = null;
            goal.foundPlayerOrHit = false;
            goal.relayedToLeader = true;
        }
    }

    public static void handleEdgeInvestigate(MobEntity edge, SoundTracker.SoundRecord sound) {
        AttractionGoal goal = getAttractionGoal(edge);
        if (goal != null && sound != null) {
            goal.cachedSound = sound;
            goal.isPursuingSound = true;
            goal.pursuingSoundTicksRemaining = DynamicScanCooldownManager.currentScanCooldownTicks;
            goal.targetSoundPos = sound.pos;
            goal.edgeMobState = EdgeMobState.GOING_TO_SOUND;
            goal.foundPlayerOrHit = false;
            goal.relayedToLeader = false;
        }
    }

    public static AttractionGoal getAttractionGoal(MobEntity mob) {
        if (mob == null) return null;
        for (net.minecraft.entity.ai.goal.Goal goal : ((com.example.soundattract.mixin.MobEntityAccessor) mob).getGoalSelector().getGoals()) {
            if (goal instanceof AttractionGoal ag) {
                return ag;
            }
        }
        return null;
    }
}