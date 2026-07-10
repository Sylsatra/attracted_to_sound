package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Assists combat pathing by scheduling BlockBreakerPosGoal when a mob has a
 * target but cannot reach it because blocks are in the way.
 */
public class CombatBlockBreakAssistGoal extends Goal {
    private static final int STUCK_THRESHOLD = 10;

    private final Mob mob;
    private int stuckTicks = 0;
    private BlockPos lastPos = null;

    public CombatBlockBreakAssistGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (mob == null || mob.level() == null || mob.level().isClientSide()) return false;
        if (!SoundAttractConfig.COMMON.enableBlockBreaking.get()) return false;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (shouldPreferRangedAttack(target)) return false;

        return BlockBreakerPosGoal.findFirstBlockingBlock(mob.level(), mob, target.blockPosition()) != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob == null || mob.level() == null || mob.level().isClientSide()) return false;
        if (!SoundAttractConfig.COMMON.enableBlockBreaking.get()) return false;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        return !shouldPreferRangedAttack(target);
    }

    @Override
    public void tick() {
        if (lastPos != null && lastPos.equals(mob.blockPosition())) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPos = mob.blockPosition();
        }

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;

        if (!(mob.getNavigation() != null && mob.getNavigation().isStuck()) && stuckTicks < STUCK_THRESHOLD) {
            return;
        }

        BlockPos dest = target.blockPosition();
        BlockPos blocking = BlockBreakerPosGoal.findFirstBlockingBlock(mob.level(), mob, dest);
        if (blocking == null) return;

        boolean hasBreaker = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof BlockBreakerPosGoal);
        if (hasBreaker) return;

        double mult = SoundAttractConfig.COMMON.blockBreakingTimeMultiplier.get();
        boolean toolOnly = SoundAttractConfig.COMMON.blockBreakingToolOnly.get();
        boolean properOnly = SoundAttractConfig.COMMON.blockBreakingProperToolOnly.get();
        boolean properReq = SoundAttractConfig.COMMON.blockBreakingProperToolRequired.get();

        BlockBreakerPosGoal breaker = new BlockBreakerPosGoal(mob, dest, mult, toolOnly, properOnly, properReq);
        BlockBreakerManager.scheduleAdd(mob, breaker, 2);

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[CombatBlockBreakAssistGoal] {} scheduled BlockBreakerPosGoal toward target at {} (blocked at {}).",
                    mob.getName().getString(), dest, blocking);
        }
    }

    private boolean shouldPreferRangedAttack(LivingEntity target) {
        try {
            if (!(mob instanceof RangedAttackMob)) return false;

            boolean hasLineOfSight = mob.getSensing() != null && mob.getSensing().hasLineOfSight(target);
            if (!hasLineOfSight) return false;

            Vec3 mobPos = mob.position();
            Vec3 targetPos = target.position();
            double maxRange = 16.0d;
            return mobPos.distanceToSqr(targetPos) <= maxRange * maxRange;
        } catch (Exception e) {
            return false;
        }
    }
}
