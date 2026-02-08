package com.example.soundattract.quantified;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.worker.LocalWorkScheduler;
import com.example.soundattract.worker.SoundAttractWorkScheduler;
import com.example.soundattract.worker.WorkerComputations;
import com.example.soundattract.worker.WorkerScheduler;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuantifiedWorkScheduler implements SoundAttractWorkScheduler {
    private final BlockingQueue<WorkerScheduler.GroupComputeResult> groupResults = new LinkedBlockingQueue<>();
    private final BlockingQueue<WorkerScheduler.SoundScoreResult> soundResults = new LinkedBlockingQueue<>();

    private final LocalWorkScheduler fallback = new LocalWorkScheduler();

    private final Method register;
    private final Method submit;

    public QuantifiedWorkScheduler() {
        try {
            Class<?> api = Class.forName("org.admany.quantified.api.QuantifiedAPI");
            this.register = api.getMethod("register", String.class);
            this.submit = api.getMethod("submit", Class.forName("org.admany.quantified.api.model.QuantifiedTask$Builder"));
            this.register.invoke(null, SoundAttractMod.MOD_ID);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize QuantifiedWorkScheduler", e);
        }
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

        CompletableFuture<?> future;
        CompletableFuture<List<WorkerScheduler.SoundScoreResult>> parallel = ParallelComputeBridge.trySubmitSoundScores(batch, deadlineMs, timeout);
        if (parallel != null) {
            future = parallel;
        } else {
            future = submitFuture(
                "soundattract_sound_score",
                () -> WorkerComputations.computeSoundScores(batch, deadlineMs),
                timeout
            );
        }
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
            Class<?> taskClass = Class.forName("org.admany.quantified.api.model.QuantifiedTask");
            Object builder = taskClass.getMethod("builder", String.class, String.class, java.util.function.Supplier.class)
                    .invoke(null, SoundAttractMod.MOD_ID, taskName, work);
            
            builder.getClass().getMethod("priorityBackground").invoke(builder);
            builder.getClass().getMethod("threadSafe", boolean.class).invoke(builder, true);
            
            if (timeout != null) {
                builder.getClass().getMethod("timeout", Duration.class).invoke(builder, timeout);
            }
            
            return (CompletableFuture<?>) submit.invoke(null, builder);
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

    private static final class ParallelComputeBridge {
        private static final Object INIT_LOCK = new Object();
        private static boolean initialized = false;

        private static Method parallelBuilderFactory;
        private static Method slicesMethod;
        private static Method sliceExecutorMethod;
        private static Method maxParallelismMethod;
        private static Method failurePolicyMethod;
        private static Object failurePolicyBestEffort;
        private static Method submitParallelMethod;

        private static boolean ensureInit() {
            if (initialized) {
                return submitParallelMethod != null;
            }
            synchronized (INIT_LOCK) {
                if (initialized) {
                    return submitParallelMethod != null;
                }
                try {
                    Class<?> parallelComputeClass = Class.forName("org.admany.quantified.api.parallel.ParallelCompute");
                    parallelBuilderFactory = parallelComputeClass.getMethod("builder", String.class, String.class, long.class);

                    Class<?> builderClass = Class.forName("org.admany.quantified.api.parallel.ParallelCompute$Builder");
                    slicesMethod = builderClass.getMethod("slices", Supplier.class);
                    sliceExecutorMethod = builderClass.getMethod("sliceExecutor", Function.class);
                    maxParallelismMethod = builderClass.getMethod("maxParallelism", int.class);

                    try {
                        Class<?> failurePolicyClass = Class.forName("org.admany.quantified.core.common.parallel.policy.ParallelFailurePolicy");
                        failurePolicyBestEffort = Enum.valueOf((Class<? extends Enum>) failurePolicyClass.asSubclass(Enum.class), "BEST_EFFORT");
                        failurePolicyMethod = builderClass.getMethod("failurePolicy", failurePolicyClass);
                    } catch (Throwable ignored) {
                        failurePolicyBestEffort = null;
                        failurePolicyMethod = null;
                    }

                    submitParallelMethod = builderClass.getMethod("submit");
                } catch (Throwable t) {
                    submitParallelMethod = null;
                }
                initialized = true;
                return submitParallelMethod != null;
            }
        }

        private static CompletableFuture<List<WorkerScheduler.SoundScoreResult>> trySubmitSoundScores(
            List<WorkerScheduler.SoundScoreRequest> batch,
            long deadlineMs,
            Duration timeout
        ) {
            if (batch == null || batch.isEmpty()) {
                return CompletableFuture.completedFuture(java.util.Collections.emptyList());
            }
            if (!ensureInit()) {
                return null;
            }
            try {
                long taskKey = System.nanoTime();
                Object builder = parallelBuilderFactory.invoke(null, SoundAttractMod.MOD_ID, "soundattract_sound_score_parallel", taskKey);

                Supplier<List<WorkerScheduler.SoundScoreRequest>> supplier = () -> batch;
                slicesMethod.invoke(builder, supplier);

                Function<WorkerScheduler.SoundScoreRequest, WorkerScheduler.SoundScoreResult> scorer = req -> {
                    try {
                        return WorkerComputations.computeSoundScore(req, deadlineMs);
                    } catch (Throwable t) {
                        return new WorkerScheduler.SoundScoreResult(req == null ? null : req.mobUuid, null, 0.0);
                    }
                };
                sliceExecutorMethod.invoke(builder, scorer);

                int maxParallelism = 0;
                try {
                    maxParallelism = SoundAttractConfig.COMMON.workerThreads.get();
                } catch (Throwable ignored) {
                }
                if (maxParallelism > 0) {
                    maxParallelismMethod.invoke(builder, maxParallelism);
                }

                if (failurePolicyMethod != null && failurePolicyBestEffort != null) {
                    failurePolicyMethod.invoke(builder, failurePolicyBestEffort);
                }

                CompletableFuture<List<WorkerScheduler.SoundScoreResult>> future = (CompletableFuture<List<WorkerScheduler.SoundScoreResult>>) submitParallelMethod.invoke(builder);
                if (future == null) {
                    return null;
                }
                if (timeout != null) {
                    long ms = Math.max(1L, timeout.toMillis());
                    return future.orTimeout(ms, TimeUnit.MILLISECONDS);
                }
                return future;
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
