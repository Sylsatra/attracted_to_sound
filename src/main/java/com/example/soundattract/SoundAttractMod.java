package com.example.soundattract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.soundattract.ai.MobCellAssignmentHooks;
import com.example.soundattract.config.ConfigLoader;
import com.example.soundattract.config.SoundAttractConfigData;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.integration.PointBlankIntegrationHandler;
import com.example.soundattract.integration.VanillaIntegrationEvents;
import com.example.soundattract.network.FabricSimpleNbtSync;
import com.example.soundattract.loot.ModLootTables;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;


public class SoundAttractMod implements ModInitializer {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static SoundAttractConfigData CONFIG;

    private long lastTickTimeNanos = 0L;
    private double averageTickTimeNanos = 50_000_000.0;
    private final double tpsSmoothingFactor = 0.05;

    @Override
    public void onInitialize() {
        CONFIG = ConfigLoader.load();
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
        FovEvents.buildCaches();
        StealthDetectionEvents.register();

        PointBlankIntegrationHandler();

        registerServerLifecycleEvents();
        registerTickEvents();
        registerEntityEvents();

        if (CONFIG.debugLogging) {
            LOGGER.info("[SoundAttract] Initialization complete.");
            LOGGER.info("[DEBUG] Registered Packet IDs (Server Perspective):");
            LOGGER.info("[DEBUG]   SOUND_MESSAGE_ID: {}", SoundAttractNetwork.SOUND_MESSAGE_ID);
        }
    }

    private void PointBlankIntegrationHandler() {
        if (FabricLoader.getInstance().isModLoaded("pointblank")) {
            if (CONFIG.enablePointBlankIntegration ) {
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
                LOGGER.info("[SoundAttract] Server started. Registering NBT sync and network handlers.");
            }
            FabricSimpleNbtSync.registerServerReceiver(LOGGER);

            if (CONFIG.debugLogging) LOGGER.info("[SoundAttract] Registering SOUND_MESSAGE_ID receiver.");
            ServerPlayNetworking.registerGlobalReceiver(
                    SoundAttractNetwork.SOUND_MESSAGE_ID,
                    (s, player, handler, buf, responseSender) -> {
                        SoundMessage msg = SoundMessage.decode(buf);
                        s.execute(() -> SoundMessage.handle(msg, player));
                    }
            );

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
                CONFIG.lastKnownTps = tps;
            }

            for (ServerWorld level : server.getWorlds()) {
                SoundAttractionEvents.onServerTick(level);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register((ServerWorld world) -> {
            SoundAttractionEvents.onWorldTick(world);
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