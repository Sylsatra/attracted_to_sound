package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class TaczIntegrationEvents {

    private static final boolean IS_TACZ_LOADED = ModList.get().isLoaded("tacz");
    private static final ResourceLocation TACZ_GUN_SOUND_ID = new ResourceLocation("tacz", "gun");

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (!IS_TACZ_LOADED || !SoundAttractConfig.TACZ_ENABLED_CACHE) return;

        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player && !player.level().isClientSide()) {
            SoundEvent taczGunSound = ForgeRegistries.SOUND_EVENTS.getValue(TACZ_GUN_SOUND_ID);

            if (taczGunSound == null) {
                SoundAttractMod.LOGGER.warn("[SoundAttract-TaCz] Failed to find 'tacz:gun' SoundEvent during GunReloadEvent for player {}. Skipping sound tracking for this event.", player.getName().getString());
                return;
            }

            Level level = player.level();
            BlockPos pos = player.blockPosition();
            String dimensionKey = level.dimension().location().toString();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            double range = SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE;
            double weight = SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE;

            SoundTracker.addSound(
                    taczGunSound,
                    pos,
                    dimensionKey,
                    range,
                    weight,
                    lifetime
            );
            SoundAttractMod.LOGGER.debug("Added TaCz reload sound (tacz:gun, R={}, W={}) for player {} at {}",
                range, weight, player.getName().getString(), pos);
        }
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (!IS_TACZ_LOADED || !SoundAttractConfig.TACZ_ENABLED_CACHE) return;

        LivingEntity entity = event.getShooter();
        if (entity instanceof Player player && !player.level().isClientSide()) {
            SoundEvent taczGunSound = ForgeRegistries.SOUND_EVENTS.getValue(TACZ_GUN_SOUND_ID);

            if (taczGunSound == null) {
                SoundAttractMod.LOGGER.warn("[SoundAttract-TaCz] Failed to find 'tacz:gun' SoundEvent during GunShootEvent for player {}. Skipping sound tracking for this event.", player.getName().getString());
                return;
            }

            Level level = player.level();
            BlockPos pos = player.blockPosition();
            String dimensionKey = level.dimension().location().toString();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            double range = SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;
            double weight = SoundAttractConfig.TACZ_SHOOT_WEIGHT_CACHE;

            SoundTracker.addSound(
                    taczGunSound,
                    pos,
                    dimensionKey,
                    range,
                    weight,
                    lifetime
            );
            SoundAttractMod.LOGGER.debug("Added TaCz shoot sound (tacz:gun, R={}, W={}) for player {} at {}",
                range, weight, player.getName().getString(), pos);
        }
    }
} 