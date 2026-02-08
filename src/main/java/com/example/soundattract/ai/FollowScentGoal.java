package com.example.soundattract.ai;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class FollowScentGoal extends Goal {
    private final PathfinderMob mob;
    private final ScentManager scentManager;
    private UUID targetScentOwner;
    private long lastScentTime;
    private Vec3 targetPos;
    private int ambushTimer;
    private boolean isAmbushing;

    public FollowScentGoal(PathfinderMob mob) {
        this.mob = mob;
        this.scentManager = mob.level().getCapability(ScentManager.INSTANCE).orElse(null);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return false;
        if (scentManager == null) return false;
        
        if (mob.getRandom().nextInt(10) != 0) return false;

        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;

        return findScentTrail();
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;
        
        if (isAmbushing && ambushTimer <= 0) return false;

        return targetPos != null && mob.getNavigation().getPath() != null && !mob.getNavigation().isDone(); 
    }

    @Override
    public void start() {
        this.ambushTimer = SoundAttractConfig.COMMON.scentAmbushDurationTicks.get();
        moveToTarget();
    }

    @Override
    public void tick() {
        if (targetPos == null) return;

        if (mob.distanceToSqr(targetPos) < 4.0) {
            if (!findScentTrail()) {
                startAmbush();
            } else {
                moveToTarget();
            }
        }
        
        if (isAmbushing) {
            ambushTimer--;

        }
    }

    private void startAmbush() {
        if (isAmbushing) return;
        isAmbushing = true;

    }

    private void moveToTarget() {
        isAmbushing = false;
        Path path = mob.getNavigation().createPath(BlockPos.containing(targetPos), 1);
        if (path != null) {
            mob.getNavigation().moveTo(path, 1.0);
        }
    }

    private boolean findScentTrail() {
        if (scentManager == null) return false;

        ChunkPos chunkPos = mob.chunkPosition();
        List<ScentNode> nodes = scentManager.getNodesInArea(chunkPos);

        if (nodes.isEmpty()) return false;

        long currentTime = mob.level().getGameTime();
        long maxDuration = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();


        if (targetScentOwner == null) {


            ScentNode freshestNode = nodes.stream()
                .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
                .max(Comparator.comparingLong(ScentNode::getTimestamp))
                .orElse(null);
            
            if (freshestNode == null) return false;
            
            this.targetScentOwner = freshestNode.getOwnerUUID();
            


            ScentNode startNode = nodes.stream()
                .filter(n -> n.getOwnerUUID().equals(targetScentOwner))
                .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
                .min(Comparator.comparingDouble(n -> n.getPosition().distanceToSqr(mob.position())))
                .orElse(freshestNode);
                
            this.targetPos = startNode.getPosition();
            this.lastScentTime = startNode.getTimestamp();
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[FollowScentGoal] Mob {} acquired INITIAL scent trail for owner {} at {}.", 
                    mob.getName().getString(), targetScentOwner, targetPos);
            }
            return true;
        } else {




            
            ScentNode nextNode = nodes.stream()
                .filter(n -> n.getOwnerUUID().equals(targetScentOwner))
                .filter(n -> (currentTime - n.getTimestamp()) <= maxDuration)
                .filter(n -> n.getTimestamp() > lastScentTime)
                .min(Comparator.comparingLong(ScentNode::getTimestamp))
                .orElse(null);

            if (nextNode != null) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[FollowScentGoal] Mob {} found NEXT scent node at {} (time: {}).", 
                        mob.getName().getString(), nextNode.getPosition(), nextNode.getTimestamp());
                }
                this.targetPos = nextNode.getPosition();
                this.lastScentTime = nextNode.getTimestamp();
                return true;
            }
        }

        return false;
    }
}
