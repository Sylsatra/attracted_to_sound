package com.example.soundattract.integration;

// ============================================================================
// TACZ INTEGRATION - COMMENTED OUT FOR 1.21.11 (TACZ not available for NeoForge 1.21.11)
// ============================================================================

/*
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.SoundTracker;
import com.example.soundattract.StealthDetectionEvents; 
import com.example.soundattract.config.SoundAttractConfig;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
*/

public class TaczIntegrationServerEvents {

    /*
    private static final String TACZ_SHOOT_SOUND_ID = "tacz:gun_shoot";
    private static final String TACZ_RELOAD_SOUND_ID = "tacz:gun_reload";

    @SubscribeEvent
    public static void onGunShoot(GunShootEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }

        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof ServerPlayer player)) {
            return;
        }
        
        IGun iGun = IGun.getIGunOrNull(event.getGunItemStack());
        if (iGun != null) {
            double flashRange = SoundAttractConfig.COMMON.gunshotBaseDetectionRange.get();
            double reduction = 0.0;
            Identifier muzzleId = iGun.getAttachmentId(event.getGunItemStack(), AttachmentType.MUZZLE);
            if (muzzleId != null) {
                reduction = SoundAttractConfig.TACZ_MUZZLE_FLASH_REDUCTION_CACHE.getOrDefault(muzzleId.toString(), 0.0);
            }
            double finalDetectionRange = Math.max(0, flashRange - reduction);
            StealthDetectionEvents.recordPlayerGunshot(player, finalDetectionRange);

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                    "[TaczIntegration] Gunshot Flash: BaseRange={}, Muzzle='{}', Reduction={}, FinalRange={}",
                    String.format("%.2f", flashRange),
                    muzzleId != null ? muzzleId.toString() : "None",
                    String.format("%.2f", reduction),
                    String.format("%.2f", finalDetectionRange)
                );
            }
        }

        double[] rangeAndWeight = calculateShootRangeWeight(event.getGunItemStack());
        double range = rangeAndWeight[0];
        double weight = rangeAndWeight[1];
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

        SoundTracker.addSound(
            null, 
            player.blockPosition(),
            player.level().dimension().location().toString(),
            range,
            weight,
            lifetime,
            TACZ_SHOOT_SOUND_ID 
        );
    }

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }
        
        LivingEntity reloader = event.getEntity();
        if (!(reloader instanceof ServerPlayer player)) {
            return;
        }
        
        double[] rangeAndWeight = calculateReloadRangeWeight(event.getGunItemStack());
        double range = rangeAndWeight[0];
        double weight = rangeAndWeight[1];
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

        SoundTracker.addSound(
            null, 
            player.blockPosition(),
            player.level().dimension().location().toString(),
            range,
            weight,
            lifetime,
            TACZ_RELOAD_SOUND_ID 
        );
    }

    private static double[] calculateShootRangeWeight(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        double finalRange = SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;

        if (iGun != null) {
            Identifier gunId = iGun.getGunId(gunStack);
            Identifier attId = iGun.getAttachmentId(gunStack, AttachmentType.MUZZLE);
            var gunStats = SoundAttractConfig.TACZ_GUN_SHOOT_DB_CACHE.get(gunId.toString());
            double gunRange = (gunStats != null) ? gunStats.getLeft() : SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE;
            double reduction = 0.0;
            if (attId != null) {
                var attachmentStats = SoundAttractConfig.TACZ_ATTACHMENT_REDUCTION_DB_CACHE.get(attId.toString());
                if (attachmentStats != null) {
                    reduction = attachmentStats.getRight();
                }
            }
            finalRange = Math.max(0, gunRange - reduction);
        }
        double weight = finalRange / 10.0;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] Shot: range={}, weight={}", String.format("%.2f", finalRange), String.format("%.2f", weight));
        }
        return new double[]{finalRange, weight};
    }

    private static double[] calculateReloadRangeWeight(ItemStack gunStack) {
        double range = SoundAttractConfig.TACZ_RELOAD_RANGE_CACHE;
        double weight = SoundAttractConfig.TACZ_RELOAD_WEIGHT_CACHE;
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info("[TaczIntegration] Reload: range={}, weight={}", String.format("%.2f", range), String.format("%.2f", weight));
        }
        return new double[]{range, weight};
    }
    */
}
