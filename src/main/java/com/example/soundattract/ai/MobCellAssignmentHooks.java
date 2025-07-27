package com.example.soundattract.ai;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;
import java.util.WeakHashMap;

public class MobCellAssignmentHooks {
    private static final WeakHashMap<MobEntity, Long> lastCellKey = new WeakHashMap<>();

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((Entity entity, ServerWorld world) -> {
            if (!(entity instanceof MobEntity mob)) return;
            UUID uuid = mob.getUuid();
            BlockPos pos = mob.getBlockPos();
            SpatialPartitionModule.addMobToCell(uuid, pos);
            SpatialPartitionModule.addMobToCache(uuid, mob);
            lastCellKey.put(mob, SpatialPartitionModule.cellKey(pos));
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((Entity entity, ServerWorld world) -> {
            if (!(entity instanceof MobEntity mob)) return;
            UUID uuid = mob.getUuid();
            SpatialPartitionModule.enqueueDeadUuid(uuid);
            SpatialPartitionModule.removeMobFromCache(uuid);
            lastCellKey.remove(mob);
        });

        ServerTickEvents.END_WORLD_TICK.register((ServerWorld world) -> {
            for (Entity entity : world.iterateEntities()) {
                if (!(entity instanceof MobEntity mob)) continue;
                UUID uuid = mob.getUuid();
                BlockPos pos = mob.getBlockPos();
                long newKey = SpatialPartitionModule.cellKey(pos);
                Long lastKey = lastCellKey.get(mob);
                if (lastKey == null) {
                    lastCellKey.put(mob, newKey);
                    SpatialPartitionModule.addMobToCell(uuid, pos);
                } else if (lastKey != newKey) {
                    SpatialPartitionModule.removeMobFromCell(uuid);
                    SpatialPartitionModule.addMobToCell(uuid, pos);
                    lastCellKey.put(mob, newKey);
                }
            }
        });
    }
}
