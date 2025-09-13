package com.example.soundattract.ai;

import com.example.soundattract.FovEvents;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.StealthDetectionEvents;
import com.example.soundattract.StealthUtils;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

/**
 * Follower edge/deserter investigation and relay goal.
 * - Edge/deserter followers investigate a promising sound.
 * - If they detect a player at/near the sound, they return to leader and relay it.
 * - Raid is scheduled on relay.
 */
public class FollowerEdgeRelayGoal extends Goal {
    private final MobEntity mob;
    private final double moveSpeed;

    private enum EdgeState { GOING_TO_SOUND, RETURNING_TO_LEADER }

    private EdgeState state = null;
    private SoundTracker.SoundRecord cachedSound;
    private BlockPos targetPos;
    private boolean foundPlayerOrHit;
    private boolean relayed;
    private int arrivalTicks;
    private int pursueTicksRemaining;


    private MobEntity cachedLeader = null;


    private BlockPos lastIssuedNavTarget = null;
    private int repathCooldown = 0;
    private static final int REPATH_COOLDOWN_TICKS = 6;
    private Vec3d lastPosVec = null;
    private int stuckTicks = 0;
    private BlockBreakerPosGoal blockBreakerGoal = null;
    private int debugTick = 0;

    public FollowerEdgeRelayGoal(MobEntity mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (mob.hasVehicle() || mob.isSleeping() || mob.getTarget() != null) return false;

        if (MobGroupManager.getLeader(mob) == mob) return false;

        boolean isEdge = MobGroupManager.isEdgeMobEntity(mob);
        boolean isDeserter = MobGroupManager.isDeserter(mob);
        if (!isEdge && !isDeserter) return false;

        World world = mob.getWorld();
        if (world.isClient()) return false;


        SoundTracker.SoundRecord best = SoundTracker.getCachedBestFor(mob, world.getRegistryKey().getValue().toString());
        if (best == null) {
            try { SoundTracker.submitAsyncSoundScore(world, mob, mob.getBlockPos()); } catch (Throwable ignored) {}
            best = SoundTracker.findNearestSound(world, mob, mob.getBlockPos(), mob.getEyePos());
        }
        if (best == null) return false;

        this.cachedSound = best;
        this.targetPos = best.pos;
        this.state = EdgeState.GOING_TO_SOUND;
        this.foundPlayerOrHit = false;
        this.relayed = false;
        this.arrivalTicks = 0;
        this.pursueTicksRemaining = Math.max(best.ticksRemaining, (SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.scanCooldownTicks * 2 : 40));

        MobEntity l = MobGroupManager.getLeader(mob);
        this.cachedLeader = (l != null && l != mob) ? l : null;
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} investigating sound {} at {} (w={})", mob.getName().getString(), String.valueOf(best.soundId), best.pos, String.format("%.2f", best.weight));
        }
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (this.state == null) return false;

        if (this.state == EdgeState.RETURNING_TO_LEADER) {
            MobEntity leader = getSafeLeader();
            return leader != null && leader != mob && leader.isAlive();
        }

        if (MobGroupManager.getLeader(mob) == mob) return false;
        if (this.pursueTicksRemaining <= 0 && this.state == EdgeState.GOING_TO_SOUND) return false;
        return true;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        if (this.blockBreakerGoal != null) {
            BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
            this.blockBreakerGoal = null;
        }
        this.state = null;
        this.cachedSound = null;
        this.targetPos = null;
        this.foundPlayerOrHit = false;
        this.relayed = false;
        this.arrivalTicks = 0;
        this.pursueTicksRemaining = 0;
        this.lastIssuedNavTarget = null;
        this.repathCooldown = 0;
        this.lastPosVec = null;
        this.stuckTicks = 0;
        this.debugTick = 0;
        this.cachedLeader = null;
    }

    @Override
    public void tick() {
        if (this.pursueTicksRemaining > 0) this.pursueTicksRemaining--;
        if (this.state == EdgeState.GOING_TO_SOUND) {
            if (this.targetPos == null) { this.state = EdgeState.RETURNING_TO_LEADER; return; }

            moveToThrottled(this.targetPos, this.moveSpeed, false);

            Vec3d cur = mob.getPos();
            if (lastPosVec != null && cur.squaredDistanceTo(lastPosVec) < 0.01) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
                lastPosVec = cur;
                if (this.blockBreakerGoal != null) {
                    BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                    this.blockBreakerGoal = null;
                }
            }
            if (stuckTicks >= 40 && SoundAttractMod.CONFIG.enableBlockBreaking && this.blockBreakerGoal == null) {
                BlockPos destination = this.targetPos;
                if (destination != null) {
                    BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                            this.mob,
                            destination,
                            SoundAttractMod.CONFIG.blockBreakTimeMultiplier,
                            SoundAttractMod.CONFIG.blockBreakToolOnly,
                            SoundAttractMod.CONFIG.blockBreakProperToolOnly,
                            SoundAttractMod.CONFIG.blockBreakProperToolRequired
                    );
                    BlockBreakerManager.scheduleAdd(this.mob, breaker, 2);
                    this.blockBreakerGoal = breaker;
                    if (SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} stuck ({} ticks). Deploying BlockBreakerPosGoal toward {}.", this.mob.getName().getString(), stuckTicks, destination);
                    }
                }
            }

            if (com.example.soundattract.StealthDetectionEvents.consumeSuppressedEdgeDetection(this.mob)) {
                this.foundPlayerOrHit = true;
                if (!this.relayed && this.targetPos != null && this.mob.getWorld() instanceof ServerWorld sw) {
                    MobEntity leader = getSafeLeader();
                    if (leader != null && leader != mob) {
                        RaidManager.scheduleRaid(leader, this.targetPos, sw.getTime());
                        this.relayed = true;
                        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} consumed suppression signal → scheduled RAID for leader {} at {}.",
                                    mob.getName().getString(), leader.getName().getString(), this.targetPos);
                        }
                    }
                }

                if (this.blockBreakerGoal != null) {
                    BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                    this.blockBreakerGoal = null;
                }

                StealthUtils.clearTargetAndMemories(mob);
                this.mob.getNavigation().stop();
                this.state = EdgeState.RETURNING_TO_LEADER;
                return;
            }
            double arrival = (SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.arrivalDistance : 6.0);
            if (mob.getPos().distanceTo(Vec3d.ofCenter(this.targetPos)) < arrival) {
                this.arrivalTicks++;
                this.mob.getNavigation().stop();

                double maxRange = (SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.standingDetectionRange : 16.0) * 2.0;
                net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(this.targetPos).expand(maxRange);
                List<PlayerEntity> players = mob.getWorld().getEntitiesByClass(PlayerEntity.class, box, PlayerEntity::isAlive);
                for (PlayerEntity p : players) {
                    if (p.isCreative() || p.isSpectator()) continue;
                    if (!FovEvents.hasSmartLineOfSight(mob, p)) continue;
                    double detectRange = StealthDetectionEvents.computeFullDetectionRange(mob, p, mob.getWorld());
                    if (p.distanceTo(mob) <= detectRange) {
                        this.foundPlayerOrHit = true;
                        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} found player {} at {}. Returning to leader.", mob.getName().getString(), p.getName().getString(), this.targetPos);
                        }
                        break;
                    }
                }
                int waitTicks = (SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.edgeInvestigateWaitTicks : 15);
                if (this.foundPlayerOrHit || this.arrivalTicks >= waitTicks) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} investigation {}. Returning to leader.",
                                mob.getName().getString(), this.foundPlayerOrHit ? "SUCCESS (player detected)" : "finished (no player)");
                    }

                    if (this.blockBreakerGoal != null) {
                        BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                        this.blockBreakerGoal = null;
                    }

                    if (MobGroupManager.isDeserter(this.mob)) {
                        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                            SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} is a deserter; will NOT return after arrival. Ending goal.", mob.getName().getString());
                        }
                        this.stop();
                        return;
                    }

                    if (this.foundPlayerOrHit) {
                        StealthUtils.clearTargetAndMemories(mob);
                    }

                    if (this.foundPlayerOrHit && !this.relayed && this.targetPos != null && this.mob.getWorld() instanceof ServerWorld sw) {
                        MobEntity leader = getSafeLeader();
                        if (leader != null && leader != mob) {
                            RaidManager.scheduleRaid(leader, this.targetPos, sw.getTime());
                            this.relayed = true;
                            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                                SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} scheduled RAID for leader {} at {} (arrival-phase).",
                                        mob.getName().getString(), leader.getName().getString(), this.targetPos);
                            }
                        }
                    }
                    this.state = EdgeState.RETURNING_TO_LEADER;
                }
            }
        } else if (this.state == EdgeState.RETURNING_TO_LEADER) {
            MobEntity leader = getSafeLeader();
            if (leader == null || leader == mob || !leader.isAlive()) { this.stop(); return; }
            double sprintMult = (SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.groupSprintMultiplier : 1.1);

            moveToThrottled(leader.getBlockPos(), this.moveSpeed * sprintMult, false);

            Vec3d curReturn = mob.getPos();
            if (lastPosVec != null && curReturn.squaredDistanceTo(lastPosVec) < 0.01) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
                lastPosVec = curReturn;
                if (this.blockBreakerGoal != null) {
                    BlockBreakerManager.scheduleRemove(this.mob, this.blockBreakerGoal);
                    this.blockBreakerGoal = null;
                }
            }
            if (stuckTicks >= 40 && SoundAttractMod.CONFIG.enableBlockBreaking && this.blockBreakerGoal == null) {
                BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(
                        this.mob,
                        leader.getBlockPos(),
                        SoundAttractMod.CONFIG.blockBreakTimeMultiplier,
                        SoundAttractMod.CONFIG.blockBreakToolOnly,
                        SoundAttractMod.CONFIG.blockBreakProperToolOnly,
                        SoundAttractMod.CONFIG.blockBreakProperToolRequired
                );
                BlockBreakerManager.scheduleAdd(this.mob, breaker, 2);
                this.blockBreakerGoal = breaker;
                if (SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} stuck while returning ({} ticks). Deploying BlockBreakerPosGoal toward leader {} at {}.",
                            this.mob.getName().getString(), stuckTicks, leader.getName().getString(), leader.getBlockPos());
                }
            }
            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                if ((debugTick++ % 20) == 0) {
                    double dist = Math.sqrt(mob.getPos().squaredDistanceTo(leader.getPos()));
                    SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} returning to leader {}. dist={}. navIdle={}",
                            mob.getName().getString(), leader.getName().getString(), String.format("%.1f", dist), mob.getNavigation().isIdle());
                }
            }
            double arrival = (SoundAttractMod.CONFIG != null ? SoundAttractMod.CONFIG.leaderReturnArrivalDistance : 2.0);
            if (mob.getPos().squaredDistanceTo(leader.getPos()) < arrival * arrival) {

                if (this.foundPlayerOrHit && !this.relayed && this.targetPos != null && this.cachedSound != null) {

                    StealthUtils.clearTargetAndMemories(mob);
                    if (mob.getWorld() instanceof ServerWorld sw) {
                        RaidManager.scheduleRaid(leader, this.targetPos, sw.getTime());
                    }
                    this.relayed = true;
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.info("[FollowerEdgeRelayGoal] {} confirmed player at {} → scheduled RAID for leader {} (no immediate relay).",
                                mob.getName().getString(), targetPos, leader.getName().getString());
                    }
                }
                this.stop();
            }
        }
    }

    private void navigateTo(BlockPos target, double speed) {
        if (target == null) {
            if (!this.mob.getNavigation().isIdle()) this.mob.getNavigation().stop();
            return;
        }
        this.mob.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
    }


    private void moveToThrottled(BlockPos dest, double speed, boolean force) {
        if (dest == null) return;
        if (force) {
            this.mob.getNavigation().startMovingTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, speed);
            this.lastIssuedNavTarget = dest;
            this.repathCooldown = REPATH_COOLDOWN_TICKS;
            return;
        }
        boolean destChanged = (this.lastIssuedNavTarget == null) || !this.lastIssuedNavTarget.equals(dest);
        boolean navIdle = this.mob.getNavigation().isIdle();
        if (this.repathCooldown > 0 && !destChanged && !navIdle) {
            this.repathCooldown--;
            return;
        }
        double distSq = this.mob.getPos().squaredDistanceTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        double thresholdSq = 4.0;
        if (destChanged || navIdle || distSq > thresholdSq) {
            this.mob.getNavigation().startMovingTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, speed);
            this.lastIssuedNavTarget = dest;
            this.repathCooldown = REPATH_COOLDOWN_TICKS;
        }
    }


    private MobEntity getSafeLeader() {
        if (this.cachedLeader != null && this.cachedLeader.isAlive() && !this.cachedLeader.isRemoved()) {
            return this.cachedLeader;
        }
        MobEntity l = MobGroupManager.getLeader(mob);
        if (l != null && l != mob && l.isAlive() && !l.isRemoved()) {
            this.cachedLeader = l;
            return l;
        }
        return null;
    }
}
