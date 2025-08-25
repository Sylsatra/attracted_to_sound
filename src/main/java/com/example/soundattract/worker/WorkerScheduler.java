package com.example.soundattract.worker;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorkerScheduler {
    private static final BlockingQueue<GroupComputeResult> GROUP_RESULTS = new LinkedBlockingQueue<>();
    private static final BlockingQueue<SoundScoreResult> SOUND_RESULTS = new LinkedBlockingQueue<>();
    private static volatile ExecutorService EXECUTOR;
    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    private WorkerScheduler() {}

    private static void ensureInit() {
        if (INIT.get()) return;
        synchronized (WorkerScheduler.class) {
            if (INIT.get()) return;
            int threads = 2;
            try {
                Integer configured = SoundAttractConfig.COMMON.workerThreads.get();
                if (configured != null) threads = Math.max(1, configured);
            } catch (Throwable ignored) {}
            EXECUTOR = new ThreadPoolExecutor(
                    threads,
                    threads,
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(1024),
                    r -> {
                        Thread t = new Thread(r, "SoundAttract-Worker");
                        t.setDaemon(true);
                        return t;
                    },
                    (r, executor) -> {
                        new ThreadPoolExecutor.DiscardOldestPolicy().rejectedExecution(r, executor);
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.warn("[WorkerScheduler] Task dropped due to saturation (DiscardOldestPolicy)");
                        }
                    }
            );
            INIT.set(true);
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[WorkerScheduler] Initialized with {} threads", threads);
            }
        }
    }

    public static Future<?> submitGroupCompute(List<MobSnapshot> mobs, ConfigSnapshot cfg) {
        ensureInit();
        long computedDeadline = System.currentTimeMillis() + 10L;
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = budget.longValue();
                computedDeadline = System.currentTimeMillis() + Math.max(5L, cfgBudget);
            }
        } catch (Throwable ignored) {}
        final long deadlineMs = computedDeadline;
        return EXECUTOR.submit(() -> {
            try {
                GroupComputeResult result = computeGroups(mobs, cfg, deadlineMs);
                if (result != null) GROUP_RESULTS.offer(result);
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.error("[WorkerScheduler] Group compute failed", t);
                }
            }
        });
    }

    public static List<GroupComputeResult> drainGroupResults() {
        List<GroupComputeResult> list = new ArrayList<>();
        GROUP_RESULTS.drainTo(list);
        return list;
    }

    public static Future<?> submitSoundScore(List<SoundScoreRequest> batch) {
        if (batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);
        ensureInit();
        long computedDeadline = System.currentTimeMillis() + 10L;
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = budget.longValue();
                computedDeadline = System.currentTimeMillis() + Math.max(5L, cfgBudget);
            }
        } catch (Throwable ignored) {}
        final long deadlineMs = computedDeadline;
        return EXECUTOR.submit(() -> {
            try {
                computeSoundScores(batch, deadlineMs);
            } catch (Throwable t) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.error("[WorkerScheduler] Sound scoring failed", t);
                }
            }
        });
    }

    public static List<SoundScoreResult> drainSoundScoreResults() {
        List<SoundScoreResult> list = new ArrayList<>();
        SOUND_RESULTS.drainTo(list);
        return list;
    }

    private static GroupComputeResult computeGroups(List<MobSnapshot> mobs, ConfigSnapshot cfg, long deadlineMs) {
        if (mobs == null || mobs.isEmpty()) return null;
        List<MobSnapshot> candidates = new ArrayList<>();
        for (MobSnapshot m : mobs) {
            if (m.alive()) candidates.add(m);
        }
        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingDouble(MobSnapshot::health).reversed());

        List<MobSnapshot> leaders = new ArrayList<>();
        Map<UUID, UUID> mobToLeader = new HashMap<>();

        double groupRadius = cfg.leaderGroupRadius();
        double leaderSpacing = groupRadius * cfg.leaderSpacingMultiplier();
        int maxLeaders = cfg.maxLeaders();
        int maxGroupSize = cfg.maxGroupSize();
        int sectors = cfg.numEdgeSectors();

        for (MobSnapshot p : candidates) {
            if (leaders.size() >= maxLeaders) break;
            boolean tooClose = false;
            for (MobSnapshot l : leaders) {
                double dx = p.x() - l.x();
                double dz = p.z() - l.z();
                double dist2 = dx*dx + dz*dz;
                if (dist2 < (leaderSpacing * leaderSpacing)) { tooClose = true; break; }
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
        for (MobSnapshot l : leaders) {
            leaderToGroup.put(l.uuid(), new ArrayList<>(Collections.singletonList(l.uuid())));
        }

        Set<UUID> assigned = new HashSet<>();
        for (MobSnapshot m : candidates) {
            if (leaders.contains(m)) { assigned.add(m.uuid()); mobToLeader.put(m.uuid(), m.uuid()); continue; }
            MobSnapshot bestLeader = null;
            double bestDist = Double.MAX_VALUE;
            for (MobSnapshot l : leaders) {
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
        for (MobSnapshot leader : leaders) {
            List<UUID> group = leaderToGroup.getOrDefault(leader.uuid(), Collections.emptyList());
            Map<Integer, List<UUID>> sectorLists = new HashMap<>();
            for (UUID memberId : group) {
                if (memberId.equals(leader.uuid())) continue;
                MobSnapshot m = find(mobs, memberId);
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
                    MobSnapshot ma = find(mobs, a);
                    MobSnapshot mb = find(mobs, b);
                    double da = (ma==null)?0:distance(ma, leader);
                    double db = (mb==null)?0:distance(mb, leader);
                    return Double.compare(db, da);
                });
                int count = Math.min(4, ids.size());
                for (int i=0;i<count;i++) edge.add(ids.get(i));
            }
            if (edge.isEmpty() && group.size() > 1) {
                UUID far = null; double best = -1;
                for (UUID id : group) {
                    if (id.equals(leader.uuid())) continue;
                    MobSnapshot m = find(mobs, id);
                    if (m == null) continue;
                    double d = distance(m, leader);
                    if (d > best) { best = d; far = id; }
                }
                if (far != null) edge.add(far);
            }
            edgeByLeader.put(leader.uuid(), edge);
            if (System.currentTimeMillis() > deadlineMs) break;
        }

        Set<UUID> deserters = new HashSet<>();
        for (MobSnapshot m : candidates) {
            if (!assigned.contains(m.uuid())) deserters.add(m.uuid());
        }

        return new GroupComputeResult(mobToLeader, edgeByLeader, deserters);
    }

    private static void computeSoundScores(List<SoundScoreRequest> batch, long deadlineMs) {
        for (SoundScoreRequest req : batch) {
            if (System.currentTimeMillis() > deadlineMs) break;
            if (req == null || req.candidates == null || req.candidates.isEmpty()) {
                SOUND_RESULTS.offer(new SoundScoreResult(req == null ? null : req.mobUuid, null, 0.0));
                continue;
            }

            Double currentScore = null;
            if (req.currentTargetSoundId != null) {
                for (SoundCandidate c : req.candidates) {
                    if (req.currentTargetSoundId.equals(c.soundId)) {
                        currentScore = scoreCandidate(req, c);
                        break;
                    }
                }
            }

            String bestId = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (SoundCandidate c : req.candidates) {
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

            SOUND_RESULTS.offer(new SoundScoreResult(req.mobUuid, bestId, bestScore == Double.NEGATIVE_INFINITY ? 0.0 : bestScore));
        }
    }

    private static double scoreCandidate(SoundScoreRequest req, SoundCandidate c) {
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

    private static double distance(MobSnapshot a, MobSnapshot b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.hypot(dx, dz);
    }

    private static MobSnapshot find(List<MobSnapshot> list, UUID id) {
        for (MobSnapshot m : list) if (m.uuid().equals(id)) return m;
        return null;
    }

    public record MobSnapshot(UUID uuid, double x, double y, double z, double health, boolean alive) {}

    public record ConfigSnapshot(double leaderGroupRadius,
                                 int maxLeaders,
                                 int maxGroupSize,
                                 double leaderSpacingMultiplier,
                                 int numEdgeSectors) {}

    public record GroupComputeResult(Map<UUID, UUID> mobUuidToLeaderUuid,
                                     Map<UUID, Set<UUID>> edgeMobsByLeaderUuid,
                                     Set<UUID> deserterUuids) {}

    public static final class SoundScoreRequest {
        public final UUID mobUuid;
        public final double mobX, mobY, mobZ;
        public final long gameTime;
        public final String currentTargetSoundId; 
        public final List<SoundCandidate> candidates;
        public final double switchRatio;
        public final double noveltyBonus;
        public final int noveltyTicks;

        public SoundScoreRequest(UUID mobUuid, double mobX, double mobY, double mobZ,
                                 long gameTime, String currentTargetSoundId,
                                 List<SoundCandidate> candidates,
                                 double switchRatio, double noveltyBonus, int noveltyTicks) {
            this.mobUuid = mobUuid;
            this.mobX = mobX; this.mobY = mobY; this.mobZ = mobZ;
            this.gameTime = gameTime;
            this.currentTargetSoundId = currentTargetSoundId;
            this.candidates = candidates == null ? Collections.emptyList() : candidates;
            this.switchRatio = switchRatio;
            this.noveltyBonus = noveltyBonus;
            this.noveltyTicks = noveltyTicks;
        }
    }

    public static final class SoundCandidate {
        public final String soundId;
        public final double x, y, z;
        public final long gameTime; 
        public final double range;  
        public final double weight; 
        public final double mufflingFactor; 

        public SoundCandidate(String soundId, double x, double y, double z, long gameTime,
                              double range, double weight, double mufflingFactor) {
            this.soundId = soundId;
            this.x = x; this.y = y; this.z = z;
            this.gameTime = gameTime;
            this.range = range;
            this.weight = weight;
            this.mufflingFactor = mufflingFactor;
        }
    }

    public record SoundScoreResult(UUID mobUuid, String soundId, double score) {}
}
