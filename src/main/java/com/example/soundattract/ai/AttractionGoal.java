package com.example.soundattract.ai;

import com.example.soundattract.SoundTracker;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.DynamicScanCooldownManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.level.Level; 
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class AttractionGoal extends Goal {

    private final Mob mob;
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
    private SoundTracker.SoundRecord cachedSound = null;
    private boolean isPursuingSound = false;
    private int pursuingSoundTicksRemaining = 0;

    private enum EdgeMobState { GOING_TO_SOUND, RETURNING_TO_LEADER }
    private EdgeMobState edgeMobState = null;
    private boolean foundPlayerOrHit = false;
    private boolean relayedToLeader = false;
    private int edgeArrivalTicks = 0;
    private static final int EDGE_WAIT_TICKS = 15;

    // --- Delayed Relay Data Structure ---
    private static class DelayedRelay {
        public final Mob leader;
        public final BlockPos soundPos;
        public final long triggerTime;
        public boolean cancelled = false;
        public DelayedRelay(Mob leader, BlockPos soundPos, long triggerTime) {
            this.leader = leader;
            this.soundPos = soundPos;
            this.triggerTime = triggerTime;
        }
    }
    private static final Map<Mob, DelayedRelay> pendingDelayedRelays = new HashMap<>();

    public AttractionGoal(Mob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = SoundAttractConfig.mobMoveSpeed.get();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private int scanCooldownTicks() {
        return com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
    }

    private double getArrivalDistance() {
        return com.example.soundattract.config.SoundAttractConfig.arrivalDistance.get();
    }

    private int getWaitTicks() {
        return com.example.soundattract.config.SoundAttractConfig.scanCooldownTicks.get();
    }

    private double getDetectionRangeForPlayer(LivingEntity player) {
        boolean isSneaking = player.isCrouching();
        boolean isCrawling = player.getPose().name().equalsIgnoreCase("SWIMMING");
        boolean hasCamouflage = false;
        // Parse camouflageSets for armor match
        List<?> camoSets = com.example.soundattract.config.SoundAttractConfig.camouflageSets.get();
        String[] equipped = new String[4];
        int idx = 0;
        for (net.minecraft.world.item.ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) {
                equipped[idx++] = null;
                continue;
            }
            net.minecraft.resources.ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();
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
        if (isCrawling && hasCamouflage) return com.example.soundattract.config.SoundAttractConfig.crawlDetectionRangeCamouflage.get();
        if (isSneaking && hasCamouflage) return com.example.soundattract.config.SoundAttractConfig.sneakDetectionRangeCamouflage.get();
        if (isCrawling) return com.example.soundattract.config.SoundAttractConfig.crawlDetectionRange.get();
        if (isSneaking) return com.example.soundattract.config.SoundAttractConfig.sneakDetectionRange.get();
        return com.example.soundattract.config.SoundAttractConfig.baseDetectionRange.get();
    }

    private boolean shouldSuppressTargeting() {
        return false;
    }

    @Override
    public boolean canUse() {
        boolean isLeader = com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob;
        boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMob(mob);
        boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
        boolean smartEdge = com.example.soundattract.config.SoundAttractConfig.edgeMobSmartBehavior.get();
        if (smartEdge && (isEdge || isDeserter) && edgeMobState == EdgeMobState.RETURNING_TO_LEADER && foundPlayerOrHit) {
            return true;
        }
        if (mob.getTarget() != null || mob.getLastHurtByMob() != null) return false;
        if (!(isLeader || isDeserter)) return false;
        SoundTracker.SoundRecord initialSound = findInterestingSoundRecord();
        if (initialSound == null) return false;
        if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Mob {} found sound: pos={}, range={}, weight={}", mob.getName().getString(), initialSound.pos, initialSound.range, initialSound.weight);
        }
        this.targetSoundPos = initialSound.pos;
        this.currentTargetWeight = initialSound.weight;
        this.lastSoundTicksRemaining = initialSound.ticksRemaining;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        boolean isLeader = com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob;
        boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMob(mob);
        boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
        boolean smartEdge = com.example.soundattract.config.SoundAttractConfig.edgeMobSmartBehavior.get();
        if (smartEdge && (isEdge || isDeserter) && edgeMobState == EdgeMobState.RETURNING_TO_LEADER && foundPlayerOrHit) {
            return true;
        }
        if (mob.getTarget() != null || mob.getLastHurtByMob() != null) return false;
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
        boolean isLeader = com.example.soundattract.ai.MobGroupManager.getLeader(mob) == mob;
        boolean isEdge = com.example.soundattract.ai.MobGroupManager.isEdgeMob(mob);
        boolean isDeserter = com.example.soundattract.ai.MobGroupManager.isDeserter(mob);
        boolean smartEdge = com.example.soundattract.config.SoundAttractConfig.edgeMobSmartBehavior.get();
        if (mob.level() != null && !mob.level().isClientSide() && smartEdge) {
            long now = mob.level().getGameTime();
            Iterator<Map.Entry<Mob, DelayedRelay>> it = pendingDelayedRelays.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Mob, DelayedRelay> entry = it.next();
                DelayedRelay relay = entry.getValue();
                if (now >= relay.triggerTime && !relay.cancelled) {
                    com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(entry.getKey(), relay.soundPos.getX(), relay.soundPos.getY(), relay.soundPos.getZ(), 8.0, 1.0, now);
                    if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                        com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Delayed relay triggered for mob {} to leader {} at pos {}!", entry.getKey().getName().getString(), relay.leader.getName().getString(), relay.soundPos);
                    }
                    it.remove();
                } else if (relay.cancelled) {
                    it.remove();
                }
            }
        }
        if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
            com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Mob {} tick: leader={}, edge={}, deserter={}, smartEdge={}", mob.getName().getString(), isLeader, isEdge, isDeserter, smartEdge);
        }
        if (!isLeader && isEdge) {
            Level level = mob.level();
            BlockPos mobPos = mob.blockPosition();
            Vec3 mobEyePos = mob.getEyePosition(1.0F);
            SoundTracker.SoundRecord detected = SoundTracker.findNearestSound(level, mobPos, mobEyePos);
            if (detected != null) {
                double distSqr = mobPos.distSqr(detected.pos);
                if (distSqr <= detected.range * detected.range) {
                    com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(
                        mob,
                        detected.pos.getX(), detected.pos.getY(), detected.pos.getZ(),
                        detected.range, detected.weight, level.getGameTime()
                    );
                    if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                        com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} relayed sound {} to leader", mob.getName().getString(), detected.pos);
                    }
                }
            }
        }
        if ((isLeader && !smartEdge) || isDeserter) {
            if (mob.getTarget() != null || mob.getLastHurtByMob() != null) {
                stop();
                return;
            }
            if ((isLeader || isEdge)) {
                if (!com.example.soundattract.DynamicScanCooldownManager.shouldScanThisTick(mob.getId(), mob.level().getGameTime())) {
                    return;
                }
            }
            scanCooldownCounter--;
            if ((isLeader || isEdge) && scanCooldownCounter <= 0) {
                cachedSound = findInterestingSoundRecord();
                scanCooldownCounter = com.example.soundattract.DynamicScanCooldownManager.currentScanCooldownTicks;
                if (cachedSound != null && com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Mob {} tick found sound: pos={}, range={}, weight={}", mob.getName().getString(), cachedSound.pos, cachedSound.range, cachedSound.weight);
                }
                if (isEdge && cachedSound != null) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} relaying sound to leader: pos={}, range={}, weight={}", mob.getName().getString(), cachedSound.pos, cachedSound.range, cachedSound.weight);
                    com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(
                        mob,
                        cachedSound.pos.getX(), cachedSound.pos.getY(), cachedSound.pos.getZ(),
                        cachedSound.range, cachedSound.weight, mob.level().getGameTime()
                    );
                }
            }
            if (cachedSound == null) {
                if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[DIAG] Mob {} found NO sound to pursue at {}", mob.getName().getString(), mob.blockPosition());
                }
                return;
            }
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[DIAG] Mob {} found sound: {} at {} (range={})", mob.getName().getString(), cachedSound.weight, cachedSound.pos, cachedSound.range);
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
                    Vec3 mobPosVec2 = mob.position();
                    BlockPos soundPos = targetSoundPos;
                    double distSqr = mobPosVec2.distanceToSqr(Vec3.atCenterOf(soundPos));
                    double arrivalDistance = getArrivalDistance();
                    double arrivalThresholdSqr = arrivalDistance * arrivalDistance;
                    if (distSqr > arrivalThresholdSqr) {
                        Vec3 soundVec = Vec3.atCenterOf(soundPos);
                        Vec3 direction = soundVec.subtract(mobPosVec2).normalize();
                        double stepDistance = Math.min(32.0, Math.sqrt(distSqr));
                        Vec3 stepTarget = mobPosVec2.add(direction.scale(stepDistance));
                        BlockPos stepBlockPos = new BlockPos((int)Math.round(stepTarget.x), (int)Math.round(stepTarget.y), (int)Math.round(stepTarget.z));
                        this.mob.getNavigation().moveTo(stepBlockPos.getX() + 0.5, stepBlockPos.getY(), stepBlockPos.getZ() + 0.5, this.moveSpeed);
                    }
                    if (this.mob.getNavigation().getTargetPos() == null || !this.mob.getNavigation().getTargetPos().equals(soundPos)) {
                        Vec3 goalVec = Vec3.atCenterOf(soundPos);
                        this.mob.getNavigation().moveTo(goalVec.x, goalVec.y, goalVec.z, moveSpeed);
                    }
                    if (distSqr <= arrivalThresholdSqr) {
                        LivingEntity target = null;
                        List<LivingEntity> entities = mob.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(soundPos).inflate(2.0));
                        for (LivingEntity ent : entities) {
                            if (ent instanceof net.minecraft.world.entity.player.Player || mob.getTarget() == ent) {
                                target = ent;
                                break;
                            }
                        }
                        if (target != null) {
                            com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(mob, soundPos.getX(), soundPos.getY(), soundPos.getZ(), 8.0, 1.0, mob.level().getGameTime());
                            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                                com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} found player/target at sound, relaying to leader!", mob.getName().getString());
                            }
                        }
                    }
                }
                return;
            }
            if (this.mob.getNavigation().isDone()) {
                if (mob.blockPosition().distSqr(cachedSound.pos) >= getArrivalDistance() * getArrivalDistance()) {
                    Vec3 mobPosVec2 = mob.position();
                    Vec3 soundVec = Vec3.atCenterOf(cachedSound.pos);
                    Vec3 direction = soundVec.subtract(mobPosVec2).normalize();
                    double stepDistance = Math.min(32.0, Math.sqrt(mob.blockPosition().distSqr(cachedSound.pos)));
                    Vec3 stepTarget = mobPosVec2.add(direction.scale(stepDistance));
                    BlockPos stepBlockPos = new BlockPos((int)Math.round(stepTarget.x), (int)Math.round(stepTarget.y), (int)Math.round(stepTarget.z));
                    this.mob.getNavigation().moveTo(stepBlockPos.getX() + 0.5, stepBlockPos.getY(), stepBlockPos.getZ() + 0.5, this.moveSpeed);
                }
            }
            if (this.mob.getNavigation().getTargetPos() == null || !this.mob.getNavigation().getTargetPos().equals(cachedSound.pos)) {
                Vec3 goalVec = Vec3.atCenterOf(cachedSound.pos);
                this.mob.getNavigation().moveTo(goalVec.x, goalVec.y, goalVec.z, moveSpeed);
            }
        }
        if (smartEdge && (isEdge || isDeserter)) {
            if (edgeMobState == null) {
                edgeMobState = EdgeMobState.GOING_TO_SOUND;
                foundPlayerOrHit = false;
                relayedToLeader = false;
                edgeArrivalTicks = 0;
                if (!mob.level().isClientSide() && targetSoundPos != null) {
                    Mob leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                    if (leader != null && leader != mob) {
                        long triggerTime = mob.level().getGameTime() + 2400; 
                        DelayedRelay relay = new DelayedRelay(leader, targetSoundPos, triggerTime);
                        pendingDelayedRelays.put(mob, relay);
                        if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                            com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Scheduled delayed relay for mob {} to leader {} at pos {} (trigger at {})", mob.getName().getString(), leader.getName().getString(), targetSoundPos, triggerTime);
                        }
                    }
                }
            }
            if (edgeMobState == EdgeMobState.GOING_TO_SOUND) {
                if (targetSoundPos != null) {
                    mob.getNavigation().moveTo(targetSoundPos.getX() + 0.5, targetSoundPos.getY() + 0.5, targetSoundPos.getZ() + 0.5, moveSpeed);
                    double dist = mob.position().distanceTo(Vec3.atCenterOf(targetSoundPos));
                    if (dist < getArrivalDistance()) {
                        edgeArrivalTicks++;
                        List<LivingEntity> entities = mob.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(targetSoundPos).inflate(getDetectionRangeForPlayer(mob)));
                        boolean seesPlayer = false;
                        LivingEntity detectedPlayer = null;
                        for (LivingEntity ent : entities) {
                            if (ent instanceof net.minecraft.world.entity.player.Player) {
                                double detectRange = getDetectionRangeForPlayer(ent);
                                if (ent.distanceTo(mob) <= detectRange) {
                                    seesPlayer = true;
                                    detectedPlayer = ent;
                                    break;
                                }
                            }
                        }
                        if (seesPlayer || mob.getLastHurtByMob() instanceof net.minecraft.world.entity.player.Player) {
                            foundPlayerOrHit = true;
                            mob.setTarget(null);
                            mob.setLastHurtByMob(null);
                            edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                            mob.getNavigation().stop();
                            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                                if (seesPlayer && detectedPlayer != null) {
                                    com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} detected player {} at sound location, returning to leader.", mob.getName().getString(), detectedPlayer.getName().getString());
                                } else {
                                    com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} was hit by player at sound location, returning to leader.", mob.getName().getString());
                                }
                            }
                            return;
                        }
                        if (edgeArrivalTicks >= getWaitTicks()) {
                            edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                            mob.getNavigation().stop();
                            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                                com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Edge mob {} waited at sound location, found nothing, returning to leader.", mob.getName().getString());
                            }
                        }
                    }
                } else {
                    if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                        com.example.soundattract.SoundAttractMod.LOGGER.warn("[AttractionGoal] targetSoundPos was null for mob {} in GOING_TO_SOUND state!", mob.getName().getString());
                    }
                    edgeMobState = null;
                }
            } else if (edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {
                Mob leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
                if (leader != null && leader != mob) {
                    double offX = (mob.getRandom().nextDouble() - 0.5) * 4.0;
                    double offZ = (mob.getRandom().nextDouble() - 0.5) * 4.0;
                    BlockPos leaderPos = leader.blockPosition().offset((int)offX, 0, (int)offZ);
                    mob.getNavigation().moveTo(leaderPos.getX() + 0.5, leaderPos.getY() + 0.5, leaderPos.getZ() + 0.5, moveSpeed);
                    mob.setTarget(null);
                    mob.setLastHurtByMob(null);
                    double distToLeader = mob.position().distanceTo(Vec3.atCenterOf(leaderPos));
                    if (distToLeader < 2.0) {
                        if (foundPlayerOrHit && !relayedToLeader) {
                            if (targetSoundPos != null) {
                                com.example.soundattract.ai.MobGroupManager.relaySoundToLeader(mob, targetSoundPos.getX(), targetSoundPos.getY(), targetSoundPos.getZ(), 8.0, 1.0, mob.level().getGameTime());
                                relayedToLeader = true;
                                if (pendingDelayedRelays.containsKey(mob)) {
                                    pendingDelayedRelays.get(mob).cancelled = true;
                                    if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                                        com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Delayed relay cancelled for mob {} (returned to leader)", mob.getName().getString());
                                    }
                                }
                                if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                                    com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Edge/Deserter mob {} relayed sound to leader after returning!", mob.getName().getString());
                                }
                            } else {
                                if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                                    com.example.soundattract.SoundAttractMod.LOGGER.warn("[AttractionGoal] targetSoundPos was null when trying to relay sound for mob {}!", mob.getName().getString());
                                }
                            }
                        } else if (!foundPlayerOrHit) {
                            if (pendingDelayedRelays.containsKey(mob)) {
                                pendingDelayedRelays.get(mob).cancelled = true;
                                if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                                    com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Delayed relay cancelled for mob {} (returned to leader, found nothing)", mob.getName().getString());
                                }
                            }
                            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                                com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Edge/Deserter mob {} found nothing at sound, returned to leader, no relay.", mob.getName().getString());
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
        if (mob.level() != null && !mob.level().isClientSide() && com.example.soundattract.DynamicScanCooldownManager.shouldScanThisTick(mob.getId(), mob.level().getGameTime())) {
            SoundTracker.SoundRecord newSound = findInterestingSoundRecord();
            if (newSound != null && newSound != cachedSound) {
                cachedSound = newSound;
                targetSoundPos = newSound.pos;
                currentTargetWeight = newSound.weight;
                lastSoundTicksRemaining = newSound.ticksRemaining;
                if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                    com.example.soundattract.SoundAttractMod.LOGGER.info("[AttractionGoal] Mob {} switched target to new sound at {}", mob.getName().getString(), newSound.pos);
                }
            }
        }
        if (cachedSound == null) {
            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[DIAG] Mob {} found NO sound to pursue at {}", mob.getName().getString(), mob.blockPosition());
            }
            return;
        }
        if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[DIAG] Mob {} found sound: {} at {} (range={})", mob.getName().getString(), cachedSound.weight, cachedSound.pos, cachedSound.range);
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
            Vec3 mobPosVec2 = mob.position();
            BlockPos soundPos = targetSoundPos;
            double distSqr = mobPosVec2.distanceToSqr(Vec3.atCenterOf(soundPos));
            double arrivalDistance = getArrivalDistance();
            double arrivalThresholdSqr = arrivalDistance * arrivalDistance;
            long seed = mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits() ^ soundPos.hashCode();
            java.util.Random rand = new java.util.Random(seed);
            double angle = rand.nextDouble() * 2 * Math.PI;
            double radius = arrivalDistance * (0.5 + rand.nextDouble() * 0.5);
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            double offsetY = (rand.nextDouble() - 0.5) * 2.0;
            Vec3 offsetTarget = Vec3.atCenterOf(soundPos).add(offsetX, offsetY, offsetZ);
            if (distSqr > arrivalThresholdSqr) {
                this.mob.getNavigation().moveTo(offsetTarget.x, offsetTarget.y, offsetTarget.z, this.moveSpeed);
            }
        }
    }

    private SoundTracker.SoundRecord findInterestingSoundRecord() {
        Level level = mob.level();
        if (level.isClientSide()) return null;
        BlockPos mobPos = mob.blockPosition();
        Vec3 mobEyePos = mob.getEyePosition(1.0F);
        Mob leader = com.example.soundattract.ai.MobGroupManager.getLeader(mob);
        SoundTracker.SoundRecord currentTarget = cachedSound;
        SoundTracker.SoundRecord best;
        if (leader == mob) {
            SoundTracker.SoundRecord direct = SoundTracker.findNearestSound(level, mobPos, mobEyePos);
            List<com.example.soundattract.ai.MobGroupManager.SoundRelay> relays = com.example.soundattract.ai.MobGroupManager.consumeRelayedSounds(mob);
            best = direct;
            for (com.example.soundattract.ai.MobGroupManager.SoundRelay relay : relays) {
                if (best == null || relay.weight > best.weight || (Math.abs(relay.weight - best.weight) < 0.001 && relay.range > best.range)) {
                    best = new SoundTracker.SoundRecord(null, new BlockPos((int)relay.x, (int)relay.y, (int)relay.z), 20, level.dimension().location().toString(), relay.range, relay.weight);
                }
            }
        } else {
            best = SoundTracker.findNearestSound(level, mobPos, mobEyePos);
        }
        if (currentTarget != null && best != null && best != currentTarget) {
            double switchRatio = com.example.soundattract.config.SoundAttractConfig.SOUND_SWITCH_RATIO_CACHE;
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
}