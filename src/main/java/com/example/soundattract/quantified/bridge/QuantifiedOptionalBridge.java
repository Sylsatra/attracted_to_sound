package com.example.soundattract.quantified.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuantifiedOptionalBridge {
    @FunctionalInterface
    public interface ClassResolver {
        Class<?> resolve(String className) throws Throwable;
    }

    private static final Object INIT_LOCK = new Object();
    private static volatile Handles handles;
    private static volatile ClassResolver classResolver = Class::forName;

    private QuantifiedOptionalBridge() {
    }

    public static boolean isAvailable() {
        Handles resolved = resolveHandles();
        return resolved != null && resolved.computeMethod != null;
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> trySubmitTask(String modId,
                                                          String taskName,
                                                          Supplier<T> work,
                                                          boolean threadSafe,
                                                          boolean foreground,
                                                          Duration timeout,
                                                          String batchKey) {
        if (work == null || taskName == null || taskName.isBlank()) {
            return null;
        }
        Handles resolved = resolveHandles();
        if (resolved == null || resolved.computeMethod == null) {
            return null;
        }
        try {
            Object builder = resolved.computeMethod.invoke(taskName);
            if (resolved.workMethod != null) {
                resolved.workMethod.invoke(builder, work);
            }
            if (resolved.keyMethod != null && batchKey != null && !batchKey.isBlank()) {
                resolved.keyMethod.invoke(builder, batchKey);
            }
            if (timeout != null && resolved.timeoutMethod != null) {
                resolved.timeoutMethod.invoke(builder, timeout);
            }
            if (resolved.threadSafeMethod != null) {
                resolved.threadSafeMethod.invoke(builder, threadSafe);
            }
            if (foreground && resolved.priorityForegroundMethod != null) {
                resolved.priorityForegroundMethod.invoke(builder);
            } else if (!foreground && resolved.priorityBackgroundMethod != null) {
                resolved.priorityBackgroundMethod.invoke(builder);
            }
            return (CompletableFuture<T>) resolved.submitMethod.invoke(builder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object tryCreateParallelBuilder(String modId, String taskName, long taskKey) {
        if (taskName == null || taskName.isBlank()) {
            return null;
        }
        Handles resolved = resolveHandles();
        if (resolved == null || resolved.parallelMethod == null) {
            return null;
        }
        try {
            return resolved.parallelMethod.invoke(taskName, taskKey);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean trySetParallelSlices(Object builder, Supplier<?> supplier) {
        Handles resolved = resolveHandles();
        if (builder == null || supplier == null || resolved == null || resolved.parallelSlicesMethod == null) {
            return false;
        }
        try {
            resolved.parallelSlicesMethod.invoke(builder, supplier);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean trySetParallelSliceExecutor(Object builder, Function<?, ?> executor) {
        Handles resolved = resolveHandles();
        if (builder == null || executor == null || resolved == null || resolved.parallelSliceExecutorMethod == null) {
            return false;
        }
        try {
            resolved.parallelSliceExecutorMethod.invoke(builder, executor);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean trySetParallelMaxParallelism(Object builder, int maxParallelism) {
        Handles resolved = resolveHandles();
        if (builder == null || maxParallelism <= 0 || resolved == null || resolved.parallelMaxParallelismMethod == null) {
            return false;
        }
        try {
            resolved.parallelMaxParallelismMethod.invoke(builder, maxParallelism);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean trySetParallelBestEffort(Object builder) {
        Handles resolved = resolveHandles();
        if (builder == null || resolved == null || resolved.parallelFailurePolicyMethod == null || resolved.failurePolicyBestEffort == null) {
            return false;
        }
        try {
            resolved.parallelFailurePolicyMethod.invoke(builder, resolved.failurePolicyBestEffort);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static <S, R> boolean trySetParallelMemorySliceCache(Object builder,
                                                                String cacheName,
                                                                Function<S, String> keyFunction,
                                                                Function<R, byte[]> serializer,
                                                                Function<byte[], R> deserializer,
                                                                Duration ttl,
                                                                long maxEntries) {
        Handles resolved = resolveHandles();
        if (builder == null || cacheName == null || keyFunction == null || serializer == null || deserializer == null || resolved == null || resolved.parallelMemorySliceCacheMethod == null) {
            return false;
        }
        try {
            resolved.parallelMemorySliceCacheMethod.invoke(builder, cacheName, keyFunction, serializer, deserializer, ttl, maxEntries);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static <S, R> boolean trySetParallelPersistentSliceCache(Object builder,
                                                                    String cacheName,
                                                                    Function<S, String> keyFunction,
                                                                    Function<R, byte[]> serializer,
                                                                    Function<byte[], R> deserializer,
                                                                    Duration ttl,
                                                                    long maxEntries,
                                                                    boolean compression) {
        Handles resolved = resolveHandles();
        if (builder == null || cacheName == null || keyFunction == null || serializer == null || deserializer == null || resolved == null || resolved.parallelPersistentSliceCacheMethod == null) {
            return false;
        }
        try {
            resolved.parallelPersistentSliceCacheMethod.invoke(builder, cacheName, keyFunction, serializer, deserializer, ttl, maxEntries, compression);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> trySubmitParallel(Object builder) {
        Handles resolved = resolveHandles();
        if (builder == null || resolved == null || resolved.parallelSubmitMethod == null) {
            return null;
        }
        try {
            return (CompletableFuture<T>) resolved.parallelSubmitMethod.invoke(builder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object tryFetchCacheManager(String modId) {
        Handles resolved = resolveHandles();
        if (resolved == null || resolved.cacheMethod == null) {
            return null;
        }
        try {
            return resolved.cacheMethod.invoke();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T tryGetCached(String modId,
                                     String cacheName,
                                     String key,
                                     Supplier<T> loader,
                                     Duration ttl,
                                     long maxSize,
                                     boolean persistence) {
        if (loader == null || cacheName == null || key == null) {
            return null;
        }
        Handles resolved = resolveHandles();
        if (resolved == null || resolved.cacheMethod == null) {
            return null;
        }
        try {
            Object cacheManager = resolved.cacheMethod.invoke();
            if (cacheManager == null || resolved.cacheBucketMethod == null) {
                return null;
            }
            Object bucketBuilder = resolved.cacheBucketMethod.invoke(cacheManager, cacheName);
            if (bucketBuilder == null) {
                return null;
            }
            if (resolved.cacheTtlMethod != null && ttl != null) {
                resolved.cacheTtlMethod.invoke(bucketBuilder, ttl);
            }
            if (resolved.cacheMaxSizeMethod != null && maxSize > 0) {
                resolved.cacheMaxSizeMethod.invoke(bucketBuilder, maxSize);
            }
            if (persistence && resolved.cachePersistMethod != null) {
                resolved.cachePersistMethod.invoke(bucketBuilder);
            }
            if (resolved.cacheGetOrComputeMethod != null) {
                return (T) resolved.cacheGetOrComputeMethod.invoke(bucketBuilder, key, loader);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean trySetCacheMemoryLimit(Object manager, long limitMb) {
        Handles resolved = resolveHandles();
        if (manager == null || resolved == null || resolved.setMemoryLimitMethod == null) {
            return false;
        }
        try {
            resolved.setMemoryLimitMethod.invoke(manager, limitMb);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Boolean tryIsCacheMemoryPressureHigh(Object manager) {
        Handles resolved = resolveHandles();
        if (manager == null || resolved == null || resolved.isMemoryPressureHighMethod == null) {
            return null;
        }
        try {
            return (Boolean) resolved.isMemoryPressureHighMethod.invoke(manager);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean tryTriggerCacheCleanup(Object manager) {
        Handles resolved = resolveHandles();
        if (manager == null || resolved == null || resolved.triggerCleanupMethod == null) {
            return false;
        }
        try {
            resolved.triggerCleanupMethod.invoke(manager);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setClassResolverForTests(ClassResolver resolver) {
        classResolver = resolver == null ? Class::forName : resolver;
        handles = null;
    }

    public static void resetForTests() {
        classResolver = Class::forName;
        handles = null;
    }

    private static Handles resolveHandles() {
        Handles current = handles;
        if (current != null) {
            return current.available ? current : null;
        }
        synchronized (INIT_LOCK) {
            current = handles;
            if (current != null) {
                return current.available ? current : null;
            }
            Handles loaded = loadHandles();
            handles = loaded;
            return loaded.available ? loaded : null;
        }
    }

    private static Handles loadHandles() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> apiClass = classResolver.resolve("org.admany.quantified.api.QuantifiedAPI");
            Class<?> parallelComputeClass = classResolver.resolve("org.admany.quantified.api.parallel.ParallelCompute");
            Class<?> parallelBuilderClass = classResolver.resolve("org.admany.quantified.api.parallel.ParallelCompute$Builder");
            Class<?> managerInterface = classResolver.resolve("org.admany.quantified.api.interfaces.ModCacheManager");
            Class<?> cacheBucketClass = classResolver.resolve("org.admany.quantified.api.cache.CacheBucket$Builder");

            Handles loaded = new Handles();
            loaded.available = true;
            loaded.computeMethod = lookup.findStatic(apiClass, "compute", MethodType.methodType(cacheBucketClass, String.class));
            loaded.workMethod = lookup.findVirtual(cacheBucketClass, "work", MethodType.methodType(cacheBucketClass, Supplier.class));
            loaded.keyMethod = lookup.findVirtual(cacheBucketClass, "key", MethodType.methodType(cacheBucketClass, String.class));
            loaded.timeoutMethod = lookup.findVirtual(cacheBucketClass, "timeout", MethodType.methodType(cacheBucketClass, Duration.class));
            loaded.threadSafeMethod = lookup.findVirtual(cacheBucketClass, "threadSafe", MethodType.methodType(cacheBucketClass, boolean.class));
            loaded.priorityForegroundMethod = lookup.findVirtual(cacheBucketClass, "priorityForeground", MethodType.methodType(cacheBucketClass));
            loaded.priorityBackgroundMethod = lookup.findVirtual(cacheBucketClass, "priorityBackground", MethodType.methodType(cacheBucketClass));
            loaded.submitMethod = lookup.findVirtual(cacheBucketClass, "submit", MethodType.methodType(CompletableFuture.class));
            loaded.parallelMethod = lookup.findStatic(parallelComputeClass, "builder", MethodType.methodType(parallelBuilderClass, String.class, long.class));
            loaded.parallelSlicesMethod = lookup.findVirtual(parallelBuilderClass, "slices", MethodType.methodType(parallelBuilderClass, Supplier.class));
            loaded.parallelSliceExecutorMethod = lookup.findVirtual(parallelBuilderClass, "sliceExecutor", MethodType.methodType(parallelBuilderClass, Function.class));
            loaded.parallelMaxParallelismMethod = lookup.findVirtual(parallelBuilderClass, "maxParallelism", MethodType.methodType(parallelBuilderClass, int.class));
            loaded.parallelMemorySliceCacheMethod = lookup.findVirtual(parallelBuilderClass, "memorySliceCache", MethodType.methodType(parallelBuilderClass, String.class, Function.class, Function.class, Function.class, Duration.class, long.class));
            loaded.parallelPersistentSliceCacheMethod = lookup.findVirtual(parallelBuilderClass, "persistentSliceCache", MethodType.methodType(parallelBuilderClass, String.class, Function.class, Function.class, Function.class, Duration.class, long.class, boolean.class));
            loaded.parallelSubmitMethod = lookup.findVirtual(parallelBuilderClass, "submit", MethodType.methodType(CompletableFuture.class));
            loaded.cacheMethod = lookup.findStatic(apiClass, "cache", MethodType.methodType(managerInterface));
            loaded.cacheBucketMethod = lookup.findVirtual(managerInterface, "bucket", MethodType.methodType(cacheBucketClass, String.class));
            loaded.cacheTtlMethod = lookup.findVirtual(cacheBucketClass, "ttl", MethodType.methodType(cacheBucketClass, Duration.class));
            loaded.cacheMaxSizeMethod = lookup.findVirtual(cacheBucketClass, "maxSize", MethodType.methodType(cacheBucketClass, long.class));
            loaded.cachePersistMethod = lookup.findVirtual(cacheBucketClass, "persist", MethodType.methodType(cacheBucketClass));
            loaded.cacheGetOrComputeMethod = lookup.findVirtual(cacheBucketClass, "getOrCompute", MethodType.methodType(Object.class, String.class, Supplier.class));
            loaded.setMemoryLimitMethod = lookup.findVirtual(managerInterface, "setMemoryLimitMB", MethodType.methodType(void.class, long.class));
            loaded.isMemoryPressureHighMethod = lookup.findVirtual(managerInterface, "isMemoryPressureHigh", MethodType.methodType(boolean.class));
            loaded.triggerCleanupMethod = lookup.findVirtual(managerInterface, "triggerMemoryPressureCleanup", MethodType.methodType(void.class));
            try {
                Class<?> failurePolicyClass = classResolver.resolve("org.admany.quantified.core.common.parallel.policy.ParallelFailurePolicy");
                loaded.failurePolicyBestEffort = Enum.valueOf((Class<? extends Enum>) failurePolicyClass.asSubclass(Enum.class), "BEST_EFFORT");
                loaded.parallelFailurePolicyMethod = lookup.findVirtual(parallelBuilderClass, "failurePolicy", MethodType.methodType(parallelBuilderClass, failurePolicyClass));
            } catch (Throwable ignored) {
                loaded.failurePolicyBestEffort = null;
                loaded.parallelFailurePolicyMethod = null;
            }
            return loaded;
        } catch (Throwable ignored) {
            return new Handles();
        }
    }

    private static final class Handles {
        private boolean available;
        private MethodHandle computeMethod;
        private MethodHandle workMethod;
        private MethodHandle keyMethod;
        private MethodHandle timeoutMethod;
        private MethodHandle threadSafeMethod;
        private MethodHandle priorityForegroundMethod;
        private MethodHandle priorityBackgroundMethod;
        private MethodHandle submitMethod;
        private MethodHandle parallelMethod;
        private MethodHandle parallelSlicesMethod;
        private MethodHandle parallelSliceExecutorMethod;
        private MethodHandle parallelMaxParallelismMethod;
        private MethodHandle parallelFailurePolicyMethod;
        private Object failurePolicyBestEffort;
        private MethodHandle parallelMemorySliceCacheMethod;
        private MethodHandle parallelPersistentSliceCacheMethod;
        private MethodHandle parallelSubmitMethod;
        private MethodHandle cacheMethod;
        private MethodHandle cacheBucketMethod;
        private MethodHandle cacheTtlMethod;
        private MethodHandle cacheMaxSizeMethod;
        private MethodHandle cachePersistMethod;
        private MethodHandle cacheGetOrComputeMethod;
        private MethodHandle setMemoryLimitMethod;
        private MethodHandle isMemoryPressureHighMethod;
        private MethodHandle triggerCleanupMethod;
    }
}
