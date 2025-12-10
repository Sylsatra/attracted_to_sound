package com.example.soundattract.ai;

import com.example.soundattract.DynamicScanCooldownManager;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundAttractionEvents;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.config.PlayerStance;
import com.example.soundattract.config.SoundAttractConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Objects;

public class AttractionGoal extends Goal {

    private final Mob mob;
    private final double moveSpeed;
    private BlockPos targetSoundPos;
    private double currentTargetWeight = -1.0;
    private int scanCooldown = 0;
    private BlockPos lastPos = null;
    private Vec3 lastLeaderPos = null;
    private int stuckTicks = 0;
    private static final int STUCK_THRESHOLD = 10;
    private static final int RECALC_THRESHOLD = 30;
    private long lastMoveToTick = -1L;
    private BlockPos lastMoveToTarget = null;
    private static final int MOVE_TO_COOLDOWN_TICKS = 20;
    private int lastSoundTicksRemaining = -1;
    private int scanTickCounter = 0;
    private int scanCooldownCounter = 0;
    private boolean isPursuingSound = false;
    private int pursuingSoundTicksRemaining = 0;

    private enum EdgeMobState { GOING_TO_SOUND, RETURNING_TO_LEADER }
    private EdgeMobState edgeMobState = null;
    private boolean foundPlayerOrHit = false;
    private boolean relayedToLeader = false;
    private int edgeArrivalTicks = 0;
    private static final int EDGE_WAIT_TICKS = 15;
    private SoundTracker.SoundRecord cachedSound;
    private SoundTracker.SoundRecord cachedNearestSoundForTick;
    private long lastSoundCheckTick = -1;

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

    private Vec3 chosenDest = null;
    private boolean hasPicked = false;

    public AttractionGoal(Mob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = SoundAttractConfig.COMMON.mobMoveSpeed.get();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private int scanCooldownTicks() {
        return DynamicScanCooldownManager.currentScanCooldownTicks;
    }

    private boolean isMobEligible() {
        java.util.Set<net.minecraft.world.entity.EntityType<?>> attractedTypes = SoundAttractionEvents.getCachedAttractedEntityTypes();
        boolean byType = attractedTypes.contains(this.mob.getType());
        boolean hasProfile = com.example.soundattract.config.SoundAttractConfig.getMatchingProfile(this.mob) != null;
        return byType || hasProfile;
    }
    private double getArrivalDistance() {
        return SoundAttractConfig.COMMON.arrivalDistance.get();
    }

    public BlockPos getTargetSoundPos() {
        return this.targetSoundPos;
    }

    private int getWaitTicks() {
        return SoundAttractConfig.COMMON.scanCooldownTicks.get();
    }

    private PlayerStance determinePlayerStance(LivingEntity player) {
        if (player.getPose() == Pose.SWIMMING || player.getPose() == Pose.FALL_FLYING || player.getPose() == Pose.SPIN_ATTACK) {
            if (player.getBbHeight() < 1.0F) {
                return PlayerStance.CRAWLING;
            }
        }
        if (player.isCrouching()) {
            return PlayerStance.SNEAKING;
        }
        return PlayerStance.STANDING;
    }

    private double getDetectionRangeForPlayer(LivingEntity player) {
        com.example.soundattract.config.MobProfile mobProfile = SoundAttractConfig.getMatchingProfile(this.mob);
        PlayerStance currentStance = determinePlayerStance(player);

        double baseRange;
        Optional<Double> override = Optional.empty();

        if (mobProfile != null) {
            override = mobProfile.getDetectionOverride(currentStance);
        }

        if (override.isPresent()) {
            baseRange = override.get();
        } else {
            switch (currentStance) {
                case CRAWLING:
                    baseRange = SoundAttractConfig.COMMON.crawlingDetectionRangePlayer.get();
                    break;
                case SNEAKING:
                    baseRange = SoundAttractConfig.COMMON.sneakingDetectionRangePlayer.get();
                    break;
                case STANDING:
                default:
                    baseRange = SoundAttractConfig.COMMON.standingDetectionRangePlayer.get();
                    break;
            }
        }

        boolean hasCamouflage = false;
        int wornCamouflagePieces = 0;
        if (SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (!slot.isArmor()) continue;
                ItemStack armorItem = player.getItemBySlot(slot);
                if (!armorItem.isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(armorItem.getItem()).toString();
                    if (SoundAttractConfig.COMMON.camouflageArmorItems.get().contains(itemId)) {
                        wornCamouflagePieces++;
                    }
                }
            }
            if (SoundAttractConfig.COMMON.requireFullSetForCamouflageBonus.get()) {
                hasCamouflage = wornCamouflagePieces == 4;
            } else {
                hasCamouflage = wornCamouflagePieces > 0;
            }
        }

        if (hasCamouflage) {
            double totalEffectiveness = 0.0;
            List<ItemStack> armorItems = new ArrayList<>();
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (slot.isArmor()) {
                    armorItems.add(player.getItemBySlot(slot));
                }
            }
            for (int i = 0; i < armorItems.size(); i++) {
                ItemStack stack = armorItems.get(i);
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                if (itemId != null && SoundAttractConfig.COMMON.camouflageArmorItems.get().contains(itemId.toString())) {
                    switch (i) {
                        case 3:
                            totalEffectiveness += SoundAttractConfig.COMMON.helmetCamouflageEffectiveness.get();
                            break;
                        case 2:
                            totalEffectiveness += SoundAttractConfig.COMMON.chestplateCamouflageEffectiveness.get();
                            break;
                        case 1:
                            totalEffectiveness += SoundAttractConfig.COMMON.leggingsCamouflageEffectiveness.get();
                            break;
                        case 0:
                            totalEffectiveness += SoundAttractConfig.COMMON.bootsCamouflageEffectiveness.get();
                            break;
                    }
                }
            }
            baseRange *= Math.max(0.0, 1.0 - totalEffectiveness);
        }

