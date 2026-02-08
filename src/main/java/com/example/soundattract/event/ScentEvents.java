package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentBiomeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = SoundAttractMod.MOD_ID)
public class ScentEvents {

    private static final Map<UUID, Vec3> lastScentPos = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastScentTime = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel) {
            if (!event.getObject().getCapability(ScentManager.INSTANCE).isPresent()) {
                ResourceLocation scentId = ResourceLocation.tryParse(SoundAttractMod.MOD_ID + ":scent");
                if (scentId != null) {
                    event.addCapability(scentId, new ScentManager.Provider(event.getObject()));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;

        ServerPlayer player = (ServerPlayer) event.player;
        
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
            float baseStrength = 1.0f;
            


            ScentNode node = new ScentNode(currentPos, currentTime, baseStrength, playerId);
            
            player.level().getCapability(ScentManager.INSTANCE).ifPresent(manager -> {
                manager.addScentNode(node);
            });

            lastScentPos.put(playerId, currentPos);

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[ScentEvents] Created scent node for player {} at {}. Strength: {}", 
                    player.getName().getString(), currentPos, baseStrength);
            }

            if (SoundAttractConfig.COMMON.enableScentParticles.get()) {
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, currentPos.x, currentPos.y + 0.5, currentPos.z, 1, 0, 0, 0, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;
        
        if (event.level.getGameTime() % 200 == 0) {


        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!SoundAttractConfig.COMMON.enableScentSystem.get()) return;
        
        if (event.getEntity() instanceof net.minecraft.world.entity.PathfinderMob mob) {
            String id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).toString();
            java.util.List<? extends String> whitelist = SoundAttractConfig.COMMON.scentEligibleMobs.get();
            
            if (whitelist.contains(id)) {
                // Try SBL integration first
                if (SoundAttractConfig.COMMON.enableSmartBrainLibIntegration.get()) {
                    if (com.example.soundattract.integration.smartbrainlib.SmartBrainLibCompat.tryAttachSoundAttractBrain(mob)) {
                        return;
                    }
                }

                mob.goalSelector.addGoal(5, new com.example.soundattract.ai.FollowScentGoal(mob));
            }
        }
    }
}
