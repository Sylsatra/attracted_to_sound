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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.Arrays;

public class TaczIntegrationEvents {

    private static final boolean IS_TACZ_LOADED = ModList.get().isLoaded("tacz");
    private static final ResourceLocation TACZ_GUN_SOUND_ID = new ResourceLocation("tacz", "gun");

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (!IS_TACZ_LOADED || !SoundAttractConfig.TACZ_ENABLED_CACHE) return;

        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player && !player.level().isClientSide()) {
            SoundEvent taczGunSound = ForgeRegistries.SOUND_EVENTS.getValue(TACZ_GUN_SOUND_ID);

            if (taczGunSound == null) return;

            Level level = player.level();
            BlockPos pos = player.blockPosition();
            String dimensionKey = level.dimension().location().toString();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            double[] rw = calculateTaczReloadRangeWeight(player);
            double range = rw[0];
            double weight = rw[1];

            SoundTracker.addSound(
                    taczGunSound,
                    pos,
                    dimensionKey,
                    range,
                    weight,
                    lifetime
            );
        }
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (!IS_TACZ_LOADED || !SoundAttractConfig.TACZ_ENABLED_CACHE) return;

        LivingEntity entity = event.getShooter();
        if (entity instanceof Player player && !player.level().isClientSide()) {
            SoundEvent taczGunSound = ForgeRegistries.SOUND_EVENTS.getValue(TACZ_GUN_SOUND_ID);

            if (taczGunSound == null) return;

            Level level = player.level();
            BlockPos pos = player.blockPosition();
            String dimensionKey = level.dimension().location().toString();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            double[] rw = calculateTaczShootRangeWeight(player);
            double range = rw[0];
            double weight = rw[1];

            SoundTracker.addSound(
                    taczGunSound,
                    pos,
                    dimensionKey,
                    range,
                    weight,
                    lifetime
            );
        }
    }

    private static double[] calculateTaczReloadRangeWeight(Player player) {
        ItemStack gunStack = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(gunStack);
            Double shootDb = SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId);
            if (shootDb != null) {
                double reloadRange = shootDb / 20.0;
                double reloadWeight = (shootDb / 10.0) / 2.0;
                SoundAttractMod.LOGGER.info("[GunReload] Player {} reloaded gun {}: reloadRange={}, reloadWeight={}", player.getName().getString(), gunId, reloadRange, reloadWeight);
                return new double[]{reloadRange, reloadWeight};
            }
        }
        SoundAttractMod.LOGGER.info("[GunReload] Player {} reloaded unknown gun: using default reloadRange={}, reloadWeight={}", player.getName().getString(), SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE, SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE);
        return new double[]{SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE, SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE};
    }

    private static double[] calculateTaczShootRangeWeight(Player player) {
        ItemStack gunStack = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(gunStack);
            Double db = SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId);

            if (db != null) {
                ResourceLocation attId = iGun.getAttachmentId(gunStack, AttachmentType.MUZZLE);
                Double reduction = SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE.get(attId);

                if (reduction != null) {
                    db = Math.max(0, db - reduction);
                }

                SoundAttractMod.LOGGER.info("[GunSound] Player {} fired gun {} with attachment {}: db={}, range={}, weight={}", player.getName().getString(), gunId, attId, db, db, db / 10.0);
                return new double[]{db, db / 10.0};
            }
        }
        SoundAttractMod.LOGGER.info("[GunSound] Player {} fired unknown gun: using default range={}, weight={}", player.getName().getString(), SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE, SoundAttractConfig.TACZ_SHOOT_WEIGHT_CACHE);
        return new double[]{SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE, SoundAttractConfig.TACZ_SHOOT_WEIGHT_CACHE};
    }
}