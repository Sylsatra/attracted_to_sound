package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.worker.LocalWorkScheduler;
import com.example.soundattract.worker.SoundAttractWorkScheduler;
import com.example.soundattract.worker.WorkerComputations;
import com.example.soundattract.worker.WorkerScheduler;
import net.minecraft.resources.ResourceLocation;
import org.admany.quantified.api.QuantifiedAPI;
import org.admany.quantified.api.model.QuantifiedTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

public final class QuantifiedWorkScheduler implements SoundAttractWorkScheduler {
    private final BlockingQueue<WorkerScheduler.GroupComputeResult> groupResults = new LinkedBlockingQueue<>();
    private final BlockingQueue<WorkerScheduler.SoundScoreResult> soundResults = new LinkedBlockingQueue<>();

    private final LocalWorkScheduler fallback = new LocalWorkScheduler();

    public QuantifiedWorkScheduler() {
        QuantifiedAPI.register(SoundAttractMod.MOD_ID);
    }

    @Override
    public Future<?> submitGroupCompute(List<WorkerScheduler.MobSnapshot> mobs, WorkerScheduler.ConfigSnapshot cfg, ResourceLocation dimension) {
        long deadlineMs = computeDeadlineMs();
        Duration timeout = computeTimeout();

        CompletableFuture<?> future = submitFuture(
            "soundattract_group_compute",
            () -> WorkerComputations.computeGroups(mobs, cfg, deadlineMs, dimension),
            timeout
        );
        return future.handle((result, throwable) -> {
            if (throwable != null || result == null) {
                return this.fallback.submitGroupCompute(mobs, cfg, dimension);
            }
            if (result instanceof WorkerScheduler.GroupComputeResult r && r.dimension() != null) {
                this.groupResults.offer(r);
            }
            return null;
        });
    }

    @Override
    public Future<?> submitSoundScore(List<WorkerScheduler.SoundScoreRequest> batch) {
        if (batch == null || batch.isEmpty()) return CompletableFuture.completedFuture(null);

        long deadlineMs = computeDeadlineMs();
        Duration timeout = computeTimeout();

        CompletableFuture<?> future = submitFuture(
            "soundattract_sound_score",
            () -> WorkerComputations.computeSoundScores(batch, deadlineMs),
            timeout
        );
        return future.handle((result, throwable) -> {
            if (throwable != null) {
                return this.fallback.submitSoundScore(batch);
            }
            if (result == null) {
                return this.fallback.submitSoundScore(batch);
            }
            if (result instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof WorkerScheduler.SoundScoreResult r) {
                        this.soundResults.offer(r);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<WorkerScheduler.GroupComputeResult> drainGroupResults() {
        List<WorkerScheduler.GroupComputeResult> out = new ArrayList<>();
        this.groupResults.drainTo(out);
        out.addAll(WorkerScheduler.drainGroupResults());
        return out;
    }

    @Override
    public List<WorkerScheduler.SoundScoreResult> drainSoundScoreResults() {
        List<WorkerScheduler.SoundScoreResult> out = new ArrayList<>();
        this.soundResults.drainTo(out);
        out.addAll(WorkerScheduler.drainSoundScoreResults());
        return out;
    }

    private CompletableFuture<?> submitFuture(String taskName, java.util.function.Supplier<?> work, Duration timeout) {
        try {
            QuantifiedTask.Builder<?> builder = QuantifiedTask.builder(SoundAttractMod.MOD_ID, taskName, work)
                .priorityBackground()
                .threadSafe(true);
            if (timeout != null) {
                builder.timeout(timeout);
            }
            return QuantifiedAPI.submit(builder);
        } catch (Throwable t) {
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(t);
            return failed;
        }
    }

    private long computeDeadlineMs() {
        long computed = System.currentTimeMillis() + 10L;
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = budget.longValue();
                computed = System.currentTimeMillis() + Math.max(5L, cfgBudget);
            }
        } catch (Throwable ignored) {}
        return computed;
    }

    private Duration computeTimeout() {
        try {
            Integer budget = SoundAttractConfig.COMMON.workerTaskBudgetMs.get();
            if (budget != null) {
                long cfgBudget = Math.max(5L, budget.longValue());
                return Duration.ofMillis(cfgBudget);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
