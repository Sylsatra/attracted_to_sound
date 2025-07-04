package com.example.soundattract;

import com.example.soundattract.ai.MobCellAssignmentHooks;
import com.example.soundattract.config.ConfigLoader;
import com.example.soundattract.config.SoundAttractConfigData;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.integration.TaczGunshotMessage;
import com.example.soundattract.integration.TaczReloadMessage;
import com.example.soundattract.integration.VanillaIntegrationEvents; 
import com.example.soundattract.network.FabricSimpleNbtSync;
import com.example.soundattract.integration.TaczIntegrationEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents; 
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; 

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
            LOGGER.info("[SoundAttractMod] Initializing. Environment: {}, Thread: {}",
                    FabricLoader.getInstance().getEnvironmentType(),
                    Thread.currentThread().getName());
            LOGGER.info("[SoundAttractMod] Debug logging is ENABLED.");
        }

        ModEnchantments.register();
        MobCellAssignmentHooks.register(); 
        ConfigReloadListener.registerCommand(); 
        TaczGunshotMessage.register(); 
        TaczReloadMessage.register();  
        VanillaIntegrationEvents.register();
        FovEvents.buildCaches();
        StealthDetectionEvents.register(); 



        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (CONFIG.debugLogging) {
                LOGGER.info("[SoundAttractMod] Server started. Registering NBT sync and network handlers.");
            }
            FabricSimpleNbtSync.registerServerReceiver(LOGGER);


            if (CONFIG.debugLogging) LOGGER.info("[SoundAttractMod] Registering SOUND_MESSAGE_ID receiver.");
            ServerPlayNetworking.registerGlobalReceiver(
                    SoundAttractNetwork.SOUND_MESSAGE_ID,
                    (s, player, handler, buf, responseSender) -> {
                        SoundMessage msg = SoundMessage.decode(buf);
                        s.execute(() -> SoundMessage.handle(msg, player));
                    }
            );

            if (CONFIG.debugLogging) LOGGER.info("[SoundAttractMod] Registering TACZ_RELOAD_ID receiver.");
            ServerPlayNetworking.registerGlobalReceiver(
                    SoundAttractNetwork.TACZ_RELOAD_ID,
                    (s, player, handler, buf, responseSender) -> {
                        TaczReloadMessage msg = new TaczReloadMessage(buf); 
                        s.execute(() ->
                                TaczIntegrationEvents.handleReloadFromClient(player, msg.getGunId())
                        );
                    }
            );

            if (CONFIG.debugLogging) LOGGER.info("[SoundAttractMod] Registering TACZ_GUNSHOT_ID receiver.");
            ServerPlayNetworking.registerGlobalReceiver(
                    SoundAttractNetwork.TACZ_GUNSHOT_ID,
                    (s, player, handler, buf, responseSender) -> {
                        String gunId = buf.readString(64);
                        String attachmentId = buf.readString(64);
                        if (CONFIG.debugLogging) {
                            LOGGER.info("[SoundAttractMod] TACZ_GUNSHOT_ID packet received: gunId={}, attachmentId={}", gunId, attachmentId);
                        }
                        s.execute(() -> {
                            try {
                                TaczIntegrationEvents.handleGunshotFromClient(player, gunId, attachmentId);
                            } catch (Exception e) {
                                LOGGER.error("[SoundAttractMod] Exception handling TACZ_GUNSHOT_ID packet for gunId={}, attachmentId={}",
                                        gunId, attachmentId, e);
                            }
                        });
                    }
            );
        });


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

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof MobEntity mob) {
                SoundAttractionEvents.onEntityJoinWorld(mob);
            }
        });


        if (CONFIG.debugLogging) {
            LOGGER.info("[SoundAttractMod] Initialization complete.");
            LOGGER.info("[DEBUG] Registered Packet IDs (Server Perspective):");
            LOGGER.info("[DEBUG]   SOUND_MESSAGE_ID: {}", SoundAttractNetwork.SOUND_MESSAGE_ID);
            LOGGER.info("[DEBUG]   TACZ_RELOAD_ID: {}", SoundAttractNetwork.TACZ_RELOAD_ID);
            LOGGER.info("[DEBUG]   TACZ_GUNSHOT_ID: {}", SoundAttractNetwork.TACZ_GUNSHOT_ID);
        }
    }
}