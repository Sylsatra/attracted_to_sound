package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * NeoForge-native block breaker goal aimed at a destination position.
 * Time is based on block hardness and a config multiplier.
 */
public class BlockBreakerPosGoal extends Goal {

    private final Mob miner;
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

    public BlockBreakerPosGoal(Mob miner,
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
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!SoundAttractConfig.COMMON.enableBlockBreaking.get()) return false;
        if (destinationPos == null) return false;
        if (toolOnly && !isHoldingTool()) return false;
        this.targetPos = findFirstBlockingBlock(miner.level(), miner, destinationPos);
        if (this.targetPos == null) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] {}: No blocking block found toward {}.", miner.getName().getString(), destinationPos);
            }
            return false;
        }
        this.targetState = miner.level().getBlockState(this.targetPos);
        if (!passesRules(targetState, targetPos)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] {}: Target {} rejected by rules.", miner.getName().getString(), targetPos);
            }
            return false;
        }
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] {}: Starting to break {} to reach {}.", miner.getName().getString(), targetPos, destinationPos);
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPos == null) return false;
        if (destinationPos == null) return false;
        Level world = miner.level();
        if (world.isEmptyBlock(targetPos)) return false;
        if (properToolOnly && targetState != null && !canBreakBlock(targetState)) return false;

        BlockPos ahead = findFirstBlockingBlock(world, miner, destinationPos);
        return ahead != null;
    }

    @Override
    public void start() {
        if (targetPos != null) {
            initBlockBreak();
            miner.setAggressive(true);

            double distSq = miner.position().distanceToSqr(Vec3.atCenterOf(targetPos));
            if (distSq > (reachDistance * reachDistance)) {
                miner.getNavigation().moveTo(
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
        if (targetPos != null && miner.level() instanceof ServerLevel sw) {
            sw.destroyBlockProgress(miner.getId(), targetPos, -1);
        }
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[BlockBreakerPosGoal] {}: Stopping block breaking.", miner.getName().getString());
        }
        targetPos = null;
        targetState = null;
        tickToBreak = 0;
        breakingTick = 0;
        prevBreakProgress = 0;
        miner.setAggressive(false);

        BlockBreakerManager.scheduleRemove(miner, this);
    }

    @Override
    public void tick() {
        if (targetPos == null || targetState == null) return;
        Level world = miner.level();
        if (world.isEmptyBlock(targetPos)) return;
        if (properToolOnly && !canBreakBlock(targetState)) return;

        double distSqToTarget = miner.position().distanceToSqr(Vec3.atCenterOf(targetPos));
        if (distSqToTarget > (reachDistance * reachDistance)) {
            if (miner.getNavigation().isDone()) {
                miner.getNavigation().moveTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5,
                    1.0
                );
            }
            if (world instanceof ServerLevel sw) {
                sw.destroyBlockProgress(miner.getId(), targetPos, -1);
            }
            return;
        }

        breakingTick++;
        miner.getLookControl().setLookAt(
            targetPos.getX() + 0.5,
            targetPos.getY() + 0.5,
            targetPos.getZ() + 0.5
        );

        int progress = (int) ((breakingTick / (float) Math.max(1, tickToBreak)) * 10);
        if (progress != prevBreakProgress) {
            prevBreakProgress = progress;
            if (world instanceof ServerLevel sw) {
                sw.destroyBlockProgress(miner.getId(), targetPos, progress);
            }
        }

        if (breakingTick % 6 == 0) {
            miner.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }

        if (breakingTick >= tickToBreak && world instanceof ServerLevel sw) {
            sw.destroyBlockProgress(miner.getId(), targetPos, -1);
            sw.destroyBlock(targetPos, true, miner);

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
        this.targetState = miner.level().getBlockState(targetPos);
        this.tickToBreak = computeTicksToBreak(targetState);
        this.breakingTick = 0;
        this.prevBreakProgress = 0;
    }

    private int computeTicksToBreak(BlockState state) {
        float hardness = state.getDestroySpeed(miner.level(), targetPos);
        if (hardness == 0f) return 1;
        double base = 20.0 * hardness * timeToBreakMultiplier;
        if (state.requiresCorrectToolForDrops() && !isSuitableFor(state)) {
            base *= 4.0;
        }
        return Math.max(1, (int) Math.ceil(base));
    }

    private boolean canBreakBlock(BlockState state) {
        if (!state.requiresCorrectToolForDrops() || !properToolRequired) return true;
        return isSuitableFor(state);
    }

    private boolean isSuitableFor(BlockState state) {
        ItemStack main = miner.getMainHandItem();
        ItemStack off = miner.getOffhandItem();
        return (!main.isEmpty() && main.isCorrectToolForDrops(state)) || (!off.isEmpty() && off.isCorrectToolForDrops(state));
    }

    private boolean isHoldingTool() {
        return !miner.getMainHandItem().isEmpty() || !miner.getOffhandItem().isEmpty();
    }

    private boolean passesRules(BlockState state, BlockPos pos) {
        if (pos.getY() > SoundAttractConfig.COMMON.blockBreakMaxY.get()) return false;
        if (state.hasBlockEntity() && SoundAttractConfig.COMMON.blockBreakBlacklistTileEntities.get()) return false;


        java.util.List<? extends String> list = SoundAttractConfig.COMMON.blockBreakBlockList.get();
        boolean whitelist = SoundAttractConfig.COMMON.blockBreakListAsWhitelist.get();
        if (list != null && !list.isEmpty()) {
            String idStr = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            boolean listed = list.stream().map(String::trim).anyMatch(s -> !s.isEmpty() && s.equals(idStr));
            if (!whitelist && listed) return false;
            if (whitelist && !listed) return false;
        }
        return true;
    }

    public static BlockPos findFirstBlockingBlock(Level world, Mob mob, BlockPos dest) {
        if (world == null || mob == null || dest == null) return null;
        Vec3 start = mob.getEyePosition();
        Vec3 end = Vec3.atCenterOf(dest);
        ClipContext ctx = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob);
        BlockHitResult hit = world.clip(ctx);
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            return hit.getBlockPos();
        }


        Vec3 mobPos = mob.position();
        Vec3 destCenter = Vec3.atCenterOf(dest);
        Vec3 dir = destCenter.subtract(mobPos);
        double length = dir.length();
        if (length < 0.001) return null;
        Vec3 step = dir.normalize().scale(0.5);
        int maxSteps = (int) Math.ceil(length / 0.5);
        Vec3 probeFeet = new Vec3(mobPos.x, mob.getY(), mobPos.z);
        Vec3 probeEye = new Vec3(mobPos.x, mob.getEyeY(), mobPos.z);
        for (int i = 0; i < maxSteps; i++) {
            probeFeet = probeFeet.add(step);
            probeEye = probeEye.add(step);
            BlockPos feetPos = BlockPos.containing(probeFeet);
            BlockPos eyePos = BlockPos.containing(probeEye);
            if (isObstacle(world, feetPos)) return feetPos;
            if (isObstacle(world, eyePos)) return eyePos;
        }
        return null;
    }

    private static boolean isObstacle(Level world, BlockPos pos) {
        BlockState st = world.getBlockState(pos);
        if (st.isAir()) return false;

        if (!st.getCollisionShape(world, pos).isEmpty()) return true;
        FluidState fs = world.getFluidState(pos);
        return !fs.isEmpty();
    }
}
