package com.example.soundattract;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfigData;

public class SoundAttractMod implements ModInitializer {
    public static final String MOD_ID = "soundattract";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static SoundAttractConfigData CONFIG;

    @Override
    public void onInitialize() {
        com.example.soundattract.ai.MobCellAssignmentHooks.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] Registered FabricSimpleNbtSync on LOGICAL SERVER");
            com.example.soundattract.network.FabricSimpleNbtSync.registerServerReceiver(LOGGER);
            if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] Registering TaczGunshotMessage and TaczReloadMessage packet handlers on server");
            com.example.soundattract.integration.TaczGunshotMessage.register();
            com.example.soundattract.integration.TaczReloadMessage.register();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            
            com.example.soundattract.network.FabricSimpleNbtSync.registerServerReceiver(LOGGER);
        });

        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] SoundAttractMod.onInitialize: Starting initialization");
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] TACZ_GUNSHOT_ID (server) = {}", SoundAttractNetwork.TACZ_GUNSHOT_ID);
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] SOUND_MESSAGE_ID (server) = {}", SoundAttractNetwork.SOUND_MESSAGE_ID);
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] TACZ_RELOAD_ID (server) = {}", SoundAttractNetwork.TACZ_RELOAD_ID);
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] TaczGunshotMessage.ID = {}", com.example.soundattract.integration.TaczGunshotMessage.ID);

        CONFIG = com.example.soundattract.config.ConfigLoader.load();
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[SoundAttractMod] onInitialize called. Thread: {} Env: {}", Thread.currentThread().getName(), net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType());
        if (CONFIG != null && CONFIG.debugLogging) {
            if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[SoundAttractMod] onInitialize called, config debugLogging={}", CONFIG.debugLogging);
        }
        ConfigReloadListener.registerCommand();
        com.example.soundattract.integration.TaczGunshotMessage.register();
        com.example.soundattract.integration.TaczReloadMessage.register();
        com.example.soundattract.integration.TaczIntegrationEvents.register();
        com.example.soundattract.integration.VanillaIntegrationEvents.register();
        com.example.soundattract.StealthDetectionEvents.register();

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (net.minecraft.server.world.ServerWorld level : server.getWorlds()) {
                SoundAttractionEvents.onServerTick(level);
            }
        });
        
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
                SoundAttractionEvents.onEntityJoinWorld(mob);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] ServerLifecycleEvents.SERVER_STARTED: Registering server-side packet handlers (Env: {})", net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType());
            if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] Registering server-side SOUND_MESSAGE_ID handler for ID: {}", SoundAttractNetwork.SOUND_MESSAGE_ID);
            ServerPlayNetworking.registerGlobalReceiver(
                SoundAttractNetwork.SOUND_MESSAGE_ID,
                (server1, player, handler, buf, responseSender) -> {
                    SoundMessage msg = SoundMessage.decode(buf);
                    server1.execute(() -> SoundMessage.handle(msg, player));
                }
            );
            if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] Registering server-side TACZ_RELOAD_ID handler for ID: {}", SoundAttractNetwork.TACZ_RELOAD_ID);
            ServerPlayNetworking.registerGlobalReceiver(
                SoundAttractNetwork.TACZ_RELOAD_ID,
                (server1, player, handler, buf, responseSender) -> {
                    com.example.soundattract.integration.TaczReloadMessage msg = new com.example.soundattract.integration.TaczReloadMessage(buf);
                    server1.execute(() -> com.example.soundattract.integration.TaczIntegrationEvents.handleReloadFromClient(player, msg.getGunId()));
                }
            );
            if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] Registering server-side TACZ_GUNSHOT_ID handler for ID: {}", SoundAttractNetwork.TACZ_GUNSHOT_ID);
            ServerPlayNetworking.registerGlobalReceiver(
                SoundAttractNetwork.TACZ_GUNSHOT_ID,
                (server1, player, handler, buf, responseSender) -> {
                    if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG_SERVER] TACZ_GUNSHOT_ID packet handler triggered!");
                    String gunId = buf.readString(64);
                    String attachmentId = buf.readString(64);
                    server1.execute(() -> {
                        try {
                            com.example.soundattract.integration.TaczIntegrationEvents.handleGunshotFromClient(player, gunId, attachmentId);
                        } catch (Exception e) {
                            SoundAttractMod.LOGGER.error("[TaczGunshotMessage] Exception in handle for gunId={}, attachmentId={}", gunId, attachmentId, e);
                        }
                    });
                }
            );
        });
    CONFIG = com.example.soundattract.config.ConfigLoader.load();
    org.slf4j.Logger logger = LOGGER;
    logger.info("[SoundAttractMod] onInitialize called. Thread: {} Env: {}", Thread.currentThread().getName(), net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType());
    if (CONFIG != null && CONFIG.debugLogging) {
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[SoundAttractMod] onInitialize called, config debugLogging={}", CONFIG.debugLogging);
    }
    ConfigReloadListener.registerCommand();
    com.example.soundattract.integration.TaczGunshotMessage.register();
    com.example.soundattract.integration.TaczReloadMessage.register();
    com.example.soundattract.integration.TaczIntegrationEvents.register();
    com.example.soundattract.integration.VanillaIntegrationEvents.register();

    final long[] lastTickTime = {System.nanoTime()};
    final double[] tickTimeAvg = {50000000.0};
    final double alpha = 0.05;
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
        long now = System.nanoTime();
        long elapsed = now - lastTickTime[0];
        lastTickTime[0] = now;
        double tickTime = elapsed / 20.0;
        tickTimeAvg[0] = tickTimeAvg[0] * (1.0 - alpha) + tickTime * alpha;
        double tps = Math.min(20.0, 1_000_000_000.0 / tickTimeAvg[0]);
        if (CONFIG != null) CONFIG.lastKnownTps = tps;
        for (net.minecraft.server.world.ServerWorld level : server.getWorlds()) {
            SoundAttractionEvents.onServerTick(level);
        }
    });
    
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(
        (net.minecraft.server.world.ServerWorld world) -> {
            SoundAttractionEvents.onWorldTick(world);
        }
    );
    
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
        if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
            SoundAttractionEvents.onEntityJoinWorld(mob);
        }
    });

    ServerLifecycleEvents.SERVER_STARTED.register(server -> {
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] ServerLifecycleEvents.SERVER_STARTED: Registering server-side packet handlers (Env: {})", net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType());
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] Registering server-side SOUND_MESSAGE_ID handler for ID: {}", SoundAttractNetwork.SOUND_MESSAGE_ID);
        ServerPlayNetworking.registerGlobalReceiver(
            SoundAttractNetwork.SOUND_MESSAGE_ID,
            (server1, player, handler, buf, responseSender) -> {
                if (CONFIG != null && CONFIG.debugLogging) {
                    
                }
                SoundMessage msg = SoundMessage.decode(buf);
                server1.execute(() -> SoundMessage.handle(msg, player));
            }
        );
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] Registering server-side TACZ_RELOAD_ID handler for ID: {}", SoundAttractNetwork.TACZ_RELOAD_ID);
        ServerPlayNetworking.registerGlobalReceiver(
            SoundAttractNetwork.TACZ_RELOAD_ID,
            (server1, player, handler, buf, responseSender) -> {
                com.example.soundattract.integration.TaczReloadMessage msg = new com.example.soundattract.integration.TaczReloadMessage(buf);
                server1.execute(() -> com.example.soundattract.integration.TaczIntegrationEvents.handleReloadFromClient(player, msg.getGunId()));
            }
        );
        if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG] Registering server-side TACZ_GUNSHOT_ID handler for ID: {}", SoundAttractNetwork.TACZ_GUNSHOT_ID);
        ServerPlayNetworking.registerGlobalReceiver(
            SoundAttractNetwork.TACZ_GUNSHOT_ID,
            (server1, player, handler, buf, responseSender) -> {
                if (CONFIG != null && CONFIG.debugLogging) LOGGER.info("[DEBUG_SERVER] TACZ_GUNSHOT_ID packet handler triggered!");
                String gunId = buf.readString(64);
                String attachmentId = buf.readString(64);
                server1.execute(() -> {
                    try {
                        com.example.soundattract.integration.TaczIntegrationEvents.handleGunshotFromClient(player, gunId, attachmentId);
                    } catch (Exception e) {
                        SoundAttractMod.LOGGER.error("[TaczGunshotMessage] Exception in handle for gunId={}, attachmentId={}", gunId, attachmentId, e);
                    }
                });
            }
        );
    });
}

}
