package com.example.soundattract.integration.spore;

import com.Harbinger.Spore.Sentities.Organoids.Vigil;
import com.Harbinger.Spore.Sentities.Organoids.Verwa;
import com.Harbinger.Spore.Core.Sentities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Internal handler for Spore Biomass Sound System.
 * This class contains direct references to Spore mod classes and should only be 
 * registered or accessed when the Spore mod is loaded.
 */
public class BiomassSoundHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel serverLevel)) {
            return;
        }
        BiomassSoundSystem.processTick(serverLevel);
    }

    /**
     * Safely triggers a hive alert using Spore entities.
     */
    public static void triggerHiveAlert(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(64);
        List<Vigil> vigils = level.getEntitiesOfClass(Vigil.class, area);
        
        for (Vigil vigil : vigils) {
            vigil.setTrigger(3);
            vigil.setWaveSize(10); 
        }

        if (!vigils.isEmpty() || level.random.nextFloat() < 0.3f) {
            Verwa verwa = new Verwa(Sentities.VERVA.get(), level);
            verwa.moveTo(pos.getX(), pos.getY(), pos.getZ());
            verwa.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(verwa);
        }
    }
}
