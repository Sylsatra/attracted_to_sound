package com.example.soundattract.worker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;

public final class WorkerComputations {
    private WorkerComputations() {}

    public static WorkerScheduler.GroupComputeResult computeGroups(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, long deadlineMs, ResourceLocation dimension) {
        if (mobs == null || mobs.isEmpty()) return null;
        List<WorkerScheduler.MobSnapshot> candidates = new ArrayList<>();
        for (WorkerScheduler.MobSnapshot m : mobs) {
            if (m.alive()) candidates.add(m);
        }
        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingDouble(WorkerScheduler.MobSnapshot::health).reversed());

        List<WorkerScheduler.MobSnapshot> leaders = new ArrayList<>();
        Map<UUID, UUID> mobToLeader = new HashMap<>();

        double groupRadius = cfg.leaderGroupRadius();
        double leaderSpacing = groupRadius * cfg.leaderSpacingMultiplier();
        int maxLeaders = cfg.maxLeaders();
        int maxGroupSize = cfg.maxGroupSize();
        int sectors = cfg.numEdgeSectors();
        int perSector = cfg.edgeMobsPerSector();

        for (WorkerScheduler.MobSnapshot p : candidates) {
            if (leaders.size() >= maxLeaders) break;
            boolean tooClose = false;
            for (WorkerScheduler.MobSnapshot l : leaders) {
                double dx = p.x() - l.x();
                double dz = p.z() - l.z();
                double dist2 = dx * dx + dz * dz;
                if (dist2 < (leaderSpacing * leaderSpacing)) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                leaders.add(p);
            }
            if (System.currentTimeMillis() > deadlineMs) break;
        }
        if (leaders.isEmpty()) {
            leaders.add(candidates.get(0));
        }

        Map<UUID, List<UUID>> leaderToGroup = new HashMap<>();
        for (WorkerScheduler.MobSnapshot l : leaders) {
            leaderToGroup.put(l.uuid(), new ArrayList<>(Collections.singletonList(l.uuid())));
        }

        Set<UUID> assigned = new HashSet<>();
        for (WorkerScheduler.MobSnapshot m : candidates) {
            if (leaders.contains(m)) {
                assigned.add(m.uuid());
                mobToLeader.put(m.uuid(), m.uuid());
                continue;
            }
            WorkerScheduler.MobSnapshot bestLeader = null;
            double bestDist = Double.MAX_VALUE;
            for (WorkerScheduler.MobSnapshot l : leaders) {
                List<UUID> group = leaderToGroup.get(l.uuid());
                if (group.size() >= maxGroupSize) continue;
                double dx = m.x() - l.x();
                double dz = m.z() - l.z();
                double dist = Math.hypot(dx, dz);
                if (dist <= groupRadius && dist < bestDist) {
                    bestDist = dist;
                    bestLeader = l;
                }
            }
            if (bestLeader != null) {
                leaderToGroup.get(bestLeader.uuid()).add(m.uuid());
                mobToLeader.put(m.uuid(), bestLeader.uuid());
                assigned.add(m.uuid());
            }
            if (System.currentTimeMillis() > deadlineMs) break;
        }

        Map<UUID, Set<UUID>> edgeByLeader = new HashMap<>();
        for (WorkerScheduler.MobSnapshot leader : leaders) {
            List<UUID> group = leaderToGroup.getOrDefault(leader.uuid(), Collections.emptyList());
            Map<Integer, List<UUID>> sectorLists = new HashMap<>();
            for (UUID memberId : group) {
                if (memberId.equals(leader.uuid())) continue;
                WorkerScheduler.MobSnapshot m = find(mobs, memberId);
                if (m == null) continue;
                double dx = m.x() - leader.x();
                double dz = m.z() - leader.z();
                double angle = Math.atan2(dz, dx);
                int sector = (int) Math.floor(((angle + Math.PI) / (2 * Math.PI)) * sectors) % sectors;
                sectorLists.computeIfAbsent(sector, k -> new ArrayList<>()).add(memberId);
            }
            Set<UUID> edge = new HashSet<>();
            for (Map.Entry<Integer, List<UUID>> e : sectorLists.entrySet()) {
                List<UUID> ids = e.getValue();
                ids.sort((a, b) -> {
                    WorkerScheduler.MobSnapshot ma = find(mobs, a);
                    WorkerScheduler.MobSnapshot mb = find(mobs, b);
                    double da = (ma == null) ? 0 : distance(ma, leader);
                    double db = (mb == null) ? 0 : distance(mb, leader);
                    return Double.compare(db, da);
                });
                int count = Math.min(perSector, ids.size());
                for (int i = 0; i < count; i++) edge.add(ids.get(i));
            }
            if (edge.isEmpty() && group.size() > 1) {
                UUID far = null;
                double best = -1;
                for (UUID id : group) {
                    if (id.equals(leader.uuid())) continue;
                    WorkerScheduler.MobSnapshot m = find(mobs, id);
                    if (m == null) continue;
                    double d = distance(m, leader);
                    if (d > best) {
                        best = d;
                        far = id;
                    }
                }
                if (far != null) edge.add(far);
            }
            edgeByLeader.put(leader.uuid(), edge);
            if (System.currentTimeMillis() > deadlineMs) break;
        }

