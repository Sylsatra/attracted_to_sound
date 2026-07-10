package com.example.soundattract.reload;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public abstract class JsonMapReloadListener extends ContextAwareReloadListener {
    private static final Gson GSON = new Gson();
    private final String directory;

    protected JsonMapReloadListener(String directory) {
        this.directory = directory;
    }

    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> elements = new HashMap<>();
        Map<Identifier, Resource> resources = resourceManager.listResources(directory, id -> id.getPath().endsWith(".json"));

        resources.forEach((fileId, resource) -> {
            String path = fileId.getPath();
            String prefix = directory + "/";
            if (!path.startsWith(prefix) || !path.endsWith(".json")) {
                return;
            }

            Identifier elementId = Identifier.tryBuild(fileId.getNamespace(), path.substring(prefix.length(), path.length() - ".json".length()));
            if (elementId == null) {
                return;
            }

            try (BufferedReader reader = resource.openAsReader()) {
                elements.put(elementId, GSON.fromJson(reader, JsonElement.class));
            } catch (Exception ignored) {
            }
        });

        return elements;
    }

    @Override
    public final CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor, PreparationBarrier barrier, Executor gameExecutor) {
        ResourceManager resourceManager = sharedState.resourceManager();
        ProfilerFiller profiler = InactiveProfiler.INSTANCE;
        return CompletableFuture
                .supplyAsync(() -> prepare(resourceManager, profiler), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(elements -> apply(elements, resourceManager, profiler), gameExecutor);
    }

    protected abstract void apply(Map<Identifier, JsonElement> elements, ResourceManager resourceManager, ProfilerFiller profiler);
}
