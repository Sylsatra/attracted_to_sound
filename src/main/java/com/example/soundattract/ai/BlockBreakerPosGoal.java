package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfigData;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;

import java.util.EnumSet;

/**
 * Fabric-native block breaker goal aimed at a destination position.
 * Minimal dependencies; time is based on block hardness and a config multiplier.
 */
public class BlockBreakerPosGoal extends Goal {

    private final MobEntity miner;
    private final BlockPos destinationPos;
    private final double reachDistance = 4.9;

    private final double timeToBreakMultiplier;
    private final boolean toolOnly;
    private final boolean properToolOnly;
    private final boolean properToolRequired;

    private BlockPos targetPos = null;
    private BlockState targetState = null;
    private int tickToBreak = 0;
    private int breakingTick = 0;
    private int prevBreakProgress = 0;

    public BlockBreakerPosGoal(MobEntity miner,
                               BlockPos destination,
                               double timeToBreakMultiplier,
                               boolean toolOnly,
                               boolean properToolOnly,
                               boolean properToolRequired) {
        this.miner = miner;
        this.destinationPos = destination;
        this.timeToBreakMultiplier = timeToBreakMultiplier <= 0 ? 1.0 : timeToBreakMultiplier;
        this.toolOnly = toolOnly;
        this.properToolOnly = properToolOnly;
        this.properToolRequired = properToolRequired;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (miner == null) return false;
        SoundAttractConfigData cfg = SoundAttractMod.CONFIG;
        if (cfg == null || !cfg.enableBlockBreaking) return false;
        if (destinationPos == null) return false;
        if (toolOnly && !isHoldingTool()) return false;
        World world = miner.getWorld();
        if (world == null) return false;
        this.targetPos = findFirstBlockingBlock(world, miner, destinationPos);
        if (this.targetPos == null) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] {}: No blocking block found toward {}.", miner.getName().getString(), destinationPos);
            }
            return false;
        }
        this.targetState = world.getBlockState(this.targetPos);
        if (!passesRules(targetState, targetPos)) {
            if (SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] {}: Target {} rejected by rules.", miner.getName().getString(), targetPos);
            }
            return false;
        }
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] {}: Starting to break {} to reach {}.", miner.getName().getString(), targetPos, destinationPos);
        }
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (miner == null) return false;
        if (targetPos == null) return false;
        if (destinationPos == null) return false;
        World world = miner.getWorld();
        if (world == null) return false;
        if (world.isAir(targetPos)) return false;
        if (properToolOnly && targetState != null && !canBreakBlock(targetState)) return false;

        BlockPos ahead = findFirstBlockingBlock(world, miner, destinationPos);
        if (ahead == null) return false;
        return true;
    }

    @Override
    public void start() {
        if (miner == null) return;
        if (targetPos != null) {
            initBlockBreak();
            miner.setAttacking(true);

            Vec3d minerPos = miner.getPos();
            if (minerPos == null) return;
            double distSq = minerPos.squaredDistanceTo(Vec3d.ofCenter(targetPos));
            if (distSq > (reachDistance * reachDistance)) {
                miner.getNavigation().startMovingTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5,
                    1.0
                );
            }
        }
    }

    @Override
    public void stop() {
        if (targetPos != null && miner != null) {
            World world = miner.getWorld();
            if (world instanceof ServerWorld sw) {
                sw.setBlockBreakingInfo(miner.getId(), targetPos, -1);
            }
        }
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] {}: Stopping block breaking.", miner.getName().getString());
        }
        targetPos = null;
        targetState = null;
        tickToBreak = 0;
        breakingTick = 0;
        prevBreakProgress = 0;
        if (miner != null) miner.setAttacking(false);

        if (miner != null) BlockBreakerManager.scheduleRemove(miner, this);
    }

    @Override
    public void tick() {
        if (miner == null || targetPos == null) return;
        if (targetState == null) {
            World w = miner.getWorld();
            if (w != null) targetState = w.getBlockState(targetPos);
            if (targetState == null) return;
        }
        World world = miner.getWorld();
        if (world == null) return;
        if (world.isAir(targetPos)) return;
        if (properToolOnly && !canBreakBlock(targetState)) return;


        Vec3d minerPos = miner.getPos();
        if (minerPos == null) return;
        double distSqToTarget = minerPos.squaredDistanceTo(Vec3d.ofCenter(targetPos));
        if (distSqToTarget > (reachDistance * reachDistance)) {
            if (miner.getNavigation().isIdle() || miner.getNavigation().getTargetPos() == null || !miner.getNavigation().getTargetPos().equals(targetPos)) {
                miner.getNavigation().startMovingTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5,
                    1.0
                );
            }

            if (world instanceof ServerWorld sw) {
                sw.setBlockBreakingInfo(miner.getId(), targetPos, -1);
            }
            return;
        }

        breakingTick++;
        miner.getLookControl().lookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        int progress = (int) ((breakingTick / (float) Math.max(1, tickToBreak)) * 10);
        if (progress != prevBreakProgress) {
            prevBreakProgress = progress;
            if (world instanceof ServerWorld sw) {
                sw.setBlockBreakingInfo(miner.getId(), targetPos, progress);
            }
        }

        if (breakingTick % 6 == 0) {
            miner.swingHand(Hand.MAIN_HAND);
        }
        if (breakingTick % 4 == 0) {
            try {
                var group = targetState.getSoundGroup();
                world.playSound(null, targetPos, group.getHitSound(), SoundCategory.BLOCKS,
                        (group.getVolume() + 1.0F) / 8.0F, group.getPitch() * 0.5F);
            } catch (Throwable ignored) {}
        }

        if (breakingTick >= tickToBreak && world instanceof ServerWorld sw) {

            sw.setBlockBreakingInfo(miner.getId(), targetPos, -1);
            sw.breakBlock(targetPos, true, miner);

            targetPos = findFirstBlockingBlock(world, miner, destinationPos);
            if (targetPos != null) {
                targetState = world.getBlockState(targetPos);
                if (passesRules(targetState, targetPos)) {
                    initBlockBreak();
                } else {
                    targetPos = null;
                    targetState = null;
                }
            }
        }
    }

    private void initBlockBreak() {
        if (miner == null || targetPos == null) return;
        World world = miner.getWorld();
        if (world == null) return;
        this.targetState = world.getBlockState(targetPos);
        this.tickToBreak = computeTicksToBreak(targetState);
        this.breakingTick = 0;
        this.prevBreakProgress = 0;
    }

    private int computeTicksToBreak(BlockState state) {
        World world = miner != null ? miner.getWorld() : null;
        if (state == null || world == null || targetPos == null) return 20;
        float hardness = state.getHardness(world, targetPos);
        if (hardness == 0f) return 1;

        double base = 20.0 * hardness * timeToBreakMultiplier;

        if (state.isToolRequired() && !isSuitableFor(state)) {
            base *= 4.0;
        }
        return Math.max(1, (int) Math.ceil(base));
    }

    private boolean canBreakBlock(BlockState state) {
        if (state == null) return false;
        if (!state.isToolRequired() || !properToolRequired) return true;
        return isSuitableFor(state);
    }

    private boolean isSuitableFor(BlockState state) {
        if (miner == null) return false;
        return miner.getMainHandStack().isSuitableFor(state) || miner.getOffHandStack().isSuitableFor(state);
    }

    private boolean isHoldingTool() {
        if (miner == null) return false;
        return !miner.getMainHandStack().isEmpty() || !miner.getOffHandStack().isEmpty();
    }

    private boolean passesRules(BlockState state, BlockPos pos) {
        if (state == null || pos == null) return false;
        SoundAttractConfigData cfg = SoundAttractMod.CONFIG;
        if (cfg == null) return false;
        if (pos.getY() > cfg.blockBreakMaxY) return false;
        try {
            if (state.hasBlockEntity() && cfg.blockBreakBlacklistTileEntities) return false;
        } catch (Throwable ignored) {}


        Identifier id = null;
        try {
            id = Registries.BLOCK.getId(state.getBlock());
        } catch (Throwable ignored) {}
        String idStr = id != null ? id.toString() : "";
        boolean listed = cfg.blockBreakBlockList != null && cfg.blockBreakBlockList.stream()
                .map(String::trim).anyMatch(s -> !s.isEmpty() && s.equals(idStr));
        if (!cfg.blockBreakListAsWhitelist && listed) {
            return false;
        }
        if (cfg.blockBreakListAsWhitelist && !listed) {
            return false;
        }


        return true;
    }

    private static BlockPos findFirstBlockingBlock(World world, MobEntity mob, BlockPos dest) {
        if (world == null || mob == null || dest == null) return null;
        Vec3d start = mob.getEyePos();
        Vec3d end = Vec3d.ofCenter(dest);
        BlockHitResult hit = world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mob));
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            return hit.getBlockPos();
        }

        Vec3d mobPos = mob.getPos();
        Vec3d destCenter = Vec3d.ofCenter(dest);
        Vec3d dir = destCenter.subtract(mobPos);
        double length = dir.length();
        if (length < 0.001) return null;
        Vec3d step = dir.normalize().multiply(0.5);
        int maxSteps = (int) Math.ceil(length / 0.5);
        Vec3d probeFeet = new Vec3d(mobPos.x, mob.getY(), mobPos.z);
        Vec3d probeEye = new Vec3d(mobPos.x, mob.getEyeY(), mobPos.z);
        for (int i = 0; i < maxSteps; i++) {
            probeFeet = probeFeet.add(step);
            probeEye = probeEye.add(step);
            BlockPos feetPos = BlockPos.ofFloored(probeFeet);
            BlockPos eyePos = BlockPos.ofFloored(probeEye);
            if (isObstacle(world, feetPos)) return feetPos;
            if (isObstacle(world, eyePos)) return eyePos;
        }
        return null;
    }

    private static boolean isObstacle(World world, BlockPos pos) {
        BlockState st = world.getBlockState(pos);
        return !st.isAir() && st.getCollisionShape(world, pos).isEmpty() == false;
    }
}
