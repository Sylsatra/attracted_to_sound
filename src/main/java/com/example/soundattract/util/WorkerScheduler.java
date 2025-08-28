package com.example.soundattract.util;

import com.example.soundattract.SoundAttractMod;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * WorkerScheduler: bounded daemon thread pool for off-thread computations.
 *
 * Goals:
 * - Never block the server thread submitting tasks
 * - Drop oldest when saturated (bounded queue + DiscardOldestPolicy)
 * - Collect results in lock-free queues and apply them on the main thread
 * - Keep world/entity access strictly on the main thread
 */
public final class WorkerScheduler {

    private static final int DEFAULT_QUEUE_CAPACITY = 1024;
    private static final int DEFAULT_THREADS = Math.max(1, Math.min(32, Runtime.getRuntime().availableProcessors() - 1));


    private static final ConcurrentLinkedQueue<SoundScoreResult> SOUND_RESULTS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<GroupComputeResult> GROUP_RESULTS = new ConcurrentLinkedQueue<>();

    private static volatile ThreadPoolExecutor EXECUTOR;

    private WorkerScheduler() {}

    private static void ensureInitialized() {
        if (EXECUTOR != null) return;
        synchronized (WorkerScheduler.class) {
            if (EXECUTOR != null) return;

            int threads = DEFAULT_THREADS;
            int capacity = DEFAULT_QUEUE_CAPACITY;
            if (SoundAttractMod.CONFIG != null) {
                int cfgThreads = SoundAttractMod.CONFIG.workerThreads;
                threads = (cfgThreads <= 0) ? DEFAULT_THREADS : Math.max(1, Math.min(64, cfgThreads));
                int cfgCap = SoundAttractMod.CONFIG.workerQueueCapacity;
                capacity = (cfgCap <= 0) ? DEFAULT_QUEUE_CAPACITY : Math.max(64, Math.min(16384, cfgCap));
            }

            BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(capacity);

            ThreadFactory tf = new ThreadFactory() {
                private final AtomicInteger idx = new AtomicInteger(1);
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "SoundAttract-Worker-" + idx.getAndIncrement());
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                }
            };

            RejectedExecutionHandler rejector = new ThreadPoolExecutor.DiscardOldestPolicy() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                    if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                        SoundAttractMod.LOGGER.debug("[WorkerScheduler] Queue saturated. Discarding oldest task.");
                    }
                    super.rejectedExecution(r, e);
                }
            };

            EXECUTOR = new ThreadPoolExecutor(
                threads,
                threads,
                60L, TimeUnit.SECONDS,
                queue,
                tf,
                rejector
            );
            EXECUTOR.allowCoreThreadTimeOut(true);

            if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                SoundAttractMod.LOGGER.info("[WorkerScheduler] Initialized with {} threads, capacity {}.", threads, capacity);
            }
        }
    }

    public static void shutdown() {
        ThreadPoolExecutor exec = EXECUTOR;
        if (exec != null) {
            exec.shutdownNow();
            EXECUTOR = null;
        }
        SOUND_RESULTS.clear();
        GROUP_RESULTS.clear();
    }



    public static void submitSoundTask(Callable<SoundScoreResult> task) {
        Objects.requireNonNull(task, "task");
        ensureInitialized();
        EXECUTOR.execute(() -> {
            try {
                SoundScoreResult result = task.call();
                if (result != null) SOUND_RESULTS.add(result);
            } catch (Throwable t) {
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.warn("[WorkerScheduler] Sound task failed: {}", t.toString());
                }
            }
        });
    }

    public static void submitGroupTask(Callable<GroupComputeResult> task) {
        Objects.requireNonNull(task, "task");
        ensureInitialized();
        EXECUTOR.execute(() -> {
            try {
                GroupComputeResult result = task.call();
                if (result != null) GROUP_RESULTS.add(result);
            } catch (Throwable t) {
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.warn("[WorkerScheduler] Group task failed: {}", t.toString());
                }
            }
        });
    }



    public static int drainSoundResults(Consumer<SoundScoreResult> applier, long budgetMillis) {
        return drainQueue(SOUND_RESULTS, applier, budgetMillis);
    }

    public static int drainGroupResults(Consumer<GroupComputeResult> applier, long budgetMillis) {
        return drainQueue(GROUP_RESULTS, applier, budgetMillis);
    }

    private static <T> int drainQueue(ConcurrentLinkedQueue<T> q, Consumer<T> applier, long budgetMillis) {
        Objects.requireNonNull(applier, "applier");
        long start = System.nanoTime();
        int applied = 0;
        while (true) {
            T item = q.poll();
            if (item == null) break;
            try {
                applier.accept(item);
            } catch (Throwable t) {
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.warn("[WorkerScheduler] Result apply failed: {}", t.toString());
                }
            }
            applied++;
            if (budgetMillis > 0) {
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                if (elapsedMs >= budgetMillis) break;
            }
        }
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            SoundAttractMod.LOGGER.debug("[WorkerScheduler] Drained {} results in {} ms. Remaining: {}", applied, elapsedMs, q.size());
        }
        return applied;
    }



    public static final class SoundScoreResult {
        public final UUID mobUuid;
        public final String dimensionKey;
        public final com.example.soundattract.SoundTracker.SoundRecord best;
        public final long createdAtNanos;

        public SoundScoreResult(UUID mobUuid, String dimensionKey, com.example.soundattract.SoundTracker.SoundRecord best) {
            this.mobUuid = mobUuid;
            this.dimensionKey = dimensionKey;
            this.best = best;
            this.createdAtNanos = System.nanoTime();
        }

        public UUID getMobUuid() {
            return mobUuid;
        }

        public com.example.soundattract.SoundTracker.SoundRecord getBest() {
            return best;
        }

        public String getDimensionKey() {
            return dimensionKey;
        }
    }

    public static final class GroupComputeResult {
        public final Object data;
        public GroupComputeResult(Object data) { this.data = data; }
    }
}
