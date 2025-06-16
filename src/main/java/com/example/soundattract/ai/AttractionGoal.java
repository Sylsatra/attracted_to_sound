package com.example.soundattract.ai;

import com.example.soundattract.DynamicScanCooldownManager;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
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
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.registries.ForgeRegistries;

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

    private double getArrivalDistance() {
        return SoundAttractConfig.COMMON.arrivalDistance.get();
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
            for (ItemStack armorItem : player.getArmorSlots()) {
                if (!armorItem.isEmpty()) {
                    String itemId = ForgeRegistries.ITEMS.getKey(armorItem.getItem()).toString();
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
            player.getArmorSlots().forEach(armorItems::add);
            for (int i = 0; i < armorItems.size(); i++) {
                ItemStack stack = armorItems.get(i);
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
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
        if (this.mob.isVehicle() || this.mob.isSleeping() || shouldSuppressTargeting()) {
            return false;
        }

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        Mob leader = MobGroupManager.getLeader(mob);

        if (scanCooldownCounter > 0) {
            scanCooldownCounter--;
            return false;
        }
        scanCooldownCounter = scanCooldownTicks();

        if (leader != mob && smartEdge) {
            SoundTracker.SoundRecord directSound = SoundTracker.findNearestSound(
                this.mob,
                this.mob.level(),
                this.mob.blockPosition(),
                this.mob.getEyePosition()
            );
            List<MobGroupManager.SoundRelay> relayedSounds = MobGroupManager.consumeRelayedSounds(this.mob);
            boolean hasDirectSound = directSound != null;
            boolean hasRelayedSound = relayedSounds != null && !relayedSounds.isEmpty();

            if (!hasDirectSound && !hasRelayedSound) {
                SoundAttractMod.LOGGER.info(
                    "AttractionGoal canUse: Non-leader " + mob.getName().getString()
                    + " has no direct or relayed sound in smart edge mode."
                );
                return false;
            }
        }

        SoundTracker.SoundRecord newSound = findInterestingSoundRecord();
        if (newSound == null) return false;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                "[AttractionGoal] Mob {} found sound: pos={}, range={}, weight={}",
                mob.getName().getString(),
                newSound.pos,
                newSound.range,
                newSound.weight
            );
        }
        this.targetSoundPos = newSound.pos;
        this.currentTargetWeight = newSound.weight;
        this.lastSoundTicksRemaining = newSound.ticksRemaining;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.isVehicle() || mob.isSleeping() || shouldSuppressTargeting()) return false;
        if (targetSoundPos == null || mob.getNavigation().isDone()) return false;

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        Mob leader = MobGroupManager.getLeader(mob);
        if (leader != mob && smartEdge
            && mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos))
               < getArrivalDistance() * getArrivalDistance()
        ) {
            return false;
        }

        SoundTracker.SoundRecord live = SoundTracker.findNearestSound(
            mob,
            mob.level(),
            mob.blockPosition(),
            mob.getEyePosition()
        );

        if (live == null || !live.pos.equals(targetSoundPos)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "[AttractionGoal] canContinueToUse: live sound vanished for {} (target {})",
                    mob.getName().getString(),
                    targetSoundPos
                );
            }
            return false;
        }

        cachedSound = live;
        return true;
    }

    @Override
    public void stop() {
        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        Mob leader = MobGroupManager.getLeader(mob);

        this.mob.getNavigation().stop();
        this.targetSoundPos = null;
        this.currentTargetWeight = -1.0;
        this.cachedSound = null;
        this.isPursuingSound = false;
        this.pursuingSoundTicksRemaining = 0;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("AttractionGoal stop: " + mob.getName().getString());
        }

        if (leader != mob && smartEdge && edgeMobState == EdgeMobState.GOING_TO_SOUND) {
            edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "Follower " + mob.getName().getString() + " transitioning to RETURNING_TO_LEADER."
                );
            }
            edgeArrivalTicks = 0;
        } else {
            edgeMobState = null;
        }
        foundPlayerOrHit = false;
        relayedToLeader = false;
        edgeArrivalTicks = 0;
    }

    @Override
    public void tick() {
        SoundTracker.SoundRecord fresh = findInterestingSoundRecord();
        double switchRatio = SoundAttractConfig.COMMON.soundSwitchRatio.get();
        if (fresh != null) {
            boolean isBetterByWeight = fresh.weight > this.currentTargetWeight * switchRatio;
            boolean isTieButCloser = false;
            if (!isBetterByWeight && this.targetSoundPos != null && Math.abs(fresh.weight - this.currentTargetWeight) < 0.001) {
                double freshDistSq = fresh.pos.distSqr(this.mob.blockPosition());
                double currentDistSq = this.targetSoundPos.distSqr(this.mob.blockPosition());
                isTieButCloser = freshDistSq < currentDistSq;
            }
            if (isBetterByWeight || isTieButCloser) {
                this.targetSoundPos = fresh.pos;
                this.cachedSound = fresh;
                this.currentTargetWeight = fresh.weight;
                this.hasPicked = false;
                this.chosenDest = null;
                this.edgeMobState = null;
                this.foundPlayerOrHit = false;
                this.relayedToLeader = false;
                this.mob.getNavigation().stop();
            }
        }
        if (targetSoundPos == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "AttractionGoal tick: targetSoundPos is null for "
                    + mob.getName().getString() + ", goal will likely stop."
                );
            }
            return;
        }

        if (scanCooldownCounter > 0) {
            scanCooldownCounter--;
        }

        boolean smartEdge = SoundAttractConfig.COMMON.edgeMobSmartBehavior.get();
        Mob leader = MobGroupManager.getLeader(mob);

        if (lastPos != null && lastPos.equals(mob.blockPosition())) {
            stuckTicks++;
            if (stuckTicks >= RECALC_THRESHOLD && !mob.getNavigation().isStuck()) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                        "AttractionGoal tick: " + mob.getName().getString()
                        + " stuck for " + stuckTicks + " ticks at " + lastPos
                        + ", recalculating path to " + targetSoundPos
                    );
                }
                mob.getNavigation().moveTo(
                    targetSoundPos.getX(),
                    targetSoundPos.getY(),
                    targetSoundPos.getZ(),
                    moveSpeed
                );
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
            lastPos = mob.blockPosition();
        }

        SoundTracker.SoundRecord currentPursuedSound = this.cachedSound;
        if (currentPursuedSound == null || !currentPursuedSound.pos.equals(this.targetSoundPos)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "AttractionGoal tick: Cached sound mismatch for {} during expiration check. Target: {}, Cached: {}",
                    mob.getName().getString(),
                    targetSoundPos,
                    (currentPursuedSound != null ? currentPursuedSound.pos : "null")
                );
            }
            currentPursuedSound = null;
        }

        if (currentPursuedSound == null || currentPursuedSound.ticksRemaining <= 0) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "AttractionGoal tick: Sound at " + targetSoundPos
                    + " (from cache) expired or removed for " + mob.getName().getString()
                );
            }
            targetSoundPos = null;
            return;
        }

        if (leader == mob) {
            if (!hasPicked) {
                double arrivalDist = getArrivalDistance();
                long seed = mob.getUUID().getMostSignificantBits()
                          ^ mob.getUUID().getLeastSignificantBits()
                          ^ targetSoundPos.hashCode();
                Random rand = new Random(seed);
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
                SoundEvent relayEvent = ForgeRegistries.SOUND_EVENTS.getValue(
                    ResourceLocation.parse(relay.soundId)
                );
                SoundTracker.SoundRecord relayedSoundRecord = new SoundTracker.SoundRecord(
                    relayEvent,
                    new BlockPos((int) relay.x, (int) relay.y, (int) relay.z),
                    20,
                    mob.level().dimension().location().toString(),
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
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                            "Leader " + mob.getName().getString()
                            + " switching to relayed sound (ID: " + relay.soundId
                            + ") from follower at " + relayedSoundRecord.pos
                            + " (w:" + relayedSoundRecord.weight
                            + ", r:" + relayedSoundRecord.range + ")"
                        );
                    }
                    cachedSound = relayedSoundRecord;
                    targetSoundPos = cachedSound.pos;
                    currentTargetWeight = cachedSound.weight;
                    mob.getNavigation().moveTo(
                        targetSoundPos.getX(),
                        targetSoundPos.getY(),
                        targetSoundPos.getZ(),
                        this.moveSpeed
                    );
                    pursuingSoundTicksRemaining = relayedSoundRecord.ticksRemaining > 0
                        ? relayedSoundRecord.ticksRemaining
                        : DynamicScanCooldownManager.currentScanCooldownTicks;
                }
            }
        }

        if (leader != null && smartEdge) {
            if (edgeMobState == null) {
                edgeMobState = EdgeMobState.GOING_TO_SOUND;
                mob.getNavigation().moveTo(
                    targetSoundPos.getX(),
                    targetSoundPos.getY(),
                    targetSoundPos.getZ(),
                    this.moveSpeed
                );
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                        "Follower " + mob.getName().getString()
                        + " (leader: " + leader.getName().getString()
                        + ") starting to move to sound at " + targetSoundPos
                        + " (EdgeMobState: GOING_TO_SOUND)"
                    );
                }
            }

            if (edgeMobState == EdgeMobState.GOING_TO_SOUND) {
                mob.getNavigation().moveTo(
                    targetSoundPos.getX(),
                    targetSoundPos.getY(),
                    targetSoundPos.getZ(),
                    this.moveSpeed
                );
                if (mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos))
                    < getArrivalDistance() * getArrivalDistance()
                ) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                            "Follower " + mob.getName().getString()
                            + " arrived at sound location " + targetSoundPos
                            + ". Waiting for " + EDGE_WAIT_TICKS + " ticks."
                        );
                    }
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
                                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                    SoundAttractMod.LOGGER.info(
                                        "Follower " + mob.getName().getString()
                                        + " relayed 'foundPlayerOrHit' event (Sound ID: "
                                        + soundToRelay.soundId + ") to leader "
                                        + leader.getName().getString()
                                        + " for sound at " + targetSoundPos
                                    );
                                }
                                relayedToLeader = true;
                            }
                        }
                        edgeMobState = EdgeMobState.RETURNING_TO_LEADER;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                "Follower " + mob.getName().getString()
                                + " transitioning to RETURNING_TO_LEADER."
                            );
                        }
                        edgeArrivalTicks = 0;
                    }
                } else {
                    LivingEntity targetPlayer = this.mob.getTarget();
                    if (targetPlayer instanceof net.minecraft.world.entity.player.Player
                        && targetPlayer.distanceToSqr(this.mob)
                           < getDetectionRangeForPlayer(targetPlayer)
                             * getDetectionRangeForPlayer(targetPlayer)
                    ) {
                        foundPlayerOrHit = true;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                "Follower " + mob.getName().getString()
                                + " detected player " + targetPlayer.getName().getString()
                                + " en route to sound."
                            );
                        }
                    }
                }
            } else if (edgeMobState == EdgeMobState.RETURNING_TO_LEADER) {
                if (leader != null && !leader.isRemoved() && !leader.isDeadOrDying()) {
                    mob.getNavigation().moveTo(
                        leader.getX(),
                        leader.getY(),
                        leader.getZ(),
                        this.moveSpeed * 0.8
                    );
                    if (mob.distanceToSqr(leader)
                        < (getArrivalDistance() + 2.0) * (getArrivalDistance() + 2.0)
                    ) {
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                "Follower " + mob.getName().getString()
                                + " reached leader " + leader.getName().getString()
                                + ". Stopping attraction goal (will re-evaluate)."
                            );
                        }
                        targetSoundPos = null;
                        edgeMobState = null;
                    }
                } else {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                            "Follower " + mob.getName().getString()
                            + " lost its leader while returning. Stopping attraction goal."
                        );
                    }
                    targetSoundPos = null;
                    edgeMobState = null;
                }
            }
        } else {
            mob.getNavigation().moveTo(
                targetSoundPos.getX(),
                targetSoundPos.getY(),
                targetSoundPos.getZ(),
                this.moveSpeed
            );
        }

        if (isPursuingSound && this.cachedSound != null) {
            if (pursuingSoundTicksRemaining > 0) {
                pursuingSoundTicksRemaining--;
                if (pursuingSoundTicksRemaining <= 0) {
                    isPursuingSound = false;
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info(
                            "AttractionGoal tick: " + mob.getName().getString()
                            + " pursuit timer expired. Will re-evaluate sounds."
                        );
                    }
                }
            }
        } else if (!isPursuingSound) {
            pursuingSoundTicksRemaining = 0;
        }

        if (mob.position().distanceToSqr(Vec3.atCenterOf(targetSoundPos))
            < getArrivalDistance() * getArrivalDistance()
        ) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "AttractionGoal tick: " + mob.getName().getString()
                    + " arrived at " + targetSoundPos
                    + ". Scan cooldown will apply if it re-evaluates canUse."
                );
            }
        }
    }

    protected SoundTracker.SoundRecord findInterestingSoundRecord() {
        Level level = this.mob.level();
        if (level.isClientSide()) return null;

        BlockPos mobPos = this.mob.blockPosition();
        Vec3 mobEyePos = this.mob.getEyePosition(1.0F);
        Mob leader = MobGroupManager.getLeader(this.mob);

        SoundTracker.SoundRecord bestSoundOverall = SoundTracker.findNearestSound(
            this.mob,
            level,
            mobPos,
            mobEyePos
        );

        if (leader == this.mob) {
            List<MobGroupManager.SoundRelay> relays = MobGroupManager.consumeRelayedSounds(this.mob);
            if (relays != null) {
                for (MobGroupManager.SoundRelay relay : relays) {
                    SoundTracker.SoundRecord relayedSound = new SoundTracker.SoundRecord(
                        null,
                        relay.soundId,
                        new BlockPos((int) relay.x, (int) relay.y, (int) relay.z),
                        20,
                        level.dimension().location().toString(),
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
}