        return Math.max(0.0, baseRange);
    }

    private boolean shouldSuppressTargeting() {
        return SoundAttractConfig.COMMON.enableStealthMechanics.get()
            && com.example.soundattract.StealthDetectionEvents.shouldSuppressTargeting(this.mob);
    }

    @Override
    public boolean canUse() {
        if (!isMobEligible() || this.mob.isVehicle() || this.mob.isSleeping() || shouldSuppressTargeting()) {
            return false;
        }


        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
            Mob leader = MobGroupManager.getLeader(mob);
            boolean isDeserter = MobGroupManager.isDeserter(mob);
            if (leader == mob && !isDeserter) {
                return false; 
            }
            if (leader != mob) {
                return false;
            }
        }

        if (scanCooldownCounter > 0) {
            scanCooldownCounter--;
            return false;
        }
        scanCooldownCounter = scanCooldownTicks();


        SoundTracker.SoundRecord newSound = getCachedNearestSound();
        if (newSound == null) return false;

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[AttractionGoal] Mob {} canUse sound: id={}, pos={}, range={}, weight={}", mob.getName().getString(), newSound.soundId, newSound.pos, String.format("%.2f", newSound.range), String.format("%.2f", newSound.weight));
        }
        
        this.targetSoundPos = newSound.pos;
        this.currentTargetWeight = newSound.weight;
        this.cachedSound = newSound; 
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!isMobEligible() || this.mob.isVehicle() || this.mob.isSleeping() || shouldSuppressTargeting()) {
            return false;
        }
        if (SoundAttractConfig.COMMON.edgeMobSmartBehavior.get()) {
            Mob leader = MobGroupManager.getLeader(mob);
            boolean isDeserter = MobGroupManager.isDeserter(mob);
            if (leader == mob && !isDeserter) {
                return false;
            }
            if (leader != mob) {
                return false;
            }
        }
        if (targetSoundPos == null) {
            return false;
        }

        double arrivalDistSq = getArrivalDistance() * getArrivalDistance();
        double stopRangeSq = Math.max(4.0D, arrivalDistSq);
        BlockPos navTarget = getNavigableTarget(this.targetSoundPos);
        if (navTarget != null) {
            double dx = this.mob.getX() - (navTarget.getX() + 0.5);
            double dz = this.mob.getZ() - (navTarget.getZ() + 0.5);
            double horizontalDistSq = dx * dx + dz * dz;
            int dy = Math.abs(this.mob.blockPosition().getY() - navTarget.getY());
            if ((dy > 3 && horizontalDistSq <= stopRangeSq)
                || this.mob.position().distanceToSqr(Vec3.atCenterOf(navTarget)) <= stopRangeSq) {
                return false;
            }
        }
        if (this.mob.getNavigation().isDone() && this.mob.blockPosition().distSqr(this.targetSoundPos) <= stopRangeSq) {
            return false;
        }

        Mob leader = MobGroupManager.getLeader(mob);
        if (leader != mob && SoundAttractConfig.COMMON.edgeMobSmartBehavior.get() && mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos)) < getArrivalDistance() * getArrivalDistance()) {
            return false;
        }

        SoundTracker.SoundRecord bestSoundNow = getCachedNearestSound();
        if (bestSoundNow == null) {
            return false;
        }
        
        if (bestSoundNow.pos.equals(this.targetSoundPos)) {
            this.cachedSound = bestSoundNow;
            return true;
        }

        double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
        if (bestSoundNow.weight > this.currentTargetWeight * switchRatio) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[AttractionGoal] Mob {} switching from target (w:{}) to new sound (w:{})", mob.getName().getString(), String.format("%.2f", this.currentTargetWeight), String.format("%.2f", bestSoundNow.weight));
            }
            return false;
        }

        return true;
    }
    
    @Override
    public void start() {
        if (this.targetSoundPos != null) {
            BlockPos navTarget = getNavigableTarget(this.targetSoundPos);
            BlockPos dest = navTarget != null ? navTarget : this.targetSoundPos;
            this.mob.getNavigation().moveTo(dest.getX(), dest.getY(), dest.getZ(), this.moveSpeed);
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.targetSoundPos = null;
        this.currentTargetWeight = -1.0;
        this.cachedSound = null;
        this.isPursuingSound = false;
        this.pursuingSoundTicksRemaining = 0;
        
        this.edgeMobState = null;
        this.foundPlayerOrHit = false;
        this.relayedToLeader = false;
        this.edgeArrivalTicks = 0;

        this.chosenDest = null;
        this.hasPicked = false;
        this.lastMoveToTick = -1L;
        this.lastMoveToTarget = null;
        
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[AttractionGoal] stop: {}", mob.getName().getString());
        }
    }
    @Override
    public void tick() {
        if (scanCooldownCounter > 0) {
            scanCooldownCounter--;
        }
        SoundTracker.SoundRecord freshSound = getCachedNearestSound();
        boolean shouldSwitch = false;
        if (freshSound != null) {
            if (this.cachedSound == null) {
                shouldSwitch = true;
            } else {
                double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
                double freshWeight = freshSound.weight;
                double currentWeight = this.cachedSound.weight;
                if (freshWeight > currentWeight * switchRatio) {
                    shouldSwitch = true;
                }
            }
        }
        if (shouldSwitch) {
            this.cachedSound = freshSound;
            this.targetSoundPos = freshSound.pos;
            this.currentTargetWeight = freshSound.weight; 
            this.hasPicked = false;
            this.chosenDest = null;
            this.edgeMobState = null;
            this.foundPlayerOrHit = false;
            this.relayedToLeader = false;
            this.mob.getNavigation().stop();
        } else if (freshSound == null) {
            this.cachedSound = null;
            this.targetSoundPos = null;
        }
        if (targetSoundPos == null) {
            return;
        }

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        Mob leader = MobGroupManager.getLeader(mob);

        BlockPos navTarget = getNavigableTarget(targetSoundPos);

        if (navTarget != null && mob.position().distanceToSqr(Vec3.atCenterOf(navTarget)) < getArrivalDistance() * getArrivalDistance()) {
            this.mob.getNavigation().stop();
            return;
        }

        if (lastPos != null && mob.position().distanceToSqr(Vec3.atCenterOf(lastPos)) < 1.0) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPos = mob.blockPosition();
        }


        if (SoundAttractConfig.COMMON.enableBlockBreaking.get()
            && targetSoundPos != null
            && (stuckTicks >= STUCK_THRESHOLD || mob.getNavigation().isStuck())) {

            BlockPos blocking = BlockBreakerPosGoal.findFirstBlockingBlock(mob.level(), mob, targetSoundPos);
            boolean hasBreaker = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof BlockBreakerPosGoal);
            if (blocking != null && !hasBreaker) {
                double mult = SoundAttractConfig.COMMON.blockBreakTimeMultiplier.get();
                boolean toolOnly = SoundAttractConfig.COMMON.blockBreakToolOnly.get();
                boolean properOnly = SoundAttractConfig.COMMON.blockBreakProperToolOnly.get();
                boolean properReq = SoundAttractConfig.COMMON.blockBreakProperToolRequired.get();
                BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(mob, targetSoundPos, mult, toolOnly, properOnly, properReq);
                BlockBreakerManager.scheduleAdd(mob, breaker, 2);
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[AttractionGoal] {} stuck: scheduled BlockBreakerPosGoal toward {}.", mob.getName().getString(), targetSoundPos);
                }
            }
        }

        if (!SoundAttractConfig.COMMON.enableBlockBreaking.get()
            && stuckTicks >= STUCK_THRESHOLD
            && (mob.getNavigation().isDone() || mob.getNavigation().isStuck())) {

            double arrivalDistSq2 = getArrivalDistance() * getArrivalDistance();
            double stopRangeSq2 = Math.max(4.0D, arrivalDistSq2);
            double distSqToNavTarget = navTarget != null
                ? mob.position().distanceToSqr(Vec3.atCenterOf(navTarget))
                : Double.MAX_VALUE;

            if (distSqToNavTarget <= stopRangeSq2) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                        "[AttractionGoal] {} stuck near target (distSq={}). Treating as arrived to avoid spinning.",
                        mob.getName().getString(),
                        String.format("%.2f", distSqToNavTarget)
                    );
                }
                targetSoundPos = null;
                this.mob.getNavigation().stop();
                this.scanCooldownCounter = scanCooldownTicks();
                return;
            }

            if (distSqToNavTarget > stopRangeSq2) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                        "[AttractionGoal] {} stuck near ledge, far from target (distSq={}). Giving up to avoid spinning.",
                        mob.getName().getString(),
                        String.format("%.2f", distSqToNavTarget)
                    );
                }
                targetSoundPos = null;
                this.mob.getNavigation().stop();
                return;
            }
        }

        SoundTracker.SoundRecord currentPursuedSound = this.cachedSound;
        if (currentPursuedSound == null || !currentPursuedSound.pos.equals(this.targetSoundPos)) {
            currentPursuedSound = null;
        }

        if (currentPursuedSound == null || currentPursuedSound.ticksRemaining <= 0) {
            targetSoundPos = null;
            return;
        }

        if (leader == mob) {
            if (!hasPicked) {
                double arrivalDist = getArrivalDistance();
                net.minecraft.util.RandomSource rand = this.mob.getRandom();
                double angle = rand.nextDouble() * (Math.PI * 2.0);
                double radius = arrivalDist * Math.sqrt(rand.nextDouble());
                double offsetX = Math.cos(angle) * radius;
                double offsetZ = Math.sin(angle) * radius;
                int blockX = targetSoundPos.getX() + (int) Math.floor(offsetX);
                int blockZ = targetSoundPos.getZ() + (int) Math.floor(offsetZ);
                int groundY = mob.level().getHeight(
                    Heightmap.Types.MOTION_BLOCKING,
                    blockX,
                    blockZ
                );
                double finalX = blockX + 0.5;
                double finalY = groundY;
                double finalZ = blockZ + 0.5;
                chosenDest = new Vec3(finalX, finalY, finalZ);
                hasPicked = true;
            }

            if (chosenDest != null) {
                Vec3 cur = mob.position();
                if (cur.distanceToSqr(chosenDest) > 1.5 * 1.5) {
                    mob.getNavigation().moveTo(
                        chosenDest.x,
                        chosenDest.y,
                        chosenDest.z,
                        moveSpeed
                    );
                }
                if (mob.getNavigation().isDone()) {
                    hasPicked = false;
                    chosenDest = null;
                }
            }
            return;
        }

        List<MobGroupManager.SoundRelay> relays = MobGroupManager.consumeRelayedSounds(mob);
        if (relays != null && !relays.isEmpty()) {
            for (MobGroupManager.SoundRelay relay : relays) {
                java.util.Optional<net.minecraft.sounds.SoundEvent> relayEventOpt = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(relay.soundId));
                SoundTracker.SoundRecord relayedSoundRecord = new SoundTracker.SoundRecord(
                    relayEventOpt.orElse(null),
                    relay.soundId,
                    BlockPos.containing(relay.x, relay.y, relay.z),
                    20,
                    SoundTracker.getDimensionKeyString(mob.level()),
                    relay.range,
                    relay.weight
                );
                if (cachedSound == null
                    || relayedSoundRecord.weight > cachedSound.weight * SoundAttractConfig.COMMON.soundSwitchRatio.get()
                    || (
                        Math.abs(relayedSoundRecord.weight - cachedSound.weight) < 0.001
                        && relayedSoundRecord.pos.distSqr(mob.blockPosition())
                        < cachedSound.pos.distSqr(mob.blockPosition())
                    )
                ) {
                    cachedSound = relayedSoundRecord;
                    targetSoundPos = cachedSound.pos;
                    currentTargetWeight = cachedSound.weight;
                    BlockPos relayNavTarget = getNavigableTarget(targetSoundPos);
                    BlockPos dest = relayNavTarget != null ? relayNavTarget : targetSoundPos;
                    mob.getNavigation().moveTo(
                        dest.getX(),
                        dest.getY(),
                        dest.getZ(),
                        this.moveSpeed
                    );
                    pursuingSoundTicksRemaining = relayedSoundRecord.ticksRemaining > 0
                        ? relayedSoundRecord.ticksRemaining
                        : DynamicScanCooldownManager.currentScanCooldownTicks;
                }
            }
        }

        if (leader != null && smartEdge && !hasFollowerEdgeRelayGoal()) {
            if (edgeMobState == null) {
                edgeMobState = EdgeMobState.GOING_TO_SOUND;
                moveToThrottled(navTarget, this.moveSpeed);
            }

            if (edgeMobState == EdgeMobState.GOING_TO_SOUND) {
                moveToThrottled(navTarget, this.moveSpeed);
                if (navTarget != null && mob.position().distanceToSqr(Vec3.atCenterOf(navTarget))
                    < getArrivalDistance() * getArrivalDistance()
                ) {
                    edgeArrivalTicks++;
                    if (edgeArrivalTicks >= EDGE_WAIT_TICKS || foundPlayerOrHit) {
                        if (!relayedToLeader && foundPlayerOrHit) {
                            SoundTracker.SoundRecord soundToRelay = this.cachedSound;
                            if (soundToRelay != null
                                && soundToRelay.pos.equals(targetSoundPos)
                                && soundToRelay.ticksRemaining > 0
                                && soundToRelay.soundId != null
                            ) {
                                MobGroupManager.relaySoundToLeader(
                                    this.mob,
                                    soundToRelay.soundId,
                                    soundToRelay.pos.getX(),
                                    soundToRelay.pos.getY(),
                                    soundToRelay.pos.getZ(),
                                    soundToRelay.range,
                                    soundToRelay.weight,
                                    this.mob.level().getGameTime()
                                );
                                relayedToLeader = true;
                            }
                        }
                        edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                        edgeArrivalTicks = 0;
                    }
                } else {
                    LivingEntity targetPlayer = this.mob.getTarget();
                    if (targetPlayer instanceof net.minecraft.world.entity.player.Player
                        && targetPlayer.distanceToSqr(this.mob)
                        < Math.pow(StealthDetectionEvents.getRealisticStealthDetectionRange((net.minecraft.world.entity.player.Player) targetPlayer, this.mob, this.mob.level()), 2)
                    ) {
                        foundPlayerOrHit = true;
                    }
                }
            } else if (edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {
                if (leader != null && !leader.isRemoved() && leader.isAlive()) {
                    moveToThrottled(leader.blockPosition(), this.moveSpeed * 0.8);
                    if (mob.distanceToSqr(leader)
                        < (getArrivalDistance() + 2.0) * (getArrivalDistance() + 2.0)
                    ) {
                        targetSoundPos = null;
                        edgeMobState = null;
                        return;
                    }
                } else {
                    targetSoundPos = null;
                    edgeMobState = null;
                    return;
                }
            }
        } else {
            BlockPos center = navTarget != null ? navTarget : this.targetSoundPos;
            if (center != null) {
                if (!hasPicked) {
                    double arrivalDist = getArrivalDistance();
                    net.minecraft.util.RandomSource rand = this.mob.getRandom();
                    double angle = rand.nextDouble() * (Math.PI * 2.0);
                    double radius = arrivalDist * Math.sqrt(rand.nextDouble());
                    double offsetX = Math.cos(angle) * radius;
                    double offsetZ = Math.sin(angle) * radius;
                    int blockX = center.getX() + (int) Math.floor(offsetX);
                    int blockZ = center.getZ() + (int) Math.floor(offsetZ);
                    int groundY = mob.level().getHeight(
                        Heightmap.Types.MOTION_BLOCKING,
                        blockX,
                        blockZ
                    );
                    double finalX = blockX + 0.5;
                    double finalY = groundY;
                    double finalZ = blockZ + 0.5;
                    chosenDest = new Vec3(finalX, finalY, finalZ);
                    hasPicked = true;
                }

                if (chosenDest != null) {
                    Vec3 cur = mob.position();
                    if (cur.distanceToSqr(chosenDest) > 1.5 * 1.5) {
                        mob.getNavigation().moveTo(
                            chosenDest.x,
                            chosenDest.y,
                            chosenDest.z,
                            moveSpeed
                        );
                    }
                    if (mob.getNavigation().isDone()) {
                        hasPicked = false;
                        chosenDest = null;
                    }
                }
            }
        }

        if (isPursuingSound && this.cachedSound != null) {
            if (pursuingSoundTicksRemaining > 0) {
                pursuingSoundTicksRemaining--;
                if (pursuingSoundTicksRemaining <= 0) {
                    isPursuingSound = false;
                }
            }
        } else if (!isPursuingSound) {
            pursuingSoundTicksRemaining = 0;
        }

        if (mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos))
            < getArrivalDistance() * getArrivalDistance()
        ) {
            this.scanCooldownCounter = scanCooldownTicks();
        }
    }

    private SoundTracker.SoundRecord findInterestingSoundRecord() {
        Level level = this.mob.level();
        if (level.isClientSide()) return null;

        BlockPos mobPos = this.mob.blockPosition();
        Vec3 mobEyePos = this.mob.getEyePosition(1.0F);
        Mob leader = MobGroupManager.getLeader(this.mob);

        SoundTracker.SoundRecord bestSoundOverall = null;

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        if (smartEdge && leader == this.mob) {
            List<MobGroupManager.SoundRelay> relays = MobGroupManager.consumeRelayedSounds(this.mob);
            if (relays != null) {
                for (MobGroupManager.SoundRelay relay : relays) {
                    SoundTracker.SoundRecord relayedSound = new SoundTracker.SoundRecord(
                        null,
                        relay.soundId,
                        new BlockPos((int) relay.x, (int) relay.y, (int) relay.z),
                        200,
                        SoundTracker.getDimensionKeyString(level),
                        relay.range,
                        relay.weight
                    );
                    if (bestSoundOverall == null
                        || relayedSound.weight > bestSoundOverall.weight
                        || (Math.abs(relayedSound.weight - bestSoundOverall.weight) < 0.001
                            && relayedSound.range > bestSoundOverall.range)
                    ) {
                        bestSoundOverall = relayedSound;
                    }
                }
            }
        }

        if (bestSoundOverall == null) {
            bestSoundOverall = SoundTracker.findNearestSound(this.mob, level, mobPos, mobEyePos);
        }

        SoundTracker.SoundRecord currentTargetSound = this.cachedSound;
        if (currentTargetSound != null && bestSoundOverall != null
            && !areSoundsEffectivelySame(currentTargetSound, bestSoundOverall)
        ) {
            double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
            boolean canSwitch = bestSoundOverall.weight > currentTargetSound.weight * switchRatio
                || (Math.abs(bestSoundOverall.weight - currentTargetSound.weight) < 0.001
                    && bestSoundOverall.pos.distSqr(mobPos)
                       < currentTargetSound.pos.distSqr(mobPos));
            if (!canSwitch) {
                return currentTargetSound;
            }
        }

        if (bestSoundOverall != null) {
            this.isPursuingSound = true;
            this.pursuingSoundTicksRemaining = DynamicScanCooldownManager.currentScanCooldownTicks;
        } else {
            if (leader == this.mob) {
                this.isPursuingSound = false;
                this.pursuingSoundTicksRemaining = 0;
            } else if (leader != this.mob && !this.isPursuingSound) {
                this.isPursuingSound = false;
                this.pursuingSoundTicksRemaining = 0;
            }
        }
        this.cachedSound = bestSoundOverall;
        return bestSoundOverall;
    }

    private boolean areSoundsEffectivelySame(SoundTracker.SoundRecord s1, SoundTracker.SoundRecord s2) {
        if (s1 == null || s2 == null) return s1 == s2;
        return s1.pos.equals(s2.pos)
            && (s1.soundId != null && s1.soundId.equals(s2.soundId))
            && Math.abs(s1.range - s2.range) < 0.1
            && Math.abs(s1.weight - s2.weight) < 0.01;
    }

    private boolean isPlayerMovementSound(double weight) {
        return weight == 1.2 || weight == 0.6 || weight == 0.2 || weight == 0.1;
    }

    public boolean isPursuingSound() {
        return isPursuingSound;
    }

    /**
     * Gets the nearest sound, cached for the current game tick.
     * This prevents multiple expensive lookups within the same tick.
     */
    private SoundTracker.SoundRecord getCachedNearestSound() {
        long currentTick = this.mob.level().getGameTime();
        if (this.lastSoundCheckTick != currentTick) {
            this.lastSoundCheckTick = currentTick;
            this.cachedNearestSoundForTick = findInterestingSoundRecord();
        }
        return this.cachedNearestSoundForTick;
    }

    private boolean hasFollowerEdgeRelayGoal() {
        return this.mob.goalSelector.getAvailableGoals().stream()
            .anyMatch(w -> w.getGoal() instanceof com.example.soundattract.ai.FollowerEdgeRelayGoal);
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

    private void moveToThrottled(BlockPos dest, double speed) {
        moveToThrottled(dest, speed, false);
    }

    private void moveToThrottled(BlockPos dest, double speed, boolean force) {
        if (dest == null) return;
        long nowTick = this.mob.level().getGameTime();
        boolean targetChanged = this.lastMoveToTarget == null || !this.lastMoveToTarget.equals(dest);
        boolean cooldownElapsed = this.lastMoveToTick < 0 || (nowTick - this.lastMoveToTick) >= MOVE_TO_COOLDOWN_TICKS;
        boolean navDoneOrStuck = this.mob.getNavigation().isDone() || this.mob.getNavigation().isStuck();
        if (force || targetChanged || navDoneOrStuck || cooldownElapsed) {
            this.mob.getNavigation().moveTo(dest.getX(), dest.getY(), dest.getZ(), speed);
            this.lastMoveToTick = nowTick;
            this.lastMoveToTarget = dest;
        }
    }
}
