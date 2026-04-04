package com.example.soundattract.integration.spore;

import com.Harbinger.Spore.Sentities.Organoids.Vigil;
import com.Harbinger.Spore.Sentities.Organoids.Verwa;
import com.Harbinger.Spore.Core.Sentities;
import com.example.soundattract.tracking.SoundTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Spore Integration: Deluge of Sound (Biomass Sound Pressure System).
 * 
 * This system tracks 'Sound Pressure' (accumulated weight) in regions of the biomass.
 * High sound pressure triggers a 'Hive Alert', summoning reinforcements and alerting Vigil entities.
 * 
 * Safety: Uses ConcurrentHashMap for thread safety and periodic cleanup for memory management.
 */
@Mod.EventBusSubscriber(modid = "soundattract")
public class BiomassSoundSystem {
    private static final double ALERT_THRESHOLD = 500.0;
    
    private static final Map<BlockPos, Double> pressureMap = new ConcurrentHashMap<>();
    private static int cleanupTimer = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (!ModList.get().isLoaded("spore") || event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        cleanupTimer++;
        if (cleanupTimer >= 100) { 
            cleanupTimer = 0;
            pressureMap.replaceAll((pos, weight) -> weight * 0.8);
            pressureMap.values().removeIf(weight -> weight < 1.0);
        }

    }

    /**
     * Records sound weight in a specific area and checks for Hive Alerts.
     */
    public static void recordBiomassSound(ServerLevel level, BlockPos pos, double weight) {
        if (!ModList.get().isLoaded("spore")) return;

        BlockPos region = new BlockPos((pos.getX() >> 4) << 4, (pos.getY() >> 4) << 4, (pos.getZ() >> 4) << 4);
        
        double currentPressure = pressureMap.merge(region, weight, Double::sum);

        if (currentPressure > ALERT_THRESHOLD) {
            triggerHiveAlert(level, pos);
            pressureMap.put(region, 0.0); 
        }
    }

    private static void triggerHiveAlert(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(64);
        List<Vigil> vigils = level.getEntitiesOfClass(Vigil.class, area);
        
        for (Vigil vigil : vigils) {
            vigil.setTrigger(3);
            vigil.setWaveSize(10); 
        }

        if (vigils.size() > 0 || level.random.nextFloat() < 0.3f) {
            Verwa verwa = new Verwa(Sentities.VERVA.get(), level);
            verwa.moveTo(pos.getX(), pos.getY(), pos.getZ());
            verwa.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(verwa);
        }
    }
}
