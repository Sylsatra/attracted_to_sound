package com.example.soundattract.reload;

import com.example.soundattract.Soundattract;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class DataDrivenReloadRegistrar {

    public static void register() {
        Soundattract.LOGGER.info("[DataDrivenReloadRegistrar] Registering resource reload listeners");
    }
}
