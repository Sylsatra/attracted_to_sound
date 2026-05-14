package com.example.soundattract.event;

import com.example.soundattract.Soundattract;
import com.example.soundattract.ai.FollowScentGoal;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.mixin.MobAccessor;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentBiomeModifier;
import com.example.soundattract.scents.ScentSourceType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import com.example.soundattract.scents.ScentParticleColorManager;
import org.joml.Vector3f;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

public class ScentEvents {

    private static Map<UUID, Vec3> lastScentPos = new ConcurrentHashMap<>();
    private static Map<UUID, Long> lastScentTime = new ConcurrentHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                onPlayerTick(player);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel) level;
                onLevelTick(serverLevel);
                onLevelTickCleanup(serverLevel);
            }
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayer player) {
                onPlayerLoggedOut(player);
            } else if (entity instanceof Mob mob) {
                onEntityJoinLevel(mob, world);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Mob mob) {
                onEntityLeaveScentCache(mob);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            onServerTick(server);
        });
    }

    public static void reinitializeCaches() {
        int max = SoundAttractConfig.COMMON.globalCacheMaxSize.get();
        int mins = SoundAttractConfig.COMMON.globalCacheExpireMins.get();

        Map<UUID, Vec3> oldObj1 = lastScentPos;
        lastScentPos = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, Vec3>build().asMap();
        lastScentPos.putAll(oldObj1);

        Map<UUID, Long> oldObj2 = lastScentTime;
        lastScentTime = CacheBuilder.newBuilder().expireAfterWrite(mins, TimeUnit.MINUTES).maximumSize(max).concurrencyLevel(4).<UUID, Long>build().asMap();
        lastScentTime.putAll(oldObj2);

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Soundattract.LOGGER.info("[ScentEvents] Initialized Guava internal memory caches (max {}, {} mins)", max, mins);
        }
    }

    public static void onPlayerTick(ServerPlayer player) {
        if (player.level().isClientSide) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;
        
        if (player.isSpectator() || player.isCreative()) return;
        
        if (SoundAttractConfig.COMMON.waterStopsScent.get() && player.isInWater()) {
            return;
        }

        UUID playerId = player.getUUID();
        Vec3 currentPos = player.position();
        long currentTime = player.level().getGameTime();

        Vec3 lastPos = lastScentPos.computeIfAbsent(playerId, k -> currentPos);
        Long lastTime = lastScentTime.put(playerId, currentTime);

        double distSq = currentPos.distanceToSqr(lastPos);
        double interval = SoundAttractConfig.COMMON.scentCreationIntervalBlocks.get();
        double intervalSq = interval * interval;

        if (distSq >= intervalSq) {
            float durationMod = ScentBiomeModifier.getDurationModifier(player.level(), player.blockPosition());
            float baseStrength = 1.0f * durationMod;
            
            double blockFactor = com.example.soundattract.camo.CamouflageCapability.getCapability(player)
                    .map(com.example.soundattract.camo.CamouflageCapability::getScentBlockFactor)
                    .orElse(0.0);
            
            baseStrength *= (float) (1.0 - blockFactor);

            if (baseStrength <= 0.05f) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    Soundattract.LOGGER.info("[ScentEvents] Scent for player {} fully blocked by camouflage.", player.getName().getString());
                }
                lastScentPos.put(playerId, currentPos);
                return;
            }

            ScentNode node = new ScentNode(currentPos, currentTime, baseStrength, playerId, ScentSourceType.PLAYER_WALK);
            
            ScentManager manager = ScentManager.getForLevel((ServerLevel) player.level());
            if (manager != null) {
                manager.addScentNode(node);
            }

            lastScentPos.put(playerId, currentPos);

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                Soundattract.LOGGER.info("[ScentEvents] Created scent node for player {} at {}. Strength: {}", 
                    player.getName().getString(), currentPos, baseStrength);
            }

            if (SoundAttractConfig.COMMON.enableScentParticles.get()) {
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, currentPos.x, currentPos.y + 0.5, currentPos.z, 1, 0, 0, 0, 0.0);
            }
        }
    }

    public static void onLevelTick(ServerLevel level) {
        if (level.isClientSide) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;
        

        if (level.getGameTime() % 200 == 0) {

        }


        if (!SoundAttractConfig.COMMON.enableGameplayScentParticles.get()) return;

        int spawnInterval = SoundAttractConfig.COMMON.scentParticleSpawnInterval.get();
        if (level.getGameTime() % spawnInterval != 0) return;

        ServerLevel serverLevel = (ServerLevel) level;
        long currentTime = serverLevel.getGameTime();
        long maxDuration = SoundAttractConfig.COMMON.scentNodeDurationTicks.get();
        double baseRenderDist = SoundAttractConfig.COMMON.scentParticleRenderDistance.get();
        final double renderDistSq = baseRenderDist * baseRenderDist;

        ScentManager manager = ScentManager.getForLevel(serverLevel);
        if (manager != null) {
            net.minecraft.server.MinecraftServer server = serverLevel.getServer();
            if (server == null) return;

            List<ScentNode> allNodes = manager.getAllNodes();


            Map<UUID, Boolean> particleEnabledCache = new java.util.HashMap<>();
            Map<UUID, org.joml.Vector3f> colorCache = new java.util.HashMap<>();

            for (ScentNode node : allNodes) {
                long age = currentTime - node.getTimestamp();
                if (age > maxDuration || age < 0) continue;

                UUID ownerUUID = node.getOwnerUUID();
                if (ownerUUID == null) continue;


                Boolean enabled = particleEnabledCache.get(ownerUUID);
                if (enabled == null) {
                    enabled = true;
                    net.minecraft.server.level.ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUUID);
                    if (ownerPlayer != null) {
                        com.example.soundattract.config.PlayerProfile2 profile = SoundAttractConfig.getMatchingPlayerProfile(ownerPlayer);
                        if (profile != null && profile.scentEmission().isPresent()) {
                            enabled = profile.scentEmission().get().showScentParticles();
                        }
                    }
                    particleEnabledCache.put(ownerUUID, enabled);
                }
                if (!enabled) continue;


                org.joml.Vector3f color = colorCache.get(ownerUUID);
                if (color == null) {
                    net.minecraft.server.level.ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUUID);
                    if (ownerPlayer != null) {
                        color = ScentParticleColorManager.getColorForPlayer(ownerUUID, ownerPlayer);
                    } else {
                        color = ScentParticleColorManager.getColorForPlayer(ownerUUID);
                    }
                    colorCache.put(ownerUUID, color);
                }


                float lifeRatio = 1.0f - ((float) age / maxDuration);
                float particleScale = 0.4f + (lifeRatio * 0.6f);
                int particleCount = lifeRatio > 0.5f ? 2 : 1;

                Vec3 pos = node.getPosition();


                net.minecraft.core.particles.DustParticleOptions dustParticle =
                        new net.minecraft.core.particles.DustParticleOptions(color, particleScale);

                for (ServerPlayer nearbyPlayer : serverLevel.players()) {
                    if (nearbyPlayer.position().distanceToSqr(pos) <= renderDistSq) {
                        com.example.soundattract.config.PlayerProfile2 viewerProfile = SoundAttractConfig.getMatchingPlayerProfile(nearbyPlayer);
                        boolean showScent = true;
                        if (viewerProfile != null && viewerProfile.scentVisibility().isPresent()) {
                            com.example.soundattract.config.ScentVisibilityConfig vis = viewerProfile.scentVisibility().get();
                            showScent = switch (node.getSourceType()) {
                                case PLAYER_WALK -> vis.showPlayerWalk();
                                case ARROW_PATH, ARROW_ORIGIN, MOB_PROJECTILE_PATH, MOB_PROJECTILE_ORIGIN -> vis.showArrowScent();
                            };
                        }
                        if (!showScent) continue;
                        serverLevel.sendParticles(nearbyPlayer, dustParticle, true,
                                pos.x, pos.y + 0.3, pos.z,
                                particleCount, 0.1, 0.05, 0.1, 0.0);
                    }
                }
            }
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        UUID playerId = player.getUUID();
        lastScentPos.remove(playerId);
        lastScentTime.remove(playerId);
    }

    public static void onEntityJoinLevel(Mob mob, Level level) {
        if (level.isClientSide) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;
        
        if (mob instanceof PathfinderMob pathfinderMob) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            java.util.List<? extends String> whitelist = SoundAttractConfig.COMMON.scentEligibleMobs.get();
            
            if (whitelist.contains(id)) {
                if (SoundAttractConfig.COMMON.enableSmartBrainLibIntegration.get()) {
                    if (com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat.tryAttachSoundAttractBrain(pathfinderMob)) {
                        return;
                    }
                }
                
                MobAccessor accessor = (MobAccessor) mob;
                accessor.getGoalSelector().addGoal(5, new FollowScentGoal(pathfinderMob));
            }
        }
    }

    public static void onLevelTickCleanup(ServerLevel level) {
        if (SoundAttractConfig.COMMON == null) return;
        long now = level.getGameTime();
        if (now % 200L != 0L) return;
        com.example.soundattract.util.ScentQueryCache.cleanup(
            now,
            SoundAttractConfig.COMMON.scentQueryCacheTtlTicks.get(),
            SoundAttractConfig.COMMON.scentQueryCacheMaxEntries.get()
        );
    }

    public static void onEntityLeaveScentCache(Mob mob) {
        com.example.soundattract.util.ScentQueryCache.invalidate(mob.getUUID());
    }

    public static void onServerTick(MinecraftServer server) {
        com.example.soundattract.util.ScentQueryCache.clear();
    }
}
