package com.example.soundattract.pathfinding;

import com.example.soundattract.Soundattract;
import com.example.soundattract.ai.MobGroupManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlowFieldManager {

    private static final Map<String, FlowField> FIELDS = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "SA-FlowField-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final int FIELD_RADIUS = 32;
    private static final int MAX_NODES = 2000;
    private static final long CACHE_TTL_MS = 2000;
    private static final int MAX_FIELDS = 16;

    public static Vec3 getNextStep(Mob mob, Vec3 target) {
        if (mob == null || target == null) return null;

        Mob leader = MobGroupManager.getLeader(mob);
        int groupId = leader != null ? leader.getId() : mob.getId();
        String key = getKey(groupId, target);
        FlowField field = FIELDS.get(key);

        long now = System.currentTimeMillis();

        if (FIELDS.size() > MAX_FIELDS) {
            FIELDS.entrySet().removeIf(e -> e.getValue().isExpired(now));
        }

        if (field == null || field.isExpired(now) || field.isTargetMoved(target)) {
            if (field == null || !field.generating.get()) {
                triggerGeneration(key, mob, target, now);
            }
            if (field == null) return null;
        }

        return field.getVector(mob.blockPosition());
    }

    private static String getKey(int groupId, Vec3 target) {
        BlockPos p = new BlockPos((int) target.x, (int) target.y, (int) target.z);
        return groupId + "_" + p.getX() + "_" + p.getY() + "_" + p.getZ();
    }

    private static void triggerGeneration(String key, Mob mob, Vec3 target, long now) {
        FlowField existing = FIELDS.get(key);
        if (existing != null && existing.generating.get()) return;

        FlowField field = (existing != null) ? existing : new FlowField(target, now);
        FIELDS.put(key, field);

        if (field.generating.getAndSet(true)) return;

        Level level = mob.level();
        BlockPos startNode = new BlockPos((int) target.x, (int) target.y, (int) target.z);

        Map<Long, Boolean> walkabilitySnapshot = snapshotWalkability(level, mob, startNode);

        EXECUTOR.submit(() -> {
            try {
                Map<BlockPos, Vec3> vectors = generateFlowField(walkabilitySnapshot, startNode);
                field.update(vectors, System.currentTimeMillis());
            } catch (Exception e) {
            } finally {
                field.generating.set(false);
            }
        });
    }

    private static Map<Long, Boolean> snapshotWalkability(Level level, Mob mob, BlockPos start) {
        Map<Long, Boolean> snapshot = new HashMap<>();
        int radius = FIELD_RADIUS;
        int radiusSqr = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSqr) continue;
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = start.offset(dx, dy, dz);
                    long k = pos.asLong();
                    if (!level.hasChunkAt(pos)) {
                        snapshot.put(k, false);
                        continue;
                    }
                    boolean walkable = NodeRouter.isWalkable(mob, Vec3.atBottomCenterOf(pos));
                    snapshot.put(k, walkable);
                }
            }
        }
        return snapshot;
    }

    private static Map<BlockPos, Vec3> generateFlowField(Map<Long, Boolean> walkabilitySnapshot, BlockPos start) {
        Map<BlockPos, Integer> costs = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.cost));

        costs.put(start, 0);
        open.add(new Node(start, 0));

        int processed = 0;

        while (!open.isEmpty() && processed < MAX_NODES) {
            Node current = open.poll();
            BlockPos pos = current.pos;

            if (costs.getOrDefault(pos, Integer.MAX_VALUE) < current.cost) continue;
            processed++;

            for (Direction dir : Direction.values()) {
                BlockPos nPos = pos.relative(dir);

                if (Math.abs(nPos.getX() - start.getX()) > FIELD_RADIUS ||
                        Math.abs(nPos.getZ() - start.getZ()) > FIELD_RADIUS ||
                        Math.abs(nPos.getY() - start.getY()) > 10) continue;

                Boolean walkable = walkabilitySnapshot.get(nPos.asLong());
                if (walkable == null || !walkable) continue;

                int newCost = current.cost + 1;
                if (newCost < costs.getOrDefault(nPos, Integer.MAX_VALUE)) {
                    costs.put(nPos, newCost);
                    open.add(new Node(nPos, newCost));
                }
            }
        }

        Map<BlockPos, Vec3> flow = new HashMap<>();
        for (BlockPos pos : costs.keySet()) {
            if (pos.equals(start)) {
                flow.put(pos, Vec3.ZERO);
                continue;
            }

            BlockPos bestNeighbor = null;
            int minCost = costs.get(pos);

            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                if (costs.containsKey(n)) {
                    int c = costs.get(n);
                    if (c < minCost) {
                        minCost = c;
                        bestNeighbor = n;
                    }
                }
            }

            if (bestNeighbor != null) {
                double ex = bestNeighbor.getX() + 0.5;
                double ey = bestNeighbor.getY() + 0.5;
                double ez = bestNeighbor.getZ() + 0.5;

                double sx = pos.getX() + 0.5;
                double sy = pos.getY() + 0.5;
                double sz = pos.getZ() + 0.5;

                double ddx = ex - sx;
                double ddy = ey - sy;
                double ddz = ez - sz;

                double len = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                if (len > 1.0e-5) {
                    ddx /= len;
                    ddy /= len;
                    ddz /= len;
                }

                flow.put(pos, new Vec3(ddx, ddy, ddz));
            }
        }

        return flow;
    }

    private static class Node {
        BlockPos pos;
        int cost;
        Node(BlockPos p, int c) { this.pos = p; this.cost = c; }
    }

    private static class FlowField {
        final Vec3 target;
        volatile Map<BlockPos, Vec3> vectors = new ConcurrentHashMap<>();
        volatile long created;
        AtomicBoolean generating = new AtomicBoolean(false);

        FlowField(Vec3 target, long now) {
            this.target = target;
            this.created = now;
        }

        void update(Map<BlockPos, Vec3> newVectors, long now) {
            this.vectors = Collections.unmodifiableMap(newVectors);
            this.created = now;
        }

        boolean isExpired(long now) {
            return (now - created) > CACHE_TTL_MS;
        }

        boolean isTargetMoved(Vec3 newTarget) {
            return newTarget.distanceToSqr(target) > 2.0;
        }

        Vec3 getVector(BlockPos pos) {
            return vectors.get(pos);
        }
    }
}
