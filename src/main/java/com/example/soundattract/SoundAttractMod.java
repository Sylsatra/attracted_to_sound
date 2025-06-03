package com.example.soundattract;

import com.example.soundattract.ai.MobCellAssignmentHooks;
import com.example.soundattract.config.ConfigLoader;
import com.example.soundattract.config.SoundAttractConfigData;
import com.example.soundattract.enchantment.ModEnchantments;
import com.example.soundattract.integration.TaczGunshotMessage;
import com.example.soundattract.integration.TaczReloadMessage;
import com.example.soundattract.integration.VanillaIntegrationEvents; // Keep if it uses Fabric events you're not replacing yet
import com.example.soundattract.network.FabricSimpleNbtSync;
import com.example.soundattract.integration.TaczIntegrationEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents; // Keep if not using mixin for server tick
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents; // Keep if not using mixin for entity load
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // Use standard SLF4J LoggerFactory

public class SoundAttractMod implements ModInitializer {
    public static final String MOD_ID = "soundattract";
    // It's conventional to use LoggerFactory.getLogger for SLF4J
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static SoundAttractConfigData CONFIG;

    // For TPS Calculation
    private long lastTickTimeNanos = 0L;
    private double averageTickTimeNanos = 50_000_000.0; // 50ms, or 20 TPS
    private final double tpsSmoothingFactor = 0.05; // Alpha for EWMA

    @Override
    public void onInitialize() {
        // 1. Load Configuration EARLY
        // Ensure ConfigLoader.load() doesn't depend on anything initialized later.
        CONFIG = ConfigLoader.load();
        if (CONFIG == null) {
            // Handle critical config load failure, perhaps create a default or throw an error
            LOGGER.error("Failed to load SoundAttractMod configuration! Using default or limited functionality.");
            CONFIG = new SoundAttractConfigData(); // Example: Create a default instance
        }

        if (CONFIG.debugLogging) {
            LOGGER.info("[SoundAttractMod] Initializing. Environment: {}, Thread: {}",
                    FabricLoader.getInstance().getEnvironmentType(),
                    Thread.currentThread().getName());
            LOGGER.info("[SoundAttractMod] Debug logging is ENABLED.");
        }

        // 2. Register Core Mod Components
        ModEnchantments.register();
        MobCellAssignmentHooks.register(); // Ensure this doesn't use Fabric events we are removing
        ConfigReloadListener.registerCommand(); // For runtime config reloads

        // 3. Register Integrations (Classes that might self-register or need explicit calls)
        // These .register() methods should ideally only register things like packet handlers now,
        // not Fabric event listeners if we're replacing them with mixins.
        TaczGunshotMessage.register(); // Assumed to register its packet related logic
        TaczReloadMessage.register();  // Assumed to register its packet related logic

        // For TaczIntegrationEvents.register() and VanillaIntegrationEvents.register():
        // TaczIntegrationEvents.register() is GONE. Its functionality is in TaczIntegrationServerLogic + Mixins.
        // VanillaIntegrationEvents.register() - If this still relies on Fabric events you want to keep, leave it.
        // Otherwise, it needs similar mixin treatment. For now, let's assume it's kept as is.
            VanillaIntegrationEvents.register();
    
            StealthDetectionEvents.register(); // Same logic as VanillaIntegrationEvents


        // 4. Register Fabric API Event Listeners (for events we are NOT replacing with Mixins yet)

        // SERVER_STARTED: For logic that runs once when the server fully starts.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (CONFIG.debugLogging) {
                LOGGER.info("[SoundAttractMod] Server started. Registering NBT sync and network handlers.");
            }
            FabricSimpleNbtSync.registerServerReceiver(LOGGER);

            // Packet Handlers (previously in TaczGunshotMessage.register() and TaczReloadMessage.register()
            // but also good to centralize or ensure they are idempotent if called multiple times)
            // These are now called inside the SERVER_STARTED event to ensure server is ready.

            // a) SOUND_MESSAGE_ID -> SoundMessage
            if (CONFIG.debugLogging) LOGGER.info("[SoundAttractMod] Registering SOUND_MESSAGE_ID receiver.");
            ServerPlayNetworking.registerGlobalReceiver(
                    SoundAttractNetwork.SOUND_MESSAGE_ID,
                    (s, player, handler, buf, responseSender) -> {
                        SoundMessage msg = SoundMessage.decode(buf);
                        s.execute(() -> SoundMessage.handle(msg, player));
                    }
            );

