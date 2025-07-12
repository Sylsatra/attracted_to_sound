package com.example.soundattract.integration;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.config.SoundAttractConfig;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.ModList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;

public class TaczIntegrationServerEvents {

    private static final boolean IS_TACZ_LOADED = ModList.get().isLoaded("tacz");
    private static final ResourceLocation TACZ_GUN_SOUND_ID = ResourceLocation.fromNamespaceAndPath("tacz", "gun");

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (!IS_TACZ_LOADED || !SoundAttractConfig.COMMON.enableTaczIntegration.get() || event.getLogicalSide() != LogicalSide.SERVER) return;

        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player) {
            Optional<SoundEvent> taczGunSound = BuiltInRegistries.SOUND_EVENT.getOptional(TACZ_GUN_SOUND_ID);
            if (taczGunSound.isEmpty()) return;

            Level level = player.level();
            BlockPos pos = player.blockPosition();
            String dimensionKey = level.dimension().location().toString();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
            ItemStack gunStack = event.getGunItemStack();

            double[] rw = calculateTaczReloadRangeWeight(gunStack);
            double range = rw[0];
            double weight = rw[1];

            SoundTracker.addSound(taczGunSound.get(), pos, dimensionKey, range, weight, lifetime, "tacz_reload");
        }
    }

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (!IS_TACZ_LOADED || !SoundAttractConfig.COMMON.enableTaczIntegration.get() || event.getLogicalSide() != LogicalSide.SERVER) return;

        LivingEntity entity = event.getShooter();
        if (entity instanceof Player player) {
            Optional<SoundEvent> taczGunSound = BuiltInRegistries.SOUND_EVENT.getOptional(TACZ_GUN_SOUND_ID);
            if (taczGunSound.isEmpty()) return;

            Level level = player.level();
            BlockPos pos = player.blockPosition();
            String dimensionKey = level.dimension().location().toString();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
            ItemStack gunStack = event.getGunItemStack();

            double[] rw = calculateTaczShootRangeWeight(gunStack);
            double range = rw[0];
            double weight = rw[1];

            SoundTracker.addSound(taczGunSound.get(), pos, dimensionKey, range, weight, lifetime, "tacz_shoot");
        }
    }

    private static double[] calculateTaczReloadRangeWeight(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(gunStack);
            Pair<Double, Double> shootValues = SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId);
            if (shootValues != null) {
                double shootDb = shootValues.getLeft();
                double reloadRange = shootDb / 20.0;
                double reloadWeight = (shootDb / 10.0) / 2.0; 
                SoundAttractMod.LOGGER.info("[GunReload] Reloaded gun {}: reloadRange={}, reloadWeight={}", gunId, reloadRange, reloadWeight);
                return new double[]{reloadRange, reloadWeight};
            }
        }
        SoundAttractMod.LOGGER.info("[GunReload] Reloaded unknown gun: using default reloadRange={}, reloadWeight={}", SoundAttractConfig.COMMON.taczReloadRange.get(), SoundAttractConfig.COMMON.taczReloadWeight.get());
        return new double[]{SoundAttractConfig.COMMON.taczReloadRange.get(), SoundAttractConfig.COMMON.taczReloadWeight.get()};
    }

    private static double[] calculateTaczShootRangeWeight(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(gunStack);
            Pair<Double, Double> shootValues = SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId);

            if (shootValues != null) {
                double range = shootValues.getLeft();
                double weight = shootValues.getRight();

                ResourceLocation attId = iGun.getAttachmentId(gunStack, AttachmentType.MUZZLE);
                Pair<Double, Double> reduction = SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE.get(attId);

                if (reduction != null) {
                    range = Math.max(0, range - reduction.getLeft());
                    weight = Math.max(0, weight - reduction.getRight());
                }

                SoundAttractMod.LOGGER.info("[GunSound] Fired gun {} with attachment {}: range={}, weight={}", gunId, attId, range, weight);
                return new double[]{range, weight};
            }
        }
        SoundAttractMod.LOGGER.info("[GunSound] Fired unknown gun: using default range={}, weight={}", SoundAttractConfig.COMMON.taczShootRange.get(), SoundAttractConfig.COMMON.taczShootWeight.get());
        return new double[]{SoundAttractConfig.COMMON.taczShootRange.get(), SoundAttractConfig.COMMON.taczShootWeight.get()};
    }
}