package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Assists combat pathing by scheduling BlockBreakerPosGoal when a mob has a target
 * but cannot reach due to blocking blocks. Prefers ranged attacking if feasible.
 */
public class CombatBlockBreakAssistGoal extends Goal {
    private final Mob mob;


    private int stuckTicks = 0;
    private BlockPos lastPos = null;
    private static final int STUCK_THRESHOLD = 10;

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


        BlockPos dest = target.blockPosition();
        BlockPos blocking = BlockBreakerPosGoal.findFirstBlockingBlock(mob.level(), mob, dest);
        return blocking != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob == null || mob.level() == null || mob.level().isClientSide()) return false;
        if (!SoundAttractConfig.COMMON.enableBlockBreaking.get()) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (shouldPreferRangedAttack(target)) return false;
        return true;
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
        if (dest == null) return;
        BlockPos blocking = BlockBreakerPosGoal.findFirstBlockingBlock(mob.level(), mob, dest);
        if (blocking == null) return;

        boolean hasBreaker = mob.goalSelector.getAvailableGoals().stream()
            .anyMatch(w -> w.getGoal() instanceof BlockBreakerPosGoal);
        if (hasBreaker) return;

        double mult = SoundAttractConfig.COMMON.blockBreakTimeMultiplier.get();
        boolean toolOnly = SoundAttractConfig.COMMON.blockBreakToolOnly.get();
        boolean properOnly = SoundAttractConfig.COMMON.blockBreakProperToolOnly.get();
        boolean properReq = SoundAttractConfig.COMMON.blockBreakProperToolRequired.get();

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

            boolean hasLOS = mob.getSensing() != null && mob.getSensing().hasLineOfSight(target);
            if (!hasLOS) return false;
            Vec3 me = mob.position();
            Vec3 tp = target.position();
            double distSq = me.distanceToSqr(tp);

            double maxRange = 16.0;
            return distSq <= (maxRange * maxRange);
        } catch (Exception e) {
            return false;
        }
    }
}