            // b) TACZ_RELOAD_ID -> TaczReloadMessage (calls TaczIntegrationServerLogic)
            if (CONFIG.debugLogging) LOGGER.info("[SoundAttractMod] Registering TACZ_RELOAD_ID receiver.");
            ServerPlayNetworking.registerGlobalReceiver(
                    SoundAttractNetwork.TACZ_RELOAD_ID,
                    (s, player, handler, buf, responseSender) -> {
                        TaczReloadMessage msg = new TaczReloadMessage(buf); // Assuming constructor from PacketByteBuf
                        s.execute(() ->
                                // UPDATED to call TaczIntegrationServerLogic
                                TaczIntegrationEvents.handleReloadFromClient(player, msg.getGunId())
                        );
                    }
            );

            // c) TACZ_GUNSHOT_ID -> TaczGunshotMessage (calls TaczIntegrationServerLogic)
            if (CONFIG.debugLogging) LOGGER.info("[SoundAttractMod] Registering TACZ_GUNSHOT_ID receiver.");
            ServerPlayNetworking.registerGlobalReceiver(
                    SoundAttractNetwork.TACZ_GUNSHOT_ID,
                    (s, player, handler, buf, responseSender) -> {
                        // Assuming TaczGunshotMessage.decode or similar is more robust
                        // For now, sticking to your direct read:
                        String gunId = buf.readString(64);
                        String attachmentId = buf.readString(64); // Make sure this matches client sending
                        if (CONFIG.debugLogging) {
                            LOGGER.info("[SoundAttractMod] TACZ_GUNSHOT_ID packet received: gunId={}, attachmentId={}", gunId, attachmentId);
                        }
                        s.execute(() -> {
                            try {
                                // UPDATED to call TaczIntegrationServerLogic
                                TaczIntegrationEvents.handleGunshotFromClient(player, gunId, attachmentId);
                            } catch (Exception e) {
                                LOGGER.error("[SoundAttractMod] Exception handling TACZ_GUNSHOT_ID packet for gunId={}, attachmentId={}",
                                        gunId, attachmentId, e);
                            }
                        });
                    }
            );
        });


        // SERVER_TICK_EVENTS & ENTITY_LOAD_EVENTS
        // Option 1: Keep using Fabric API events (as they are now)
        // Option 2: Replace with Mixins for full API removal (more advanced, requires careful mixin placement)

        // --- Option 1: Keep Fabric API Events for Server Tick & Entity Load (Simpler for now) ---
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // TPS Calculation
            if (lastTickTimeNanos == 0L) { // First tick
                lastTickTimeNanos = System.nanoTime();
            } else {
                long currentTimeNanos = System.nanoTime();
                long elapsedNanos = currentTimeNanos - lastTickTimeNanos;
                lastTickTimeNanos = currentTimeNanos;

                // Exponentially Weighted Moving Average for tick time
                averageTickTimeNanos = averageTickTimeNanos * (1.0 - tpsSmoothingFactor) + elapsedNanos * tpsSmoothingFactor;
                double tps = Math.min(20.0, 1_000_000_000.0 / averageTickTimeNanos);
                CONFIG.lastKnownTps = tps; // Make sure lastKnownTps is thread-safe if accessed elsewhere or make volatile
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
        // --- End of Option 1 ---

        // --- Option 2: Replace with Mixins (Example Stubs - Implement if desired) ---
        // If you choose this, remove the ServerTickEvents and ServerEntityEvents.ENTITY_LOAD.register calls above.
        // You would create mixins like:
        // - MinecraftServerMixin for end server tick (inject into MinecraftServer.tickWorlds)
        // - ServerWorldMixin for end world tick (inject into ServerWorld.tick)
        // - ServerWorldMixin for entity load (inject into ServerWorld.loadEntity or addEntity)
        // Example: public static void onEndServerTick(MinecraftServer server) { /* logic from above */ }
        // Example: public static void onEndWorldTick(ServerWorld world) { /* logic from above */ }
        // Example: public static void onEntityLoadInWorld(Entity entity, ServerWorld world) { /* logic from above */ }
        // These static methods would then be called from your mixins.
        // --- End of Option 2 ---


        if (CONFIG.debugLogging) {
            LOGGER.info("[SoundAttractMod] Initialization complete.");
            LOGGER.info("[DEBUG] Registered Packet IDs (Server Perspective):");
            LOGGER.info("[DEBUG]   SOUND_MESSAGE_ID: {}", SoundAttractNetwork.SOUND_MESSAGE_ID);
            LOGGER.info("[DEBUG]   TACZ_RELOAD_ID: {}", SoundAttractNetwork.TACZ_RELOAD_ID);
            LOGGER.info("[DEBUG]   TACZ_GUNSHOT_ID: {}", SoundAttractNetwork.TACZ_GUNSHOT_ID);
        }
    }
}