package com.example.soundattract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.soundattract.ai.BlockBreakerManager;
import com.example.soundattract.ai.MobCellAssignmentHooks;
import com.example.soundattract.config.ConfigLoader;
import com.example.soundattract.config.SoundAttractConfigData;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.integration.PlasmoIntegration;
import com.example.soundattract.integration.PointBlankIntegrationHandler;
import com.example.soundattract.integration.VanillaIntegrationEvents;
import com.example.soundattract.logic.SoundMessageHandler;
import com.example.soundattract.loot.ModLootTables;
import com.example.soundattract.network.SimpleNbtSyncPayload;
import com.example.soundattract.util.WorkerScheduler;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import su.plo.voice.api.server.PlasmoVoiceServer;

import java.util.Optional;

/**
 * Main mod class.
 */
public class SoundAttractMod implements ModInitializer {
    public static final String MOD_ID = "soundattract";
        public static final MemoryModuleType<BlockPos> SOUND_ATTRACTION_MEMORY = Registry.register(
            Registries.MEMORY_MODULE_TYPE,
            Identifier.of(MOD_ID, "sound_attraction_memory"),
            new MemoryModuleType<>(Optional.of(BlockPos.CODEC))
    );
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static SoundAttractConfigData CONFIG;

    private final double tpsSmoothingFactor = 0.05;
    private long lastTickTimeNanos = 0L;
    private double averageTickTimeNanos = 50_000_000.0;
    private PlasmoIntegration plasmo;

    @Override
    public void onInitialize() {
        ConfigReloadListener.reloadConfig();
        if (CONFIG == null) {
            LOGGER.error("Failed to load SoundAttractMod configuration! Using default or limited functionality.");
            CONFIG = new SoundAttractConfigData();
        }

        if (CONFIG.debugLogging) {
            LOGGER.info("[SoundAttract] Initializing. Environment: {}, Thread: {}",
                    FabricLoader.getInstance().getEnvironmentType(),
                    Thread.currentThread().getName());
            LOGGER.info("[SoundAttract] Debug logging is ENABLED.");
        }


        ModEnchantments.register();
        ModLootTables.register();
        MobCellAssignmentHooks.register();
        ConfigReloadListener.registerCommand();
        VanillaIntegrationEvents.register();

        StealthDetectionEvents.register();

        registerNetworkHandlers();

        PointBlankIntegrationHandler();

        if (FabricLoader.getInstance().isModLoaded("plasmovoice")) {
            LOGGER.info("[SoundAttract] Plasmo Voice mod found. Preparing integration.");
            this.plasmo = new PlasmoIntegration();
        }

        registerServerLifecycleEvents();

        registerTickEvents();
        registerEntityEvents();

        if (CONFIG.debugLogging) {
            LOGGER.info("[SoundAttract] Initialization complete.");
            LOGGER.info("[DEBUG] Registered Packet Payloads (Server Perspective):");
            LOGGER.info("[DEBUG]   SoundMessagePayload ID: {}", SoundMessagePayload.ID.id());
            LOGGER.info("[DEBUG]   SimpleNbtSyncPayload ID: {}", SimpleNbtSyncPayload.ID.id());
        }
    }

    private void registerNetworkHandlers() {
        PayloadTypeRegistry.playC2S().register(SoundMessagePayload.ID, SoundMessagePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SoundMessagePayload.ID, (payload, context) -> {
            context.server().execute(() -> SoundMessageHandler.handle(payload, context.player()));
        });

        PayloadTypeRegistry.playC2S().register(SimpleNbtSyncPayload.ID, SimpleNbtSyncPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SimpleNbtSyncPayload.ID, (payload, context) -> {
            NbtCompound nbt = payload.nbt();
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (CONFIG != null && CONFIG.debugLogging) {
                    LOGGER.info("[FabricSimpleNbtSync] Server received NBT from {}: {}", player.getName().getString(), nbt);
                }
            });
        });
    }

    private void PointBlankIntegrationHandler() {
        if (FabricLoader.getInstance().isModLoaded("pointblank")) {
            if (CONFIG.enablePointBlankIntegration) {
                LOGGER.info("[SoundAttract] Point Blank mod found and integration is enabled. Registering server-side event listeners.");
                try {
                    PointBlankIntegrationHandler.register();
                } catch (Throwable e) {
                    LOGGER.error("Failed to register Point Blank integration events. This may be a mixin conflict or an API change.", e);
                }
            } else {
                LOGGER.info("[SoundAttract] Point Blank integration is disabled in the config.");
            }
        } else {
            LOGGER.info("[SoundAttract] Point Blank mod not found, skipping integration.");
        }
    }

    private void registerServerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (CONFIG.debugLogging) {
                LOGGER.info("[SoundAttract] Server started. Registering other handlers.");
            }

            if (plasmo != null) {
                try {
                    LOGGER.info("[SoundAttract] Loading Plasmo Voice addon...");
                    PlasmoVoiceServer.getAddonsLoader().load(plasmo);
                    LOGGER.info("[SoundAttract] Plasmo Voice addon loaded.");
                } catch (Throwable t) {
                    LOGGER.error("[SoundAttract] Failed to load Plasmo Voice addon", t);
                }
            }
        });
    }

    private void registerTickEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (lastTickTimeNanos == 0L) {
                lastTickTimeNanos = System.nanoTime();
            } else {
                long currentTimeNanos = System.nanoTime();
                long elapsedNanos = currentTimeNanos - lastTickTimeNanos;
                lastTickTimeNanos = currentTimeNanos;

                averageTickTimeNanos = averageTickTimeNanos * (1.0 - tpsSmoothingFactor) + elapsedNanos * tpsSmoothingFactor;
                double tps = Math.min(20.0, 1_000_000_000.0 / averageTickTimeNanos);
                if (CONFIG != null) {
                    CONFIG.lastKnownTps = tps;
                }
            }


            SoundAttractionEvents.onServerTick(server);

            BlockBreakerManager.processPendingActions();


            long applyBudgetMs = 2L;
            WorkerScheduler.drainGroupResults(com.example.soundattract.ai.MobGroupManager::applyGroupResult, applyBudgetMs);
            WorkerScheduler.drainSoundResults(result -> com.example.soundattract.SoundTracker.applySoundScoreResult(server, result), applyBudgetMs);
        });
    }

    private void registerEntityEvents() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof MobEntity mob) {
                SoundAttractionEvents.onEntityJoinWorld(mob);
            }
        });
    }
}