        Set<UUID> deserters = new HashSet<>();
        for (WorkerScheduler.MobSnapshot m : candidates) {
            if (!assigned.contains(m.uuid())) deserters.add(m.uuid());
        }

        return new WorkerScheduler.GroupComputeResult(dimension, mobToLeader, edgeByLeader, deserters);
    }

    public static List<WorkerScheduler.SoundScoreResult> computeSoundScores(List<WorkerScheduler.SoundScoreRequest> batch, long deadlineMs) {
        if (batch == null || batch.isEmpty()) return Collections.emptyList();
        List<WorkerScheduler.SoundScoreResult> out = new ArrayList<>(batch.size());
        for (WorkerScheduler.SoundScoreRequest req : batch) {
            if (System.currentTimeMillis() > deadlineMs) break;
            if (req == null || req.candidates == null || req.candidates.isEmpty()) {
                out.add(new WorkerScheduler.SoundScoreResult(req == null ? null : req.mobUuid, null, 0.0));
                continue;
            }

            Double currentScore = null;
            if (req.currentTargetSoundId != null) {
                for (WorkerScheduler.SoundCandidate c : req.candidates) {
                    if (req.currentTargetSoundId.equals(c.soundId)) {
                        currentScore = scoreCandidate(req, c);
                        break;
                    }
                }
            }

            String bestId = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (WorkerScheduler.SoundCandidate c : req.candidates) {
                double s = scoreCandidate(req, c);
                if (s > bestScore) {
                    bestScore = s;
                    bestId = c.soundId;
                }
                if (System.currentTimeMillis() > deadlineMs) break;
            }

            if (currentScore != null && req.currentTargetSoundId != null && bestId != null && !req.currentTargetSoundId.equals(bestId)) {
                if (bestScore < currentScore * req.switchRatio) {
                    bestId = req.currentTargetSoundId;
                    bestScore = currentScore;
                }
            }

            out.add(new WorkerScheduler.SoundScoreResult(req.mobUuid, bestId, bestScore == Double.NEGATIVE_INFINITY ? 0.0 : bestScore));
        }
        return out;
    }

    private static double scoreCandidate(WorkerScheduler.SoundScoreRequest req, WorkerScheduler.SoundCandidate c) {
        double dx = req.mobX - c.x;
        double dz = req.mobZ - c.z;
        double dist = Math.hypot(dx, dz);
        double effectiveRange = Math.max(0.0001, c.range);
        if (dist > effectiveRange) return Double.NEGATIVE_INFINITY;
        double proximity = Math.max(0.0, 1.0 - (dist / effectiveRange));

        double muffling = c.mufflingFactor;
        if (muffling < 0) muffling = 0; else if (muffling > 1) muffling = 1;

        double score = c.weight * proximity * (0.5 + 0.5 * muffling);

        long ageTicks = Math.max(0L, req.gameTime - c.gameTime);
        if (ageTicks <= req.noveltyTicks) {
            score += req.noveltyBonus;
        }
        return score;
    }

    private static double distance(WorkerScheduler.MobSnapshot a, WorkerScheduler.MobSnapshot b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.hypot(dx, dz);
    }

    private static WorkerScheduler.MobSnapshot find(List<WorkerScheduler.MobSnapshot> list, UUID id) {
        for (WorkerScheduler.MobSnapshot m : list) if (m.uuid().equals(id)) return m;
        return null;
    }
}
